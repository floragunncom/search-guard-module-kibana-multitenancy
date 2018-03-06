package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import static com.floragunn.dlic.auth.http.jwt.keybyoidc.CxfTestTools.toJson;

import java.io.Closeable;
import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

class MockIpdServer implements Closeable {
	final static String CTX_DISCOVER = "/discover";
	final static String CTX_KEYS = "/api/oauth/keys";

	private HttpServer httpServer;
	private int port = 8081;
	private String uri = "http://localhost:" + port;

	MockIpdServer() throws IOException {
		httpServer = ServerBootstrap.bootstrap().setListenerPort(port)
				.registerHandler(CTX_DISCOVER, new HttpRequestHandler() {

					@Override
					public void handle(HttpRequest request, HttpResponse response, HttpContext context)
							throws HttpException, IOException {

						response.setStatusCode(200);
						response.setHeader("Cache-Control", "public, max-age=31536000");
						response.setEntity(new StringEntity("{\"jwks_uri\": \"" + uri + CTX_KEYS + "\",\n"
								+ "\"issuer\": \"" + uri + "\", \"unknownPropertyToBeIgnored\": 42}"));

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

		httpServer.start();
	}

	@Override
	public void close() throws IOException {
		httpServer.stop();
	}

	public HttpServer getHttpServer() {
		return httpServer;
	}

	public String getUri() {
		return uri;
	}

	public String getDiscoverUri() {
		return uri + CTX_DISCOVER;
	}

	public int getPort() {
		return port;
	}

}
