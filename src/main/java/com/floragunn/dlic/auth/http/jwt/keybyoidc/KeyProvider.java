package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import org.apache.cxf.rs.security.jose.jwk.JsonWebKey;

public interface KeyProvider {
	public JsonWebKey getKeyByKid(String kid) throws AuthenticatorUnavailableExption;
}
