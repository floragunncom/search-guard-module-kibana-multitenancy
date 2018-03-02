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
