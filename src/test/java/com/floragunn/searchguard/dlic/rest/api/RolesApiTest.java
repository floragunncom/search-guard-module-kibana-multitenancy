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

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator.ErrorType;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class RolesApiTest extends AbstractRestApiUnitTest {

	@Test
	public void testRolesApi() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// check roles exists
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/roles");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// -- GET

		// GET sg_all_access, exists
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();				
		Assert.assertEquals(8, settings.size());

		// GET, role does not exist
		response = rh.executeGetRequest("/_searchguard/api/roles/nothinghthere", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// GET, new URL endpoint in SG6
		response = rh.executeGetRequest("/_searchguard/api/roles/", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// GET, new URL endpoint in SG6
		response = rh.executeGetRequest("/_searchguard/api/roles", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// create index
		setupStarfleetIndex();

		// add user picard, role starfleet, maps to sg_role_starfleet
		addUserWithPassword("picard", "picard", new String[] { "starfleet", "captains" }, HttpStatus.SC_CREATED);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		// TODO: only one doctype allowed for ES6
		//checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		// TODO: only one doctype allowed for ES6
		//checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);

		// -- DELETE

		rh.sendHTTPClientCertificate = true;

		// Non-existing role
		response = rh.executeDeleteRequest("/_searchguard/api/roles/idonotexist", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// read only role
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_transport_client", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());

		// remove complete role mapping for sg_role_starfleet_captains
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		rh.sendHTTPClientCertificate = false;
		// only starfleet role left, write access to ships is forbidden now
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		// TODO: only one doctype allowed for ES6
		//checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		//checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);

		rh.sendHTTPClientCertificate = true;
		// remove also starfleet role, nothing is allowed anymore
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 0);
		// TODO: only one doctype allowed for ES6
		//checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 0);
		// TODO: only one doctype allowed for ES6
		// checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 0);

		// -- PUT
		// put with empty roles, must fail
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet", "", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.PAYLOAD_MANDATORY.getMessage(), settings.get("reason"));

		// put new configuration with invalid payload, must fail
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet",
				FileHelper.loadFile("restapi/roles_not_parseable.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.BODY_NOT_PARSEABLE.getMessage(), settings.get("reason"));

		// put new configuration with invalid keys, must fail
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet",
				FileHelper.loadFile("restapi/roles_invalid_keys.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.INVALID_CONFIGURATION.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("indizes"));
		Assert.assertTrue(
				settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("kluster"));

		// put new configuration with wrong datatypes, must fail
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet",
				FileHelper.loadFile("restapi/roles_wrong_datatype.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));		
		Assert.assertTrue(settings.get("cluster").equals("Array expected"));

		// put read only role, must be forbidden
		response = rh.executePutRequest("/_searchguard/api/roles/sg_transport_client",
				FileHelper.loadFile("restapi/roles_captains.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		
		// restore starfleet role
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet",
				FileHelper.loadFile("restapi/roles_starfleet.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusCode());
		rh.sendHTTPClientCertificate = false;
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		// TODO: only one doctype allowed for ES6
		//checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 0);
		// TODO: only one doctype allowed for ES6
		// checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);

		rh.sendHTTPClientCertificate = true;

		// restore captains role
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_captains.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusCode());
		rh.sendHTTPClientCertificate = false;
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		// TODO: only one doctype allowed for ES6
		//checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		// TODO: only one doctype allowed for ES6
		//checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);

		rh.sendHTTPClientCertificate = true;
        response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
                FileHelper.loadFile("restapi/roles_complete_invalid.json"), new Header[0]);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		
		rh.sendHTTPClientCertificate = true;
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_multiple.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_multiple_2.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		
		// check tenants
		rh.sendHTTPClientCertificate = true;
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_captains_tenants.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(2, settings.size());
		Assert.assertEquals(settings.get("status"), "OK");
		
		
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(5, settings.size());
		Assert.assertEquals(settings.get("sg_role_starfleet_captains.tenants.tenant1"), "RO");
		Assert.assertEquals(settings.get("sg_role_starfleet_captains.tenants.tenant2"), "RW");

		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_captains_tenants2.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(2, settings.size());
		Assert.assertEquals(settings.get("status"), "OK");

		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(7, settings.size());
		Assert.assertEquals(settings.get("sg_role_starfleet_captains.tenants.tenant1"), "RO");
		Assert.assertEquals(settings.get("sg_role_starfleet_captains.tenants.tenant2"), "RW");
		Assert.assertEquals(settings.get("sg_role_starfleet_captains.tenants.tenant3"), "RO");
		Assert.assertEquals(settings.get("sg_role_starfleet_captains.tenants.tenant4"), "RW");
		
		// remove tenants from role
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_captains_no_tenants.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(2, settings.size());
		Assert.assertEquals(settings.get("status"), "OK");

		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(3, settings.size());	
		Assert.assertNull(settings.get("sg_role_starfleet_captains.tenants.tenant1"));
		Assert.assertNull(settings.get("sg_role_starfleet_captains.tenants.tenant2"));
		Assert.assertNull(settings.get("sg_role_starfleet_captains.tenants.tenant3"));
		Assert.assertNull(settings.get("sg_role_starfleet_captains.tenants.tenant4"));

		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_captains_tenants_malformed.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.get("status"), "error");
		Assert.assertEquals(settings.get("reason"), ErrorType.INVALID_CONFIGURATION.getMessage());
	}
}
