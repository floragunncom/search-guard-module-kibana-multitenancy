/*
 * Copyright 2016-2018 by floragunn GmbH - All rights reserved
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

package com.floragunn.searchguard.auditlog.routing;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.auditlog.impl.AuditMessage;
import com.floragunn.searchguard.auditlog.impl.AuditMessage.Category;
import com.floragunn.searchguard.auditlog.sink.AuditLogSink;
import com.floragunn.searchguard.auditlog.sink.SinkProvider;
import com.floragunn.searchguard.dlic.rest.support.Utils;
import com.floragunn.searchguard.support.ConfigConstants;

public class AuditMessageRouter {

	protected final Logger log = LogManager.getLogger(this.getClass());	
	final AuditLogSink defaultSink;
	final Map<Category, List<AuditLogSink>> categorySinks = new HashMap<>();
	final SinkProvider sinkProvider;
	final AsyncStoragePool storagePool;
	boolean hasMultipleEndpoints;
	
	public AuditMessageRouter(final Settings settings, final Client clientProvider, ThreadPool threadPool,
			final Path configPath) {
		this.sinkProvider = new SinkProvider(settings, clientProvider, threadPool, configPath);
		this.storagePool = new AsyncStoragePool(settings);
		
		// get the default sink
		this.defaultSink = sinkProvider.getDefaultSink();
		if (defaultSink == null) {
			log.warn("No default storage available, audit log may not work properly. Please check configuration. Using debug storage type instead.");			
		}
		
		// create sinks for categories/routes
		Map<String, Object> routesConfiguration = Utils.convertJsonToxToStructuredMap(settings.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_ROUTES));
		for (Entry<String, Object> routesEntry : routesConfiguration.entrySet()) {
			log.trace("Setting up routes for endpoint {}, configuraton is {}", routesEntry.getKey(), routesEntry.getValue());
			String categoryName = routesEntry.getKey();
			try {
				Category category = Category.valueOf(categoryName.toUpperCase());
				// support duplicate definitions
				List<AuditLogSink> sinksForCategory = categorySinks.get(category);
				if (categorySinks.get(category) != null) {
					log.warn("Duplicate routing configuration detected for category {}, skipping.", category);
					continue;
				} 
				sinksForCategory = createSinksForCategory(category, settings.getAsSettings(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_ROUTES + "." + categoryName));
				if (sinksForCategory.size() > 0) {
					hasMultipleEndpoints = true;
					categorySinks.put(category, sinksForCategory);
					if(log.isTraceEnabled()) {
						log.debug("Created {} endpoints for category {}", sinksForCategory.size(), category );
					}					
				} else {
					if(log.isDebugEnabled()) {
						log.debug("No valid endpoints found for category {}, category will not be added to route configuration", category );
					}										
				}

				
			} catch (Exception e ) {
				log.error("Invalid category '{}' found in routing configuration. Must be one of: {}", categoryName, Category.values());
			}
		}
		
	}

	private List<AuditLogSink> createSinksForCategory(Category category, Settings configuration) {
		List<AuditLogSink> sinksForCategory = new LinkedList<>();
		List<String> sinks = configuration.getAsList("endpoints");
		if (sinks == null || sinks.size() == 0) {
			log.error("No endpoints configured for category {}", category);
			return sinksForCategory;
		}
		for (String sinkName : sinks) {
			AuditLogSink sink = sinkProvider.getSink(sinkName);
			if (sink != null && !sinksForCategory.contains(sink)) {
				sinksForCategory.add(sink);	
			} else {
				log.error("Configured endpoint '{}' not available", sinkName);
			}
		}
		return sinksForCategory;
	}

	public void route(final AuditMessage msg) {
		if (!hasMultipleEndpoints) {
			store(defaultSink, msg);
		} else {
			for (AuditLogSink sink : getSinksFor(msg)) {
				store(sink, msg);
			}			
		}
	}
	
	private void store(AuditLogSink sink, AuditMessage msg) {
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

	@SuppressWarnings("unchecked")
	protected List<AuditLogSink> getSinksFor(AuditMessage message) {
		Category category = message.getCategory();
		if (categorySinks.containsKey(message.getCategory())) {
			return categorySinks.get(category);
		}
		return Collections.EMPTY_LIST;
	}

	public void close() {
		// shutdown storage pool
		storagePool.close();
		// close default
		sinkProvider.close();
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
