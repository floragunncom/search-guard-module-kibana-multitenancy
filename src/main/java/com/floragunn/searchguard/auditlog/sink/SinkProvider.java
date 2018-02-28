/*
 * Copyright 2016-2017 by floragunn GmbH - All rights reserved
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

public class SinkProvider {
	
	protected final Logger log = LogManager.getLogger(this.getClass());
	
	private final Client clientProvider;
	private final ThreadPool threadPool;
	private final Path configPath;
	
	public SinkProvider(final Client clientProvider, ThreadPool threadPool, final Path configPath) {
		this.clientProvider = clientProvider;
		this.threadPool = threadPool;
		this.configPath = configPath;
	}

	public final AuditLogSink createSink(final String type, final Settings settings, final Settings sinkSettings) {
		AuditLogSink sink = null;
		if (type != null) {
			switch (type.toLowerCase()) {
			case "internal_elasticsearch":
				sink = new InternalESSink(settings, sinkSettings, configPath, clientProvider, threadPool);
				break;
			case "external_elasticsearch":
				try {
					sink = new ExternalESSink(settings, sinkSettings, configPath);
				} catch (Exception e) {
					log.error("Audit logging unavailable: Unable to setup HttpESAuditLog due to", e);
				}
				break;
			case "webhook":
				try {
					sink = new WebhookSink(settings, sinkSettings, configPath);
                } catch (Exception e1) {
                    log.error("Audit logging unavailable: Unable to setup WebhookAuditLog due to", e1);
                }
				break;				
			case "debug":
				sink = new DebugSink(settings, sinkSettings);
				break;
			case "log4j":
				sink = new Log4JSink(settings, sinkSettings);
                break;
			default:
                try {
                    Class<?> delegateClass = Class.forName(type);

                    if (AuditLogSink.class.isAssignableFrom(delegateClass)) {
                        try {
                        	sink = (AuditLogSink) delegateClass.getConstructor(Settings.class, Settings.class,Settings.class, ThreadPool.class).newInstance(settings, sinkSettings, threadPool);
                        } catch (Throwable e) {
                        	sink = (AuditLogSink) delegateClass.getConstructor(Settings.class, Settings.class).newInstance(settings, sinkSettings);
                        }
                    } else {
                        log.error("Audit logging unavailable: '{}' is not a subclass of {}", type, AuditLogSink.class.getSimpleName());
                    }
                } catch (Throwable e) { //we need really catch a Throwable here!
                    log.error("Audit logging unavailable: Cannot instantiate object of class {} due to "+e, type);
                }
			}
		}
		return sink;
	}
}
