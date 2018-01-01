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
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.configuration.SearchGuardLicense;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class InvalidLicenseTest extends LicenseTest {

	@Test
	public void testInvalidLicenseUpload() throws Exception {

		setupAllowInvalidLicenses();
		rh.sendHTTPClientCertificate = true;
		
		String license = FileHelper.loadFile("restapi/license/single_expired.txt");
		HttpResponse response = rh.executePutRequest("/_searchguard/api/license", createLicenseRequestBody(license), new Header[0]);
		Assert.assertEquals(201, response.getStatusCode());
		
		 Settings settingsAsMap = getCurrentLicense();
		 Assert.assertEquals(SearchGuardLicense.Type.SINGLE.name(), settingsAsMap.get("sg_license.type"));
		 Assert.assertEquals("1", settingsAsMap.get("sg_license.allowed_node_count_per_cluster"));
		 Assert.assertEquals(Boolean.FALSE.toString(), settingsAsMap.get("sg_license.is_valid"));
		 Assert.assertEquals(expiredStartDate.format(formatter), settingsAsMap.get("sg_license.start_date"));
		 Assert.assertEquals(expiredExpiryDate.format(formatter), settingsAsMap.get("sg_license.expiry_date"));
		 Assert.assertEquals("Purchase a license. Visit docs.search-guard.com/v6/search-guard-enterprise-edition or write to <sales@floragunn.com>", settingsAsMap.get("sg_license.action"));
		 Assert.assertEquals("License is expired", settingsAsMap.getAsList("sg_license.msgs").get(0));
		 Assert.assertEquals("Only 1 node(s) allowed but you run 3 node(s)", settingsAsMap.getAsList("sg_license.msgs").get(1));
	}

	private final String createLicenseRequestBody(String licenseString) throws Exception {
		return "{ \"sg_license\": \"" + licenseString + "\"}";
	}

}
