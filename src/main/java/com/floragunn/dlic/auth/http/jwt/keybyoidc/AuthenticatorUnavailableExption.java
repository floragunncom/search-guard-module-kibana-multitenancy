package com.floragunn.dlic.auth.http.jwt.keybyoidc;

public class AuthenticatorUnavailableExption extends RuntimeException {
	private static final long serialVersionUID = -7007025852090301416L;

	public AuthenticatorUnavailableExption() {
		super();
	}

	public AuthenticatorUnavailableExption(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public AuthenticatorUnavailableExption(String message, Throwable cause) {
		super(message, cause);
	}

	public AuthenticatorUnavailableExption(String message) {
		super(message);
	}

	public AuthenticatorUnavailableExption(Throwable cause) {
		super(cause);
	}

}
