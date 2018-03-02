package com.floragunn.dlic.auth.http.jwt.keybyoidc;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.cxf.rs.security.jose.jwk.JsonWebKey;
import org.apache.cxf.rs.security.jose.jwk.JsonWebKeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SelfRefreshingKeySet implements KeyProvider {
	private static final Logger log = LogManager.getLogger(SelfRefreshingKeySet.class);

	private final KeySetProvider keySetProvider;
	private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 10, 1000, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>());
	private JsonWebKeys kidToKeyMap = new JsonWebKeys();
	private boolean refreshInProgress = false;
	private long refreshCount = 0;
	private long recentRefreshCount = 0;
	private long refreshTime = 0;
	private Throwable lastRefreshFailure = null;

	public SelfRefreshingKeySet(KeySetProvider refreshFunction) {
		this.keySetProvider = refreshFunction;
	}

	public JsonWebKey getKeyByKid(String kid) throws AuthenticatorUnavailableExption {
		JsonWebKey result = kidToKeyMap.getKey(kid);

		if (result != null) {
			return result;
		}

		synchronized (this) {
			// Recheck to handle any races

			result = kidToKeyMap.getKey(kid);

			if (result != null) {
				return result;
			}

			if (refreshInProgress) {
				long currentRefreshCount = refreshCount;

				try {
					wait(5000);
				} catch (InterruptedException e) {
					log.debug(e);
				}

				// Just be optimistic and re-check the key

				result = kidToKeyMap.getKey(kid);

				if (result != null) {
					return result;
				}

				if (refreshInProgress && currentRefreshCount == refreshCount) {
					// The wait() call returned due to the timeout.
					throw new AuthenticatorUnavailableExption("Authentication backend timed out");
				} else if (lastRefreshFailure != null) {
					throw new AuthenticatorUnavailableExption("Authentication backend failed", lastRefreshFailure);
				} else {
					// Refresh was successful, but we did not get a matching key
					return null;
				}
			}

			if (System.currentTimeMillis() - refreshTime < 10000) {
				recentRefreshCount++;

				if (recentRefreshCount > 10) {
					throw new AuthenticatorUnavailableExption("Too many unknown kids recently: " + recentRefreshCount);
				}
			}

			refreshInProgress = true;
			refreshCount++;

			long currentRefreshCount = refreshCount;

			try {

				Future<?> future = threadPoolExecutor.submit(new Runnable() {

					@Override
					public void run() {
						try {
							JsonWebKeys newMap = keySetProvider.get();

							if (newMap == null) {
								throw new RuntimeException("Refresh function " + keySetProvider + " yielded null");
							}

							synchronized (SelfRefreshingKeySet.this) {
								kidToKeyMap = newMap;
								refreshInProgress = false;
								lastRefreshFailure = null;
								recentRefreshCount = 0;
								refreshTime = System.currentTimeMillis();

								notifyAll();
							}
						} catch (Throwable e) {
							synchronized (SelfRefreshingKeySet.this) {
								lastRefreshFailure = e;
								refreshInProgress = false;
								notifyAll();
							}
						}

					}
				});

				try {
					wait(5000);
				} catch (InterruptedException e) {
					log.debug(e);
				}

				result = kidToKeyMap.getKey(kid);
				

				if (result != null) {
					return result;
				}

				if (refreshInProgress && currentRefreshCount == refreshCount) {
					if (!future.isDone()) {
						future.cancel(true);
					}

					lastRefreshFailure = new AuthenticatorUnavailableExption("Authentication backend timed out");

					throw new AuthenticatorUnavailableExption("Authentication backend timed out");
				}

				if (lastRefreshFailure != null) {
					throw new AuthenticatorUnavailableExption("Authentication backend failed", lastRefreshFailure);
				}

			} catch (RejectedExecutionException e) {
				throw new AuthenticatorUnavailableExption("Did not try to call authentication backend because of "
						+ threadPoolExecutor.getActiveCount() + " pending threads", e);
			} finally {
				if (refreshInProgress && currentRefreshCount == refreshCount) {
					refreshInProgress = false;
					notifyAll();
				}
			}
		}

		return null;
	}
}
