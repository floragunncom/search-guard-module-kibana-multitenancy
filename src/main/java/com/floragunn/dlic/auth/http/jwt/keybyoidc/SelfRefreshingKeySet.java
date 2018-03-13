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
	private volatile JsonWebKeys jsonWebKeys = new JsonWebKeys();
	private boolean refreshInProgress = false;
	private long refreshCount = 0;
	private long queuedGetCount = 0;
	private long recentRefreshCount = 0;
	private long refreshTime = 0;
	private Throwable lastRefreshFailure = null;
	private int requestTimeoutMs = 5000;
	private int queuedThreadTimeoutMs = 2500;
	private int refreshRateLimitTimeWindowMs = 10000;
	private int refreshRateLimitCount = 10;

	public SelfRefreshingKeySet(KeySetProvider refreshFunction) {
		this.keySetProvider = refreshFunction;
	}

	public JsonWebKey getKeyByKid(String kid) throws AuthenticatorUnavailableException {
		JsonWebKey result = jsonWebKeys.getKey(kid);

		if (result != null) {
			return result;
		}

		synchronized (this) {
			// Re-check to handle any races

			result = jsonWebKeys.getKey(kid);

			if (result != null) {
				return result;
			}

			if (refreshInProgress) {
				return waitForRefreshToFinish(kid);
			} else {
				return performRefresh(kid);
			}
		}
	}

	private JsonWebKey waitForRefreshToFinish(String kid) {
		queuedGetCount++;
		long currentRefreshCount = refreshCount;

		try {
			wait(queuedThreadTimeoutMs);
		} catch (InterruptedException e) {
			log.debug(e);
		}

		// Just be optimistic and re-check the key

		JsonWebKey result = jsonWebKeys.getKey(kid);

		if (result != null) {
			return result;
		}

		if (refreshInProgress && currentRefreshCount == refreshCount) {
			// The wait() call returned due to the timeout.
			throw new AuthenticatorUnavailableException("Authentication backend timed out");
		} else if (lastRefreshFailure != null) {
			throw new AuthenticatorUnavailableException("Authentication backend failed", lastRefreshFailure);
		} else {
			// Refresh was successful, but we did not get a matching key
			return null;
		}
	}

	private JsonWebKey performRefresh(String kid) {
		if (log.isDebugEnabled()) {
			log.debug("performRefresh({})", kid);
		}

		final boolean recentRefresh;

		if (System.currentTimeMillis() - refreshTime < refreshRateLimitTimeWindowMs) {
			recentRefreshCount++;
			recentRefresh = true;

			if (recentRefreshCount > refreshRateLimitCount) {
				throw new AuthenticatorUnavailableException("Too many unknown kids recently: " + recentRefreshCount);
			}
		} else {
			recentRefresh = false;
		}

		refreshInProgress = true;
		refreshCount++;

		log.info("Performing refresh {}", refreshCount);

		long currentRefreshCount = refreshCount;

		try {

			Future<?> future = threadPoolExecutor.submit(new Runnable() {

				@Override
				public void run() {
					try {
						JsonWebKeys newKeys = keySetProvider.get();

						if (newKeys == null) {
							throw new RuntimeException("Refresh function " + keySetProvider + " yielded null");
						}

						log.info("KeySetProvider finished");

						synchronized (SelfRefreshingKeySet.this) {
							jsonWebKeys = newKeys;
							refreshInProgress = false;
							lastRefreshFailure = null;
							SelfRefreshingKeySet.this.notifyAll();
						}
					} catch (Throwable e) {
						synchronized (SelfRefreshingKeySet.this) {
							lastRefreshFailure = e;
							refreshInProgress = false;
							SelfRefreshingKeySet.this.notifyAll();
						}
						log.warn("KeySetProvider threw error", e);
					} finally {
						if (!recentRefresh) {
							recentRefreshCount = 0;
							refreshTime = System.currentTimeMillis();
						}
					}

				}
			});

			try {
				wait(requestTimeoutMs);
			} catch (InterruptedException e) {
				log.debug(e);
			}

			JsonWebKey result = jsonWebKeys.getKey(kid);

			if (result != null) {
				return result;
			}

			if (refreshInProgress && currentRefreshCount == refreshCount) {
				if (!future.isDone()) {
					future.cancel(true);
				}

				lastRefreshFailure = new AuthenticatorUnavailableException("Authentication backend timed out");

				throw new AuthenticatorUnavailableException("Authentication backend timed out");
			}

			if (lastRefreshFailure != null) {
				throw new AuthenticatorUnavailableException("Authentication backend failed", lastRefreshFailure);
			}

			return null;

		} catch (RejectedExecutionException e) {
			throw new AuthenticatorUnavailableException("Did not try to call authentication backend because of "
					+ threadPoolExecutor.getActiveCount() + " pending threads", e);
		} finally {
			if (refreshInProgress && currentRefreshCount == refreshCount) {
				refreshInProgress = false;
				notifyAll();
			}
		}
	}

	public int getRequestTimeoutMs() {
		return requestTimeoutMs;
	}

	public void setRequestTimeoutMs(int requestTimeoutMs) {
		this.requestTimeoutMs = requestTimeoutMs;
	}

	public int getQueuedThreadTimeoutMs() {
		return queuedThreadTimeoutMs;
	}

	public void setQueuedThreadTimeoutMs(int queuedThreadTimeoutMs) {
		this.queuedThreadTimeoutMs = queuedThreadTimeoutMs;
	}

	public long getRefreshCount() {
		return refreshCount;
	}

	public long getQueuedGetCount() {
		return queuedGetCount;
	}

	public int getRefreshRateLimitTimeWindowMs() {
		return refreshRateLimitTimeWindowMs;
	}

	public void setRefreshRateLimitTimeWindowMs(int refreshRateLimitTimeWindowMs) {
		this.refreshRateLimitTimeWindowMs = refreshRateLimitTimeWindowMs;
	}

	public int getRefreshRateLimitCount() {
		return refreshRateLimitCount;
	}

	public void setRefreshRateLimitCount(int refreshRateLimitCount) {
		this.refreshRateLimitCount = refreshRateLimitCount;
	}
}
