/*
 * Copyright 2017 by floragunn GmbH - All rights reserved
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * This software is free of charge for non-commercial and academic use.
 * For commercial use in a production environment you have to obtain a license
 * from https://floragunn.com
 * 
 */

package com.floragunn.searchguard.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.CompositeIndicesRequest;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.IndicesRequest.Replaceable;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetRequest.Item;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.support.replication.ReplicationRequest;
import org.elasticsearch.action.support.single.shard.SingleShardRequest;
import org.elasticsearch.action.termvectors.MultiTermVectorsRequest;
import org.elasticsearch.action.termvectors.TermVectorsRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.util.concurrent.ThreadContext.StoredContext;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.configuration.PrivilegesEvaluator.IndexType;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.user.User;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class PrivilegesInterceptorImpl extends PrivilegesInterceptor {

    private final static IndicesOptions DEFAULT_INDICES_OPTIONS = IndicesOptions.lenientExpandOpen();
    private static final String USER_TENANT = "__user__";

    private static final String EMPTY_STRING = "";

    protected final Logger log = LogManager.getLogger(this.getClass());
   
    protected final Cache<String, String> createdIndicesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS).build();
    
    private Set<String> upgradesChecked = new HashSet<String>();

    static {
        printLicenseInfo();
    }
    
    //@Inject //omit for >= 5.2
    public PrivilegesInterceptorImpl(IndexNameExpressionResolver resolver, ClusterService clusterService, Client client,
            ThreadPool threadPool) {
        super(resolver, clusterService, client, threadPool);
    }
    
    private void createKibanaUserIndex(final String originalIndexName, final String newIndexName, final String action) {
        
        if (upgradesChecked.contains(newIndexName) && createdIndicesCache.getIfPresent(newIndexName) != null) {
            if (log.isTraceEnabled()) {
                log.trace("All cached, no need to create or update {} (upgradesChecked={},isCached={})",newIndexName, upgradesChecked.contains(newIndexName),createdIndicesCache.getIfPresent(newIndexName) != null);
            }
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("create new kibana index {} (from original {}) if not exists already", newIndexName, originalIndexName);
        }

        final CountDownLatch latch = new CountDownLatch(1);

        final ThreadContext threadContext = getThreadContext();

        try (StoredContext ctx = threadContext.stashContext()) {
            threadContext.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true"); // header
                                                                                     // needed
                                                                                     // here
            
            client.admin().indices().prepareExists(newIndexName).execute(new ActionListener<IndicesExistsResponse>() {

                @Override
                public void onResponse(IndicesExistsResponse response) {
                    if (!response.isExists()) {

                        if (log.isDebugEnabled()) {
                            log.debug("index {} not exists, create it", newIndexName); 
                        }

                        client.admin().indices().prepareCreate(newIndexName).setSettings("number_of_shards", 1)
                                .addMapping("config", "buildNum", "type=string,index=not_analyzed")
                                .execute(new ActionListener<CreateIndexResponse>() {

                                    @Override
                                    public void onResponse(CreateIndexResponse response) {
                                        boolean ack = response.isAcknowledged();

                                        if (ack) {

                                            if (log.isDebugEnabled()) {
                                                log.debug("index {} created, will now copy data", newIndexName);
                                            }
//
                                            client.prepareSearch(originalIndexName).setTypes("config").setSize(100)
                                                    .execute(new ActionListener<SearchResponse>() {

                                                        @Override
                                                        public void onResponse(SearchResponse response) {

                                                            final SearchHit[] hits = response.getHits().getHits();
                                                            
                                                            if (hits != null && hits.length > 0) {
                                                                final CountDownLatch ilatch = new CountDownLatch(hits.length);
                                                                for (int i = 0; i < hits.length; i++) {
                                                                    final String id = hits[i].getId();
                                                                    
                                                                    if (log.isTraceEnabled()) {
                                                                        log.trace("copy config for version {} -> {}", id, hits[i].getSource());
                                                                    }

                                                                    client.prepareIndex(newIndexName, "config").setId(id).setSource(hits[i].getSource())
                                                                            .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                                                                            .execute(new ActionListener<IndexResponse>() {

                                                                                @Override
                                                                                public void onResponse(IndexResponse response) {
                                                                                    
                                                                                    if (log.isTraceEnabled()) {
                                                                                        log.trace("Set needUpgradeCheck now to false for {} because we just created it, so no upgrades until restart", newIndexName);
                                                                                    }
                                                                                    upgradesChecked.add(newIndexName);
                                                                                    
                                                                                    createdIndicesCache.put(newIndexName, newIndexName);
                                                                                    ilatch.countDown();
                                                                                }

                                                                                @Override
                                                                                public void onFailure(Exception e) {
                                                                                    log.error("Failed to index (2) "+e, e);
                                                                                    ilatch.countDown();
                                                                                }
                                                                            });
                                                                }
                                                                
                                                                try {
                                                                    if (!ilatch.await(100, TimeUnit.SECONDS)) {
                                                                        log.error("Timeout creating index (1)");
                                                                    }
                                                                } catch (InterruptedException e1) {
                                                                    log.error("Interrupted", e1);
                                                                }
                                                                
                                                                latch.countDown();

                                                            } else {
                                                                log.error("no search hits for config");
                                                                latch.countDown();
                                                            }
                                                        }

                                                        @Override
                                                        public void onFailure(Exception e) {
                                                            log.error("Failed to search config (1) "+e, e);
                                                            latch.countDown();
                                                        }
                                                    });
                                        } else {
                                            log.error("Failed to create index (not acknowledged");
                                            latch.countDown();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        log.error("Failed to create index "+ e, e);
                                        latch.countDown();
                                    }
                                });

                    } else if (!upgradesChecked.contains(newIndexName)) {
                        
                        createdIndicesCache.put(newIndexName, newIndexName);

                        if (log.isTraceEnabled()) {
                            log.trace("Index {} already exists, will update it because upgrade check needed", newIndexName);
                        }
                        
                        client.prepareSearch(newIndexName).setTypes("config").setSize(1)
                        .execute(new ActionListener<SearchResponse>() {
                            
                            @Override
                            public void onResponse(final SearchResponse responseFromNewIndex) {
                                client.prepareSearch(originalIndexName).setTypes("config").setSize(1000)
                                .execute(new ActionListener<SearchResponse>() {

                                    @Override
                                    public void onResponse(SearchResponse response) {

                                        final SearchHit[] hits = response.getHits().getHits();
                                        final CountDownLatch ilatch = new CountDownLatch(hits.length);
                                        
                                        for (int i = 0; i < hits.length; i++) {
                                            final SearchHit searchHit = hits[i];
                                            
                                            if(log.isTraceEnabled()) {
                                                log.trace(i+". action "+action);
                                                log.trace(i+". upsert config with _id={}", searchHit.getId()); 
                                                log.trace(i+". orig _source={}", searchHit.getSourceAsString()); 
                                                log.trace(i+". other _source={}", responseFromNewIndex.getHits().getAt(0).getSource()); 
                                                log.trace(i+". upsert config with _source={}", updateOrAddDefaultIndexPattern(searchHit.getSource(), responseFromNewIndex.getHits().getAt(0).getSource()));                                        
                                            }
                                            
                                            if(action.contains("indices:data/write") 
                                                    || action.contains("indices:admin/mapping/put")
                                                    || action.contains("indices:admin/create")) {
                                                
                                                if(log.isTraceEnabled()) {
                                                    log.trace("skipped because of action="+action);
                                                }
                                                ilatch.countDown();
                                                continue;
                                            }

                                            
                                            client.prepareIndex(newIndexName, "config")
                                            .setId(searchHit.getId())
                                            .setSource(updateOrAddDefaultIndexPattern(searchHit.getSource(), responseFromNewIndex.getHits().getAt(0).getSource()))
                                            .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                                            .execute(new ActionListener<IndexResponse>() {

                                                @Override
                                                public void onResponse(IndexResponse response) {
                                                    
                                                    if (log.isTraceEnabled()) {
                                                        log.trace("Set needUpgradeCheck now to false for {} because we upgraded, so no upgrades until restart", newIndexName);
                                                    }
                                                    upgradesChecked.add(newIndexName);
                                                    
                                                    ilatch.countDown();
                                                }

                                                @Override
                                                public void onFailure(Exception e) {
                                                    log.error("Failed to index/update "+e, e);
                                                    ilatch.countDown();
                                                }
                                            });
                                        }
                                        
                                        try {
                                            if (!ilatch.await(100, TimeUnit.SECONDS)) {
                                                log.error("Timeout creating index (2)");
                                            }
                                        } catch (InterruptedException e1) {
                                            log.error("Interrupted", e1);
                                        }
                                        
                                        latch.countDown();
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        log.error("Failed to search config " + e, e);
                                        latch.countDown();
                                    }
                                });
                            }
                            
                            @Override
                            public void onFailure(Exception e) {
                                log.error("Failed to query new index");
                                latch.countDown();
                            }
                        });
                    } else {
                        
                        createdIndicesCache.put(newIndexName, newIndexName);
                        
                        if (log.isTraceEnabled()) {
                            log.trace("No update needed for {}", newIndexName);
                        }
                        
                        latch.countDown();
                        
                    }//end-else
                    
                }//end on response for exists newIndexName

                @Override
                public void onFailure(Exception e) { //failure for check if newIndexName exists
                    log.error("Failed to check if index {} exists due to "+e,newIndexName);
                    log.error(e);
                    latch.countDown();
                }

            });
        }

        try {
            if (!latch.await(100, TimeUnit.SECONDS)) {
                log.error("Timeout creating index");
                return;
            }
        } catch (InterruptedException e1) {
            log.error("Interrupted", e1);
            return;
        }

        return;
    }
    
    private boolean isTenantAllowed(final ActionRequest request, final String action, final User user, final Map<String, Boolean> tenants, final String requestedTenant) {
        
        if (!tenants.keySet().contains(requestedTenant)) {
            log.warn("Tenant {} is not allowed for user {}", requestedTenant, user.getName());
            return false;
        } else {
            // allowed, check read-write permissions
            boolean isBuildNumRequest = false;
            
            if(log.isDebugEnabled()) {
                log.debug("request "+request.getClass());
            }
            
            if (request instanceof IndexRequest) {

                final IndexRequest ir = ((IndexRequest) request);

                if (log.isDebugEnabled()) {
                    log.debug("type " + ir.type());
                    log.debug("id " + ir.id());
                    log.debug("source " + (ir.source() == null ? null : ir.source().utf8ToString()));
                }

                /*if (ir.type().equals("config") 
                        && Character.isDigit(ir.id().charAt(0)) 
                        && ir.source().toUtf8().contains("buildNum")) {
                    isBuildNumRequest = true;
                }*/
            }
            
            if (request instanceof UpdateRequest) {

                final UpdateRequest ir = ((UpdateRequest) request);

                if (log.isDebugEnabled()) {
                    log.debug("type " + ir.type());
                    log.debug("id " + ir.id());
                    log.debug("source " + (ir.doc() == null ? null : ir.doc().source()==null?null:ir.doc().source().utf8ToString()));
                }
            }
            
            if (!isBuildNumRequest && tenants.get(requestedTenant) == Boolean.FALSE 
                    && action.startsWith("indices:data/write")) {
                log.warn("Tenant {} is not allowed to write (user: {})", requestedTenant, user.getName());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * return Boolean.TRUE to prematurely deny request
     * return Boolean.FALSE to prematurely allow request
     * return null to go through original eval flow
     *
     */
    @Override
    public Boolean replaceKibanaIndex(final ActionRequest request, final String action, final User user, final Settings config, final Set<String> requestedResolvedIndices, final Map<String, Boolean> tenants) { 
        
        final boolean enabled = config.getAsBoolean("searchguard.dynamic.kibana.multitenancy_enabled", true);
        
        if(!enabled) {
            return null;
        }
        
        //next two lines needs to be retrieved from configuration
        final String kibanaserverUsername = config.get("searchguard.dynamic.kibana.server_username","kibanaserver");
        final String kibanaIndexName = config.get("searchguard.dynamic.kibana.index",".kibana");

        String requestedTenant = user.getRequestedTenant();
        
        if(log.isDebugEnabled()) {
            log.debug("raw requestedTenant: '"+requestedTenant+"'");
        }
        
        if(requestedTenant == null || requestedTenant.length() == 0) {
            if(log.isTraceEnabled()) {
                log.trace("No tenant, will resolve to "+kibanaIndexName);    
            }

            return null;
        }
        
        if(USER_TENANT.equals(requestedTenant)) {
            requestedTenant = user.getName();
        }
        
        if (!user.getName().equals(kibanaserverUsername) 
                && requestedResolvedIndices.size() == 1
                && requestedResolvedIndices.contains(toUserIndexName(kibanaIndexName, requestedTenant))) {
            
            if(isTenantAllowed(request, action, user, tenants, requestedTenant)) {
                return Boolean.FALSE;
            }
            
        }
        
        //intercept when requests are not made by the kibana server and if the kibana index (.kibana) is the only index involved
        if (!user.getName().equals(kibanaserverUsername) 
                && requestedResolvedIndices.contains(kibanaIndexName)
                && requestedResolvedIndices.size() == 1) {
            
            if(log.isDebugEnabled()) {
                log.debug("requestedTenant: "+requestedTenant);
                log.debug("is user tenant: "+requestedTenant.equals(user.getName()));
            }
                        
            if(!isTenantAllowed(request, action, user, tenants, requestedTenant)) {
                return Boolean.TRUE;
            }

            //TODO handle user tenant in that way that this tenant cannot be specified as regular tenant
            //to avoid security issue
            
            replaceIndex(request, kibanaIndexName, toUserIndexName(kibanaIndexName, requestedTenant), action);
            return Boolean.FALSE;

        } else if (!user.getName().equals(kibanaserverUsername)) {

            if (log.isTraceEnabled()) {
                log.trace("not a request to only the .kibana index");
                log.trace(user.getName() + "/" + kibanaserverUsername);
                log.trace(requestedResolvedIndices + " does not contain only " + kibanaIndexName);
            }

        }
        
        return null;
    }
    
    
    private void replaceIndex(final ActionRequest request, final String oldIndexName, final String newIndexName, final String action) {
        boolean kibOk = false;
                
        if(log.isDebugEnabled()) {
            log.debug("{} index will be replaced with {} in this {} request", oldIndexName, newIndexName, request.getClass().getName());
        }
        
        if(request instanceof GetFieldMappingsIndexRequest
                || request instanceof GetFieldMappingsRequest) {
            return;
        }

        createKibanaUserIndex(oldIndexName, newIndexName, action);
        
        //handle msearch and mget
        //in case of GET change the .kibana index to the userskibanaindex
        //in case of Search add the userskibanaindex
        if (request instanceof CompositeIndicesRequest && !(request instanceof DocWriteRequest)) {

            if (request instanceof BulkRequest) {

                for (DocWriteRequest ar : ((BulkRequest) request).requests()) {
                    if (ar instanceof Replaceable) {
                        Replaceable replaceableRequest = (Replaceable) ar;
                        //log.debug("rplc  "+Arrays.toString(replaceableRequest.indices()) + " with "+new String[]{newIndexName}+" for "+request.getClass().getName());
                        replaceableRequest.indices(new String[]{newIndexName});
                        //List<String> indices = new ArrayList<String>(Arrays.asList(rep.indices()));
                        //if (indices.indexOf(oldIndexName) > -1) {
                        //    indices.add(newIndexName);
                        //    rep.indices(indices.toArray(new String[0]));
                        //    kibOk = true;
                        //}
                        kibOk = true;
                    }
                }

            } else if (request instanceof MultiGetRequest) {

                for (Item item : ((MultiGetRequest) request).getItems()) {
                    item.index(newIndexName);
                    kibOk = true;
                }

            } else if (request instanceof MultiSearchRequest) {

                for (ActionRequest ar : ((MultiSearchRequest) request).requests()) {
                    if (ar instanceof Replaceable) {
                        Replaceable replaceableRequest = (Replaceable) ar;
                        //log.debug("rplc  "+Arrays.toString(replaceableRequest.indices()) + " with "+new String[]{newIndexName}+" for "+request.getClass().getName());
                        replaceableRequest.indices(new String[]{newIndexName});
                        
                        //List<String> indices = new ArrayList<String>(Arrays.asList(rep.indices()));
                        //if (indices.indexOf(oldIndexName) > -1) {
                        //    indices.add(newIndexName);
                        //    rep.indices(indices.toArray(new String[0]));
                        //    kibOk = true;
                        //}
                        kibOk = true;
                    }
                }

            } else if (request instanceof MultiTermVectorsRequest) {

                for (ActionRequest ar : (Iterable<TermVectorsRequest>) () -> ((MultiTermVectorsRequest) request).iterator()) {
                    if (ar instanceof Replaceable) {
                        Replaceable replaceableRequest = (Replaceable) ar;
                        //log.debug("rplc  "+Arrays.toString(replaceableRequest.indices()) + " with "+new String[]{newIndexName}+" for "+request.getClass().getName());
                        replaceableRequest.indices(new String[]{newIndexName});
                        
                        //List<String> indices = new ArrayList<String>(Arrays.asList(rep.indices()));
                        //if (indices.indexOf(oldIndexName) > -1) {
                        //    indices.add(newIndexName);
                        //    rep.indices(indices.toArray(new String[0]));
                        //    kibOk = true;
                        //}
                        kibOk = true;
                    }
                }

            } else {
                log.warn("Can not handle composite request of type '" + request + "' here (replaceIndex())");
            }
        }

        //handle update request
        //change the .kibana index to the userskibanaindex
        if (request instanceof UpdateRequest) {
            ((UpdateRequest) request).index(newIndexName);
            kibOk = true;
        }
        
        if (request instanceof SingleShardRequest) {
            ((SingleShardRequest) request).index(newIndexName);
            kibOk = true;
        }

        //handle index request
        //change the .kibana index to the userskibanaindex
        if (request instanceof IndexRequest) {
            ((IndexRequest) request).index(newIndexName);
            kibOk = true;
        }

        //handle search and other replaceable request
        //add the userskibanaindex
        if (request instanceof Replaceable) {
            Replaceable replaceableRequest = (Replaceable) request;
            //log.debug("rplc  "+Arrays.toString(replaceableRequest.indices()) + " with "+new String[]{newIndexName}+" for "+request.getClass().getName());
            replaceableRequest.indices(new String[]{newIndexName});
            //List<String> indices = new ArrayList<String>(Arrays.asList(replaceableRequest.indices()));
            //if (indices.indexOf(oldIndexName) > -1) {
            //    indices.add(newIndexName);
            //    replaceableRequest.indices(indices.toArray(new String[0]));
            //} 
            kibOk = true;
        }

        //seems we must do nothing special here
        if (request instanceof RefreshRequest) {
            //System.out.println("refresh "+ Arrays.toString(((RefreshRequest) request).indices()));
            kibOk = true;
        }

        //seems we must do nothing special here
        if (request instanceof GetFieldMappingsRequest) {
            //System.out.println("GetFieldMappingsRequest "+ Arrays.toString(((GetFieldMappingsRequest) request).indices()));
            kibOk = true;
        }

        //seems we must do nothing special here
        if (request instanceof GetFieldMappingsIndexRequest) {
            //System.out.println("GetFieldMappingsIndexRequest "+ Arrays.toString(((GetFieldMappingsIndexRequest) request).indices()));
            kibOk = true;
        }         
        
        //refresh request (and delete request)
        if (request instanceof ReplicationRequest) {
            ((ReplicationRequest) request).index(newIndexName);
             kibOk = true;
        }

        if(!kibOk) {
            log.warn("Unhandled kibana related request {}", request.getClass());
        }
    }

    @Override
    public boolean replaceAllowedIndices(final ActionRequest request, final String action, final User user, final Settings config,
            final Map<String, Set<PrivilegesEvaluator.IndexType>> leftOvers) {

        final boolean enabled = config.getAsBoolean("searchguard.dynamic.kibana.do_not_fail_on_forbidden", false);

        if (!enabled || leftOvers.size() == 0) {
            return false;
        }

        if (!action.startsWith("indices:data/read/") 
                && !action.startsWith("indices:admin/mappings/fields/get")) {
            return false;
        }
        
        Entry<String, Set<IndexType>> min = null;
        
        //find role with smallest number of leftovers
        //what when two ore more als equal in size??
        
        for(Entry<String, Set<IndexType>> entry: leftOvers.entrySet()) {
            if(min == null || entry.getValue().size() < min.getValue().size()) {
                min = entry;
            }
        }
        
        if(min == null) {
            log.warn("No valid leftover found");
            return false;
        }

        final Set<String> leftOversIndex = new HashSet<String>();

        for (IndexType indexType: min.getValue()) {
            leftOversIndex.add(indexType.getIndex());
        }

        if(log.isDebugEnabled()) {
            log.debug("handle {}/{} for leftovers {}", action, request.getClass(), leftOversIndex);
        }
        
        if (request instanceof CompositeIndicesRequest && !(request instanceof DocWriteRequest)) {
            
            if(request instanceof BulkRequest) {

                for(DocWriteRequest ar: ((BulkRequest) request).requests()) {
                    final boolean ok = applyIndexReduce0(ar, action, leftOversIndex);
                    if (!ok) {
                        return false;
                    }
                }
                
            } else if(request instanceof MultiGetRequest) {
                
                for(Item item: ((MultiGetRequest) request).getItems()) {
                    final boolean ok = applyIndexReduce0(item, action, leftOversIndex);
                    if (!ok) {
                        return false;
                    }
                }
                
            } else if(request instanceof MultiSearchRequest) {
                
                for(ActionRequest ar: ((MultiSearchRequest) request).requests()) {
                    final boolean ok = applyIndexReduce0(ar, action, leftOversIndex);
                    if (!ok) {
                        return false;
                    }
                }
                
            } else if(request instanceof MultiTermVectorsRequest) {
                
                for(ActionRequest ar: (Iterable<TermVectorsRequest>) () -> ((MultiTermVectorsRequest) request).iterator()) {
                    final boolean ok = applyIndexReduce0(ar, action, leftOversIndex);
                    if (!ok) {
                        return false;
                    }
                }
            } else {
                log.warn("Can not handle composite request of type '"+request+"' here (replaceAllowedIndices())");
            }

            return true;

        } else {
            return applyIndexReduce0(request, action, leftOversIndex);
        }
    }

    private boolean applyIndexReduce0(final Object request, final String action, final Set<String> leftOversIndex) {

        if (request instanceof Replaceable) {
            final Replaceable ir = (Replaceable) request;
            
            if(log.isDebugEnabled()) {
                log.debug("handle Replaceable, indices: {}", Arrays.toString(ir.indices()));
            }
            
            final String[] resolved = resolve(ir.indices(), leftOversIndex);
            
            if(resolved == null) {
                return false;
            }
            
            ir.indices(resolved);
        } else if (request instanceof SingleShardRequest) {
            final SingleShardRequest<?> gr = (SingleShardRequest<?>) request;
            final String[] indices = gr.indices();
            final String index = gr.index();

            final List<String> indicesL = new ArrayList<String>();

            if (index != null) {
                indicesL.add(index);
            }

            if (indices != null && indices.length > 0) {
                indicesL.addAll(Arrays.asList(indices));
            }

            if(log.isDebugEnabled()) {
                log.debug("handle SingleShardRequest, indices: {}", indicesL);
            }
            
            final String[] resolved = resolve(indicesL.toArray(new String[0]), leftOversIndex);
            
            if(resolved == null) {
                return false;
            }
            
            gr.index(resolved[0]);
            
            //if (r.length == 0) {
            //    gr.index(EMPTY_STRING);
            //}

        } else if (request instanceof MultiGetRequest.Item) {
            final MultiGetRequest.Item i = (MultiGetRequest.Item) request;
            
            if(log.isDebugEnabled()) {
                log.debug("handle MultiGetRequest.Item, indices: {}", Arrays.toString(i.indices()));
            }
            
            final String[] resolved = resolve(i.indices(), leftOversIndex);
            
            if(resolved == null) {
                return false;
            }
            
            i.index(resolved[0]);
            
        } else {
            log.error(request.getClass() + " not supported");
            return false;
        }

        return true;
    }
    
    private String[] resolve(final String[] unresolved, final Set<String> leftOversIndex) {

        if (leftOversIndex.contains("*") || leftOversIndex.contains("_all")) {
            
            if(log.isDebugEnabled()) {
                log.debug("resolved {} with {} to [''] because of * leftovers", Arrays.toString(unresolved), leftOversIndex);
            }
            
            return null;
        }

        final String[] concreteIndices = resolver.concreteIndexNames(clusterService.state(), DEFAULT_INDICES_OPTIONS, unresolved);
        final Set<String> survivors = new HashSet<String>(Arrays.asList(concreteIndices));
        survivors.removeAll(leftOversIndex);

        if (survivors.isEmpty()) {
            
            if(log.isDebugEnabled()) {
                log.debug("resolved {} with {} to [''] because of no survivors", Arrays.toString(unresolved), leftOversIndex);
            }
            
            return null;
        }
        
        if(log.isDebugEnabled()) {
            log.debug("resolved {} with {} - survived: {}", Arrays.toString(unresolved), leftOversIndex, survivors);
        }

        return survivors.toArray(new String[0]);
    }


    private String toUserIndexName(final String originalKibanaIndex, final String tenant) {
        
        if(tenant == null) {
            throw new ElasticsearchException("tenant must not be null here");
        }
        
        return originalKibanaIndex+"_"+tenant.hashCode()+"_"+tenant.toLowerCase().replaceAll("[^a-z0-9]+",EMPTY_STRING);
    }
    
    private static Map<String, Object> updateOrAddDefaultIndexPattern(final Map<String, Object> source, final Map<String, Object> newSource) {
        final Map<String, Object> map = new HashMap<String, Object>(source);
        map.put("defaultIndex", newSource.get("defaultIndex"));
        map.put("buildNum", newSource.get("buildNum"));
        return map;
    }
    
    private static void printLicenseInfo() {
        final StringBuilder sb = new StringBuilder();
        sb.append("******************************************************"+System.lineSeparator());
        sb.append("Search Guard Kibana Multitenancy module is not free"+System.lineSeparator());
        sb.append("software for commercial use in production."+System.lineSeparator());
        sb.append("You have to obtain a license if you "+System.lineSeparator());
        sb.append("use it in production."+System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("See https://floragunn.com/searchguard-validate-license"+System.lineSeparator());
        sb.append("In case of any doubt mail to <sales@floragunn.com>"+System.lineSeparator());
        sb.append("*****************************************************");
        
        final String licenseInfo = sb.toString();
        
        if(!Boolean.getBoolean("sg.display_lic_none")) {
            
            if(!Boolean.getBoolean("sg.display_lic_only_stdout")) {
                LogManager.getLogger(PrivilegesInterceptorImpl.class).warn(licenseInfo);
                System.err.println(licenseInfo);
            }
    
            System.out.println(licenseInfo);
        }
        
    }

}
