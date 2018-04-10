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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportRequest;

import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.auditlog.impl.AuditMessage.Category;
import com.floragunn.searchguard.support.Base64Helper;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.support.WildcardMatcher;
import com.floragunn.searchguard.user.User;

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

    private List<String> ignoreAuditUsers;
    private final List<String> ignoreAuditRequests;
    private final List<String> disabledRestCategories;
    private final List<String> disabledTransportCategories;
    private final List<String> defaultDisabledCategories = 
            Arrays.asList(new String[]{Category.AUTHENTICATED.toString(), Category.GRANTED_PRIVILEGES.toString()});
    private final List<String> defaultIgnoredUsers = 
            Arrays.asList(new String[]{"kibanaserver"});
    
    private final String searchguardIndex;

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
        
        ignoreAuditUsers = new ArrayList<>(settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_IGNORE_USERS, defaultIgnoredUsers));
        
        if(ignoreAuditUsers.size() == 1 && "NONE".equals(ignoreAuditUsers.get(0))) {
            ignoreAuditUsers.clear();
        }
        
        if (ignoreAuditUsers.size() > 0) {
            log.info("Configured Users to ignore: {}", ignoreAuditUsers);
        }
        
        ignoreAuditRequests = settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_IGNORE_REQUESTS, Collections.emptyList());
        if (ignoreAuditUsers.size() > 0) {
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
    }
    
    @Override
    public void logFailedLogin(String effectiveUser, boolean sgadmin, String initiatingUser, TransportRequest request, Task task) {
        final String action = null;
        
        if(!checkTransportFilter(Category.FAILED_LOGIN, action, effectiveUser, request)) {
            return;
        }
        
        final TransportAddress remoteAddress = getRemoteAddress();
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.FAILED_LOGIN, getOrigin(), action, null, effectiveUser, sgadmin, initiatingUser, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, null);
        
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
            msg.addRestHeaders(request.getHeaders());
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
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.AUTHENTICATED, getOrigin(), action, null, effectiveUser, sgadmin, initiatingUser,remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, null);
        
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
            msg.addRestHeaders(request.getHeaders());
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
            msg.addRestHeaders(request.getHeaders());
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
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.MISSING_PRIVILEGES, getOrigin(), action, privilege, getUser(), null, null, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, null);
        
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
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.GRANTED_PRIVILEGES, getOrigin(), action, privilege, getUser(), null, null, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, null);
        
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
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.BAD_HEADERS, getOrigin(), action, null, getUser(), null, null, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, null);
        
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
            msg.addRestHeaders(request.getHeaders());
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
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.SG_INDEX_ATTEMPT, getOrigin(), action, null, getUser(), false, null, remoteAddress, request, getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, null);
        
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
        final List<AuditMessage> msgs = RequestResolver.resolve(Category.SSL_EXCEPTION, Origin.TRANSPORT, action, null, getUser(), false, null, remoteAddress, request, 
                getThreadContextHeaders(), task, resolver, clusterService, settings, logRequestBody, resolveIndices, resolveBulkRequests, searchguardIndex, t);
        
        for(AuditMessage msg: msgs) {
            save(msg);
        }
    }

    @Override
    public void logSSLException(RestRequest request, Throwable t) {
        
        if(!checkRestFilter(Category.SSL_EXCEPTION, getUser(), request)) {
            return;
        }
        
        AuditMessage msg = new AuditMessage(Category.SSL_EXCEPTION, clusterService, Origin.REST, Origin.REST);
        TransportAddress remoteAddress = getRemoteAddress();
        msg.addRemoteAddress(remoteAddress);
        if(request != null && logRequestBody && request.hasContentOrSourceParam()) {
            msg.addBody(request.contentOrSourceParam());
        }
        
        if(request != null) {
            msg.addPath(request.path());
            msg.addRestHeaders(request.getHeaders());
            msg.addRestParams(request.params());
        }
        msg.addException(t);
        msg.addEffectiveUser(getUser());
        save(msg);
    }

    private Origin getOrigin() {
        String origin = (String) threadPool.getThreadContext().getTransient(ConfigConstants.SG_ORIGIN);
        
        if(origin == null && threadPool.getThreadContext().getHeader(ConfigConstants.SG_ORIGIN_HEADER) != null) {
            origin = (String) threadPool.getThreadContext().getHeader(ConfigConstants.SG_ORIGIN_HEADER);
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
        
        if (ignoreAuditUsers.size() > 0 && WildcardMatcher.matchAny(ignoreAuditUsers, effectiveUser)) {
            
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
        
        if (ignoreAuditUsers.size() > 0 && WildcardMatcher.matchAny(ignoreAuditUsers, effectiveUser)) {
            
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
