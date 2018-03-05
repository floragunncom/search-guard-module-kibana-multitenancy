package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import static com.floragunn.dlic.auth.http.jwt.keybyoidc.CxfTestTools.toJson;

import java.io.IOException;
import java.util.HashMap;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.elasticsearch.common.settings.Settings;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.floragunn.dlic.auth.http.jwt.FakeRestRequest;
import com.floragunn.searchguard.user.AuthCredentials;
import com.google.common.collect.ImmutableMap;

public class HTTPJwtKeyByOpenIdConnectAuthenticatorTest {

	private final static String CTX_DISCOVER = "/discover";
	private final static String CTX_KEYS = "/api/oauth/keys";

	private static int port = 8081;
	private static String mockIdpServerUri = "http://localhost:" + port;
	protected static HttpServer mockIdpServer = null;

	@BeforeClass
	public static void setUp() throws Exception {
		mockIdpServer = ServerBootstrap.bootstrap().setListenerPort(port)
				.registerHandler(CTX_DISCOVER, new HttpRequestHandler() {

					@Override
					public void handle(HttpRequest request, HttpResponse response, HttpContext context)
							throws HttpException, IOException {

						response.setStatusCode(200);
						response.setEntity(new StringEntity("{\"jwks_uri\": \"" + mockIdpServerUri + CTX_KEYS + "\",\n"
								+ "\"issuer\": \"" + mockIdpServerUri + "\", \"unknownPropertyToBeIgnored\": 42}"));

					}
				}).registerHandler(CTX_KEYS, new HttpRequestHandler() {

					@Override
					public void handle(HttpRequest request, HttpResponse response, HttpContext context)
							throws HttpException, IOException {

						response.setStatusCode(200);
						response.setEntity(new StringEntity(toJson(TestJwks.ALL)));

					}
				})

				.create();

		mockIdpServer.start();

	}

	@AfterClass
	public static void tearDown() {
		if (mockIdpServer != null) {
			try {
				mockIdpServer.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Test
	public void basicTest() {
		Settings settings = Settings.builder().put("openid_connect_url", mockIdpServerUri + CTX_DISCOVER).build();

		HTTPJwtKeyByOpenIdConnectAuthenticator jwtAuth = new HTTPJwtKeyByOpenIdConnectAuthenticator(settings, null);

		AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(
				ImmutableMap.of("Authorization", TestJwts.MC_COY_SIGNED_OCT_1), new HashMap<String, String>()), null);

		Assert.assertNotNull(creds);
		Assert.assertEquals(TestJwts.MCCOY_SUBJECT, creds.getUsername());
		Assert.assertEquals(TestJwts.TEST_AUDIENCE, creds.getAttributes().get("attr.jwt.aud"));
		Assert.assertEquals(0, creds.getBackendRoles().size());
		Assert.assertEquals(3, creds.getAttributes().size());
	}

	@Test
	public void bearerTest() {
		Settings settings = Settings.builder().put("openid_connect_url", mockIdpServerUri + CTX_DISCOVER).build();

		HTTPJwtKeyByOpenIdConnectAuthenticator jwtAuth = new HTTPJwtKeyByOpenIdConnectAuthenticator(settings, null);

		AuthCredentials creds = jwtAuth.extractCredentials(
				new FakeRestRequest(ImmutableMap.of("Authorization", "Bearer " + TestJwts.MC_COY_SIGNED_OCT_1),
						new HashMap<String, String>()),
				null);

		Assert.assertNotNull(creds);
		Assert.assertEquals(TestJwts.MCCOY_SUBJECT, creds.getUsername());
		Assert.assertEquals(TestJwts.TEST_AUDIENCE, creds.getAttributes().get("attr.jwt.aud"));
		Assert.assertEquals(0, creds.getBackendRoles().size());
		Assert.assertEquals(3, creds.getAttributes().size());
	}

	@Test
	public void testRoles() throws Exception {
		Settings settings = Settings.builder().put("openid_connect_url", mockIdpServerUri + CTX_DISCOVER)
				.put("roles_key", TestJwts.ROLES_CLAIM).build();

		HTTPJwtKeyByOpenIdConnectAuthenticator jwtAuth = new HTTPJwtKeyByOpenIdConnectAuthenticator(settings, null);

		AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(
				ImmutableMap.of("Authorization", TestJwts.MC_COY_SIGNED_OCT_1), new HashMap<String, String>()), null);

		Assert.assertNotNull(creds);
		Assert.assertEquals(TestJwts.MCCOY_SUBJECT, creds.getUsername());
		Assert.assertEquals(TestJwts.TEST_ROLES, creds.getBackendRoles());
	}

	@Test
	public void testExp() throws Exception {
		Settings settings = Settings.builder().put("openid_connect_url", mockIdpServerUri + CTX_DISCOVER).build();

		HTTPJwtKeyByOpenIdConnectAuthenticator jwtAuth = new HTTPJwtKeyByOpenIdConnectAuthenticator(settings, null);

		AuthCredentials creds = jwtAuth.extractCredentials(
				new FakeRestRequest(ImmutableMap.of("Authorization", TestJwts.MC_COY_EXPIRED_SIGNED_OCT_1),
						new HashMap<String, String>()),
				null);

		Assert.assertNull(creds);
	}

	@Test
	public void testRS256() throws Exception {

		Settings settings = Settings.builder().put("openid_connect_url", mockIdpServerUri + CTX_DISCOVER).build();

		HTTPJwtKeyByOpenIdConnectAuthenticator jwtAuth = new HTTPJwtKeyByOpenIdConnectAuthenticator(settings, null);

		AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(
				ImmutableMap.of("Authorization", TestJwts.MC_COY_SIGNED_RSA_1), new HashMap<String, String>()), null);

		Assert.assertNotNull(creds);
		Assert.assertEquals(TestJwts.MCCOY_SUBJECT, creds.getUsername());
		Assert.assertEquals(TestJwts.TEST_AUDIENCE, creds.getAttributes().get("attr.jwt.aud"));
		Assert.assertEquals(0, creds.getBackendRoles().size());
		Assert.assertEquals(3, creds.getAttributes().size());
	}

	@Test
	public void testBadSignature() throws Exception {

		Settings settings = Settings.builder().put("openid_connect_url", mockIdpServerUri + CTX_DISCOVER).build();

		HTTPJwtKeyByOpenIdConnectAuthenticator jwtAuth = new HTTPJwtKeyByOpenIdConnectAuthenticator(settings, null);

		AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(
				ImmutableMap.of("Authorization", TestJwts.MC_COY_SIGNED_RSA_X), new HashMap<String, String>()), null);

		Assert.assertNull(creds);
	}

}
