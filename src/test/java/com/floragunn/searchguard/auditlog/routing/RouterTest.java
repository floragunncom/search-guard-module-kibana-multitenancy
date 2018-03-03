package com.floragunn.searchguard.auditlog.routing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.floragunn.searchguard.auditlog.helper.MockAuditMessageFactory;
import com.floragunn.searchguard.auditlog.impl.AuditMessage;
import com.floragunn.searchguard.auditlog.impl.AuditMessage.Category;
import com.floragunn.searchguard.auditlog.sink.AuditLogSink;
import com.floragunn.searchguard.auditlog.sink.DebugSink;
import com.floragunn.searchguard.auditlog.sink.ExternalESSink;
import com.floragunn.searchguard.auditlog.sink.InternalESSink;
import com.floragunn.searchguard.auditlog.sink.LoggingSink;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.test.helper.file.FileHelper;

public class RouterTest {

	ClusterService cs = mock(ClusterService.class);
    DiscoveryNode dn = mock(DiscoveryNode.class);

    @Before
    public void setup() {
        when(dn.getHostAddress()).thenReturn("hostaddress");
        when(dn.getId()).thenReturn("hostaddress");
        when(dn.getHostName()).thenReturn("hostaddress");
        when(cs.localNode()).thenReturn(dn);
        when(cs.getClusterName()).thenReturn(new ClusterName("cname"));
    }
    
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
    public void testMessageRouting() throws Exception {

		Settings.Builder settingsBuilder = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/routing/routing.yml"));
		
		Settings settings = settingsBuilder
    			.put("path.home", ".")
    			.put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DISABLED_TRANSPORT_CATEGORIES, "NONE")
                .put("searchguard.audit.threadpool.size", 0)
                .build();
        
		AuditMessageRouter router = new AuditMessageRouter(settings, null, null, null);
        AuditMessage msg = MockAuditMessageFactory.validAuditMessage(Category.MISSING_PRIVILEGES);
        router.route(msg);
        testMessageDeliveredForCategory(router, msg, Category.MISSING_PRIVILEGES, "endpoint1", "endpoint2", "default");
        
        router = new AuditMessageRouter(settings, null, null, null);
        msg = MockAuditMessageFactory.validAuditMessage(Category.COMPLIANCE_DOC_READ);
        router.route(msg);
        testMessageDeliveredForCategory(router, msg, Category.COMPLIANCE_DOC_READ, "endpoint3");

        router = new AuditMessageRouter(settings, null, null, null);
        msg = MockAuditMessageFactory.validAuditMessage(Category.COMPLIANCE_DOC_WRITE);
        router.route(msg);
        testMessageDeliveredForCategory(router, msg, Category.COMPLIANCE_DOC_WRITE, "default");

        router = new AuditMessageRouter(settings, null, null, null);
        msg = MockAuditMessageFactory.validAuditMessage(Category.FAILED_LOGIN);
        router.route(msg);
        testMessageDeliveredForCategory(router, msg, Category.FAILED_LOGIN, "default");

        router = new AuditMessageRouter(settings, null, null, null);
        msg = MockAuditMessageFactory.validAuditMessage(Category.GRANTED_PRIVILEGES);
        router.route(msg);
        testMessageDeliveredForCategory(router, msg, Category.GRANTED_PRIVILEGES, "default");

    }	

    private void testMessageDeliveredForCategory(AuditMessageRouter router, AuditMessage msg, Category categoryToCheck, String ... sinkNames) {
    	Map<Category, List<AuditLogSink>> sinksForCategory = router.categorySinks;
    	for(Category category : Category.values()) {
    		if (category.equals(categoryToCheck)) {    			
    			List<AuditLogSink> sinks = sinksForCategory.get(category);
    			// actual sinks must match the given sink names
    			Assert.assertEquals(sinkNames.length, sinks.size());
    			// actual sinks must be contained in sink names of router
    			for(String sinkName : sinkNames) {
    				Assert.assertTrue(Arrays.stream(sinkNames).anyMatch(sinkName::equals));
    			}
    			// each sink must contain our message
    			for(AuditLogSink sink : sinks) {
    				LoggingSink logSink = (LoggingSink)sink;
    				Assert.assertEquals(1, logSink.messages.size());
    				Assert.assertEquals(msg, logSink.messages.get(0));
    				Assert.assertTrue(logSink.sb.length() > 0);
    			}
    		} else {
    			// make sure sinks are empty for all other categories, exclude default
    			List<AuditLogSink> sinks = sinksForCategory.get(category);
    			if (sinks == null) {
    				continue;
    			}
    			for(AuditLogSink sink : sinks) {
    				// default is configured for multiple categores, skip
    				if (sink.getName().equals("default")) {
    					continue;
    				}
    				LoggingSink logSink = (LoggingSink)sink;
    				Assert.assertEquals(0, logSink.messages.size());
    				Assert.assertTrue(logSink.sb.length() == 0);
    			}
    		}
    	}
    }

}
