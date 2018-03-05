package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import org.apache.cxf.rs.security.jose.jwk.JsonWebKeys;

@FunctionalInterface
public interface KeySetProvider {
	JsonWebKeys get() throws AuthenticatorUnavailableException;
}
