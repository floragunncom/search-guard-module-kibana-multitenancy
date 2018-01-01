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

package com.floragunn.searchguard.auditlog.impl;

import org.elasticsearch.common.settings.Settings;

public class MockWebhookAuditLog extends WebhookAuditLog {
	
	String payload = null;
	String url = null;
	
	MockWebhookAuditLog(Settings settings) throws Exception {
		super(settings, null, null, null, null);
	}

	@Override
	boolean doPost(String url, String payload) {
		this.payload = payload;
		return true;
	}
	
	
	@Override
	boolean doGet(String url) {
		this.url = url;
		return true;
	}
}
