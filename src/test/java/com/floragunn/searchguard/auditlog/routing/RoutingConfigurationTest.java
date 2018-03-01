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
        Settings settings = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/configuration_valid.yml")).build();
        AuditMessageRouter router = new AuditMessageRouter(settings,null, null, null);
        // default
        Assert.assertEquals(1, router.defaultSinks.size());
        Assert.assertEquals("default", router.defaultSinks.get(0).getName());
        Assert.assertEquals(ExternalESSink.class, router.defaultSinks.get(0).getClass());
        // test category sinks
        List<AuditLogSink> sinks = router.categorySinks.get(AuditMessage.Category.MISSING_PRIVILEGES);
        Assert.assertNotNull(sinks);
        // 3, since we include default as well
        Assert.assertEquals(3, sinks.size());
        Assert.assertEquals("default", sinks.get(0).getName());
        Assert.assertEquals(ExternalESSink.class, sinks.get(0).getClass());
        Assert.assertEquals("endpoint1", sinks.get(1).getName());
        Assert.assertEquals(InternalESSink.class, sinks.get(1).getClass());
        Assert.assertEquals("endpoint2", sinks.get(2).getName());
        Assert.assertEquals(ExternalESSink.class, sinks.get(2).getClass());
        sinks = router.categorySinks.get(AuditMessage.Category.COMPLIANCE_DOC_READ);
        // 1, since we do not include default
        Assert.assertEquals(1, sinks.size());        
        Assert.assertEquals("endpoint3", sinks.get(0).getName());
        Assert.assertEquals(DebugSink.class, sinks.get(0).getClass());
	}
}
