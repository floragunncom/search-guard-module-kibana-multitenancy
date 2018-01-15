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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.FieldInfo;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.mapper.Uid;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.support.Base64Helper;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.user.User;
import com.github.wnameless.json.flattener.JsonFlattener;

public class FieldReadCallback {

    private final ThreadContext threadContext;
    private final ClusterService clusterService;
    private final Index index;
    private final ComplianceConfig complianceConfig;
    private final Map<String, Set<String>> fields = new HashMap<>();
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

      //Objects.requireNonNull(indexService).cache().

        //TODO cache types in ES/lucene


       /*

        searchguard.compliance.pii_fields:
          -
            field_patterns:
              - a*
              - b*
            index_pattern: myindex*
          -
            field_patterns:
              - xxx
              - b*
            index_pattern: xxx*



        final List<String> piiFields = this.settings.getAsList("searchguard.compliance.pii_fields", Collections.emptyList());
        for(String pii: piiFields) {
            String[] split = pii.split(",");
            if(split.length == 1) {
                fields.put(split[0], Collections.emptySet());
            } else {
                fields.put(split[0], Collections.emptySet());
            }
        }*/






    }

    private boolean recordField(final String fieldName) {
        return complianceConfig.enabledForField(index.getName(), fieldName);
    }

    //TODO  We need to deal with caching!!
    //Currently we disable caching (and realtime requests) when FLS or DLS is apply
    //For fieldhistory this may not be appropriate because of performance reasons
    //So we must check this
    public void binaryFieldRead(final FieldInfo fieldInfo, final byte[] fieldValue) {
        try {
            if(!recordField(fieldInfo.name)) {
                return;
            }

            if(fieldInfo.name.equals("_source")) {
                //final BytesReference bytesRef = new BytesArray((byte[])fieldValue);
                //final Tuple<XContentType, Map<String, Object>> bytesRefTuple = XContentHelper.convertToMap(bytesRef, false, XContentType.JSON);
                //Map<String, Object> filteredSource = bytesRefTuple.v2();
                Map<String, Object> filteredSource = new JsonFlattener(new String(fieldValue, StandardCharsets.UTF_8)).flattenAsMap();
                for(String k: filteredSource.keySet()) {
                    //System.out.println(k+"="+filteredSource.get(k)+" -- "+filteredSource.get(k).getClass());
                    if(!recordField(k)) {
                        return;
                    }
                    fieldRead0(k, filteredSource.get(k));
                }

                //fieldRead0(fieldInfo, JsonFlattener.flattenAsMap(new String(fieldValue, StandardCharsets.UTF_8)), key);
            } else if (fieldInfo.name.equals("_id")) {
                fieldRead0(fieldInfo.name, Uid.decodeId(fieldValue));
            }  else {
                fieldRead0(fieldInfo.name, new String(fieldValue, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void stringFieldRead(final FieldInfo fieldInfo, final byte[] fieldValue) {
        try {
            if(!recordField(fieldInfo.name)) {
                return;
            }
            fieldRead0(fieldInfo.name, new String(fieldValue, StandardCharsets.UTF_8));
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void numericFieldRead(final FieldInfo fieldInfo, final Number fieldValue) {
        try {
            if(!recordField(fieldInfo.name)) {
                return;
            }
            fieldRead0(fieldInfo.name, fieldValue);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void fieldRead0(final String fieldName, final Object fieldValue) {

        if(doc != null) {
            if(fieldName.equals("_id")) {
                doc.setId(fieldValue.toString());
            } else {
                doc.adField(new Field(fieldName, fieldValue));
            }
        } else {
            final DiscoveryNode localNode = clusterService.localNode();
            final long utcTimestamp = new DateTime(DateTimeZone.UTC).getMillis(); //TODO maybe a lot of object will generated here, maybe just use System.currentTimeMillis()
            final String clusterName = clusterService.getClusterName().value();
            final String clusterUUID = clusterService.state().metaData().clusterUUID();
            final String nodeId = localNode.getId();
            final String nodeName = localNode.getName();
            final String nodeHostName = localNode.getHostName();
            final String nodeHostAddress = localNode.getHostAddress();
            final String indexName = index.getName();
            final String indexUUID = index.getUUID();
            final String user = getUser(); //TODO we also need here the initiating user as well as the effective user
            TransportAddress ta = getRemoteAddress();
            final String remoteAddress = ta==null?null:ta.getAddress();
            if(fieldName.equals("_id")) {
                doc = new Doc(utcTimestamp, clusterName, clusterUUID, nodeId, nodeName, nodeHostName, nodeHostAddress, indexName, indexUUID, user, remoteAddress, fieldValue.toString());
            } else {
                doc = new Doc(utcTimestamp, clusterName, clusterUUID, nodeId, nodeName, nodeHostName, nodeHostAddress, indexName, indexUUID, user, remoteAddress, null);
                doc.adField(new Field(fieldName, fieldValue));
            }
        }
    }


    private TransportAddress getRemoteAddress() {
        TransportAddress address = threadContext.getTransient(ConfigConstants.SG_REMOTE_ADDRESS);
        if(address == null && threadContext.getHeader(ConfigConstants.SG_REMOTE_ADDRESS_HEADER) != null) {
            address = new TransportAddress((InetSocketAddress) Base64Helper.deserializeObject(threadContext.getHeader(ConfigConstants.SG_REMOTE_ADDRESS_HEADER)));
        }
        return address;
    }

    private String getUser() {
        User user = threadContext.getTransient(ConfigConstants.SG_USER);
        if(user == null && threadContext.getHeader(ConfigConstants.SG_USER_HEADER) != null) {
            user = (User) Base64Helper.deserializeObject(threadContext.getHeader(ConfigConstants.SG_USER_HEADER));
        }
        return user==null?null:user.getName();
    }

    public void finished() {
        //System.out.println("READ FIELDHISTORY :"+doc.toString());
        Map<String, String> f = new HashMap<String, String>();
        for(Field fi: doc.fields) {
            f.put(fi.fieldName, String.valueOf(fi.fieldValue));
        }
        auditLog.logDocumentRead(doc.indexName, doc.id, f);
    }

    private class Doc {
        final long utcTimestamp;
        final String clusterName;
        final String clusterUUID;
        final String nodeId;
        final String nodeName;
        final String nodeHostName;
        final String nodeHostAddress;
        final String indexName;
        final String indexUUID;
        final String user;
        final String remoteAddress;
        String id;
        final List<Field> fields = new ArrayList<Field>();

        public Doc(long utcTimestamp, String clusterName, String clusterUUID, String nodeId, String nodeName,
                String nodeHostName, String nodeHostAddress, String indexName, String indexUUID, String user, String remoteAddress,
                String id) {
            super();
            this.utcTimestamp = utcTimestamp;
            this.clusterName = clusterName;
            this.clusterUUID = clusterUUID;
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.nodeHostName = nodeHostName;
            this.nodeHostAddress = nodeHostAddress;
            this.indexName = indexName;
            this.indexUUID = indexUUID;
            this.user = user;
            this.remoteAddress = remoteAddress;
            this.id = id;
        }

        public void adField(Field f) {
            fields.add(f);
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Doc [utcTimestamp=" + utcTimestamp + ", clusterName=" + clusterName + ", clusterUUID="
                    + clusterUUID + ", nodeId=" + nodeId + ", nodeName=" + nodeName + ", nodeHostName=" + nodeHostName
                    + ", nodeHostAddress=" + nodeHostAddress + ", indexName=" + indexName + ", indexUUID=" + indexUUID + ", user=" + user
                    + ", remoteAddress=" + remoteAddress + ", id=" + id + ", fields=" + fields + "]";
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
