/*
 * Copyright 2018 by floragunn GmbH - All rights reserved
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

package com.floragunn.searchguard.compliance;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.engine.Engine.Delete;
import org.elasticsearch.index.engine.Engine.DeleteResult;
import org.elasticsearch.index.engine.Engine.Index;
import org.elasticsearch.index.engine.Engine.IndexResult;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.ParentFieldMapper;
import org.elasticsearch.index.mapper.RoutingFieldMapper;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;

import com.floragunn.searchguard.auditlog.AuditLog;

public final class ComplianceIndexingOperationListenerImpl extends ComplianceIndexingOperationListener {

    private final Logger log = LogManager.getLogger(this.getClass());
    private final ComplianceConfig complianceConfig;
    private final AuditLog auditlog;
    private volatile IndexService is;

    public ComplianceIndexingOperationListenerImpl(final ComplianceConfig complianceConfig, final AuditLog auditlog) {
        super();
        this.complianceConfig = complianceConfig;
        this.auditlog = auditlog;
    }

    @Override
    public void setIs(IndexService is) {
        if(this.is != null) {
            throw new ElasticsearchException("Index service already set");
        }
        this.is = is;
    }

    @Override
    public void postDelete(ShardId shardId, Delete delete, DeleteResult result) {
        Objects.requireNonNull(is);
        if(!result.hasFailure() && result.isFound() && delete.origin() == org.elasticsearch.index.engine.Engine.Operation.Origin.PRIMARY) {
            auditlog.logDocumentDeleted(shardId, delete, result);
        }
    }

    private final Map<String, GetResult> seq = new HashMap<>();

    @Override
    public Index preIndex(ShardId shardId, Index index) {
        Objects.requireNonNull(is);

        final IndexShard shard;

        if (index.origin() != org.elasticsearch.index.engine.Engine.Operation.Origin.PRIMARY) {
            return index;
        }

        if((shard = is.getShardOrNull(shardId.getId())) == null) {
            return index;
        }


        //System.out.println("SEQ pre "+index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id()+" for "+Thread.currentThread().getName());

        //System.out.println("orig source "+index.parsedDoc().source().utf8ToString());
        //System.out.println("orig source docs size "+index.parsedDoc().docs().size());
        //int i=0;
        //for(org.elasticsearch.index.mapper.ParseContext.Document doc: index.parsedDoc().docs()) {
        //    System.out.println((++i)+".    "+doc.getClass().getSimpleName() + " "+ doc.getFields().stream().map(a->a.name()).collect(Collectors.toList()));
        //}

        //System.out.println("added/upd document: " + shardId.getIndexName() + "#" + index.type() + "#" + index.id());
        //System.out.println("current source: " + index.source().utf8ToString());

        //TODO stored fields???

        if (shard.isReadAllowed()) {
            try {
            MapperService mapperService = shard.mapperService();
            final boolean sourceMapperEnabled = mapperService.documentMapper(index.type()).sourceMapper().enabled();

            if(!sourceMapperEnabled) {
                log.warn("_source deactivated"); //TODO single and explaining warning
            }

            /*System.out.println("source mapper enabled? "+mapperService.documentMapper(index.type()).sourceMapper().enabled());
            List<String> storedFields = new ArrayList<String>(30);
            Iterator<FieldMapper> fm = mapperService.documentMapper(index.type()).mappers().iterator();
            while(fm.hasNext()) {
                FieldMapper fma = fm.next();
                if(fma.fieldType().stored() && !fma.name().startsWith("_")) {
                    System.out.println(fma.name());
                    storedFields.add(fma.name());
                }

            }*/

            //storedFields.toArray(new String[0])


                final GetResult getResult = shard
                        .getService()
                        .get(index.type(), index.id(), new String[]{RoutingFieldMapper.NAME, ParentFieldMapper.NAME}, true,
                                index.version(), index.versionType(), FetchSourceContext.FETCH_SOURCE);

                if (getResult.isExists()) {
                     seq.put(index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id(), getResult);
                     //System.out.println("exists "+index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());
                } else {
                   // System.out.println("NOT exists "+index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());
                }
            } catch (Exception e) {
                System.out.println(e.toString());
            }

            //if (getResult.isExists()) {

                //DocumentField df = null;
                //df.getName()
                //df.getValues()

                //System.out.println(""+getResult.getFields());
                //System.out.println(""+getResult.internalSourceRef());
                //System.out.println(""+getResult.sourceAsString());



                //if(getResult.internalSourceRef() == null && getResult.getFields().size() == 0) {
                    //    return index;
                    //}

                // final Tuple<XContentType, Map<String, Object>> original = XContentHelper.convertToMap(
                //        getResult.internalSourceRef(), true);
                //final Map<String, Object> originalSourceAsMap = original.v2();

                //final Tuple<XContentType, Map<String, Object>> current = XContentHelper.convertToMap(index.source(), true);
                //final Map<String, Object> currentSourceAsMap =  current.v2();

                //final boolean noop = !XContentHelper.update(updatedSourceAsMap, originalSourceAsMap, true);

                //System.out.println("originalSourceAsMap (left) "+originalSourceAsMap);
                //System.out.println("currentSourceAsMap (right) "+currentSourceAsMap);
                //System.out.println("noop "+noop);
                //System.out.println("diff: "+Maps.difference(originalSourceAsMap, currentSourceAsMap));
                //final boolean noop = !XContentHelper.update(originalSourceAsMap, currentSourceAsMap, true);
                //System.out.println("noop: "+noop);
                //System.out.println("res "+originalSourceAsMap);
                //System.out.println("diff2: "+Maps.difference(originalSourceAsMap, updatedSourceAsMap));
                //if(!noop) {
                    //System.out.println("diff: "+Maps.difference(originalSourceAsMap, updatedSourceAsMap));
                //}

                //System.out.println("old source " + updatedSourceAsMap);
            //}
        } else {
            //System.out.println("Cannot read shard for "+index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());
        }
        //System.out.println("current source: " + index.source().utf8ToString());
        // pendig.put(shardId.getIndexName()+"#"+index.id(), index)

        // retrieve other stored fields than _source
        // localClient.prepareGet(new GetRequest(shardId.getIndexName(),
        // index.type(), index.id())).
        return index;

    }


    @Override
    public void postIndex(ShardId shardId, Index index, Exception ex) {
        seq.remove(index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());
        //System.out.println("removed "+index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id()+" due to "+ex);
    }

    @Override
    public void postIndex(ShardId shardId, Index index, IndexResult result) {
        final GetResult previousContent = seq.remove(index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());
        Objects.requireNonNull(is);

        final IndexShard shard;
        if (result.hasFailure() || index.origin() != org.elasticsearch.index.engine.Engine.Operation.Origin.PRIMARY) {
            return;
        }

        if((shard = is.getShardOrNull(shardId.getId())) == null) {
            return;
        }

        //System.out.println("SEQ post "+index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id()+" for "+Thread.currentThread().getName());
        if(previousContent == null) {
            //no previous content
            if(!result.isCreated())
            log.warn("No previous content and not created (its an update but do not find orig source) for {}", index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());

            //assert result.isCreated():"No previous content and not created";
        } else {
            if(result.isCreated())
            log.warn("Previous content and created for {}",index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());

            //assert !result.isCreated():"Previous content and created";
        }


        auditlog.logDocumentWritten(shardId, previousContent, index, result);

        //System.out.println("SEQ's ("+seq.keySet().size()+"): "+seq.keySet());

        //if(!result.hasFailure() && index.origin() == org.elasticsearch.index.engine.Engine.Operation.Origin.PRIMARY) {
            //auditLog.logDocumentWritten(shardId, index, result);
            //System.out.println(threadPool.getThreadContext().getHeaders());
            //System.out.println("added/upd document: "+shardId.getIndexName()+"#"+index.type()+"#"+index.id());
            //System.out.println("current source: "+index.source().utf8ToString());
            //System.out.println("created: "+result.isCreated());
            //System.out.println("version: "+result.getVersion());
        //}

        /*if(index.origin() == org.elasticsearch.index.engine.Engine.Operation.Origin.PRIMARY) {
            System.out.println(threadPool.getThreadContext().getHeaders());
            System.out.println(shardId.getIndexName()+"-"+index.type()+"#"+index.id());
            System.out.println(index.source().utf8ToString());
            System.out.println(result.isCreated()+"/"+result.getOperationType());

            for(org.elasticsearch.index.mapper.ParseContext.Document d: index.parsedDoc().docs()) {
                System.out.println("--doc---");
                for(IndexableField iff: d.getFields()) {
                    if(!iff.name().startsWith("_") && iff.fieldType().stored()) {
                        System.out.println("        "+iff.getClass().getSimpleName());
                        System.out.println("        "+iff.name()+"="+iff.stringValue()+"-"+iff.fieldType().stored());
                        System.out.println("        "+iff.name()+"="+iff.binaryValue());
                        System.out.println("        "+iff.name()+"="+iff.numericValue());
                        System.out.println();
                    }
                }

            }



        }*/
    }

}
