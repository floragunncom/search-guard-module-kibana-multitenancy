package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

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
}
