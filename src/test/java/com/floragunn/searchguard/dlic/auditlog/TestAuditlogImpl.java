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

package com.floragunn.searchguard.dlic.auditlog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.auditlog.impl.AuditLogSink;
import com.floragunn.searchguard.auditlog.impl.AuditMessage;

public class TestAuditlogImpl extends AuditLogSink {

    public static List<AuditMessage> messages = new ArrayList<AuditMessage>(100);
    public static StringBuffer sb = new StringBuffer();
    
    public TestAuditlogImpl(Settings settings, final Path configPath, ThreadPool threadPool, 
            IndexNameExpressionResolver resolver, ClusterService clusterService) {
        super(settings, threadPool, resolver, clusterService);
    }

    @Override
    public synchronized void store(AuditMessage msg) {
        sb.append(msg.toPrettyString()+System.lineSeparator());
        messages.add(msg);
    }
    
    public static synchronized void clear() {
        sb.setLength(0);
        messages.clear();
    }

    @Override
    public boolean isHandlingBackpressure() {
        return true;
    }
    
    
}
