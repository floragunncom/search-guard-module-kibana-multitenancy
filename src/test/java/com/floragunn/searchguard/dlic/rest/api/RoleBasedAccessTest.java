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

import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class RoleBasedAccessTest extends AbstractRestApiUnitTest {

	@Test
	public void testActionGroupsApi() throws Exception {

		setupWithRestRoles();

		rh.sendHTTPClientCertificate = false;

		// worf and sarek have access, worf has some endpoints disabled
		
		// ------ GET ------

		// --- Allowed Access ---
		
		// legacy user API, accessible for worf, single user
		HttpResponse response = rh.executeGetRequest("/_searchguard/api/internalusers/admin", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertTrue(settings.get("admin.hash") != null);
		Assert.assertEquals(settings.get("admin.hash"), "$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG");
		
		// new user API, accessible for worf, single user
		response = rh.executeGetRequest("/_searchguard/api/user/admin", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		 settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertTrue(settings.get("admin.hash") != null);

		// legacy user API, accessible for worf, get complete config
		response = rh.executeGetRequest("/_searchguard/api/user/", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.get("admin.hash"), "$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG");
		Assert.assertEquals(settings.get("sarek.hash"), "$2a$12$Ioo1uXmH.Nq/lS5dUVBEsePSmZ5pSIpVO/xKHaquU/Jvq97I7nAgG");
		Assert.assertEquals(settings.get("worf.hash"), "$2a$12$A41IxPXV1/Dx46C6i1ufGubv.p3qYX7xVcY46q33sylYbIqQVwTMu");
		
		// new user API, accessible for worf
		response = rh.executeGetRequest("/_searchguard/api/internalusers/", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.get("admin.hash"), "$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG");
		Assert.assertEquals(settings.get("sarek.hash"), "$2a$12$Ioo1uXmH.Nq/lS5dUVBEsePSmZ5pSIpVO/xKHaquU/Jvq97I7nAgG");
		Assert.assertEquals(settings.get("worf.hash"), "$2a$12$A41IxPXV1/Dx46C6i1ufGubv.p3qYX7xVcY46q33sylYbIqQVwTMu");

		// legacy user API, accessible for worf, get complete config, no trailing slash
		response = rh.executeGetRequest("/_searchguard/api/user", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.get("admin.hash"), "$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG");
		Assert.assertEquals(settings.get("sarek.hash"), "$2a$12$Ioo1uXmH.Nq/lS5dUVBEsePSmZ5pSIpVO/xKHaquU/Jvq97I7nAgG");
		Assert.assertEquals(settings.get("worf.hash"), "$2a$12$A41IxPXV1/Dx46C6i1ufGubv.p3qYX7xVcY46q33sylYbIqQVwTMu");

		// new user API, accessible for worf, get complete config, no trailing slash
		response = rh.executeGetRequest("/_searchguard/api/internalusers", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.get("admin.hash"), "$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG");
		Assert.assertEquals(settings.get("sarek.hash"), "$2a$12$Ioo1uXmH.Nq/lS5dUVBEsePSmZ5pSIpVO/xKHaquU/Jvq97I7nAgG");
		Assert.assertEquals(settings.get("worf.hash"), "$2a$12$A41IxPXV1/Dx46C6i1ufGubv.p3qYX7xVcY46q33sylYbIqQVwTMu");

		// roles API, GET accessible for worf
		response = rh.executeGetRequest("/_searchguard/api/rolesmapping", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(settings.getAsList("sg_all_access.users").get(0), "nagilum");
		Assert.assertEquals(settings.getAsList("sg_role_starfleet_library.backendroles").get(0), "starfleet*");
		Assert.assertEquals(settings.getAsList("sg_zdummy_all.users").get(0), "bug108");
		
		
		// Deprecated get configuration API, acessible for sarek
		response = rh.executeGetRequest("_searchguard/api/configuration/internalusers", encodeBasicHeader("sarek", "sarek"));
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Assert.assertEquals(settings.get("admin.hash"), "$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG");
		Assert.assertEquals(settings.get("sarek.hash"), "$2a$12$Ioo1uXmH.Nq/lS5dUVBEsePSmZ5pSIpVO/xKHaquU/Jvq97I7nAgG");
		Assert.assertEquals(settings.get("worf.hash"), "$2a$12$A41IxPXV1/Dx46C6i1ufGubv.p3qYX7xVcY46q33sylYbIqQVwTMu");

		// Deprecated get configuration API, acessible for sarek
		response = rh.executeGetRequest("_searchguard/api/configuration/actiongroups", encodeBasicHeader("sarek", "sarek"));
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Assert.assertEquals(settings.getAsList("ALL").get(0), "indices:*");
		Assert.assertEquals(settings.getAsList("CLUSTER_MONITOR").get(0), "cluster:monitor/*");
		// new format for action groups
		Assert.assertEquals(settings.getAsList("CRUD.permissions").get(0), "READ");
		
		// --- Forbidden ---
				
		// license API, not accessible for worf
		response = rh.executeGetRequest("_searchguard/api/license", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any access to endpoint LICENSE"));

		// configuration API, not accessible for worf
		response = rh.executeGetRequest("_searchguard/api/configuration/actiongroups", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any access to endpoint CONFIGURATION"));

		// cache API, not accessible for worf since it's disabled globally
		response = rh.executeDeleteRequest("_searchguard/api/cache", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any access to endpoint CACHE"));

		// cache API, not accessible for sarek since it's disabled globally
		response = rh.executeDeleteRequest("_searchguard/api/cache", encodeBasicHeader("sarek", "sarek"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any access to endpoint CACHE"));
		
		// Admin user has no eligible role at all
		response = rh.executeGetRequest("/_searchguard/api/user/admin", encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any role privileged for admin access"));

		// Admin user has no eligible role at all
		response = rh.executeGetRequest("/_searchguard/api/internalusers/admin", encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any role privileged for admin access"));
		
		// Admin user has no eligible role at all
		response = rh.executeGetRequest("/_searchguard/api/internalusers", encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any role privileged for admin access"));

		// Admin user has no eligible role at all
		response = rh.executeGetRequest("/_searchguard/api/roles", encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any role privileged for admin access"));

		// --- DELETE ---

		// Admin user has no eligible role at all
		response = rh.executeDeleteRequest("/_searchguard/api/internalusers/admin", encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any role privileged for admin access"));
		
		// Worf, has access to internalusers API, able to delete 
		response = rh.executeDeleteRequest("/_searchguard/api/internalusers/other", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("'other' deleted"));

		// Worf, has access to internalusers API, user "other" deleted now
		response = rh.executeGetRequest("/_searchguard/api/internalusers/other", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("'other' not found"));

		// Worf, has access to roles API, get captains role
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", encodeBasicHeader("worf", "worf"));
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Assert.assertEquals(settings.getAsList("sg_role_starfleet_captains.cluster").get(0), "cluster:monitor*");

		// Worf, has access to roles API, able to delete 
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_role_starfleet_captains", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("'sg_role_starfleet_captains' deleted"));

		// Worf, has access to roles API, captains role deleted now
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("'sg_role_starfleet_captains' not found"));

		// Worf, has no DELETE access to rolemappings API 
		response = rh.executeDeleteRequest("/_searchguard/api/rolesmapping/sg_unittest_1", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());

		// Worf, has no DELETE access to rolemappings API, legacy endpoint 
		response = rh.executeDeleteRequest("/_searchguard/api/rolesmapping/sg_unittest_1", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());

		// --- PUT ---

		// admin, no access
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_captains_tenants.json"), encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		
		// worf, restore role starfleet captains
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_captains_different_content.json"), encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();

		// starfleet role present again
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", encodeBasicHeader("worf", "worf"));
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Assert.assertEquals(settings.getAsList("sg_role_starfleet_captains.indices.hulla.dulla").get(0), "blafasel");

		// Try the same, but now with admin certificate
		rh.sendHTTPClientCertificate = true;
		
		// admin
		response = rh.executeGetRequest("/_searchguard/api/user/admin", encodeBasicHeader("la", "lu"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertTrue(settings.get("admin.hash") != null);
		Assert.assertEquals(settings.get("admin.hash"), "$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG");
		
		// worf and config
		response = rh.executeGetRequest("_searchguard/api/configuration/actiongroups", encodeBasicHeader("bla", "fasel"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		
		// cache
		response = rh.executeDeleteRequest("_searchguard/api/cache", encodeBasicHeader("wrong", "wrong"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		
		// -- test user, does not have any endpoints disabled, but has access to API, i.e. full access 
		
		rh.sendHTTPClientCertificate = false;
		
		// GET actiongroups
		response = rh.executeGetRequest("_searchguard/api/configuration/actiongroups", encodeBasicHeader("test", "test"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		response = rh.executeGetRequest("_searchguard/api/actiongroups", encodeBasicHeader("test", "test"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		
		// license
		response = rh.executeGetRequest("_searchguard/api/license", encodeBasicHeader("test", "test"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// clear cache - globally disabled, has to fail
		response = rh.executeDeleteRequest("_searchguard/api/cache", encodeBasicHeader("test", "test"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());

		// PUT roles
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("restapi/roles_captains_different_content.json"), encodeBasicHeader("test", "test"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// GET captions role
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", encodeBasicHeader("test", "test"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// Delete captions role
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_role_starfleet_captains", encodeBasicHeader("test", "test"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("'sg_role_starfleet_captains' deleted"));

		// GET captions role
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", encodeBasicHeader("test", "test"));
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());
		

	}
}
