/*
 * Copyright 2016-2017 by floragunn GmbH - All rights reserved
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

package com.floragunn.searchguard.auditlog.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.http.client.utils.URIBuilder;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.shard.ShardId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.floragunn.searchguard.auditlog.AuditLog.Origin;

public final class AuditMessage {
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String FORMAT_VERSION = "audit_format_version";
    public static final String CATEGORY = "audit_category";
    public static final String REQUEST_EFFECTIVE_USER = "audit_request_effective_user";
    public static final String REQUEST_INITIATING_USER = "audit_request_initiating_user";
    public static final String UTC_TIMESTAMP = "audit_utc_timestamp";
    
    public static final String NODE_ID = "audit_node_id";
    public static final String NODE_HOST_ADDRESS = "audit_node_host_address";
    public static final String NODE_HOST_NAME = "audit_node_host_name";
    public static final String NODE_NAME = "audit_node_name";
    
    public static final String ORIGIN = "audit_request_origin";
    public static final String REMOTE_ADDRESS = "audit_request_remote_address";
    
    public static final String REST_REQUEST_PATH = "audit_rest_request_path";
    //public static final String REST_REQUEST_BODY = "audit_rest_request_body";
    public static final String REST_REQUEST_PARAMS = "audit_rest_request_params";
    public static final String REST_REQUEST_HEADERS = "audit_rest_request_headers";
    
    public static final String TRANSPORT_REQUEST_TYPE = "audit_transport_request_type";
    public static final String TRANSPORT_ACTION = "audit_transport_action";
    public static final String TRANSPORT_REQUEST_HEADERS = "audit_transport_headers";
    
    public static final String ID = "audit_trace_doc_id";
    public static final String TYPES = "audit_trace_doc_types";
    //public static final String SOURCE = "audit_trace_doc_source";
    public static final String INDICES = "audit_trace_indices";
    public static final String SHARD_ID = "audit_trace_shard_id";
    public static final String RESOLVED_INDICES = "audit_trace_resolved_indices";
    
    public static final String EXCEPTION = "audit_request_exception_stacktrace";
    public static final String IS_ADMIN_DN = "audit_request_effective_user_is_admin";
    public static final String PRIVILEGE = "audit_request_privilege";
    
    public static final String TASK_ID = "audit_trace_task_id";
    public static final String TASK_PARENT_ID = "audit_trace_task_parent_id";
    
    public static final String REQUEST_BODY = "audit_request_body";
    public static final String REQUEST_LAYER = "audit_request_layer";

    private static final DateTimeFormatter DEFAULT_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
    private final Map<String, Object> auditInfo = new HashMap<String, Object>(50);
    private final Category msgCategory;

    public AuditMessage(final Category msgCategory, final ClusterService clusterService, final Origin origin, final Origin layer) {
        this.msgCategory = Objects.requireNonNull(msgCategory);
        final String currentTime = currentTime();
        auditInfo.put(FORMAT_VERSION, 3);
        auditInfo.put(CATEGORY, Objects.requireNonNull(msgCategory));
        auditInfo.put(UTC_TIMESTAMP, currentTime);
        auditInfo.put(NODE_HOST_ADDRESS, Objects.requireNonNull(clusterService).localNode().getHostAddress());
        auditInfo.put(NODE_ID, Objects.requireNonNull(clusterService).localNode().getId());
        auditInfo.put(NODE_HOST_NAME, Objects.requireNonNull(clusterService).localNode().getHostName());
        auditInfo.put(NODE_NAME, Objects.requireNonNull(clusterService).localNode().getName());
        
        if(origin != null) {
            auditInfo.put(ORIGIN, origin);
        }
        
        if(layer != null) {
            auditInfo.put(REQUEST_LAYER, layer);
        }
    }
    
    public void addRemoteAddress(TransportAddress remoteAddress) {
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            auditInfo.put(REMOTE_ADDRESS, remoteAddress.getAddress());
        }
    }
    
    public void addIsAdminDn(boolean isAdminDn) {
        auditInfo.put(IS_ADMIN_DN, isAdminDn);
    }
    
    public void addException(Throwable t) {
        if (t != null) {
            auditInfo.put(EXCEPTION, ExceptionsHelper.stackTrace(t));
        }
    }
    
    public void addPrivilege(String priv) {
        if (priv != null) {
            auditInfo.put(PRIVILEGE, priv);
        }
    }

    public void addInitiatingUser(String user) {
        if (user != null) {
            auditInfo.put(REQUEST_INITIATING_USER, user);
        }
    }
    
    public void addEffectiveUser(String user) {
        if (user != null) {
            auditInfo.put(REQUEST_EFFECTIVE_USER, user);
        }
    }

    public void addPath(String path) {
        if (path != null) {
            auditInfo.put(REST_REQUEST_PATH, path);
        }
    }

    public void addBody(Tuple<XContentType, BytesReference> xContentTuple) {
        if (xContentTuple != null) {
            try {
                auditInfo.put(REQUEST_BODY, XContentHelper.convertToJson(xContentTuple.v2(), false, xContentTuple.v1()));
            } catch (Exception e) {
                auditInfo.put(REQUEST_BODY, e.toString());
            }
        }
    }

    public void addRequestType(String requestType) {
        if (requestType != null) {
            auditInfo.put(TRANSPORT_REQUEST_TYPE, requestType);
        }
    }

    public void addAction(String action) {
        if (action != null) {
            auditInfo.put(TRANSPORT_ACTION, action);
        }
    }

    public void addId(String id) {
        if (id != null) {
            auditInfo.put(ID, id);
        }
    }

    public void addTypes(String[] types) {
        if (types != null && types.length > 0) {
            auditInfo.put(TYPES, types);
        }
    }

    public void addType(String type) {
        if (type != null) {
            auditInfo.put(TYPES, new String[] { type });
        }
    }

    public void addSource(String source) {
        if (source != null) {
            auditInfo.put(REQUEST_BODY, source);
        }
    }

    public void addIndices(String[] indices) {
        if (indices != null && indices.length > 0) {
            auditInfo.put(INDICES, indices);
        }

    }

    public void addResolvedIndices(String[] resolvedIndices) {
        if (resolvedIndices != null && resolvedIndices.length > 0) {
            auditInfo.put(RESOLVED_INDICES, resolvedIndices);
        }
    }
    
    public void addTaskId(long id) {
         auditInfo.put(TASK_ID, auditInfo.get(NODE_ID)+":"+id);
    }
    
    public void addShardId(ShardId id) {
        if(id != null) {
            auditInfo.put(SHARD_ID, id.getId());
        }
   }
    
    public void addTaskParentId(String id) {
        if(id != null) {
            auditInfo.put(TASK_PARENT_ID, id);
        }
    }
    
    public void addRestParams(Map<String,String> params) {
        if(params != null && !params.isEmpty()) {
            auditInfo.put(REST_REQUEST_PARAMS, new HashMap<>(params));
        }
    }
    
    public void addRestHeaders(Map<String,List<String>> headers) {
        if(headers != null && !headers.isEmpty()) {
            final Map<String, List<String>> headersClone = new HashMap<String, List<String>>(headers)
                    .entrySet().stream()
                    .filter(map -> !map.getKey().equalsIgnoreCase(AUTHORIZATION_HEADER))
                    .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
            auditInfo.put(REST_REQUEST_HEADERS, headersClone);
        }
    }
    
    public void addTransportHeaders(Map<String,String> headers) {
        if(headers != null && !headers.isEmpty()) {
            final Map<String,String> headersClone = new HashMap<String,String>(headers)
                    .entrySet().stream()
                    .filter(map -> !map.getKey().equalsIgnoreCase(AUTHORIZATION_HEADER))
                    .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
            auditInfo.put(TRANSPORT_REQUEST_HEADERS, headersClone);
        }
    }

    public Map<String, Object> getAsMap() {
      return new HashMap<>(this.auditInfo);
    }
    
    public String getInitiatingUser() {
        return (String) this.auditInfo.get(REQUEST_INITIATING_USER);
    }
    
    public String getEffectiveUser() {
        return (String) this.auditInfo.get(REQUEST_EFFECTIVE_USER);
    }

    public String getRequestType() {
        return (String) this.auditInfo.get(TRANSPORT_REQUEST_TYPE);
    }

	public Category getCategory() {
		return msgCategory;
	}

	@Override
	public String toString() {
		try {
			return JsonXContent.contentBuilder().map(getAsMap()).string();
		} catch (final IOException e) {
		    throw ExceptionsHelper.convertToElastic(e);
		}
	}
	
    public String toPrettyString() {
        try {
            return JsonXContent.contentBuilder().prettyPrint().map(getAsMap()).string();
        } catch (final IOException e) {
            throw ExceptionsHelper.convertToElastic(e);
        }
    }

	public String toText() {
		StringBuilder builder = new StringBuilder();
		for (Entry<String, Object> entry : getAsMap().entrySet()) {
			addIfNonEmpty(builder, entry.getKey(), stringOrNull(entry.getValue()));
		}
		return builder.toString();
	}

	public final String toJson() {
		return this.toString();
	}

	public String toUrlParameters() {
		URIBuilder builder = new URIBuilder();
		for (Entry<String, Object> entry : getAsMap().entrySet()) {
			builder.addParameter(entry.getKey(), stringOrNull(entry.getValue()));
		}
		return builder.toString();
	}
	
	protected static void addIfNonEmpty(StringBuilder builder, String key, String value) {
		if (!Strings.isEmpty(value)) {
			if (builder.length() > 0) {
				builder.append("\n");
			}
			builder.append(key).append(": ").append(value);
		}
	}
	
	protected enum Category {
        BAD_HEADERS,
        FAILED_LOGIN,
        MISSING_PRIVILEGES,
        GRANTED_PRIVILEGES,
        SG_INDEX_ATTEMPT,
        SSL_EXCEPTION,
        AUTHENTICATED;
    }

    private String currentTime() {
        DateTime dt = new DateTime(DateTimeZone.UTC);        
        return DEFAULT_FORMAT.print(dt);
    }
    
    protected String stringOrNull(Object object) {
        if(object == null) {
            return null;            
        }
        
        return String.valueOf(object);
    }

}
