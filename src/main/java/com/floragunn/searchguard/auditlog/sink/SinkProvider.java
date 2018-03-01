/*
 * Copyright 2016-2018 by floragunn GmbH - All rights reserved
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * For use in a production environment you have to obtain a license 
 * from https://floragunn.com
 * 
 */
package com.floragunn.searchguard.auditlog.sink;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.support.ConfigConstants;

public class SinkProvider {

	protected final Logger log = LogManager.getLogger(this.getClass());

	private final Client clientProvider;
	private final ThreadPool threadPool;
	private final Path configPath;
	private final Settings settings;
	final Map<String, AuditLogSink> allSinks = new HashMap<>();
	final AuditLogSink defaultSink;

	public SinkProvider(final Settings settings, final Client clientProvider, ThreadPool threadPool,
			final Path configPath) {
		this.settings = settings;
		this.clientProvider = clientProvider;
		this.threadPool = threadPool;
		this.configPath = configPath;

		// create default sink
		defaultSink = this.createSink("default", settings.get(ConfigConstants.SEARCHGUARD_AUDIT_TYPE_DEFAULT), settings,
				settings.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DEFAULT));
		if (defaultSink == null) {
			log.error("Default sink could not be created, auditlog will not work.");
		}

	}

	public AuditLogSink getSink(String sinkName) {
		if (allSinks.containsKey(sinkName)) {
			return allSinks.get(sinkName);
		}

		Settings sinkDefinition = settings
				.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_ENDPOINTS + "." + sinkName);

		if (sinkDefinition == null || sinkDefinition.size() == 0) {
			log.error("Sink with name {} does not exist in  configuration, skipping.", sinkName);
			return null;
		}
		String type = sinkDefinition.get("type");
		Settings sinkConfiguration = sinkDefinition.getAsSettings("config");
		if (type == null || sinkConfiguration == null || sinkConfiguration.size() == 0) {
			log.error(
					"Invalid configuration for endpoint {} found. Either type is missing or configuration is empty. Found type '{}' and configuration'{}'",
					sinkName, type, sinkConfiguration);
		}
		AuditLogSink sink = createSink(sinkName, type, this.settings, sinkConfiguration);
		if (sink == null) {
			log.error("Endpoint '{}' could not be created, check log file for further information.", sinkName);
			return null;
		}
		allSinks.put(sinkName, sink);
		if (log.isDebugEnabled()) {
			log.debug("sink '{}' created successfully.", sinkName);
		}
		return sink;
	}

	public AuditLogSink getDefaultSink() {
		return defaultSink;
	}

	public void close() {
		for (AuditLogSink sink : allSinks.values()) {
			close(sink);
		}		
	}

	protected void close(AuditLogSink sink) {
		try {
			log.info("Closing {}", sink.getClass().getSimpleName());
			sink.close();
		} catch (Exception ex) {
			log.info("Could not close sink '{}' due to '{}'", sink.getClass().getSimpleName(), ex.getMessage());
		}
	}

	private final AuditLogSink createSink(final String name, final String type, final Settings settings, final Settings sinkSettings) {
		AuditLogSink sink = null;
		if (type != null) {
			switch (type.toLowerCase()) {
			case "internal_elasticsearch":
				sink = new InternalESSink(name, settings, sinkSettings, configPath, clientProvider, threadPool);
				break;
			case "external_elasticsearch":
				try {
					sink = new ExternalESSink(name, settings, sinkSettings, configPath);
				} catch (Exception e) {
					log.error("Audit logging unavailable: Unable to setup HttpESAuditLog due to", e);
				}
				break;
			case "webhook":
				try {
					sink = new WebhookSink(name, settings, sinkSettings, configPath);
				} catch (Exception e1) {
					log.error("Audit logging unavailable: Unable to setup WebhookAuditLog due to", e1);
				}
				break;
			case "debug":
				sink = new DebugSink(name, settings, sinkSettings);
				break;
			case "log4j":
				sink = new Log4JSink(name, settings, sinkSettings);
				break;
			default:
				try {
					Class<?> delegateClass = Class.forName(type);

					if (AuditLogSink.class.isAssignableFrom(delegateClass)) {
						try {
							sink = (AuditLogSink) delegateClass
									.getConstructor(String.class, Settings.class, Settings.class, Settings.class, ThreadPool.class)
									.newInstance(name, settings, sinkSettings, threadPool);
						} catch (Throwable e) {
							sink = (AuditLogSink) delegateClass.getConstructor(String.class, Settings.class, Settings.class)
									.newInstance(name, settings, sinkSettings);
						}
					} else {
						log.error("Audit logging unavailable: '{}' is not a subclass of {}", type,
								AuditLogSink.class.getSimpleName());
					}
				} catch (Throwable e) { // we need really catch a Throwable here!
					log.error("Audit logging unavailable: Cannot instantiate object of class {} due to " + e, type);
				}
			}
		}
		return sink;
	}
}
