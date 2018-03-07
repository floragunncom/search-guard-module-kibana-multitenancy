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
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.dlic.rest.support.Utils;
import com.floragunn.searchguard.support.ConfigConstants;

public class SinkProvider {

	protected final Logger log = LogManager.getLogger(this.getClass());

	private final Client clientProvider;
	private final ThreadPool threadPool;
	private final Path configPath;
	private final Settings settings;
	final Map<String, AuditLogSink> allSinks = new HashMap<>();
	AuditLogSink defaultSink;
	AuditLogSink fallbackSink;

	public SinkProvider(final Settings settings, final Client clientProvider, ThreadPool threadPool, final Path configPath) {
		this.settings = settings;
		this.clientProvider = clientProvider;
		this.threadPool = threadPool;
		this.configPath = configPath;

		// fall back sink, make sure we don't lose messages
		Settings fallbackSinkSettings = settings.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_FALLBACK);
		if(!fallbackSinkSettings.isEmpty()) {
			this.fallbackSink = createSink("fallback", fallbackSinkSettings.get("type"), settings, fallbackSinkSettings.getAsSettings("config"));
		}
		// make sure we always have a fallback to write to
		if (this.fallbackSink == null) {
			this.fallbackSink = new DebugSink("fallback", null);
		}

		// create default sink
		defaultSink = this.createSink("default", settings.get(ConfigConstants.SEARCHGUARD_AUDIT_TYPE_DEFAULT), settings, settings.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DEFAULT));
		if (defaultSink == null) {
			log.error("Default endpoint could not be created, auditlog will not work properly. Using debug storage endpoint instead.");
			defaultSink = new DebugSink("default", fallbackSink);
		}
		allSinks.put("default", defaultSink);
		
		// create all other sinks
		Map<String, Object> sinkSettingsMap = Utils.convertJsonToxToStructuredMap(settings.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_ENDPOINTS));

		for (Entry<String, Object> sinkEntry : sinkSettingsMap.entrySet()) {
			String sinkName = sinkEntry.getKey();
			Settings sinkSettings = settings.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_ENDPOINTS + "." + sinkName);
			String type = sinkSettings.get("type");
			Settings sinkConfiguration = sinkSettings.getAsSettings("config");
			if (type == null) {
				log.error("No type defined for endpoint {}.", sinkName);
				continue;
			}
			AuditLogSink sink = createSink(sinkName, type, this.settings, sinkConfiguration);
			if (sink == null) {
				log.error("Endpoint '{}' could not be created, check log file for further information.", sinkName);
				continue;
			}
			allSinks.put(sinkName.toLowerCase(), sink);
			if (log.isDebugEnabled()) {
				log.debug("sink '{}' created successfully.", sinkName);
			}
		}
	}

	public AuditLogSink getSink(String sinkName) {
		return allSinks.get(sinkName.toLowerCase());
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
				sink = new InternalESSink(name, settings, sinkSettings, configPath, clientProvider, threadPool, fallbackSink);
				break;
			case "external_elasticsearch":
				try {
					sink = new ExternalESSink(name, settings, sinkSettings, configPath, fallbackSink);
				} catch (Exception e) {
					log.error("Audit logging unavailable: Unable to setup HttpESAuditLog due to", e);
				}
				break;
			case "webhook":
				try {
					sink = new WebhookSink(name, settings, sinkSettings, configPath, fallbackSink);
				} catch (Exception e1) {
					log.error("Audit logging unavailable: Unable to setup WebhookAuditLog due to", e1);
				}
				break;
			case "debug":
				sink = new DebugSink(name, fallbackSink);
				break;
			case "log4j":
				sink = new Log4JSink(name, settings, sinkSettings, fallbackSink);
				break;
			case "kafka":
				sink = new KafkaSink(name, settings, sinkSettings, fallbackSink);
				break;
			default:
				try {
					Class<?> delegateClass = Class.forName(type);
					if (AuditLogSink.class.isAssignableFrom(delegateClass)) {
						try {
							sink = (AuditLogSink) delegateClass.getConstructor(String.class, Settings.class, Settings.class, Path.class, Client.class, ThreadPool.class, AuditLogSink.class).newInstance(name, settings, sinkSettings, configPath,
									clientProvider, threadPool, fallbackSink);
						} catch (Throwable e) {
							sink = (AuditLogSink) delegateClass.getConstructor(String.class, Settings.class, Settings.class, AuditLogSink.class).newInstance(name, settings, sinkSettings, fallbackSink);
						}
					} else {
						log.error("Audit logging unavailable: '{}' is not a subclass of {}", type, AuditLogSink.class.getSimpleName());
					}
				} catch (Throwable e) { // we need really catch a Throwable here!
					log.error("Audit logging unavailable: Cannot instantiate object of class {} due to " + e, type);
				}
			}
		}
		return sink;
	}
}
