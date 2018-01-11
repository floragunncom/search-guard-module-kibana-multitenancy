package com.floragunn.searchguard.compliance.fieldhistory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.index.FieldInfo;
import org.bouncycastle.util.encoders.Hex;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.floragunn.searchguard.support.Base64Helper;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.user.User;

public class FieldReadCallback {
    
    private final ThreadContext threadContext;
    private final ClusterService clusterService;
    private final Index index;
    
    public FieldReadCallback(final ThreadContext threadContext, final IndexService indexService, final ClusterService clusterService) {
        super();
        this.threadContext = Objects.requireNonNull(threadContext);
        this.clusterService = Objects.requireNonNull(clusterService);
        this.index = Objects.requireNonNull(indexService).index();
        //Objects.requireNonNull(indexService).cache().
    }

    //TODO  We need to deal with caching!!
    //Currently we disable caching (and realtime requests) when FLS or DLS is apply
    //For fieldhistory this may not be appropriate because of performance reasons
    //So we must check this
    
    public void fieldRead(final FieldInfo fieldInfo, final Object fieldValue) { 
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
        final String remoteAddress = getRemoteAddress().getAddress();
        final String fieldName = fieldInfo.name;
        Object readableFieldValue = fieldValue;
        
        if(fieldName.equals("_source")) {
            final BytesReference bytesRef = new BytesArray((byte[])fieldValue);
            final Tuple<XContentType, Map<String, Object>> bytesRefTuple = XContentHelper.convertToMap(bytesRef, false, XContentType.JSON);
            Map<String, Object> source = bytesRefTuple.v2();
            readableFieldValue = source;
        } else if (fieldValue instanceof byte[]) {
            readableFieldValue = Hex.toHexString((byte[])fieldValue);
        }
        
        

        System.out.println(utcTimestamp);
        System.out.println(clusterName);
        System.out.println(clusterUUID);
        System.out.println(nodeId);
        System.out.println(nodeName);
        System.out.println(nodeHostName);
        System.out.println(nodeHostAddress);
        System.out.println(indexName);
        System.out.println(indexUUID);
        System.out.println(user);
        System.out.println(remoteAddress);
        System.out.println(fieldName);
        System.out.println(readableFieldValue);
        
        //TODO store it somwhere, maybe we can reuse auditlog storages?
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

}
