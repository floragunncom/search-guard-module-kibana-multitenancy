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

package com.floragunn.searchguard.auditlog.sink;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;

import com.floragunn.searchguard.auditlog.impl.AuditMessage;

public final class Log4JSink extends AuditLogSink {

    private final Logger auditLogger;
    private final Level logLevel;

    public Log4JSink(final String name, final Settings settings, final Settings sinkSettings, AuditLogSink fallbackSink) {
        super(name, settings, sinkSettings, fallbackSink);
        auditLogger = LogManager.getLogger(settings.get("searchguard.audit.config.log4j.logger_name","sgaudit"));
        logLevel = Level.toLevel(settings.get("searchguard.audit.config.log4j.level","INFO").toUpperCase());
    }

    /*public boolean isHandlingBackpressure() {
        return true;
    }*/


    public boolean doStore(final AuditMessage msg) {
        auditLogger.log(logLevel, msg.toJson());
        return true;
    }

}
