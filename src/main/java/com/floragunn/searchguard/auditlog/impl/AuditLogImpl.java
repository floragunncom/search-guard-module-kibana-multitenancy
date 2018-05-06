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

	private static void printLicenseInfo() {
		final StringBuilder sb = new StringBuilder();
		sb.append("******************************************************" + System.lineSeparator());
		sb.append("Search Guard Audit Log is not free software" + System.lineSeparator());
		sb.append("for commercial use in production." + System.lineSeparator());
		sb.append("You have to obtain a license if you " + System.lineSeparator());
		sb.append("use it in production." + System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("See https://floragunn.com/searchguard-validate-license" + System.lineSeparator());
		sb.append("In case of any doubt mail to <sales@floragunn.com>" + System.lineSeparator());
		sb.append("*****************************************************" + System.lineSeparator());

		final String licenseInfo = sb.toString();

		if (!Boolean.getBoolean("sg.display_lic_none")) {

			if (!Boolean.getBoolean("sg.display_lic_only_stdout")) {
				LogManager.getLogger(AuditLogImpl.class).warn(licenseInfo);
				System.err.println(licenseInfo);
			}

			System.out.println(licenseInfo);
		}

	}

	static {
		// printLicenseInfo();
	}

	public AuditLogImpl(final Settings settings, final Path configPath, Client clientProvider, ThreadPool threadPool,
			final IndexNameExpressionResolver resolver, final ClusterService clusterService) {
		super(settings, threadPool, resolver, clusterService);
		//final String type = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_TYPE_DEFAULT, null);

		this.messageRouter = new AuditMessageRouter(settings, clientProvider, threadPool, configPath);

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
		messageRouter.route(msg);
	}
	
}
