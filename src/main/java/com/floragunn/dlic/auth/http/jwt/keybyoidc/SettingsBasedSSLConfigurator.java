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

package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import java.net.Socket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.PrivateKeyDetails;
import org.apache.http.ssl.PrivateKeyStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.common.settings.Settings;

import com.floragunn.searchguard.ssl.util.SSLConfigConstants;
import com.floragunn.searchguard.support.PemKeyReader;
import com.google.common.collect.ImmutableList;

public class SettingsBasedSSLConfigurator {

	public static final String CERT_ALIAS = "cert_alias";
	public static final String ENABLE_SSL = "enable_ssl";
	public static final String ENABLE_SSL_CLIENT_AUTH = "enable_ssl_client_auth";
	public static final String PEMKEY_FILEPATH = "pemkey_filepath";
	public static final String PEMKEY_CONTENT = "pemkey_content";
	public static final String PEMKEY_PASSWORD = "pemkey_password";
	public static final String PEMCERT_FILEPATH = "pemcert_filepath";
	public static final String PEMCERT_CONTENT = "pemcert_content";
	public static final String PEMTRUSTEDCAS_CONTENT = "pemtrustedcas_content";
	public static final String PEMTRUSTEDCAS_FILEPATH = "pemtrustedcas_filepath";
	public static final String VERIFY_HOSTNAMES = "verify_hostnames";

	private static final List<String> DEFAULT_TLS_PROTOCOLS = ImmutableList.of("TLSv1.2", "TLSv1.1");

	private final SSLContextBuilder delegate = SSLContexts.custom();
	private final Settings settings;
	private final String settingsKeyPrefix;
	private final Path configPath;

	private boolean enabled;
	private boolean enableSslClientAuth;
	private KeyStore effectiveTruststore;
	private KeyStore effectiveKeystore;
	private char[] effectiveKeyPassword;
	private String effectiveKeyAlias;

	SettingsBasedSSLConfigurator(Settings settings, Path configPath, String settingsKeyPrefix) {
		this.settings = settings;
		this.configPath = configPath;
		this.settingsKeyPrefix = normalizeSettingsKeyPrefix(settingsKeyPrefix);
	}

	SSLContext buildSSLContext() throws Exception {
		// XXX Note: I'm not happy at all with throws Exception in the signature. This
		// stems from the methods in PemKeyReader which also are just declared as throws
		// Exception. Maybe, this could be fine-tuned

		configureWithSettings();

		if (!this.enabled) {
			return null;
		}

		return delegate.build();
	}

	SSLConfig buildSSLConfig() throws Exception {
		SSLContext sslContext = buildSSLContext();

		if (sslContext == null) {
			// disabled
			return null;
		}

		return new SSLConfig(sslContext, getSupportedProtocols(), getSupportedCipherSuites(), getHostnameVerifier());
	}

	private HostnameVerifier getHostnameVerifier() {
		if (getSettingAsBoolean(VERIFY_HOSTNAMES, true)) {
			return new DefaultHostnameVerifier();
		} else {
			return NoopHostnameVerifier.INSTANCE;
		}
	}

	private String[] getSupportedProtocols() {
		return getSettingAsArray("enabled_ssl_protocols", DEFAULT_TLS_PROTOCOLS);
	}

	private String[] getSupportedCipherSuites() {
		return getSettingAsArray("enabled_ssl_ciphers", null);

	}

	private void configureWithSettings() throws Exception {
		this.enabled = getSettingAsBoolean(ENABLE_SSL, false);

		if (!this.enabled) {
			return;
		}

		this.enableSslClientAuth = getSettingAsBoolean(ENABLE_SSL_CLIENT_AUTH, false);

		if (settings.get(PEMTRUSTEDCAS_FILEPATH, null) != null || settings.get(PEMTRUSTEDCAS_CONTENT, null) != null) {
			initFromPem();
		} else {
			initFromKeyStore();
		}

		if (enableSslClientAuth) {
			if (effectiveTruststore != null) {
				delegate.loadTrustMaterial(effectiveTruststore, null);
			}

			if (effectiveKeystore != null) {
				try {
					delegate.loadKeyMaterial(effectiveKeystore, effectiveKeyPassword, new PrivateKeyStrategy() {

						@Override
						public String chooseAlias(Map<String, PrivateKeyDetails> aliases, Socket socket) {
							if (aliases == null || aliases.isEmpty()) {
								return effectiveKeyAlias;
							}

							if (effectiveKeyAlias == null || effectiveKeyAlias.isEmpty()) {
								return aliases.keySet().iterator().next();
							}

							return effectiveKeyAlias;
						}
					});
				} catch (UnrecoverableKeyException e) {
					throw new RuntimeException(e);
				}
			}
		}

	}

	private void initFromPem() throws Exception {
		X509Certificate[] trustCertificates = PemKeyReader.loadCertificatesFromStream(
				PemKeyReader.resolveStream(settingsKeyPrefix + PEMTRUSTEDCAS_CONTENT, settings));

		if (trustCertificates == null) {
			trustCertificates = PemKeyReader.loadCertificatesFromFile(
					PemKeyReader.resolve(settingsKeyPrefix + PEMTRUSTEDCAS_FILEPATH, settings, configPath, true));
		}

		// for client authentication
		X509Certificate[] authenticationCertificate = PemKeyReader
				.loadCertificatesFromStream(PemKeyReader.resolveStream(settingsKeyPrefix + PEMCERT_CONTENT, settings));

		if (authenticationCertificate == null) {
			authenticationCertificate = PemKeyReader.loadCertificatesFromFile(PemKeyReader
					.resolve(settingsKeyPrefix + PEMCERT_FILEPATH, settings, configPath, enableSslClientAuth));
		}

		PrivateKey authenticationKey = PemKeyReader.loadKeyFromStream(getSetting(PEMKEY_PASSWORD),
				PemKeyReader.resolveStream(settingsKeyPrefix + PEMKEY_CONTENT, settings));

		if (authenticationKey == null) {
			authenticationKey = PemKeyReader.loadKeyFromFile(getSetting(PEMKEY_PASSWORD), PemKeyReader
					.resolve(settingsKeyPrefix + PEMKEY_FILEPATH, settings, configPath, enableSslClientAuth));
		}

		effectiveKeyPassword = PemKeyReader.randomChars(12);
		effectiveKeyAlias = "al";
		effectiveTruststore = PemKeyReader.toTruststore(effectiveKeyAlias, trustCertificates);
		effectiveKeystore = PemKeyReader.toKeystore(effectiveKeyAlias, effectiveKeyPassword, authenticationCertificate,
				authenticationKey);

	}

	private void initFromKeyStore() throws Exception {
		final KeyStore trustStore = PemKeyReader.loadKeyStore(
				PemKeyReader.resolve(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_FILEPATH, settings,
						configPath, true),
				settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_PASSWORD,
						SSLConfigConstants.DEFAULT_STORE_PASSWORD),
				settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_TYPE));

		// for client authentication
		final KeyStore keyStore = PemKeyReader.loadKeyStore(
				PemKeyReader.resolve(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_FILEPATH, settings,
						configPath, enableSslClientAuth),
				settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_PASSWORD,
						SSLConfigConstants.DEFAULT_STORE_PASSWORD),
				settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_TYPE));
		final String keyStorePassword = settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_PASSWORD,
				SSLConfigConstants.DEFAULT_STORE_PASSWORD);
		effectiveKeyPassword = keyStorePassword == null || keyStorePassword.isEmpty() ? null
				: keyStorePassword.toCharArray();
		effectiveKeyAlias = getSetting(CERT_ALIAS);

		if (enableSslClientAuth && effectiveKeyAlias == null) {
			throw new IllegalArgumentException(settingsKeyPrefix + CERT_ALIAS + " not given");
		}

		effectiveTruststore = trustStore;
		effectiveKeystore = keyStore;

	}

	private String getSetting(String key) {
		return settings.get(settingsKeyPrefix + key);
	}

	private Boolean getSettingAsBoolean(String key, Boolean defaultValue) {
		return settings.getAsBoolean(settingsKeyPrefix + key, defaultValue);
	}

	private List<String> getSettingAsList(String key, List<String> defaultValue) {
		return settings.getAsList(settingsKeyPrefix + key, defaultValue);
	}

	private String[] getSettingAsArray(String key, List<String> defaultValue) {
		List<String> list = getSettingAsList(key, defaultValue);

		if (list == null) {
			return null;
		}

		return list.toArray(new String[list.size()]);
	}

	private static String normalizeSettingsKeyPrefix(String settingsKeyPrefix) {
		if (settingsKeyPrefix == null || settingsKeyPrefix.length() == 0) {
			return "";
		} else if (!settingsKeyPrefix.endsWith(".")) {
			return settingsKeyPrefix + ".";
		} else {
			return settingsKeyPrefix;
		}
	}

	public static class SSLConfig {

		private final SSLContext sslContext;
		private final String[] supportedProtocols;
		private final String[] supportedCipherSuites;
		private final HostnameVerifier hostnameVerifier;

		SSLConfig(SSLContext sslContext, String[] supportedProtocols, String[] supportedCipherSuites,
				HostnameVerifier hostnameVerifier) {
			this.sslContext = sslContext;
			this.supportedProtocols = supportedProtocols;
			this.supportedCipherSuites = supportedCipherSuites;
			this.hostnameVerifier = hostnameVerifier;
		}

		public SSLContext getSslContext() {
			return sslContext;
		}

		public String[] getSupportedProtocols() {
			return supportedProtocols;
		}

		public String[] getSupportedCipherSuites() {
			return supportedCipherSuites;
		}

		public HostnameVerifier getHostnameVerifier() {
			return hostnameVerifier;
		}

		public SSLIOSessionStrategy toSSLIOSessionStrategy() {
			return new SSLIOSessionStrategy(sslContext, supportedProtocols, supportedCipherSuites, hostnameVerifier);
		}

		public SSLConnectionSocketFactory toSSLConnectionSocketFactory() {
			return new SSLConnectionSocketFactory(sslContext, supportedProtocols, supportedCipherSuites,
					hostnameVerifier);
		}
	}
}
