package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import java.io.IOException;

import org.apache.cxf.rs.security.jose.jwk.JsonWebKeys;
import org.apache.cxf.rs.security.jose.jwk.JwkUtils;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floragunn.dlic.auth.http.jwt.oidc.json.OpenIdProviderConfiguration;

public class KeySetRetriever implements KeySetProvider {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	private String openIdConnectEndpoint;

	KeySetRetriever(String openIdConnectEndpoint) {
		this.openIdConnectEndpoint = openIdConnectEndpoint;
	}

	public JsonWebKeys get() throws AuthenticatorUnavailableExption {
		String uri = getJwksUri();

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

			HttpGet httpGet = new HttpGet(uri);

			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				StatusLine statusLine = response.getStatusLine();

				if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() >= 300) {
					throw new AuthenticatorUnavailableExption("Error while getting " + uri + ": " + statusLine);
				}

				HttpEntity httpEntity = response.getEntity();

				if (httpEntity == null) {
					throw new AuthenticatorUnavailableExption("Error while getting " + uri + ": Empty response entity");
				}

				JsonWebKeys keySet = JwkUtils.readJwkSet(httpEntity.getContent());

				return keySet;
			}
		} catch (IOException e) {
			throw new AuthenticatorUnavailableExption("Error while getting " + uri + ": " + e, e);
		}

	}

	String getJwksUri() throws AuthenticatorUnavailableExption {
		// TODO caching

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(openIdConnectEndpoint);
			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				StatusLine statusLine = response.getStatusLine();

				if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() >= 300) {
					throw new AuthenticatorUnavailableExption(
							"Error while getting " + openIdConnectEndpoint + ": " + statusLine);
				}

				HttpEntity httpEntity = response.getEntity();

				if (httpEntity == null) {
					throw new AuthenticatorUnavailableExption(
							"Error while getting " + openIdConnectEndpoint + ": Empty response entity");
				}

				OpenIdProviderConfiguration parsedEntity = objectMapper.readValue(httpEntity.getContent(),
						OpenIdProviderConfiguration.class);

				return parsedEntity.getJwksUri();

			}

		} catch (IOException e) {
			throw new AuthenticatorUnavailableExption("Error while getting " + openIdConnectEndpoint + ": " + e, e);
		}

	}

}
