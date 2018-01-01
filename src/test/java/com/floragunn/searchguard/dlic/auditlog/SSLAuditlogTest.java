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

package com.floragunn.searchguard.dlic.auditlog;

import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class SSLAuditlogTest extends AbstractAuditlogiUnitTest {
    
    @Test
    public void testExternalPemUserPass() throws Exception {

        Settings additionalSettings = Settings.builder()
                .put("searchguard.audit.type", "external_elasticsearch")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_THREADPOOL_SIZE, 0)
                .putList(ConfigConstants.SEARCHGUARD_AUDIT_IGNORE_USERS, "*spock*","admin", "CN=kirk,OU=client,O=client,L=Test,C=DE")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_ENABLE_TRANSPORT, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_RESOLVE_BULK_REQUESTS, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_ENABLE_SSL, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_ENABLE_SSL_CLIENT_AUTH, false)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMTRUSTEDCAS_FILEPATH, 
                        FileHelper.getAbsoluteFilePathFromClassPath("chain-ca.pem"))
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMCERT_FILEPATH, 
                        FileHelper.getAbsoluteFilePathFromClassPath("spock.crtfull.pem"))
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMKEY_FILEPATH, 
                        FileHelper.getAbsoluteFilePathFromClassPath("spock.key.pem"))
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_USERNAME, 
                        "admin")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_PASSWORD, 
                        "admin")
                .build();
        
        setup(additionalSettings);
        System.out.println("### write");
        HttpResponse response = rh.executeGetRequest("_search");
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusCode());
        Thread.sleep(2000);
        response = rh.executeGetRequest("sg6-auditlog*/_search", encodeBasicHeader("admin", "admin"));
        System.out.println(response.getBody());
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        assertContains(response, "*\"hits\":{\"total\":1,*");
        
    }
    
    @Test
    public void testExternalPemClientAuth() throws Exception {

        Settings additionalSettings = Settings.builder()
                .put("searchguard.audit.type", "external_elasticsearch")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_THREADPOOL_SIZE, 0)
                .putList(ConfigConstants.SEARCHGUARD_AUDIT_IGNORE_USERS, "*spock*","admin", "CN=kirk,OU=client,O=client,L=Test,C=DE")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_ENABLE_TRANSPORT, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_RESOLVE_BULK_REQUESTS, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_ENABLE_SSL, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_ENABLE_SSL_CLIENT_AUTH, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMTRUSTEDCAS_FILEPATH, 
                        FileHelper.getAbsoluteFilePathFromClassPath("chain-ca.pem"))
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMCERT_FILEPATH, 
                        FileHelper.getAbsoluteFilePathFromClassPath("kirk.crtfull.pem"))
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMKEY_FILEPATH, 
                        FileHelper.getAbsoluteFilePathFromClassPath("kirk.key.pem"))
                .build();
        
        setup(additionalSettings);
        HttpResponse response = rh.executeGetRequest("_search");
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusCode());
        Thread.sleep(2000);
        response = rh.executeGetRequest("sg6-auditlog*/_search", encodeBasicHeader("admin", "admin"));
        System.out.println(response.getBody());
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        assertContains(response, "*\"hits\":{\"total\":1,*");
    }
    
    @Test
    public void testExternalPemUserPassTp() throws Exception {

        Settings additionalSettings = Settings.builder()
                .put("searchguard.audit.type", "external_elasticsearch")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_THREADPOOL_SIZE, 10)
                .putList(ConfigConstants.SEARCHGUARD_AUDIT_IGNORE_USERS, "*spock*","admin", "CN=kirk,OU=client,O=client,L=Test,C=DE")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_ENABLE_TRANSPORT, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_RESOLVE_BULK_REQUESTS, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_ENABLE_SSL, true)
                .put(ConfigConstants.SEARCHGUARD_AUDIT_SSL_PEMTRUSTEDCAS_FILEPATH, 
                        FileHelper.getAbsoluteFilePathFromClassPath("chain-ca.pem"))
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_USERNAME, 
                        "admin")
                .put(ConfigConstants.SEARCHGUARD_AUDIT_CONFIG_PASSWORD, 
                        "admin")        
                .build();
        
        setup(additionalSettings);
        System.out.println("### write");
        HttpResponse response = rh.executeGetRequest("_search");
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusCode());
        Thread.sleep(2000);
        response = rh.executeGetRequest("sg6-auditlog-*/_search", encodeBasicHeader("admin", "admin"));
        System.out.println(response.getBody());
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        assertContains(response, "*\"hits\":{\"total\":1,*");
    }
}
