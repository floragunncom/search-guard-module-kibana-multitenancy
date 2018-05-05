package com.floragunn.searchguard.auditlog.routing;

import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.auditlog.AbstractAuditlogiUnitTest;
import com.floragunn.searchguard.test.helper.file.FileHelper;

public class ThreadPoolSettingsTest extends AbstractAuditlogiUnitTest {

	@Test
	public void testNoMultipleEndpointsConfiguration() throws Exception {		
		Settings settings = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/sink/configuration_no_multiple_endpoints.yml")).build();
		AuditMessageRouter router = createMessageRouterComplianceEnabled(settings);		
		Assert.assertEquals(5, router.storagePool.threadPoolSize);
		Assert.assertEquals(200000, router.storagePool.threadPoolMaxQueueLen);
	}
}
