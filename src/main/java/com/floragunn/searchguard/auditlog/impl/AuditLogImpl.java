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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.support.ConfigConstants;

public final class AuditLogImpl extends AbstractAuditLog {
    
    private final static int DEFAULT_THREAD_POOL_SIZE = 10;
    private final static int DEFAULT_THREAD_POOL_MAX_QUEUE_LEN = 100 * 1000;
    
	// package private for unit tests :(
    final ExecutorService pool;
        
    AuditLogSink delegate;
    
    private static void printLicenseInfo() {
        final StringBuilder sb = new StringBuilder();
        sb.append("******************************************************"+System.lineSeparator());
        sb.append("Search Guard Audit Log is not free software"+System.lineSeparator());
        sb.append("for commercial use in production."+System.lineSeparator());
        sb.append("You have to obtain a license if you "+System.lineSeparator());
        sb.append("use it in production."+System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("See https://floragunn.com/searchguard-validate-license"+System.lineSeparator());
        sb.append("In case of any doubt mail to <sales@floragunn.com>"+System.lineSeparator());
        sb.append("*****************************************************"+System.lineSeparator());
        
        final String licenseInfo = sb.toString();
        
        if(!Boolean.getBoolean("sg.display_lic_none")) {
            
            if(!Boolean.getBoolean("sg.display_lic_only_stdout")) {
                LogManager.getLogger(AuditLogImpl.class).warn(licenseInfo);
                System.err.println(licenseInfo);
            }
    
            System.out.println(licenseInfo);
        }
        
    }

    static {
        //printLicenseInfo();
    }
    
    private ThreadPoolExecutor createExecutor(final int threadPoolSize, final int maxQueueLen) {
        if(log.isDebugEnabled()) {
            log.debug("Create new executor with threadPoolSize: {} and maxQueueLen: {}", threadPoolSize, maxQueueLen);
        }
        return new ThreadPoolExecutor(threadPoolSize, threadPoolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(maxQueueLen));
    }

    public AuditLogImpl(final Settings settings, final Path configPath, Client clientProvider, ThreadPool threadPool,
            final IndexNameExpressionResolver resolver, final ClusterService clusterService) {
    	super(settings, threadPool, resolver, clusterService);
        final String type = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_TYPE, null);
        int threadPoolSize = settings.getAsInt(ConfigConstants.SEARCHGUARD_AUDIT_THREADPOOL_SIZE, DEFAULT_THREAD_POOL_SIZE).intValue();
        int threadPoolMaxQueueLen = settings.getAsInt(ConfigConstants.SEARCHGUARD_AUDIT_THREADPOOL_MAX_QUEUE_LEN, DEFAULT_THREAD_POOL_MAX_QUEUE_LEN).intValue();
        
        if (threadPoolSize <= 0) {
            threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
        }
        
        if (threadPoolMaxQueueLen <= 0) {
            threadPoolMaxQueueLen = DEFAULT_THREAD_POOL_MAX_QUEUE_LEN;
        }
        
        this.pool = createExecutor(threadPoolSize, threadPoolMaxQueueLen);        
      
        final String index = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_INDEX,"'sg6-auditlog-'YYYY.MM.dd");
        final String doctype = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_TYPE,"auditlog");
        
		if (type != null) {
			switch (type.toLowerCase()) {
			case "internal_elasticsearch":
				delegate = new ESAuditLog(settings, configPath, clientProvider, threadPool, index, doctype, resolver, clusterService);
				break;
			case "external_elasticsearch":
				try {
					delegate = new HttpESAuditLog(settings, configPath, threadPool, resolver, clusterService);
				} catch (Exception e) {
					log.error("Audit logging unavailable: Unable to setup HttpESAuditLog due to", e);
				}
				break;
			case "webhook":
				try {
                    delegate = new WebhookAuditLog(settings, configPath, threadPool, resolver, clusterService);
                } catch (Exception e1) {
                    log.error("Audit logging unavailable: Unable to setup WebhookAuditLog due to", e1);
                }
				break;				
			case "debug":
				delegate = new DebugAuditLog(settings, configPath, threadPool, resolver, clusterService);
				break;
			case "log4j":
                delegate = new Log4JAuditLog(settings, configPath, threadPool, resolver, clusterService);
                break;
			default:
                try {
                    Class<?> delegateClass = Class.forName(type);

                    if (AuditLogSink.class.isAssignableFrom(delegateClass)) {
                        try {
                            delegate = (AuditLogSink) delegateClass.getConstructor(Settings.class, ThreadPool.class).newInstance(settings, threadPool);
                        } catch (Throwable e) {
                            delegate = (AuditLogSink) delegateClass.getConstructor(Settings.class, Path.class, ThreadPool.class, IndexNameExpressionResolver.class, ClusterService.class)
                                    .newInstance(settings, configPath, threadPool, resolver, clusterService);
                        }
                    } else {
                        log.error("Audit logging unavailable: '{}' is not a subclass of {}", type, AuditLogSink.class.getSimpleName());
                    }
                } catch (Throwable e) { //we need really catch a Throwable here!
                    log.error("Audit logging unavailable: Cannot instantiate object of class {} due to "+e, type);
                }
			}
		}

        if(delegate != null) {
            log.info("Audit Log class: {}", delegate.getClass().getSimpleName());
            
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
                                log.warn("Exception while shutting down audit log {}", delegate);
                            }
                        }           
                    });
                    log.debug("Shutdown Hook registered");
                    return null;
                }
            });
            
        } else {
            log.info("Audit Log available but disabled");
        }        
    }

    @Override
    public void close() throws IOException {
        
        if(pool != null) {
            pool.shutdown(); // Disable new tasks from being submitted
                    
            try {
              // Wait a while for existing tasks to terminate
              if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    log.error("Pool did not terminate");
              }
            } catch (InterruptedException ie) {
              // (Re-)Cancel if current thread also interrupted
              pool.shutdownNow();
              // Preserve interrupt status
              Thread.currentThread().interrupt();
            }
        }
    	if(delegate != null) {
        	try {
                log.info("Closing {}", delegate.getClass().getSimpleName());           
                delegate.close();        		
        	} catch(Exception ex) {
                log.info("Could not close delegate '{}' due to '{}'", delegate.getClass().getSimpleName(), ex.getMessage());                   		
        	}
        }
        
    }

    @Override
    protected void save(final AuditMessage msg) {
    	// only save if we have a valid delegate

        if(delegate != null) {
            if(delegate.isHandlingBackpressure()) {
                delegate.store(msg);
                if(log.isTraceEnabled()) {
                    log.trace("stored on delegate {} synchronously", delegate.getClass().getSimpleName());
                }
            } else {
                saveAsync(msg); 
                if(log.isTraceEnabled()) {
                    log.trace("will store on delegate {} asynchronously", delegate.getClass().getSimpleName());
                }
            }
        } else {
            if(log.isTraceEnabled()) {
                log.trace("delegate is null");
            }
        }
    }
    
    protected void saveAsync(final AuditMessage msg) {
    	try {
            pool.submit(new Runnable() { 
                @Override
                public void run() {
                    delegate.store(msg);
                    if(log.isTraceEnabled()) {
                        log.trace("stored on delegate {} asynchronously", delegate.getClass().getSimpleName());
                    }
                }
            });                                                      		    		
    	} catch(Exception ex) {
            log.error("Could not submit audit message {} to thread pool for delegate '{}' due to '{}'", msg, delegate.getClass().getSimpleName(), ex.getMessage(), ex);                   		
    	}
    }
}
