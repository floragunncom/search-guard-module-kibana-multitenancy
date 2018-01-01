/*
 * Copyright 2017 by floragunn GmbH - All rights reserved
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
package com.floragunn.searchguard.dlic.rest.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.action.licenseinfo.LicenseInfoAction;
import com.floragunn.searchguard.action.licenseinfo.LicenseInfoRequest;
import com.floragunn.searchguard.action.licenseinfo.LicenseInfoResponse;
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.IndexBaseConfigurationRepository;
import com.floragunn.searchguard.configuration.PrivilegesEvaluator;
import com.floragunn.searchguard.configuration.SearchGuardLicense;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.LicenseValidator;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.support.LicenseHelper;

public class LicenseApiAction extends AbstractApiAction {
	
	public final static String CONFIG_LICENSE_KEY = "searchguard.dynamic.license";
	
	protected LicenseApiAction(Settings settings, Path configPath, RestController controller, Client client, AdminDNs adminDNs,
			IndexBaseConfigurationRepository cl, ClusterService cs, PrincipalExtractor principalExtractor, 
			final PrivilegesEvaluator evaluator, ThreadPool threadPool, AuditLog auditLog) {
		super(settings, configPath, controller, client, adminDNs, cl, cs, principalExtractor, evaluator, threadPool, auditLog);		
		controller.registerHandler(Method.DELETE, "/_searchguard/api/license", this);
		controller.registerHandler(Method.GET, "/_searchguard/api/license", this);
		controller.registerHandler(Method.PUT, "/_searchguard/api/license", this);
		controller.registerHandler(Method.POST, "/_searchguard/api/license", this);

	}

	@Override
	protected Endpoint getEndpoint() {
		return Endpoint.LICENSE;
	}

	@Override
	protected Tuple<String[], RestResponse> handleGet(RestRequest request, Client client, Builder additionalSettings) throws Throwable {
		
		final Semaphore sem = new Semaphore(0);
		final List<Throwable> exception = new ArrayList<Throwable>(1);
		final XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint();
		
		client.execute(LicenseInfoAction.INSTANCE, new LicenseInfoRequest(), new ActionListener<LicenseInfoResponse>() {

			@Override
			public void onFailure(final Exception e) {
				try {
					exception.add(e);
					logger.error("Cannot fetch license information due to", e);							
				} finally {
					sem.release();
				}
			}

			@Override
			public void onResponse(final LicenseInfoResponse ur) {				
				try {					
		            builder.startObject();
		            ur.toXContent(builder, ToXContent.EMPTY_PARAMS);
		            builder.endObject();
					if (log.isDebugEnabled()) {
						log.debug("Successfully fetched license " + ur.toString());
					}					
				} catch (IOException e) {
					exception.add(e);
					logger.error("Cannot fetch convert license to XContent due to", e);		
				} finally {
					sem.release();
				}
			}
		});
		
		try {
			if (!sem.tryAcquire(2, TimeUnit.MINUTES)) {
				logger.error("Cannot fetch config due to timeout");
				throw new ElasticsearchException("Timeout fetching license.");
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		if (exception.size() != 0) {
		    request.params().clear();
		    logger.error("Unable to fetch license due to", exception.get(0));
		    return internalErrorResponse("Unable to fetch license: " + exception.get(0).getMessage());
		}
			
		return new Tuple<String[], RestResponse>(new String[0],
				new BytesRestResponse(RestStatus.OK, builder));
	}
	
	@Override
	protected Tuple<String[], RestResponse> handlePut(final RestRequest request, final Client client,
			final Settings.Builder licenseBuilder) throws Throwable {
		
		String licenseString = licenseBuilder.get("sg_license");
		
		if (licenseString == null || licenseString.length() == 0) {
			return badRequestResponse("License must not be null.");
		}
		
		// try to decode the license String as base 64, armored PGP encoded String
		String plaintextLicense;
		
		try {
			plaintextLicense = LicenseHelper.validateLicense(licenseString);					
		} catch (Exception e) {
			log.error("Could not decode license {} due to", licenseString, e);
			return badRequestResponse("License could not be decoded due to: " + e.getMessage());
		}
		
		SearchGuardLicense license = new SearchGuardLicense(XContentHelper.convertToMap(XContentType.JSON.xContent(), plaintextLicense, true), cs);
		
		// check if license is valid at all, honor unsupported switch in es.yml 
		if (!license.isValid() && !acceptInvalidLicense) {
			return badRequestResponse("License invalid due to: " + String.join(",", license.getMsgs()));
		}
				
		// load existing configuration into new map
		final Settings.Builder existing = load(getConfigName());
		
		if (log.isTraceEnabled()) {
			log.trace(existing.build().toString());	
		}
		
		// license already present?		
		boolean licenseExists = existing.get(CONFIG_LICENSE_KEY) != null;
		
		// license is valid, overwrite old value
		existing.put(CONFIG_LICENSE_KEY, licenseString);
		
		save(client, request, getConfigName(), existing);
		if (licenseExists) {
			return successResponse("License updated.", getConfigName());
		} else {
			// fallback, should not happen since we always have at least a trial license
			log.warn("License created via REST API.");
			return createdResponse("License created.", getConfigName());
		}
	}

	protected Tuple<String[], RestResponse> handlePost(final RestRequest request, final Client client,
			final Settings.Builder additionalSettings) throws Throwable {
		return notImplemented(Method.POST);
	}

	@Override
	protected AbstractConfigurationValidator getValidator(Method method, BytesReference ref) {
		return new LicenseValidator(method, ref);
	}

	@Override
	protected String getResourceName() {
		// not needed
		return null;
	}

	@Override
	protected String getConfigName() {		
		return ConfigConstants.CONFIGNAME_CONFIG;
	}

}
