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

import org.apache.cxf.rs.security.jose.jwe.JweDecryptionProvider;
import org.apache.cxf.rs.security.jose.jwe.JweHeaders;
import org.apache.cxf.rs.security.jose.jwk.JsonWebKey;
import org.apache.cxf.rs.security.jose.jws.JwsHeaders;
import org.apache.cxf.rs.security.jose.jws.JwsSignatureVerifier;
import org.apache.cxf.rs.security.jose.jws.JwsUtils;
import org.apache.cxf.rs.security.jose.jwt.JoseJwtConsumer;
import org.apache.cxf.rs.security.jose.jwt.JwtClaims;
import org.apache.cxf.rs.security.jose.jwt.JwtException;
import org.apache.cxf.rs.security.jose.jwt.JwtToken;
import org.apache.cxf.rs.security.jose.jwt.JwtUtils;

import com.google.common.base.Strings;

public class JwtVerifier extends JoseJwtConsumer {

	private final KeyProvider keyProvider;

	public JwtVerifier(KeyProvider keyProvider) {
		this.keyProvider = keyProvider;
	}

	protected final JwsSignatureVerifier getInitializedSignatureVerifier(JwtToken jwt) {
		return getInitializedSignatureVerifier(jwt.getJwsHeaders());
	}

	protected JwsSignatureVerifier getInitializedSignatureVerifier(JwsHeaders jwsHeaders) {
		String keyId = jwsHeaders.getKeyId();

		if (Strings.isNullOrEmpty(keyId)) {
			throw new JwtException("JWT did not contain kid (Headers: " + jwsHeaders + ")");
		}

		JsonWebKey key = keyProvider.getKeyByKid(keyId);

		if (key != null) {
			return JwsUtils.getSignatureVerifier(key);
		} else {
			throw new JwtException("Unknown kid " + keyId + " (Headers: " + jwsHeaders + ")");
		}
	}

	protected JweDecryptionProvider getInitializedDecryptionProvider(JweHeaders jweHeaders) {
		return null;
	}

	public boolean isJwsRequired() {
		return true;
	}

	@Override
	protected void validateToken(JwtToken jwt) {
		super.validateToken(jwt);

		JwtClaims claims = jwt.getClaims();

		if (claims != null) {
			JwtUtils.validateJwtExpiry(claims, getClockOffset(), false);
			JwtUtils.validateJwtNotBefore(claims, getClockOffset(), false);
		}
	}
}
