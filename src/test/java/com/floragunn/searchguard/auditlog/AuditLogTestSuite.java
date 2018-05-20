package com.floragunn.searchguard.auditlog;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.floragunn.searchguard.auditlog.compliance.ComplianceAuditlogTest;
import com.floragunn.searchguard.auditlog.compliance.RestApiComplianceAuditlogTest;
import com.floragunn.searchguard.auditlog.impl.AuditlogTest;
import com.floragunn.searchguard.auditlog.impl.DelegateTest;
import com.floragunn.searchguard.auditlog.impl.DisabledCategoriesTest;
import com.floragunn.searchguard.auditlog.impl.IgnoreAuditUsersTest;
import com.floragunn.searchguard.auditlog.impl.TracingTests;
import com.floragunn.searchguard.auditlog.integration.BasicAuditlogTest;
import com.floragunn.searchguard.auditlog.integration.SSLAuditlogTest;
import com.floragunn.searchguard.auditlog.routing.FallbackTest;
import com.floragunn.searchguard.auditlog.routing.RouterTest;
import com.floragunn.searchguard.auditlog.routing.RoutingConfigurationTest;
import com.floragunn.searchguard.auditlog.routing.ThreadPoolSettingsTest;
import com.floragunn.searchguard.auditlog.sink.KafkaSinkTest;
import com.floragunn.searchguard.auditlog.sink.SinkProviderTLSTest;
import com.floragunn.searchguard.auditlog.sink.SinkProviderTest;
import com.floragunn.searchguard.auditlog.sink.WebhookAuditLogTest;

@RunWith(Suite.class)

@Suite.SuiteClasses({
	ComplianceAuditlogTest.class,
	RestApiComplianceAuditlogTest.class,
	AuditlogTest.class,
	DelegateTest.class,
	DisabledCategoriesTest.class,
	IgnoreAuditUsersTest.class,
	TracingTests.class,
	BasicAuditlogTest.class,
	SSLAuditlogTest.class,
	FallbackTest.class,
	RouterTest.class,
	RoutingConfigurationTest.class,
	ThreadPoolSettingsTest.class,
	SinkProviderTest.class,
	SinkProviderTLSTest.class,
	WebhookAuditLogTest.class,
	KafkaSinkTest.class
})
public class AuditLogTestSuite {

}
