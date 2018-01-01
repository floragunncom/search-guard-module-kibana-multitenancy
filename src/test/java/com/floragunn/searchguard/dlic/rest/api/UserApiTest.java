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

import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;
import com.google.common.base.Strings;

public class UserApiTest extends AbstractRestApiUnitTest {

	@Test
	public void testUserApi() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// initial configuration, 5 users
		HttpResponse response = rh
				.executeGetRequest("_searchguard/api/configuration/" + ConfigConstants.CONFIGNAME_INTERNAL_USERS);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(8, settings.size());

		// --- GET

		// GET, user admin, exists
		response = rh.executeGetRequest("/_searchguard/api/user/admin", new Header[0]);
		Assert.assertEquals(response.getBody(), HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(1, settings.size());
		Assert.assertEquals("$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG",
				settings.get("admin.hash"));

		// GET, user does not exist
		response = rh.executeGetRequest("/_searchguard/api/user/nothinghthere", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// GET, new URL endpoint in SG6
		response = rh.executeGetRequest("/_searchguard/api/user/", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// GET, new URL endpoint in SG6
		response = rh.executeGetRequest("/_searchguard/api/user", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// -- PUT

		// no username given
		response = rh.executePutRequest("/_searchguard/api/user/", "{hash: \"123\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, response.getStatusCode());

		// Faulty JSON payload
		response = rh.executePutRequest("/_searchguard/api/user/nagilum", "{some: \"thing\" asd  other: \"thing\"}",
				new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.get("reason"), AbstractConfigurationValidator.ErrorType.BODY_NOT_PARSEABLE.getMessage());

		// Missing quotes in JSON - parseable in 6.x, but wrong config keys
		response = rh.executePutRequest("/_searchguard/api/user/nagilum", "{some: \"thing\", other: \"thing\"}",
				new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		//JK: this should be "Could not parse content of request." because JSON is truly invalid
		//Assert.assertEquals(settings.get("reason"), AbstractConfigurationValidator.ErrorType.INVALID_CONFIGURATION.getMessage());
		//Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("some"));
		//Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("other"));

		// Wrong config keys
		response = rh.executePutRequest("/_searchguard/api/user/nagilum", "{\"some\": \"thing\", \"other\": \"thing\"}",
				new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.get("reason"), AbstractConfigurationValidator.ErrorType.INVALID_CONFIGURATION.getMessage());
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("some"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("other"));
		
		// add user with correct setting. User is in role "sg_all_access"

		// check access not allowed
		checkGeneralAccess(HttpStatus.SC_UNAUTHORIZED, "nagilum", "nagilum");

		// add/update user, user is read only, forbidden
		rh.sendHTTPClientCertificate = true;
		addUserWithHash("sarek", "$2a$12$n5nubfWATfQjSYHiWtUyeOxMIxFInUHOAx8VMmGmxFNPGpaBmeB.m",
				HttpStatus.SC_FORBIDDEN);

		
		// add users
		rh.sendHTTPClientCertificate = true;
		addUserWithHash("nagilum", "$2a$12$n5nubfWATfQjSYHiWtUyeOxMIxFInUHOAx8VMmGmxFNPGpaBmeB.m",
				HttpStatus.SC_CREATED);

		// access must be allowed now
		checkGeneralAccess(HttpStatus.SC_OK, "nagilum", "nagilum");

		// try remove user, no username
		rh.sendHTTPClientCertificate = true;
		response = rh.executeDeleteRequest("/_searchguard/api/user", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, response.getStatusCode());

		// try remove user, nonexisting user
		response = rh.executeDeleteRequest("/_searchguard/api/user/picard", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// try remove readonly user
		response = rh.executeDeleteRequest("/_searchguard/api/user/sarek", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());

		// now really remove user
		deleteUser("nagilum");

		// Access must be forbidden now
		rh.sendHTTPClientCertificate = false;
		checkGeneralAccess(HttpStatus.SC_UNAUTHORIZED, "nagilum", "nagilum");

		// use password instead of hash
		rh.sendHTTPClientCertificate = true;
		addUserWithPassword("nagilum", "correctpassword", HttpStatus.SC_CREATED);

		rh.sendHTTPClientCertificate = false;
		checkGeneralAccess(HttpStatus.SC_UNAUTHORIZED, "nagilum", "wrongpassword");
		checkGeneralAccess(HttpStatus.SC_OK, "nagilum", "correctpassword");

		deleteUser("nagilum");

		// Check unchanged password functionality
		rh.sendHTTPClientCertificate = true;
		
		// new user, password or hash is mandatory
		addUserWithoutPasswordOrHash("nagilum", new String[] { "starfleet" }, HttpStatus.SC_BAD_REQUEST);
		// new user, add hash
		addUserWithHash("nagilum", "$2a$12$n5nubfWATfQjSYHiWtUyeOxMIxFInUHOAx8VMmGmxFNPGpaBmeB.m",
				HttpStatus.SC_CREATED);
		// update user, do not specify hash or password, hash must remain the same
		addUserWithoutPasswordOrHash("nagilum", new String[] { "starfleet" }, HttpStatus.SC_OK);
		// get user, check hash, must be untouched
		response = rh.executeGetRequest("/_searchguard/api/user/nagilum", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertTrue(settings.get("nagilum.hash").equals("$2a$12$n5nubfWATfQjSYHiWtUyeOxMIxFInUHOAx8VMmGmxFNPGpaBmeB.m"));

		
		// ROLES
		// create index first
		setupStarfleetIndex();

		// wrong datatypes in roles file
		rh.sendHTTPClientCertificate = true;
		response = rh.executePutRequest("/_searchguard/api/user/picard", FileHelper.loadFile("restapi/users_wrong_datatypes.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get("roles").equals("Array expected"));
		rh.sendHTTPClientCertificate = false;

		rh.sendHTTPClientCertificate = true;
		response = rh.executePutRequest("/_searchguard/api/user/picard", FileHelper.loadFile("restapi/users_wrong_datatypes.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get("roles").equals("Array expected"));
		rh.sendHTTPClientCertificate = false;
		
		rh.sendHTTPClientCertificate = true;
		response = rh.executePutRequest("/_searchguard/api/user/picard", FileHelper.loadFile("restapi/users_wrong_datatypes2.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get("password").equals("String expected"));
		Assert.assertTrue(settings.get("roles") == null);
		rh.sendHTTPClientCertificate = false;		

		rh.sendHTTPClientCertificate = true;
		response = rh.executePutRequest("/_searchguard/api/user/picard", FileHelper.loadFile("restapi/users_wrong_datatypes3.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get("roles").equals("Array expected"));
		rh.sendHTTPClientCertificate = false;	
		
		// use backendroles when creating user. User picard does not exist in
		// the internal user DB
		// and is also not assigned to any role by username
		addUserWithPassword("picard", "picard", HttpStatus.SC_CREATED);
		// changed in ES5, you now need cluster:monitor/main which pucard does not have
		checkGeneralAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard");

		// check read access to starfleet index and ships type, must fail
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 0);
		
		// overwrite user picard, and give him role "starfleet".
		addUserWithPassword("picard", "picard", new String[] { "starfleet" }, HttpStatus.SC_OK);

		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);


		// overwrite user picard, and give him role "starfleet" plus "captains
		addUserWithPassword("picard", "picard", new String[] { "starfleet", "captains" }, HttpStatus.SC_OK);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkWriteAccess(HttpStatus.SC_CREATED, "picard", "picard", "sf", "ships", 1);

		rh.sendHTTPClientCertificate = true;
		response = rh.executeGetRequest("/_searchguard/api/user/picard", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertNotEquals(null, Strings.emptyToNull(settings.get("picard.hash")));
		List<String> roles = settings.getAsList("picard.roles");
		Assert.assertNotNull(roles);
		Assert.assertEquals(2, roles.size());
		Assert.assertTrue(roles.contains("starfleet"));
		Assert.assertTrue(roles.contains("captains"));

	}

}
