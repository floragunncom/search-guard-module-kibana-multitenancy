/*
 * Copyright 2016-2017 by floragunn GmbH - All rights reserved
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

import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class GetConfigurationApiTest extends AbstractRestApiUnitTest {

	@Test
	public void testGetConfiguration() throws Exception {

		setup();
		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// wrong config name -> bad request
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/doesnotexists");
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// test that every config is accessible
		// sg_config
		response = rh.executeGetRequest("_searchguard/api/configuration/config");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(
				settings.getAsBoolean("searchguard.dynamic.authc.authentication_domain_basic_internal.enabled", false),
				true);

		// internalusers
		response = rh.executeGetRequest("_searchguard/api/configuration/internalusers");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.get("admin.hash"), "$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG");
		Assert.assertEquals(settings.get("other.hash"), "someotherhash");

		// roles
		response = rh.executeGetRequest("_searchguard/api/configuration/roles");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.getAsList("sg_all_access.cluster").get(0), "cluster:*");

		// roles
		response = rh.executeGetRequest("_searchguard/api/configuration/rolesmapping");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.getAsList("sg_role_starfleet.backendroles").get(0), "starfleet");

		// action groups
		response = rh.executeGetRequest("_searchguard/api/configuration/actiongroups");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.getAsList("ALL").get(0), "indices:*");
	}

}
