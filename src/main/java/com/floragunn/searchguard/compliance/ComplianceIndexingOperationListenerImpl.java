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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;

import com.floragunn.searchguard.auditlog.AuditLog;

public final class ComplianceIndexingOperationListenerImpl extends ComplianceIndexingOperationListener {

    private static final Logger log = LogManager.getLogger(ComplianceIndexingOperationListenerImpl.class);
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

    private static final class Context {
        private final GetResult getResult;
        private final String[] storedFields;

        public Context(GetResult getResult, String[] storedFields) {
            super();
            this.getResult = getResult;
            this.storedFields = storedFields;
        }

        public GetResult getGetResult() {
            return getResult;
        }

        public String[] getStoredFields() {
            return storedFields;
        }
    }

    private static final ThreadLocal<Context> threadContext = new ThreadLocal<Context>();

    @Override
    public void postDelete(ShardId shardId, Delete delete, DeleteResult result) {
        Objects.requireNonNull(is);
        if(!result.hasFailure() && result.isFound() && delete.origin() == org.elasticsearch.index.engine.Engine.Operation.Origin.PRIMARY) {
            auditlog.logDocumentDeleted(shardId, delete, result);
        }
    }

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

        if (shard.isReadAllowed()) {
            try {
                final MapperService mapperService = shard.mapperService();
                // final boolean sourceMapperEnabled =
                // mapperService.documentMapper(index.type()).sourceMapper().enabled();

                // if(!sourceMapperEnabled) {
                // log.warn("log_source deactivated"); //TODO single and
                // explaining warning
                // }


                final List<String> storedFields = new ArrayList<String>(30);
                final Iterator<FieldMapper> fm = mapperService.documentMapper(index.type()).mappers().iterator();
                while(fm.hasNext()) {
                    FieldMapper fma = fm.next();
                    if(fma.fieldType().stored() && !fma.name().startsWith("_")) {
                        storedFields.add(fma.name());
                    }
                }

                final String[] storedFieldsA = storedFields.toArray(new String[0]);

                final GetResult getResult = shard.getService().get(index.type(), index.id(),
                        storedFieldsA, true, index.version(), index.versionType(),
                        FetchSourceContext.DO_NOT_FETCH_SOURCE);

                if (getResult.isExists()) {
                    threadContext.set(new Context(getResult, storedFieldsA));
                } else {
                    threadContext.set(new Context(null, storedFieldsA));
                }
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Cannot retrieve original document due to {}", e.toString());
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Cannot read from shard {}", shardId);
            }
        }
        return index;

    }


    @Override
    public void postIndex(ShardId shardId, Index index, Exception ex) {
        threadContext.remove();
    }

    @Override
    public void postIndex(ShardId shardId, Index index, IndexResult result) {
        final Context context = threadContext.get();// seq.remove(index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());
        final GetResult previousContent = context==null?null:context.getGetResult();
        final String[] storedFieldsA = context==null?null:context.getStoredFields();
        threadContext.remove();
        Objects.requireNonNull(is);

        final IndexShard shard;
        if (result.hasFailure() || index.origin() != org.elasticsearch.index.engine.Engine.Operation.Origin.PRIMARY) {
            return;
        }

        if((shard = is.getShardOrNull(shardId.getId())) == null) {
            return;
        }

        final GetResult getResult = shard.getService().get(index.type(), index.id(),
                storedFieldsA, true, result.getVersion(), index.versionType(),
                FetchSourceContext.DO_NOT_FETCH_SOURCE);

        if(previousContent == null) {
            //no previous content
            if(!result.isCreated()) {
                log.warn("No previous content and not created (its an update but do not find orig source) for {}", index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());
            }
            assert result.isCreated():"No previous content and not created";
        } else {
            if(result.isCreated()) {
                log.warn("Previous content and created for {}",index.startTime()+"/"+shardId+"/"+index.type()+"/"+index.id());
            }
            assert !result.isCreated():"Previous content and created";
        }

        auditlog.logDocumentWritten(shardId, previousContent, getResult, index, result, complianceConfig);
    }

}
