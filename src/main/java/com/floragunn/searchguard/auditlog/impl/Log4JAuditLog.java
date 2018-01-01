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

package com.floragunn.searchguard.auditlog.impl;

import java.nio.file.Path;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

public final class Log4JAuditLog extends AuditLogSink {

    private final Logger auditLogger;
    private final Level logLevel;

    public Log4JAuditLog(final Settings settings, final Path configPath, ThreadPool threadPool,
            final IndexNameExpressionResolver resolver, final ClusterService clusterService) {
        super(settings, threadPool, resolver, clusterService);
        auditLogger = LogManager.getLogger(settings.get("searchguard.audit.config.log4j.logger_name","sgaudit"));
        logLevel = Level.toLevel(settings.get("searchguard.audit.config.log4j.level","INFO").toUpperCase());
    }

    /*public boolean isHandlingBackpressure() {
        return true;
    }*/

    @Override
    public void store(final AuditMessage msg) {
        auditLogger.log(logLevel, msg.toJson());
    }

}
