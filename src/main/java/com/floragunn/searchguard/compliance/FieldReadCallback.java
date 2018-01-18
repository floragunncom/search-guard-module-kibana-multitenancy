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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.FieldInfo;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.mapper.Uid;

import com.floragunn.searchguard.auditlog.AuditLog;
import com.github.wnameless.json.flattener.JsonFlattener;

//TODO  We need to deal with caching!!
//Currently we disable caching (and realtime requests) when FLS or DLS is applied
//Check if we can hook in into the caches

//stored fields are already done here

public final class FieldReadCallback {

    private static final Logger log = LogManager.getLogger(FieldReadCallback.class);
    private final ThreadContext threadContext;
    private final ClusterService clusterService;
    private final Index index;
    private final ComplianceConfig complianceConfig;
    private final AuditLog auditLog;
    private Doc doc;

    public FieldReadCallback(final ThreadContext threadContext, final IndexService indexService,
            final ClusterService clusterService, final ComplianceConfig complianceConfig, final AuditLog auditLog) {
        super();
        this.threadContext = Objects.requireNonNull(threadContext);
        this.clusterService = Objects.requireNonNull(clusterService);
        this.index = Objects.requireNonNull(indexService).index();
        this.complianceConfig = complianceConfig;
        this.auditLog = auditLog;
    }

    private boolean recordField(final String fieldName) {
        return complianceConfig.enabledForField(index.getName(), fieldName);
    }

    public void binaryFieldRead(final FieldInfo fieldInfo, final byte[] fieldValue) {
        try {
            if(!recordField(fieldInfo.name) && !fieldInfo.name.equals("_source") && !fieldInfo.name.equals("_id")) {
                return;
            }

            if(fieldInfo.name.equals("_source")) {
                Map<String, Object> filteredSource = new JsonFlattener(new String(fieldValue, StandardCharsets.UTF_8)).flattenAsMap();
                for(String k: filteredSource.keySet()) {
                    if(!recordField(k)) {
                        return;
                    }
                    fieldRead0(k, filteredSource.get(k));
                }
            } else if (fieldInfo.name.equals("_id")) {
                fieldRead0(fieldInfo.name, Uid.decodeId(fieldValue));
            }  else {
                fieldRead0(fieldInfo.name, new String(fieldValue, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.error("Unexpected error reading binary field '{}' in index '{}'", fieldInfo.name, index.getName());
        }
    }

    public void stringFieldRead(final FieldInfo fieldInfo, final byte[] fieldValue) {
        try {
            if(!recordField(fieldInfo.name)) {
                return;
            }
            fieldRead0(fieldInfo.name, new String(fieldValue, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Unexpected error reading string field '{}' in index '{}'", fieldInfo.name, index.getName());
        }
    }

    public void numericFieldRead(final FieldInfo fieldInfo, final Number fieldValue) {
        try {
            if(!recordField(fieldInfo.name)) {
                return;
            }
            fieldRead0(fieldInfo.name, fieldValue);
        } catch (Exception e) {
            log.error("Unexpected error reading numeric field '{}' in index '{}'", fieldInfo.name, index.getName());
        }
    }

    private void fieldRead0(final String fieldName, final Object fieldValue) {
        if(doc != null) {
            if(fieldName.equals("_id")) {
                doc.setId(fieldValue.toString());
            } else {
                doc.addField(new Field(fieldName, fieldValue));
            }
        } else {
            final String indexName = index.getName();
            if(fieldName.equals("_id")) {
                doc = new Doc(indexName, fieldValue.toString());
            } else {
                doc = new Doc(indexName, null);
                doc.addField(new Field(fieldName, fieldValue));
            }
        }
    }

    public void finished() {
        if(doc == null) {
            return;
        }
        try {
            Map<String, String> f = new HashMap<String, String>();
            for(Field fi: doc.fields) {
                f.put(fi.fieldName, String.valueOf(fi.fieldValue));
            }
            auditLog.logDocumentRead(doc.indexName, doc.id, f, complianceConfig);
        } catch (Exception e) {
            log.error("Unexpected error finished compliance read entry in index '{}'", index.getName());
        }
    }

    private class Doc {
        final String indexName;
        String id;
        final List<Field> fields = new ArrayList<Field>();

        public Doc(String indexName, String id) {
            super();
            this.indexName = indexName;
            this.id = id;
        }

        public void addField(Field f) {
            fields.add(f);
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Doc [indexName=" + indexName + ", id=" + id + ", fields=" + fields + "]";
        }
    }

    private class Field {
        final String fieldName;
        final Object fieldValue;
        public Field(String fieldName, Object fieldValue) {
            super();
            this.fieldName = fieldName;
            this.fieldValue = fieldValue;
        }
        @Override
        public String toString() {
            return "Field [fieldName=" + fieldName + ", fieldValue=" + fieldValue + "]";
        }
    }
}
