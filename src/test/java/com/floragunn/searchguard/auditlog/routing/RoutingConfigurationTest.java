package com.floragunn.searchguard.auditlog.routing;

import java.util.List;

import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.auditlog.impl.AuditMessage;
import com.floragunn.searchguard.auditlog.sink.AuditLogSink;
import com.floragunn.searchguard.auditlog.sink.DebugSink;
import com.floragunn.searchguard.auditlog.sink.ExternalESSink;
import com.floragunn.searchguard.auditlog.sink.InternalESSink;
import com.floragunn.searchguard.test.helper.file.FileHelper;

public class RoutingConfigurationTest {

	@Test
	public void testValidConfiguration() throws Exception {
		Settings settings = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/routing/configuration_valid.yml")).build();
		AuditMessageRouter router = new AuditMessageRouter(settings, null, null, null);
		// default
		Assert.assertEquals("default", router.defaultSink.getName());
		Assert.assertEquals(ExternalESSink.class, router.defaultSink.getClass());
		// test category sinks
		List<AuditLogSink> sinks = router.categorySinks.get(AuditMessage.Category.MISSING_PRIVILEGES);
		Assert.assertNotNull(sinks);
		// 3, since we include default as well
		Assert.assertEquals(3, sinks.size());
		Assert.assertEquals("endpoint1", sinks.get(0).getName());
		Assert.assertEquals(InternalESSink.class, sinks.get(0).getClass());
		Assert.assertEquals("endpoint2", sinks.get(1).getName());
		Assert.assertEquals(ExternalESSink.class, sinks.get(1).getClass());
		Assert.assertEquals("default", sinks.get(2).getName());
		Assert.assertEquals(ExternalESSink.class, sinks.get(2).getClass());
		sinks = router.categorySinks.get(AuditMessage.Category.COMPLIANCE_DOC_READ);
		// 1, since we do not include default
		Assert.assertEquals(1, sinks.size());
		Assert.assertEquals("endpoint3", sinks.get(0).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(0).getClass());
	}

	@Test
	public void testNoDefaultSink() throws Exception {
		Settings settings = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/routing/configuration_no_default.yml")).build();
		AuditMessageRouter router = new AuditMessageRouter(settings, null, null, null);
		// no default sink, we fall back to debug sink
		Assert.assertEquals(DebugSink.class, router.defaultSink.getClass());
		List<AuditLogSink> sinks = router.categorySinks.get(AuditMessage.Category.MISSING_PRIVILEGES);
		// 3, since default is not valid but replaced with Debug
		Assert.assertEquals(3, sinks.size());
		Assert.assertEquals("default", sinks.get(0).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(0).getClass());
		Assert.assertEquals("endpoint1", sinks.get(1).getName());
		Assert.assertEquals(InternalESSink.class, sinks.get(1).getClass());
		Assert.assertEquals("endpoint2", sinks.get(2).getName());
		Assert.assertEquals(ExternalESSink.class, sinks.get(2).getClass());
	}

	@Test
	public void testMissingEndpoints() throws Exception {
		Settings settings = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/routing/configuration_wrong_endpoint_names.yml")).build();
		AuditMessageRouter router = new AuditMessageRouter(settings, null, null, null);
		// fallback to debug sink if no default is given
		Assert.assertEquals(InternalESSink.class, router.defaultSink.getClass());
		// missing configuration for endpoint2 / External ES. Fallback to
		// localhost
		List<AuditLogSink> sinks = router.categorySinks.get(AuditMessage.Category.MISSING_PRIVILEGES);
		// 2 valid endpoints
		Assert.assertEquals(2, sinks.size());
		Assert.assertEquals("endpoint1", sinks.get(0).getName());
		Assert.assertEquals(InternalESSink.class, sinks.get(0).getClass());
		Assert.assertEquals("endpoint3", sinks.get(1).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(1).getClass());
		sinks = router.categorySinks.get(AuditMessage.Category.COMPLIANCE_DOC_WRITE);
		Assert.assertEquals(1, sinks.size());
		Assert.assertEquals("default", sinks.get(0).getName());
		Assert.assertEquals(InternalESSink.class, sinks.get(0).getClass());
		// no valid endpoints for category, should not be present in router
		sinks = router.categorySinks.get(AuditMessage.Category.COMPLIANCE_DOC_READ);
		Assert.assertEquals(null, sinks);

	}

	@Test
	public void testWrongCategories() throws Exception {
		Settings settings = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/routing/configuration_wrong_categories.yml")).build();
		AuditMessageRouter router = new AuditMessageRouter(settings, null, null, null);
		// no default sink, we fall back to debug sink
		Assert.assertEquals(DebugSink.class, router.defaultSink.getClass());

		List<AuditLogSink> sinks = router.categorySinks.get(AuditMessage.Category.MISSING_PRIVILEGES);
		// 3, since default is not valid but replaced with Debug
		Assert.assertEquals(3, sinks.size());
		Assert.assertEquals("default", sinks.get(0).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(0).getClass());
		Assert.assertEquals("endpoint1", sinks.get(1).getName());
		Assert.assertEquals(InternalESSink.class, sinks.get(1).getClass());
		Assert.assertEquals("endpoint2", sinks.get(2).getName());
		Assert.assertEquals(ExternalESSink.class, sinks.get(2).getClass());

		sinks = router.categorySinks.get(AuditMessage.Category.GRANTED_PRIVILEGES);
		Assert.assertEquals(3, sinks.size());
		Assert.assertEquals("endpoint1", sinks.get(0).getName());
		Assert.assertEquals(InternalESSink.class, sinks.get(0).getClass());
		Assert.assertEquals("endpoint3", sinks.get(1).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(1).getClass());
		Assert.assertEquals("default", sinks.get(2).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(2).getClass());

		sinks = router.categorySinks.get(AuditMessage.Category.AUTHENTICATED);
		Assert.assertEquals(1, sinks.size());
		Assert.assertEquals("endpoint1", sinks.get(0).getName());
		Assert.assertEquals(InternalESSink.class, sinks.get(0).getClass());

		// bad headers has not valid endpoint
		Assert.assertEquals(null, router.categorySinks.get(AuditMessage.Category.BAD_HEADERS));
	}

	@Test
	public void testWrongEndpointTypes() throws Exception {
		Settings settings = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/routing/configuration_wrong_endpoint_types.yml")).build();
		AuditMessageRouter router = new AuditMessageRouter(settings, null, null, null);
		// debug sink not valid, fallback to debug
		Assert.assertEquals(DebugSink.class, router.defaultSink.getClass());

		List<AuditLogSink> sinks = router.categorySinks.get(AuditMessage.Category.MISSING_PRIVILEGES);
		// 2 valid endpoints in config, default falls back to debug
		Assert.assertEquals(3, sinks.size());
		Assert.assertEquals("endpoint2", sinks.get(0).getName());
		Assert.assertEquals(ExternalESSink.class, sinks.get(0).getClass());
		Assert.assertEquals("endpoint3", sinks.get(1).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(1).getClass());
		Assert.assertEquals("default", sinks.get(2).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(2).getClass());

		sinks = router.categorySinks.get(AuditMessage.Category.COMPLIANCE_DOC_WRITE);
		Assert.assertEquals(1, sinks.size());
		Assert.assertEquals("default", sinks.get(0).getName());
		Assert.assertEquals(DebugSink.class, sinks.get(0).getClass());

		// no valid endpoints for category, should not be present in router
		sinks = router.categorySinks.get(AuditMessage.Category.COMPLIANCE_DOC_READ);
		Assert.assertEquals(null, sinks);
	}
}
