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
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.engine.Engine.Delete;
import org.elasticsearch.index.engine.Engine.DeleteResult;
import org.elasticsearch.index.engine.Engine.Index;
import org.elasticsearch.index.engine.Engine.IndexResult;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.auditlog.impl.AuditMessage.Category;
import com.floragunn.searchguard.compliance.ComplianceConfig;
import com.floragunn.searchguard.support.Base64Helper;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.support.WildcardMatcher;
import com.floragunn.searchguard.user.AuthCredentials;
import com.floragunn.searchguard.user.User;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

public abstract class AbstractAuditLog implements AuditLog {

    protected final Logger log = LogManager.getLogger(this.getClass());
    protected final ThreadPool threadPool;
    protected final IndexNameExpressionResolver resolver;
    protected final ClusterService clusterService;
    protected final Settings settings;
    protected final boolean restAuditingEnabled;
    protected final boolean transportAuditingEnabled;
    protected final boolean resolveBulkRequests;

    protected final boolean logRequestBody;
    protected final boolean resolveIndices;

    private List<String> ignoredAuditUsers;
    private List<String> ignoredComplianceUsersForRead;
    private List<String> ignoredComplianceUsersForWrite;
    private final List<String> ignoreAuditRequests;
    private final List<String> disabledRestCategories;
    private final List<String> disabledTransportCategories;
    private final List<String> defaultDisabledCategories =
            Arrays.asList(new String[]{Category.AUTHENTICATED.toString(), Category.GRANTED_PRIVILEGES.toString()});
    private final List<String> defaultIgnoredUsers =
            Arrays.asList(new String[]{"kibanaserver"});
    private final boolean excludeSensitiveHeaders;

    private final String searchguardIndex;
    private final ObjectMapper mapper = new ObjectMapper();

    protected AbstractAuditLog(Settings settings, final ThreadPool threadPool, final IndexNameExpressionResolver resolver, final ClusterService clusterService) {
        super();
        this.threadPool = threadPool;

        this.settings = settings;
        this.resolver = resolver;
        this.clusterService = clusterService;

        this.searchguardIndex = settings.get(ConfigConstants.SEARCHGUARD_CONFIG_INDEX_NAME, ConfigConstants.SG_DEFAULT_CONFIG_INDEX);

        resolveBulkRequests = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_RESOLVE_BULK_REQUESTS, false);

        restAuditingEnabled = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_ENABLE_REST, true);
        transportAuditingEnabled = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_ENABLE_TRANSPORT, true);

        disabledRestCategories = new ArrayList<>(settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DISABLED_REST_CATEGORIES, defaultDisabledCategories).stream()
                .map(c->c.toUpperCase()).collect(Collectors.toList()));

        if(disabledRestCategories.size() == 1 && "NONE".equals(disabledRestCategories.get(0))) {
            disabledRestCategories.clear();
        }

        if (disabledRestCategories.size() > 0) {
            log.info("Configured categories on rest layer to ignore: {}", disabledRestCategories);
        }

        disabledTransportCategories = new ArrayList<>(settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DISABLED_TRANSPORT_CATEGORIES, defaultDisabledCategories).stream()
                .map(c->c.toUpperCase()).collect(Collectors.toList()));

        if(disabledTransportCategories.size() == 1 && "NONE".equals(disabledTransportCategories.get(0))) {
            disabledTransportCategories.clear();
        }

        if (disabledTransportCategories.size() > 0) {
            log.info("Configured categories on transport layer to ignore: {}", disabledTransportCategories);
        }

        logRequestBody = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_LOG_REQUEST_BODY, true);
        resolveIndices = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_RESOLVE_INDICES, true);

        ignoredAuditUsers = new ArrayList<>(settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_IGNORE_USERS, defaultIgnoredUsers));

        if(ignoredAuditUsers.size() == 1 && "NONE".equals(ignoredAuditUsers.get(0))) {
            ignoredAuditUsers.clear();
        }

        if (ignoredAuditUsers.size() > 0) {
            log.info("Configured Users to ignore: {}", ignoredAuditUsers);
        }

        ignoredComplianceUsersForRead = new ArrayList<>(settings.getAsList(ConfigConstants.SEARCHGUARD_COMPLIANCE_HISTORY_READ_IGNORE_USERS, defaultIgnoredUsers));

        if(ignoredComplianceUsersForRead.size() == 1 && "NONE".equals(ignoredComplianceUsersForRead.get(0))) {
            ignoredComplianceUsersForRead.clear();
        }

        if (ignoredComplianceUsersForRead.size() > 0) {
            log.info("Configured Users to ignore for read compliance events: {}", ignoredComplianceUsersForRead);
        }

        ignoredComplianceUsersForWrite = new ArrayList<>(settings.getAsList(ConfigConstants.SEARCHGUARD_COMPLIANCE_HISTORY_WRITE_IGNORE_USERS, defaultIgnoredUsers));

        if(ignoredComplianceUsersForWrite.size() == 1 && "NONE".equals(ignoredComplianceUsersForWrite.get(0))) {
            ignoredComplianceUsersForWrite.clear();
        }

        if (ignoredComplianceUsersForWrite.size() > 0) {
            log.info("Configured Users to ignore for write compliance events: {}", ignoredComplianceUsersForWrite);
        }



        ignoreAuditRequests = settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_IGNORE_REQUESTS, Collections.emptyList());
        if (ignoreAuditRequests.size() > 0) {
            log.info("Configured Requests to ignore: {}", ignoreAuditRequests);
        }

        // check if some categories are invalid
        for (String event : disabledRestCategories) {
        	try {
        		AuditMessage.Category.valueOf(event.toUpperCase());
        	} catch(Exception iae) {
        		log.error("Unkown category {}, please check searchguard.audit.config.disabled_categories settings", event);
        	}
		}

        // check if some categories are invalid
        for (String event : disabledTransportCategories) {
            try {
                AuditMessage.Category.valueOf(event.toUpperCase());
            } catch(Exception iae) {
                log.error("Unkown category {}, please check searchguard.audit.config.disabled_categories settings", event);
            }
        }
        
        this.excludeSensitiveHeaders = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_EXCLUDE_SENSITIVE_HEADERS, true);
    }

    @Override
    public void logFailedLogin(String effectiveUser, boolean sgadmin, String initiatingUser, TransportRequest request, Task task) {
        final String action = null;

        if(!checkTransportFilter(Category.FAILED_LOGIN, action, effectiveUser, request)) {
            return;
        }

        final TransportAddress remoteAddress = getRemoteAddress();
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.FAILED_LOGIN, getOrigin(), action, null, effectiveUser, sgadmin, initiatingUser, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, excludeSensitiveHeaders, null);

        for(AuditMessage msg: msgs) {
            save(msg);
        }
    }


    @Override
    public void logFailedLogin(String effectiveUser, boolean sgadmin, String initiatingUser, RestRequest request) {

        if(!checkRestFilter(Category.FAILED_LOGIN, effectiveUser, request)) {
            return;
        }

        AuditMessage msg = new AuditMessage(Category.FAILED_LOGIN, clusterService, getOrigin(), Origin.REST);
        TransportAddress remoteAddress = getRemoteAddress();
        msg.addRemoteAddress(remoteAddress);
        if(request != null && logRequestBody && request.hasContentOrSourceParam()) {
            msg.addBody(request.contentOrSourceParam());
        }

        if(request != null) {
            msg.addPath(request.path());
            msg.addRestHeaders(request.getHeaders(), excludeSensitiveHeaders);
            msg.addRestParams(request.params());
        }

        msg.addInitiatingUser(initiatingUser);
        msg.addEffectiveUser(effectiveUser);
        msg.addIsAdminDn(sgadmin);

        save(msg);
    }

    @Override
    public void logSucceededLogin(String effectiveUser, boolean sgadmin, String initiatingUser, TransportRequest request, String action, Task task) {

        if(!checkTransportFilter(Category.AUTHENTICATED, action, effectiveUser, request)) {
            return;
        }

        final TransportAddress remoteAddress = getRemoteAddress();
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.AUTHENTICATED, getOrigin(), action, null, effectiveUser, sgadmin, initiatingUser,remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, excludeSensitiveHeaders, null);

        for(AuditMessage msg: msgs) {
            save(msg);
        }
    }

    @Override
    public void logSucceededLogin(String effectiveUser, boolean sgadmin, String initiatingUser, RestRequest request) {

        if(!checkRestFilter(Category.AUTHENTICATED, effectiveUser, request)) {
            return;
        }

        AuditMessage msg = new AuditMessage(Category.AUTHENTICATED, clusterService, getOrigin(), Origin.REST);
        TransportAddress remoteAddress = getRemoteAddress();
        msg.addRemoteAddress(remoteAddress);
        if(request != null && logRequestBody && request.hasContentOrSourceParam()) {
           msg.addBody(request.contentOrSourceParam());
        }

        if(request != null) {
            msg.addPath(request.path());
            msg.addRestHeaders(request.getHeaders(), excludeSensitiveHeaders);
            msg.addRestParams(request.params());
        }

        msg.addInitiatingUser(initiatingUser);
        msg.addEffectiveUser(effectiveUser);
        msg.addIsAdminDn(sgadmin);
        save(msg);
    }

    @Override
    public void logMissingPrivileges(String privilege, String effectiveUser, RestRequest request) {
        if(!checkRestFilter(Category.MISSING_PRIVILEGES, effectiveUser, request)) {
            return;
        }

        AuditMessage msg = new AuditMessage(Category.MISSING_PRIVILEGES, clusterService, getOrigin(), Origin.REST);
        TransportAddress remoteAddress = getRemoteAddress();
        msg.addRemoteAddress(remoteAddress);
        if(request != null && logRequestBody && request.hasContentOrSourceParam()) {
           msg.addBody(request.contentOrSourceParam());
        }
        if(request != null) {
            msg.addPath(request.path());
            msg.addRestHeaders(request.getHeaders(), excludeSensitiveHeaders);
            msg.addRestParams(request.params());
        }

        msg.addEffectiveUser(effectiveUser);
        save(msg);
    }

    @Override
    public void logMissingPrivileges(String privilege, TransportRequest request, Task task) {
        final String action = null;

        if(!checkTransportFilter(Category.MISSING_PRIVILEGES, privilege, getUser(), request)) {
            return;
        }

        final TransportAddress remoteAddress = getRemoteAddress();
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.MISSING_PRIVILEGES, getOrigin(), action, privilege, getUser(), null, null, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, excludeSensitiveHeaders, null);

        for(AuditMessage msg: msgs) {
            save(msg);
        }
    }

    @Override
    public void logGrantedPrivileges(String privilege, TransportRequest request, Task task) {
        final String action = null;

        if(!checkTransportFilter(Category.GRANTED_PRIVILEGES, privilege, getUser(), request)) {
            return;
        }

        final TransportAddress remoteAddress = getRemoteAddress();
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.GRANTED_PRIVILEGES, getOrigin(), action, privilege, getUser(), null, null, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, excludeSensitiveHeaders, null);

        for(AuditMessage msg: msgs) {
            save(msg);
        }
    }

    @Override
    public void logBadHeaders(TransportRequest request, String action, Task task) {

        if(!checkTransportFilter(Category.BAD_HEADERS, action, getUser(), request)) {
            return;
        }

        final TransportAddress remoteAddress = getRemoteAddress();
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.BAD_HEADERS, getOrigin(), action, null, getUser(), null, null, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, excludeSensitiveHeaders, null);

        for(AuditMessage msg: msgs) {
            save(msg);
        }
    }

    @Override
    public void logBadHeaders(RestRequest request) {

        if(!checkRestFilter(Category.BAD_HEADERS, getUser(), request)) {
            return;
        }

        AuditMessage msg = new AuditMessage(Category.BAD_HEADERS, clusterService, getOrigin(), Origin.REST);
        TransportAddress remoteAddress = getRemoteAddress();
        msg.addRemoteAddress(remoteAddress);
        if(request != null && logRequestBody && request.hasContentOrSourceParam()) {
            msg.addBody(request.contentOrSourceParam());
        }
        if(request != null) {
            msg.addPath(request.path());
            msg.addRestHeaders(request.getHeaders(), excludeSensitiveHeaders);
            msg.addRestParams(request.params());
        }

        msg.addEffectiveUser(getUser());

        save(msg);
    }

    @Override
    public void logSgIndexAttempt(TransportRequest request, String action, Task task) {

        if(!checkTransportFilter(Category.SG_INDEX_ATTEMPT, action, getUser(), request)) {
            return;
        }

        final TransportAddress remoteAddress = getRemoteAddress();
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.SG_INDEX_ATTEMPT, getOrigin(), action, null, getUser(), false, null, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, excludeSensitiveHeaders, null);

        for(AuditMessage msg: msgs) {
            save(msg);
        }
    }

    @Override
    public void logSSLException(TransportRequest request, Throwable t, String action, Task task) {

        if(!checkTransportFilter(Category.SSL_EXCEPTION, action, getUser(), request)) {
            return;
        }

        final TransportAddress remoteAddress = getRemoteAddress();
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.SSL_EXCEPTION, getOrigin(), action, null, getUser(), false, null, remoteAddress, request,
                getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, excludeSensitiveHeaders, t);

        for(AuditMessage msg: msgs) {
            save(msg);
        }
    }

    @Override
    public void logSSLException(RestRequest request, Throwable t) {

        if(!checkRestFilter(Category.SSL_EXCEPTION, getUser(), request)) {
            return;
        }

        AuditMessage msg = new AuditMessage(Category.SSL_EXCEPTION, clusterService, getOrigin(), Origin.REST);
        TransportAddress remoteAddress = getRemoteAddress();
        msg.addRemoteAddress(remoteAddress);
        if(request != null && logRequestBody && request.hasContentOrSourceParam()) {
            msg.addBody(request.contentOrSourceParam());
        }

        if(request != null) {
            msg.addPath(request.path());
            msg.addRestHeaders(request.getHeaders(), excludeSensitiveHeaders);
            msg.addRestParams(request.params());
        }
        msg.addException(t);
        msg.addEffectiveUser(getUser());
        save(msg);
    }

    @Override
    public void logDocumentRead(String index, String id, Map<String, String> fieldNameValues, ComplianceConfig complianceConfig) {

        String effectiveUser = getUser();

        if(!checkComplianceFilter(Category.COMPLIANCE_DOC_READ, effectiveUser)) {
            return;
        }

        if(fieldNameValues != null && !fieldNameValues.isEmpty()) {
            AuditMessage msg = new AuditMessage(Category.COMPLIANCE_DOC_READ, clusterService, getOrigin(), null);
            TransportAddress remoteAddress = getRemoteAddress();
            msg.addRemoteAddress(remoteAddress);
            msg.addEffectiveUser(effectiveUser);
            msg.addIndices(new String[]{index});
            msg.addResolvedIndices(new String[]{index});
            msg.addId(id);

            try {
                if(complianceConfig.logMetadataOnly()) {
                    msg.addSource(mapper.writeValueAsString(fieldNameValues.keySet()));
                } else {
                    msg.addSource(mapper.writeValueAsString(fieldNameValues));
                }
            } catch (JsonProcessingException e) {
                log.error("Unable to generate request body for {} and {}",msg.toPrettyString(),fieldNameValues, e);
            }

            save(msg);
        }

    }

    @Override
    public void logDocumentWritten(ShardId shardId, GetResult originalIndex, GetResult currentGet, Index currentIndex, IndexResult result, ComplianceConfig complianceConfig) {

        String effectiveUser = getUser();

        if(!checkComplianceFilter(Category.COMPLIANCE_DOC_WRITE, effectiveUser)) {
            return;
        }

        AuditMessage msg = new AuditMessage(Category.COMPLIANCE_DOC_WRITE, clusterService, getOrigin(), null);
        TransportAddress remoteAddress = getRemoteAddress();
        msg.addRemoteAddress(remoteAddress);
        msg.addEffectiveUser(effectiveUser);
        msg.addIndices(new String[]{shardId.getIndexName()});
        msg.addResolvedIndices(new String[]{shardId.getIndexName()});
        msg.addId(currentIndex.id());
        msg.addType(currentIndex.type());
        msg.addShardId(shardId);
        msg.addComplianceDocVersion(result.getVersion());
        msg.addComplianceOperation(result.isCreated()?Operation.CREATE:Operation.UPDATE);

        if(!complianceConfig.logMetadataOnly()) {

            final Map<String, DocumentField> currentFields = currentGet.getFields();

            if(originalIndex != null) {
                final Map<String, DocumentField> originalFields = originalIndex.getFields();
                MapDifference<String, Object> mapDiff = Maps.difference(currentFields, originalFields);
                msg.addComplianceWriteStoredFields(mapDiff.areEqual()?"":mapDiff.toString());
            } else if (!complianceConfig.logDiffsOnlyForWrite() && currentGet.getFields().size() > 0) {
                msg.addComplianceWriteStoredFields(currentGet.getFields().toString());
            }


            if(originalIndex != null && originalIndex.isExists() && originalIndex.internalSourceRef() != null) {
                try {
                    final String originalSource = XContentHelper.convertToJson(originalIndex.internalSourceRef(), false, XContentType.JSON);
                    final String currentSource =  XContentHelper.convertToJson(currentIndex.source(), false, XContentType.JSON);
                    final JsonNode diffnode = JsonDiff.asJson(mapper.readTree(originalSource), mapper.readTree(currentSource));
                    msg.addComplianceWriteDiffSource(diffnode.size() == 0?"":diffnode.toString());
                } catch (IOException e) {
                    log.error("Unable to generate diff for {}",msg.toPrettyString(),e);
                }
            } else if (!complianceConfig.logDiffsOnlyForWrite()){
                msg.addBody(new Tuple<XContentType, BytesReference>(XContentType.JSON, currentIndex.source()));
            }
        }
        save(msg);
    }

    @Override
    public void logDocumentDeleted(ShardId shardId, Delete delete, DeleteResult result) {

        String effectiveUser = getUser();

        if(!checkComplianceFilter(Category.COMPLIANCE_DOC_WRITE, effectiveUser)) {
            return;
        }

        AuditMessage msg = new AuditMessage(Category.COMPLIANCE_DOC_WRITE, clusterService, getOrigin(), null);
        TransportAddress remoteAddress = getRemoteAddress();
        msg.addRemoteAddress(remoteAddress);
        msg.addEffectiveUser(effectiveUser);
        msg.addIndices(new String[]{shardId.getIndexName()});
        msg.addResolvedIndices(new String[]{shardId.getIndexName()});
        msg.addId(delete.id());
        msg.addType(delete.type());
        msg.addShardId(shardId);
        msg.addComplianceDocVersion(result.getVersion());
        msg.addComplianceOperation(Operation.DELETE);
        save(msg);
    }

    @Override
    public void logExternalConfig(Settings settings, Environment environment) {

        if(!checkComplianceFilter(Category.COMPLIANCE_EXTERNAL_CONFIG, null)) {
            return;
        }

        final String configAsString = Strings.toString(settings);
        
        final SecurityManager sm = System.getSecurityManager();
        
        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
        }

        final String envAsString = AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return String.valueOf(System.getenv());
            }
        });
        
        final String propsAsString = AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return String.valueOf(System.getProperties());
            }
        });

        final String sha256 = DigestUtils.sha256Hex(configAsString+envAsString+propsAsString);
        AuditMessage msg = new AuditMessage(Category.COMPLIANCE_EXTERNAL_CONFIG, clusterService, null, null);
        msg.addSource(configAsString+"  "+envAsString+"  "+propsAsString+"  "+sha256);
        Map<String, Path> paths = new HashMap<String, Path>();
        for(String key: settings.keySet()) {
            if(key.startsWith("searchguard") &&
                    (key.contains("filepath") || key.contains("file_path"))) {
                String value = settings.get(key);
                if(value != null && !value.isEmpty()) {
                    Path path = value.startsWith("/")?Paths.get(value):environment.configFile().resolve(value);
                    paths.put(key, path);
                }
            }
        }
        msg.addFileInfos(paths);


        save(msg);
    }

    private Origin getOrigin() {
        String origin = (String) threadPool.getThreadContext().getTransient(ConfigConstants.SG_ORIGIN);

        if(origin == null && threadPool.getThreadContext().getHeader(ConfigConstants.SG_ORIGIN_HEADER) != null) {
            origin = threadPool.getThreadContext().getHeader(ConfigConstants.SG_ORIGIN_HEADER);
        }

        return origin == null?null:Origin.valueOf(origin);
    }

    private TransportAddress getRemoteAddress() {
        TransportAddress address = threadPool.getThreadContext().getTransient(ConfigConstants.SG_REMOTE_ADDRESS);
        if(address == null && threadPool.getThreadContext().getHeader(ConfigConstants.SG_REMOTE_ADDRESS_HEADER) != null) {
            address = new TransportAddress((InetSocketAddress) Base64Helper.deserializeObject(threadPool.getThreadContext().getHeader(ConfigConstants.SG_REMOTE_ADDRESS_HEADER)));
        }
        return address;
    }

    private String getUser() {
        User user = threadPool.getThreadContext().getTransient(ConfigConstants.SG_USER);
        if(user == null && threadPool.getThreadContext().getHeader(ConfigConstants.SG_USER_HEADER) != null) {
            user = (User) Base64Helper.deserializeObject(threadPool.getThreadContext().getHeader(ConfigConstants.SG_USER_HEADER));
        }
        return user==null?null:user.getName();
    }

    private Map<String, String> getThreadContextHeaders() {
        return threadPool.getThreadContext().getHeaders();
    }

    private boolean checkTransportFilter(final Category category, final String action, final String effectiveUser, TransportRequest request) {

        if(log.isTraceEnabled()) {
            log.trace("Check category:{}, action:{}, effectiveUser:{}, request:{}", category, action, effectiveUser, request==null?null:request.getClass().getSimpleName());
        }


        if(!transportAuditingEnabled) {
            //ignore for certain categories
            if(category != Category.FAILED_LOGIN
                    && category != Category.MISSING_PRIVILEGES
                    && category != Category.SG_INDEX_ATTEMPT) {

                return false;
            }

        }

        //skip internals
        if(action != null
                &&
                ( action.startsWith("internal:")
                  || action.startsWith("cluster:monitor")
                  || action.startsWith("indices:monitor")
                )
                ) {


            //if(log.isTraceEnabled()) {
            //    log.trace("Skipped audit log message due to category ({}) or action ({}) does not match", category, action);
            //}

            return false;
        }

        if (ignoredAuditUsers.size() > 0 && WildcardMatcher.matchAny(ignoredAuditUsers, effectiveUser)) {

            if(log.isTraceEnabled()) {
                log.trace("Skipped audit log message because of user {} is ignored", effectiveUser);
            }

            return false;
        }

        if (request != null && ignoreAuditRequests.size() > 0
                && (WildcardMatcher.matchAny(ignoreAuditRequests, action) || WildcardMatcher.matchAny(ignoreAuditRequests, request.getClass().getSimpleName()))) {

            if(log.isTraceEnabled()) {
                log.trace("Skipped audit log message because request {} is ignored", action+"#"+request.getClass().getSimpleName());
            }

            return false;
        }

        if (!disabledTransportCategories.contains(category.toString())) {
            return true;
        } else {
            if(log.isTraceEnabled()) {
                log.trace("Skipped audit log message because category {} not enabled", category);
            }
            return false;
        }


        //skip cluster:monitor, index:monitor, internal:*
        //check transport audit enabled
        //check category enabled
        //check action
        //check ignoreAuditUsers

    }

    private boolean checkComplianceFilter(final Category category, final String effectiveUser) {
        if(log.isTraceEnabled()) {
            log.trace("Check for REST category:{}, effectiveUser:{}", category, effectiveUser);
        }

        if(category == Category.COMPLIANCE_DOC_READ) {
            if (ignoredComplianceUsersForRead.size() > 0 && effectiveUser != null
                    && WildcardMatcher.matchAny(ignoredComplianceUsersForRead, effectiveUser)) {

                if(log.isTraceEnabled()) {
                    log.trace("Skipped compliance log message because of user {} is ignored", effectiveUser);
                }

                return false;
            }
        }

        if(category == Category.COMPLIANCE_DOC_WRITE) {
            if (ignoredComplianceUsersForWrite.size() > 0 && effectiveUser != null
                    && WildcardMatcher.matchAny(ignoredComplianceUsersForWrite, effectiveUser)) {

                if(log.isTraceEnabled()) {
                    log.trace("Skipped compliance log message because of user {} is ignored", effectiveUser);
                }

                return false;
            }
        }

        return true;
    }


    private boolean checkRestFilter(final Category category, final String effectiveUser, RestRequest request) {
        if(log.isTraceEnabled()) {
            log.trace("Check for REST category:{}, effectiveUser:{}, request:{}", category, effectiveUser, request==null?null:request.path());
        }

        if(!restAuditingEnabled) {
            //ignore for certain categories
            if(category != Category.FAILED_LOGIN
                    && category != Category.MISSING_PRIVILEGES
                    && category != Category.SG_INDEX_ATTEMPT) {

                return false;
            }

        }

        if (ignoredAuditUsers.size() > 0 && WildcardMatcher.matchAny(ignoredAuditUsers, effectiveUser)) {

            if(log.isTraceEnabled()) {
                log.trace("Skipped audit log message because of user {} is ignored", effectiveUser);
            }

            return false;
        }

        if (request != null && ignoreAuditRequests.size() > 0
                && (WildcardMatcher.matchAny(ignoreAuditRequests, request.path()))) {

            if(log.isTraceEnabled()) {
                log.trace("Skipped audit log message because request {} is ignored", request.path());
            }

            return false;
        }

        if (!disabledRestCategories.contains(category.toString())) {
            return true;
        } else {
            if(log.isTraceEnabled()) {
                log.trace("Skipped audit log message because category {} not enabled", category);
            }
            return false;
        }


        //check rest audit enabled
        //check category enabled
        //check action
        //check ignoreAuditUsers
    }


    protected abstract void save(final AuditMessage msg);
}
