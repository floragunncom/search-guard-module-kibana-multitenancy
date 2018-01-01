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

import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

public class MyOwnAuditLog extends AuditLogSink {

	public MyOwnAuditLog(Settings settings, final Path configPath, ThreadPool threadPool,
	        final IndexNameExpressionResolver resolver, final ClusterService clusterService) {
        super(settings, threadPool, resolver, clusterService);
    }

    @Override
	public void close() throws IOException {
		
	}

	@Override
	public void store(AuditMessage msg) {
	}

}
