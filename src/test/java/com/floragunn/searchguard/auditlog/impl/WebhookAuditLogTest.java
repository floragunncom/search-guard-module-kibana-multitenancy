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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.entity.ContentType;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.elasticsearch.common.settings.Settings;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.floragunn.searchguard.auditlog.impl.AuditMessage.Category;
import com.floragunn.searchguard.auditlog.impl.WebhookAuditLog.WebhookFormat;
import com.floragunn.searchguard.test.helper.file.FileHelper;

public class WebhookAuditLogTest {
    
    protected HttpServer server = null;

    @Before
    @After
    public void tearDown() {
        if(server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                //ignore
            }
        }
    }
    
	@Test
	public void invalidConfTest() throws Exception {
		AuditMessage msg = MockAuditMessageFactory.validAuditMessage();

		// provide no settings, audit log not available
		Settings settings = Settings.builder()
		        .put("path.home", ".")
		        .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
		        .build();
		MockWebhookAuditLog auditlog = new MockWebhookAuditLog(settings);
		auditlog.store(msg);
		Assert.assertEquals(null, auditlog.webhookFormat);

	}

	@SuppressWarnings("resource")
	@Test
	public void formatsTest() throws Exception {

		String url = "http://localhost";
		AuditMessage msg = MockAuditMessageFactory.validAuditMessage();

		// provide no format, defaults to TEXT
		Settings settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("path.home", ".")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
                .put("searchguard.ssl.transport.enforce_hostname_verification", false)
				.build();
		MockWebhookAuditLog auditlog = new MockWebhookAuditLog(settings);
		auditlog.store(msg);
		Assert.assertEquals(WebhookFormat.TEXT, auditlog.webhookFormat);
		Assert.assertEquals(ContentType.TEXT_PLAIN, auditlog.webhookFormat.getContentType());
		Assert.assertTrue(auditlog.payload, !auditlog.payload.startsWith("{\"text\":"));

		// provide faulty format, defaults to TEXT
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "idonotexist")
				.put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.put("path.home", ".")
				.build();
		auditlog = new MockWebhookAuditLog(settings);
		auditlog.store(msg);
		Assert.assertEquals(WebhookFormat.TEXT, auditlog.webhookFormat);
		Assert.assertEquals(ContentType.TEXT_PLAIN, auditlog.webhookFormat.getContentType());
		Assert.assertTrue(auditlog.payload, !auditlog.payload.startsWith("{\"text\":"));
		auditlog.close();

		// TEXT
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "text")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))				
				.put("path.home", ".")
				.build();
		auditlog = new MockWebhookAuditLog(settings);
		auditlog.store(msg);
		Assert.assertEquals(WebhookFormat.TEXT, auditlog.webhookFormat);
		Assert.assertEquals(ContentType.TEXT_PLAIN, auditlog.webhookFormat.getContentType());
		Assert.assertTrue(auditlog.payload, !auditlog.payload.startsWith("{\"text\":"));
		Assert.assertTrue(auditlog.payload, auditlog.payload.contains("audit_utc_timestamp"));
		Assert.assertTrue(auditlog.payload, auditlog.payload.contains("audit_request_remote_address"));

		// JSON
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "json")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
                .put("path.home", ".")
				.build();
		auditlog = new MockWebhookAuditLog(settings);
		auditlog.store(msg);
		System.out.println(auditlog.payload);
		Assert.assertEquals(WebhookFormat.JSON, auditlog.webhookFormat);
		Assert.assertEquals(ContentType.APPLICATION_JSON, auditlog.webhookFormat.getContentType());
		Assert.assertTrue(auditlog.payload, !auditlog.payload.startsWith("{\"text\":"));
		Assert.assertTrue(auditlog.payload, auditlog.payload.contains("audit_utc_timestamp"));
        Assert.assertTrue(auditlog.payload, auditlog.payload.contains("audit_request_remote_address"));

		// SLACK
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "slack")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))				
				.put("path.home", ".")
				.build();
		auditlog = new MockWebhookAuditLog(settings);
		auditlog.store(msg);
		Assert.assertEquals(WebhookFormat.SLACK, auditlog.webhookFormat);
		Assert.assertEquals(ContentType.APPLICATION_JSON, auditlog.webhookFormat.getContentType());
		Assert.assertTrue(auditlog.payload, auditlog.payload.startsWith("{\"text\":"));
		Assert.assertTrue(auditlog.payload, auditlog.payload.contains("audit_utc_timestamp"));
        Assert.assertTrue(auditlog.payload, auditlog.payload.contains("audit_request_remote_address"));
	}

	@Test
	@SuppressWarnings("resource")
	public void invalidUrlTest() throws Exception {

		String url = "faultyurl";

		final Settings settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "slack")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.put("path.home", ".")
				.build();

		MockWebhookAuditLog auditlog = new MockWebhookAuditLog(settings);
		AuditMessage msg = MockAuditMessageFactory.validAuditMessage();
		auditlog.store(msg);
		Assert.assertEquals(null, auditlog.url);
		Assert.assertEquals(null, auditlog.payload);
		Assert.assertEquals(null, auditlog.webhookUrl);
	}

	@Test
	public void noServerRunningHttpTest() throws Exception {
		String url = "http://localhost:8080/endpoint";

		Settings settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "slack")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.put("path.home", ".")
				.build();

		// just make sure no exception is thrown
		WebhookAuditLog auditlog = new WebhookAuditLog(settings, null, null, null, null);
		AuditMessage msg = MockAuditMessageFactory.validAuditMessage();
		auditlog.store(msg);
	}

	@Test
	public void postGetHttpTest() throws Exception {
		TestHttpHandler handler = new TestHttpHandler();

		server = ServerBootstrap.bootstrap()
				.setListenerPort(8080)
				.setServerInfo("Test/1.1")
				.registerHandler("*", handler)
				.create();

		server.start();

		String url = "http://localhost:8080/endpoint";

		// SLACK
		Settings settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "slack")
				.put("path.home", ".")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.build();

		WebhookAuditLog auditlog = new WebhookAuditLog(settings, null, null, null, null);
		AuditMessage msg = MockAuditMessageFactory.validAuditMessage();
		auditlog.store(msg);
		Assert.assertTrue(handler.method.equals("POST"));
		Assert.assertTrue(handler.body != null);
		Assert.assertTrue(handler.body.startsWith("{\"text\":"));
		assertStringContainsAllKeysAndValues(handler.body);

		// TEXT
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "texT")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.put("path.home", ".")
				.build();

		auditlog = new WebhookAuditLog(settings, null, null, null, null);
		auditlog.store(msg);
		Assert.assertTrue(handler.method.equals("POST"));
		Assert.assertTrue(handler.body != null);
		System.out.println(handler.body);
		Assert.assertFalse(handler.body.contains("{"));
		assertStringContainsAllKeysAndValues(handler.body);

		// JSON
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "JSon")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.put("path.home", ".")
				.build();

		auditlog = new WebhookAuditLog(settings, null, null, null, null);
		auditlog.store(msg);
		Assert.assertTrue(handler.method.equals("POST"));
		Assert.assertTrue(handler.body != null);
		Assert.assertTrue(handler.body.contains("{"));
		assertStringContainsAllKeysAndValues(handler.body);

		// URL POST
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "URL_PARAMETER_POST")
				.put("path.home", ".")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.build();

		auditlog = new WebhookAuditLog(settings, null, null, null, null);
		auditlog.store(msg);
		Assert.assertTrue(handler.method.equals("POST"));
		Assert.assertTrue(handler.body.equals(""));
		Assert.assertTrue(!handler.body.contains("{"));
		assertStringContainsAllKeysAndValues(URLDecoder.decode(handler.uri, StandardCharsets.UTF_8.displayName()));

		// URL GET
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "URL_PARAMETER_GET")
				.put("path.home", ".")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.build();

		auditlog = new WebhookAuditLog(settings, null, null, null, null);
		auditlog.store(msg);
		Assert.assertTrue(handler.method.equals("GET"));
		Assert.assertTrue(handler.body.equals(""));
		Assert.assertTrue(!handler.body.contains("{"));
		assertStringContainsAllKeysAndValues(URLDecoder.decode(handler.uri, StandardCharsets.UTF_8.displayName()));
		server.shutdown(3l, TimeUnit.SECONDS);
	}

	@Test
	public void httpsTestWithoutTLSServer() throws Exception {

		TestHttpHandler handler = new TestHttpHandler();

		server = ServerBootstrap.bootstrap()
				.setListenerPort(8081)
				.setServerInfo("Test/1.1")
				.registerHandler("*", handler)
				.create();

		server.start();

		String url = "https://localhost:8081/endpoint";

		Settings settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "slack")
				.put("path.home", ".")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.build();

		WebhookAuditLog auditlog = new WebhookAuditLog(settings, null, null, null, null);
		AuditMessage msg = MockAuditMessageFactory.validAuditMessage();
		auditlog.store(msg);
		Assert.assertTrue(handler.method == null);
		Assert.assertTrue(handler.body == null);
		Assert.assertTrue(handler.uri == null);
		server.shutdown(3l, TimeUnit.SECONDS);
	}

	@Test
	public void httpsTest() throws Exception {

		TestHttpHandler handler = new TestHttpHandler();

		server = ServerBootstrap.bootstrap()
				.setListenerPort(8082)
				.setServerInfo("Test/1.1")
				.setSslContext(createSSLContext())
				.registerHandler("*", handler)
				.create();

		server.start();

		String url = "https://localhost:8082/endpoint";
		
		// try with ssl verification on, must fail
		Settings settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "slack")
				.put("path.home", ".")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore_fail.jks"))
				.build();

		WebhookAuditLog auditlog = new WebhookAuditLog(settings, null, null, null, null);
		AuditMessage msg = MockAuditMessageFactory.validAuditMessage();
		auditlog.store(msg);
		Assert.assertTrue(handler.method, handler.method == null);
		Assert.assertTrue(handler.body, handler.body == null);
		Assert.assertTrue(handler.uri, handler.uri == null);

		// wrong key for webhook.ssl.verify
		handler.reset();
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "slack")
				.put("searchguard.audit.config.webhook.ssl.verify", "foobar")
				.put("path.home", ".")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.build();
		try {
            auditlog = new WebhookAuditLog(settings, null, null, null, null);
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Failed to parse value [foobar]"));
        }

		// disable ssl verification, call must succeed now
		handler.reset();
		settings = Settings.builder()
				.put("searchguard.audit.config.webhook.url", url)
				.put("searchguard.audit.config.webhook.format", "jSoN")
				.put("searchguard.audit.config.webhook.ssl.verify", false)
				.put("path.home", ".")
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks"))
				.build();
		auditlog = new WebhookAuditLog(settings, null, null, null, null);
		auditlog.store(msg);
		Assert.assertTrue(handler.method.equals("POST"));
		Assert.assertTrue(handler.body != null);
		Assert.assertTrue(handler.body.contains("{"));
		assertStringContainsAllKeysAndValues(handler.body);
				
		server.shutdown(3l, TimeUnit.SECONDS);
	}
	
	@Test
    public void httpsTestPem() throws Exception {

        TestHttpHandler handler = new TestHttpHandler();

        server = ServerBootstrap.bootstrap()
                .setListenerPort(8083)
                .setServerInfo("Test/1.1")
                .setSslContext(createSSLContext())
                .registerHandler("*", handler)
                .create();

        server.start();
        AuditMessage msg = MockAuditMessageFactory.validAuditMessage();

        String url = "https://localhost:8083/endpoint";
        
        // try with ssl verification on, must fail
        Settings settings = Settings.builder()
                .put("searchguard.audit.config.webhook.url", url)
                .put("searchguard.audit.config.webhook.format", "slack")
                .put("path.home", ".")
                .put("searchguard.audit.config.webhook.ssl.pemtrustedcas_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("auditlog/chain-ca.pem"))
                .build();

        WebhookAuditLog auditlog = new WebhookAuditLog(settings, null, null, null, null);
        auditlog.store(msg);
        Assert.assertTrue(handler.method.equals("POST"));
        Assert.assertTrue(handler.body != null);
        Assert.assertTrue(handler.body.contains("{"));
        assertStringContainsAllKeysAndValues(handler.body);

        // disable ssl verification, call must succeed now
        handler.reset();
        settings = Settings.builder()
                .put("searchguard.audit.config.webhook.url", url)
                .put("searchguard.audit.config.webhook.format", "jSoN")
                .put("searchguard.audit.config.webhook.ssl.verify", false)
                .put("path.home", ".")
                .build();
        auditlog = new WebhookAuditLog(settings, null, null, null, null);
        auditlog.store(msg);
        Assert.assertTrue(handler.method.equals("POST"));
        Assert.assertTrue(handler.body != null);
        Assert.assertTrue(handler.body.contains("{"));
        assertStringContainsAllKeysAndValues(handler.body);
        
        // disable ssl verification, call must succeed now
        handler.reset();
        settings = Settings.builder()
                .put("searchguard.audit.config.webhook.url", url)
                .put("searchguard.audit.config.webhook.format", "jSoN")
                .put("searchguard.audit.config.webhook.ssl.verify", true)
                .put("path.home", ".")
                .build();
        auditlog = new WebhookAuditLog(settings, null, null, null, null);
        auditlog.store(msg);
        Assert.assertNull(handler.method);
                
        server.shutdown(3l, TimeUnit.SECONDS);
    }
	
	// for TLS support on our in-memory server
	private SSLContext createSSLContext() {
		try {
			final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory
					.getDefaultAlgorithm());
			final KeyStore trustStore = KeyStore.getInstance("JKS");
			InputStream trustStream = new FileInputStream(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/truststore.jks").toFile());
			trustStore.load(trustStream, "changeit".toCharArray());
			tmf.init(trustStore);

			final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());			
			final KeyStore keyStore = KeyStore.getInstance("JKS");
			InputStream keyStream = new FileInputStream(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/node-0-keystore.jks").toFile());

			keyStore.load(keyStream, "changeit".toCharArray());
			kmf.init(keyStore, "changeit".toCharArray());

			SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			return sslContext;
		} catch (final GeneralSecurityException | IOException exc) {
			throw new RuntimeException(exc);
		}
	}

	private void assertStringContainsAllKeysAndValues(String in) {
	    System.out.println(in);
		Assert.assertTrue(in, in.contains(AuditMessage.FORMAT_VERSION));
		Assert.assertTrue(in, in.contains(AuditMessage.CATEGORY));
		Assert.assertTrue(in, in.contains(AuditMessage.FORMAT_VERSION));
		Assert.assertTrue(in, in.contains(AuditMessage.REMOTE_ADDRESS));
		Assert.assertTrue(in, in.contains(AuditMessage.ORIGIN));
		Assert.assertTrue(in, in.contains(AuditMessage.REQUEST_LAYER));
		Assert.assertTrue(in, in.contains(AuditMessage.TRANSPORT_REQUEST_TYPE));
		Assert.assertTrue(in, in.contains(AuditMessage.UTC_TIMESTAMP));
		Assert.assertTrue(in, in.contains(Category.FAILED_LOGIN.name()));
		Assert.assertTrue(in, in.contains("FAILED_LOGIN"));
		Assert.assertTrue(in, in.contains("John Doe"));
		Assert.assertTrue(in, in.contains("8.8.8.8"));
		//Assert.assertTrue(in, in.contains("CN=kirk,OU=client,O=client,L=test,C=DE"));
	}
}
