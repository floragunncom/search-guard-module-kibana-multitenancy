package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.PrivateKeyDetails;
import org.apache.http.ssl.PrivateKeyStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.file.FileHelper;

public class KeySetRetrieverTest {
	protected static MockIpdServer mockIdpServer;

	@BeforeClass
	public static void setUp() throws Exception {
		mockIdpServer = new MockIpdServer();
	}

	@AfterClass
	public static void tearDown() {
		if (mockIdpServer != null) {
			try {
				mockIdpServer.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Test
	public void cacheTest() {
		KeySetRetriever keySetRetriever = new KeySetRetriever(mockIdpServer.getDiscoverUri(), null, true);

		keySetRetriever.get();

		Assert.assertEquals(1, keySetRetriever.getOidcCacheMisses());
		Assert.assertEquals(0, keySetRetriever.getOidcCacheHits());

		keySetRetriever.get();
		Assert.assertEquals(1, keySetRetriever.getOidcCacheMisses());
		Assert.assertEquals(1, keySetRetriever.getOidcCacheHits());
	}

	@Test
	public void clientCertTest() throws Exception {

		try (MockIpdServer sslMockIdpServer = new MockIpdServer(8085, true) {
			@Override
			protected void handleDiscoverRequest(HttpRequest request, HttpResponse response, HttpContext context)
					throws HttpException, IOException {

				System.out.println(request);

				super.handleDiscoverRequest(request, response, context);

				/*
				 * X509Certificate[] certificateChain = (X509Certificate[])
				 * request.getAttribute("javax.servlet.request.X509Certificate");
				 * 
				 * if (certificateChain == null || certificateChain.length == 0 ||
				 * certificateChain[0] == null) {
				 * requestContext.abortWith(buildForbiddenResponse("No certificate chain found!"
				 * )); return; }
				 * 
				 * // The certificate of the client is always the first in the chain.
				 * X509Certificate clientCert = certificateChain[0];
				 */
			}
		}) {
			SSLContextBuilder sslContextBuilder = SSLContexts.custom();

			KeyStore trustStore = KeyStore.getInstance("JKS");
			InputStream trustStream = new FileInputStream(
					FileHelper.getAbsoluteFilePathFromClassPath("jwt/truststore.jks").toFile());
			trustStore.load(trustStream, "changeit".toCharArray());

			KeyStore keyStore = KeyStore.getInstance("JKS");
			InputStream keyStream = new FileInputStream(
					FileHelper.getAbsoluteFilePathFromClassPath("jwt/spock-keystore.jks").toFile());

			keyStore.load(keyStream, "changeit".toCharArray());

			sslContextBuilder.loadTrustMaterial(trustStore, null);

			sslContextBuilder.loadKeyMaterial(keyStore, "changeit".toCharArray(), new PrivateKeyStrategy() {

				@Override
				public String chooseAlias(Map<String, PrivateKeyDetails> aliases, Socket socket) {
					return "spock";
				}
			});

			SettingsBasedSSLConfigurator.SSLConfig sslConfig = new SettingsBasedSSLConfigurator.SSLConfig(
					sslContextBuilder.build(), new String[] { "TLSv1.2", "TLSv1.1" }, null, null);

			KeySetRetriever keySetRetriever = new KeySetRetriever(sslMockIdpServer.getDiscoverUri(), sslConfig, false);

			keySetRetriever.get();

		}
	}
}
