package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import java.io.IOException;

import org.apache.cxf.rs.security.jose.jwk.JsonWebKeys;
import org.apache.cxf.rs.security.jose.jwk.JwkUtils;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floragunn.dlic.auth.http.jwt.keybyoidc.SettingsBasedSSLConfigurator.SSLConfig;
import com.floragunn.dlic.auth.http.jwt.oidc.json.OpenIdProviderConfiguration;

public class KeySetRetriever implements KeySetProvider {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	private String openIdConnectEndpoint;
	private SSLConfig sslConfig;
	private int httpTimeoutMs = 10000;

	KeySetRetriever(String openIdConnectEndpoint, SSLConfig sslConfig) {
		this.openIdConnectEndpoint = openIdConnectEndpoint;
		this.sslConfig = sslConfig;
	}

	public JsonWebKeys get() throws AuthenticatorUnavailableException {
		String uri = getJwksUri();

		try (CloseableHttpClient httpClient = createHttpClient()) {

			HttpGet httpGet = new HttpGet(uri);

			RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(getHttpTimeoutMs())
					.setConnectTimeout(getHttpTimeoutMs()).setSocketTimeout(getHttpTimeoutMs()).build();

			httpGet.setConfig(requestConfig);

			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				StatusLine statusLine = response.getStatusLine();

				if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() >= 300) {
					throw new AuthenticatorUnavailableException("Error while getting " + uri + ": " + statusLine);
				}

				HttpEntity httpEntity = response.getEntity();

				if (httpEntity == null) {
					throw new AuthenticatorUnavailableException(
							"Error while getting " + uri + ": Empty response entity");
				}

				JsonWebKeys keySet = JwkUtils.readJwkSet(httpEntity.getContent());

				return keySet;
			}
		} catch (IOException e) {
			throw new AuthenticatorUnavailableException("Error while getting " + uri + ": " + e, e);
		}

	}

	String getJwksUri() throws AuthenticatorUnavailableException {
		// TODO caching

		try (CloseableHttpClient httpClient = createHttpClient()) {

			HttpGet httpGet = new HttpGet(openIdConnectEndpoint);

			RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(getHttpTimeoutMs())
					.setConnectTimeout(getHttpTimeoutMs()).setSocketTimeout(getHttpTimeoutMs()).build();

			httpGet.setConfig(requestConfig);

			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				StatusLine statusLine = response.getStatusLine();

				if (statusLine.getStatusCode() < 200 || statusLine.getStatusCode() >= 300) {
					throw new AuthenticatorUnavailableException(
							"Error while getting " + openIdConnectEndpoint + ": " + statusLine);
				}

				HttpEntity httpEntity = response.getEntity();

				if (httpEntity == null) {
					throw new AuthenticatorUnavailableException(
							"Error while getting " + openIdConnectEndpoint + ": Empty response entity");
				}

				OpenIdProviderConfiguration parsedEntity = objectMapper.readValue(httpEntity.getContent(),
						OpenIdProviderConfiguration.class);

				return parsedEntity.getJwksUri();

			}

		} catch (IOException e) {
			throw new AuthenticatorUnavailableException("Error while getting " + openIdConnectEndpoint + ": " + e, e);
		}

	}

	public int getHttpTimeoutMs() {
		return httpTimeoutMs;
	}

	public void setHttpTimeoutMs(int httpTimeoutMs) {
		this.httpTimeoutMs = httpTimeoutMs;
	}

	private CloseableHttpClient createHttpClient() {
		HttpClientBuilder builder = HttpClients.custom();

		if (sslConfig != null) {
			builder.setSSLSocketFactory(sslConfig.toSSLConnectionSocketFactory());
		}

		return builder.build();
	}
}
