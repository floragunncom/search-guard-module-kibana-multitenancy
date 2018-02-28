package com.floragunn.searchguard.auditlog.routing;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.auditlog.impl.AuditMessage;
import com.floragunn.searchguard.auditlog.sink.AuditLogSink;
import com.floragunn.searchguard.auditlog.sink.SinkProvider;
import com.floragunn.searchguard.support.ConfigConstants;

public class AuditMessageRouter {

	protected final Logger log = LogManager.getLogger(this.getClass());

	private final SinkProvider sinkProvider;
	private final StoragePool storagePool;
	private final List<AuditLogSink> defaultSinks = new LinkedList<>();

	public AuditMessageRouter(final Settings settings, final Client clientProvider, ThreadPool threadPool,
			final Path configPath) {
		
		this.sinkProvider = new SinkProvider(clientProvider, threadPool, configPath);
		this.storagePool = new StoragePool(settings);

		// create all sinks configured, start with the default
		AuditLogSink defaultSink = sinkProvider.createSink(settings.get(ConfigConstants.SEARCHGUARD_AUDIT_TYPE_DEFAULT), settings,
				settings.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DEFAULT));
		if (defaultSink == null) {
			log.error("Default sink could not be created, auditlog will not work.");
		} else {
			defaultSinks.add(defaultSink);
		}

		// read extended config and create routing table
		// ... todo ...
	}

	public void route(final AuditMessage msg) {
		List<AuditLogSink> sinks = getSinksFor(msg);
		for (AuditLogSink sink : sinks) {
			if (sink.isHandlingBackpressure()) {
				sink.store(msg);
				if (log.isTraceEnabled()) {
					log.trace("stored on sink {} synchronously", sink.getClass().getSimpleName());
				}
			} else {
				storagePool.submit(msg, sink);
				if (log.isTraceEnabled()) {
					log.trace("will store on sink {} asynchronously", sink.getClass().getSimpleName());
				}
			}
		}
	}


	protected List<AuditLogSink> getSinksFor(AuditMessage message) {
		return defaultSinks;
	}

	public void close() {
		// shutdown storage pool
		storagePool.close();
		// close default
		close(defaultSinks);
		// close others
		// ...

	}

	protected void close(List<AuditLogSink> sinks) {
		for (AuditLogSink sink : sinks) {
			try {
				log.info("Closing {}", sink.getClass().getSimpleName());
				sink.close();
			} catch (Exception ex) {
				log.info("Could not close delegate '{}' due to '{}'", sink.getClass().getSimpleName(), ex.getMessage());
			}
		}
	}

}
