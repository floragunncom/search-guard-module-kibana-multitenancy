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
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.logging.log4j.LogManager;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.auditlog.routing.AuditMessageRouter;
import com.floragunn.searchguard.compliance.ComplianceConfig;
import com.floragunn.searchguard.support.ConfigConstants;

public final class AuditLogImpl extends AbstractAuditLog {

	private final AuditMessageRouter messageRouter;
	private final boolean enabled;
	
	public AuditLogImpl(final Settings settings, final Path configPath, Client clientProvider, ThreadPool threadPool,
			final IndexNameExpressionResolver resolver, final ClusterService clusterService) {
		super(settings, threadPool, resolver, clusterService);

		this.messageRouter = new AuditMessageRouter(settings, clientProvider, threadPool, configPath);
		this.enabled = messageRouter.isEnabled();
		
		log.info("Message routing enabled: {}", this.enabled);
		
		final SecurityManager sm = System.getSecurityManager();

		if (sm != null) {
			log.debug("Security Manager present");
			sm.checkPermission(new SpecialPermission());
		}

		AccessController.doPrivileged(new PrivilegedAction<Object>() {
			@Override
			public Object run() {
				Runtime.getRuntime().addShutdownHook(new Thread() {

					@Override
					public void run() {
						try {
							close();
						} catch (IOException e) {
							log.warn("Exception while shutting down message router", e);
						}
					}
				});
				log.debug("Shutdown Hook registered");
				return null;
			}
		});

	}

    @Override
    public void setComplianceConfig(ComplianceConfig complianceConfig) {
    	messageRouter.setComplianceConfig(complianceConfig);
    }

	@Override
	public void close() throws IOException {
		messageRouter.close();
	}

	@Override
	protected void save(final AuditMessage msg) {
		if (enabled) {
			messageRouter.route(msg);	
		}		
	}
	
}
