/*
 * Copyright 2016-2018 by floragunn GmbH - All rights reserved
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

package com.floragunn.searchguard.auditlog.compliance;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.auditlog.AbstractAuditlogiUnitTest;
import com.floragunn.searchguard.auditlog.integration.TestAuditlogImpl;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.test.DynamicSgConfig;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class ComplianceAuditlogTest extends AbstractAuditlogiUnitTest {

    @Test
    public void testSourceFilter() throws Exception {

        Settings additionalSettings = Settings.builder()
                .put("searchguard.audit.type", TestAuditlogImpl.class.getName())
                .put(ConfigConstants.SEARCHGUARD_AUDIT_ENABLE_TRANSPORT, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_RESOLVE_BULK_REQUESTS, true)
                .put(ConfigConstants.SEARCHGUARD_COMPLIANCE_HISTORY_EXTERNAL_CONFIG_ENABLED, false)
                //.put(ConfigConstants.SEARCHGUARD_COMPLIANCE_HISTORY_WRITE_WATCHED_INDICES, "emp")
                .put(ConfigConstants.SEARCHGUARD_COMPLIANCE_HISTORY_READ_WATCHED_FIELDS, "emp")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DISABLED_TRANSPORT_CATEGORIES, "authenticated,GRANTED_PRIVILEGES")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DISABLED_REST_CATEGORIES, "authenticated,GRANTED_PRIVILEGES")
                .put("searchguard.audit.threadpool.size", 0)
                .build();

        setup(additionalSettings);
        final boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
        final String keystore = rh.keystore;
        rh.sendHTTPClientCertificate = true;
        rh.keystore = "auditlog/kirk-keystore.jks";
        rh.executePutRequest("emp/doc/0?refresh", "{\"Designation\" : \"CEO\", \"Gender\" : \"female\", \"Salary\" : 100}", new Header[0]);
        rh.executePutRequest("emp/doc/1?refresh", "{\"Designation\" : \"IT\", \"Gender\" : \"male\", \"Salary\" : 200}", new Header[0]);
        rh.executePutRequest("emp/doc/2?refresh", "{\"Designation\" : \"IT\", \"Gender\" : \"female\", \"Salary\" : 300}", new Header[0]);
        rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
        rh.keystore = keystore;

        System.out.println("#### test source includes");
        String search = "{" +
                "   \"_source\":[" +
                "      \"Gender\""+
                "   ]," +
                "   \"from\":0," +
                "   \"size\":3," +
                "   \"query\":{" +
                "      \"term\":{" +
                "         \"Salary\": 300" +
                "      }" +
                "   }" +
                "}";
        HttpResponse response = rh.executePostRequest("_search?pretty", search, encodeBasicHeader("admin", "admin"));
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        System.out.println(response.getBody());
        Thread.sleep(1500);
        System.out.println(TestAuditlogImpl.sb.toString());
        Assert.assertTrue(TestAuditlogImpl.messages.size() >= 1);
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("COMPLIANCE_DOC_READ"));
        Assert.assertFalse(TestAuditlogImpl.sb.toString().contains("Designation"));
        Assert.assertFalse(TestAuditlogImpl.sb.toString().contains("Salary"));
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("Gender"));
    }

    @Test
    public void testInternalConfig() throws Exception {

        Settings additionalSettings = Settings.builder()
                .put("searchguard.audit.type", TestAuditlogImpl.class.getName())
                .put(ConfigConstants.SEARCHGUARD_AUDIT_ENABLE_TRANSPORT, false)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_ENABLE_REST, false)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_RESOLVE_BULK_REQUESTS, false)
                .put(ConfigConstants.SEARCHGUARD_COMPLIANCE_HISTORY_METADATA_ONLY, false)
                .put(ConfigConstants.SEARCHGUARD_COMPLIANCE_HISTORY_EXTERNAL_CONFIG_ENABLED, false)
                .put(ConfigConstants.SEARCHGUARD_COMPLIANCE_HISTORY_INTERNAL_CONFIG_ENABLED, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DISABLED_TRANSPORT_CATEGORIES, "authenticated,GRANTED_PRIVILEGES")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_DISABLED_REST_CATEGORIES, "authenticated,GRANTED_PRIVILEGES")
                .put("searchguard.audit.threadpool.size", 0)
                .build();

        setup(additionalSettings);
        
        try (TransportClient tc = getInternalTransportClient()) {

            for(IndexRequest ir: new DynamicSgConfig().setSgRoles("sg_roles_2.yml").getDynamicConfig(getResourceFolder())) {
                tc.index(ir).actionGet();
            }
            
        }
        
        HttpResponse response = rh.executeGetRequest("_search?pretty", encodeBasicHeader("admin", "admin"));
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        System.out.println(response.getBody());
        Thread.sleep(1500);
        System.out.println(TestAuditlogImpl.sb.toString());
        Assert.assertEquals(57, TestAuditlogImpl.messages.size());
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("COMPLIANCE_INTERNAL_CONFIG_READ"));
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("COMPLIANCE_INTERNAL_CONFIG_WRITE"));
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("anonymous_auth_enabled"));
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("indices:data/read/suggest"));
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("internalusers"));
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("sg_all_access"));
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("indices:data/read/suggest"));
        Assert.assertFalse(TestAuditlogImpl.sb.toString().contains("eyJzZWFyY2hndWFy"));
        Assert.assertFalse(TestAuditlogImpl.sb.toString().contains("eyJBTEwiOlsiaW"));
        Assert.assertFalse(TestAuditlogImpl.sb.toString().contains("eyJhZG1pbiI6e"));
        Assert.assertFalse(TestAuditlogImpl.sb.toString().contains("eyJzZ19hb"));
        Assert.assertFalse(TestAuditlogImpl.sb.toString().contains("eyJzZ19hbGx"));
        Assert.assertFalse(TestAuditlogImpl.sb.toString().contains("dvcmYiOnsiY2x"));
        Assert.assertTrue(TestAuditlogImpl.sb.toString().contains("\\\"op\\\":\\\"remove\\\",\\\"path\\\":\\\"/sg_worf\\\""));
        
    }

}
