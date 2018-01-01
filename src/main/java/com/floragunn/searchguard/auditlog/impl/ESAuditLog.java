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
import java.nio.file.Path;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ThreadContext.StoredContext;
import org.elasticsearch.threadpool.ThreadPool;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.support.HeaderHelper;

public final class ESAuditLog extends AuditLogSink {

    private final Client clientProvider;
    private final String index;
    private final String type;
    private DateTimeFormatter indexPattern;

    public ESAuditLog(final Settings settings, final Path configPath, final Client clientProvider, ThreadPool threadPool, String index, String type,
            final IndexNameExpressionResolver resolver, final ClusterService clusterService) {
        super(settings, threadPool, resolver, clusterService);
        this.clientProvider = clientProvider;
        this.index = index;
        this.type = type;
        try {
            this.indexPattern = DateTimeFormat.forPattern(index);
        } catch (IllegalArgumentException e) {
            log.debug("Unable to parse index pattern due to {}. "
                    + "If you have no date pattern configured you can safely ignore this message", e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {

    }
    
    @Override
    public void store(final AuditMessage msg) {
        
        if (Boolean.parseBoolean((String) HeaderHelper.getSafeFromHeader(threadPool.getThreadContext(), ConfigConstants.SG_CONF_REQUEST_HEADER))) {
            if(log.isTraceEnabled()) {
                log.trace("audit log of audit log will not be executed");
            }
            return;
        }

        try(StoredContext ctx = threadPool.getThreadContext().stashContext()) {
            try {
                final IndexRequestBuilder irb = clientProvider.prepareIndex(getExpandedIndexName(indexPattern, index), type)
                        .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                        .setSource(msg.getAsMap());
                threadPool.getThreadContext().putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
                irb.setTimeout(TimeValue.timeValueMinutes(1));
                irb.execute().actionGet();
            } catch (final Exception e) {
                log.error("Unable to index audit log {} due to {}", msg, e.toString(), e);
            }
        }
    }
}
