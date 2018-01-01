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
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.floragunn.searchguard.httpclient.HttpClient;
import com.floragunn.searchguard.httpclient.HttpClient.HttpClientBuilder;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.support.PemKeyReader;

public final class HttpESAuditLog extends AuditLogSink {

    private static final List<String> DEFAULT_TLS_PROTOCOLS = Arrays.asList(new String[] { "TLSv1.2", "TLSv1.1"});
	// config in elasticsearch.yml
	private final String index;
	private final String type;
	private final HttpClient client;
	private final List<String> servers;
	private DateTimeFormatter indexPattern;
	
    static final String PKCS12 = "PKCS12";

	public HttpESAuditLog(final Settings settings, final Path configPath, ThreadPool threadPool,
	        final IndexNameExpressionResolver resolver, final ClusterService clusterService) throws Exception {

		super(settings, threadPool, resolver, clusterService);
		
		servers = settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_HTTP_ENDPOINTS, Collections.singletonList("localhost:9200"));
		this.index = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_INDEX, "'sg6-auditlog-'YYYY.MM.dd");
		
		try {
            this.indexPattern = DateTimeFormat.forPattern(index);
        } catch (IllegalArgumentException e) {
            log.debug("Unable to parse index pattern due to {}. "
                    + "If you have no date pattern configured you can safely ignore this message", e.getMessage());
        }
		
		this.type = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_TYPE, "auditlog");
		final boolean verifyHostnames = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_SSL_VERIFY_HOSTNAMES, true);
		final boolean enableSsl = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_SSL_ENABLE_SSL, false);
		final boolean enableSslClientAuth = settings.getAsBoolean(ConfigConstants.SEARCHGUARD_AUDIT_SSL_ENABLE_SSL_CLIENT_AUTH , ConfigConstants.SEARCHGUARD_AUDIT_SSL_ENABLE_SSL_CLIENT_AUTH_DEFAULT);
		final String user = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_USERNAME);
		final String password = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_PASSWORD);

		final HttpClientBuilder builder = HttpClient.builder(servers.toArray(new String[0]));

		if (enableSsl) {
		    
		    final boolean pem = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMTRUSTEDCAS_FILEPATH, null) != null
                    || settings.get(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMTRUSTEDCAS_CONTENT, null) != null;
           
		    KeyStore effectiveTruststore;
		    KeyStore effectiveKeystore;
		    char[] effectiveKeyPassword;
		    String effectiveKeyAlias;
		    
		    if(pem) {
                X509Certificate[] trustCertificates = PemKeyReader.loadCertificatesFromStream(PemKeyReader.resolveStream(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMTRUSTEDCAS_CONTENT, settings));
                
                if(trustCertificates == null) {
                    trustCertificates = PemKeyReader.loadCertificatesFromFile(PemKeyReader.resolve(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMTRUSTEDCAS_FILEPATH, settings, configPath, true));
                }
                
                //for client authentication
                X509Certificate[] authenticationCertificate = PemKeyReader.loadCertificatesFromStream(PemKeyReader.resolveStream(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMCERT_CONTENT, settings));
                
                if(authenticationCertificate == null) {
                    authenticationCertificate = PemKeyReader.loadCertificatesFromFile(PemKeyReader.resolve(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMCERT_FILEPATH, settings, configPath, enableSslClientAuth));
                }
                
                PrivateKey authenticationKey = PemKeyReader.loadKeyFromStream(settings.get(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMKEY_PASSWORD), PemKeyReader.resolveStream(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMKEY_CONTENT, settings));
                
                if(authenticationKey == null) {
                    authenticationKey = PemKeyReader.loadKeyFromFile(settings.get(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMKEY_PASSWORD), PemKeyReader.resolve(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMKEY_FILEPATH, settings, configPath, enableSslClientAuth));    
                }
          
                effectiveKeyPassword = PemKeyReader.randomChars(12);
                effectiveKeyAlias = "al";
                effectiveTruststore = PemKeyReader.toTruststore(effectiveKeyAlias, trustCertificates);
                effectiveKeystore = PemKeyReader.toKeystore(effectiveKeyAlias, effectiveKeyPassword, authenticationCertificate, authenticationKey);
                
                if(log.isDebugEnabled()) {
                    log.debug("Use PEM to secure communication with auditlog server (client auth is {})", authenticationKey!=null);
                }
                
            } else {
                final KeyStore trustStore = PemKeyReader.loadKeyStore(PemKeyReader.resolve(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_FILEPATH, settings, configPath, true)
                        , settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_PASSWORD, SSLConfigConstants.DEFAULT_STORE_PASSWORD)
                        , settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_TYPE));
                
                //for client authentication
                final KeyStore keyStore = PemKeyReader.loadKeyStore(PemKeyReader.resolve(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_FILEPATH, settings, configPath, enableSslClientAuth)
                        , settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_PASSWORD, SSLConfigConstants.DEFAULT_STORE_PASSWORD)
                        , settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_TYPE));
                final String keyStorePassword = settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_PASSWORD, SSLConfigConstants.DEFAULT_STORE_PASSWORD);
                effectiveKeyPassword = keyStorePassword==null||keyStorePassword.isEmpty()?null:keyStorePassword.toCharArray();
                effectiveKeyAlias = settings.get(ConfigConstants.SEARCHGUARD_AUDIT_SSL_JKS_CERT_ALIAS, null);
                
                if(enableSslClientAuth && effectiveKeyAlias == null) {
                    throw new IllegalArgumentException(ConfigConstants.SEARCHGUARD_AUDIT_SSL_JKS_CERT_ALIAS+" not given");
                }
                
                effectiveTruststore = trustStore;
                effectiveKeystore = keyStore;
                
                if(log.isDebugEnabled()) {
                    log.debug("Use Trust-/Keystore to secure communication with LDAP server (client auth is {})", keyStore!=null);
                    log.debug("keyStoreAlias: {}",  effectiveKeyAlias);
                }
                
            }   
		    
		    final List<String> enabledCipherSuites = settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_SSL_ENABLED_SSL_CIPHERS, null);   
            final List<String> enabledProtocols = settings.getAsList(ConfigConstants.SEARCHGUARD_AUDIT_SSL_ENABLED_SSL_PROTOCOLS, DEFAULT_TLS_PROTOCOLS);   
            
            builder.setSupportedCipherSuites(enabledCipherSuites==null?null:enabledCipherSuites.toArray(new String[0]));
            builder.setSupportedProtocols(enabledProtocols.toArray(new String[0]));
		    
            builder.enableSsl(effectiveTruststore, verifyHostnames); //trust all aliases

            if (enableSslClientAuth) {
                builder.setPkiCredentials(effectiveKeystore, effectiveKeyPassword, effectiveKeyAlias);
            }
		}

		if (user != null && password != null) {
			builder.setBasicCredentials(user, password);
		}

		client = builder.build();
	}

	@Override
	public void close() throws IOException {
		if (client != null) {
			client.close();
		}
	}

	@Override
	public void store(final AuditMessage msg) {
		try {
			boolean successful = client.index(msg.toString(), getExpandedIndexName(indexPattern, index), type, true);

			if (!successful) {
				log.error("Unable to send audit log {} to one of these servers: {}", msg, servers);
			}
		} catch (Exception e) {
			log.error("Unable to send audit log {} due to", msg, e);
		}
	}
}
