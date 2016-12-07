/*
 * Copyright 2016 by floragunn UG (haftungsbeschränkt) - All rights reserved
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

package com.floragunn.searchguard.dlic.dlsfls;

import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class FlsTest extends AbstractDlsFlsTest{
    
    
    protected void populate(TransportClient tc) {

        tc.index(new IndexRequest("searchguard").type("config").id("0").refresh(true)
                .source(FileHelper.readYamlContent("sg_config.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("internalusers").refresh(true).id("0")
                .source(FileHelper.readYamlContent("sg_internal_users.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("roles").id("0").refresh(true)
                .source(FileHelper.readYamlContent("sg_roles.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("rolesmapping").refresh(true).id("0")
                .source(FileHelper.readYamlContent("sg_roles_mapping.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("actiongroups").refresh(true).id("0")
                .source(FileHelper.readYamlContent("sg_action_groups.yml"))).actionGet();
        
        tc.index(new IndexRequest("deals").type("deals").id("0").refresh(true)
                .source("{\"customer\": {\"name\":\"cust1\"}, \"zip\": \"12345\",\"secret\": \"tellnoone\",\"amount\": 10}")).actionGet();
        tc.index(new IndexRequest("deals").type("deals").id("1").refresh(true)
                .source("{\"customer\": {\"name\":\"cust2\", \"ctype\":\"industry\"}, \"amount\": 1500}")).actionGet();
    }
    
    @Test
    public void testFlsSearch() throws Exception {
        
        setup();

        HttpResponse res;

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertTrue(res.getBody().contains("cust1"));
        Assert.assertTrue(res.getBody().contains("cust2"));
        Assert.assertTrue(res.getBody().contains("zip"));
        Assert.assertTrue(res.getBody().contains("ctype"));
        Assert.assertTrue(res.getBody().contains("amount"));
        Assert.assertTrue(res.getBody().contains("secret"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager_fls", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertTrue(res.getBody().contains("cust1"));
        Assert.assertTrue(res.getBody().contains("cust2"));
        Assert.assertTrue(res.getBody().contains("zip"));
        Assert.assertTrue(res.getBody().contains("ctype"));
        Assert.assertFalse(res.getBody().contains("amount"));
        Assert.assertFalse(res.getBody().contains("secret"));
    }
    
    @Test
    public void testFlsGet() throws Exception {
        
        setup();

        HttpResponse res;

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/deals/0?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"found\" : true"));
        Assert.assertTrue(res.getBody().contains("cust1"));
        Assert.assertFalse(res.getBody().contains("cust2"));
        Assert.assertTrue(res.getBody().contains("zip"));
        Assert.assertFalse(res.getBody().contains("ctype"));
        Assert.assertTrue(res.getBody().contains("amount"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/deals/0?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager_fls", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"found\" : true"));
        Assert.assertTrue(res.getBody().contains("cust1"));
        Assert.assertFalse(res.getBody().contains("cust2"));
        Assert.assertTrue(res.getBody().contains("zip"));
        Assert.assertFalse(res.getBody().contains("ctype"));
        Assert.assertFalse(res.getBody().contains("amount"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/deals/0?realtime=true&pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager_fls", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"found\" : true"));
        Assert.assertTrue(res.getBody().contains("cust1"));
        Assert.assertFalse(res.getBody().contains("cust2"));
        Assert.assertTrue(res.getBody().contains("zip"));
        Assert.assertFalse(res.getBody().contains("ctype"));
        Assert.assertFalse(res.getBody().contains("amount"));
        
    }
    
    @Test
    public void testFlsUpdate() throws Exception {
        
        setup();

        HttpResponse res;

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/deals/0/_update?pretty", "{\"doc\": {\"zip\": \"98765\"}}", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"_version\" : 2"));
        Assert.assertTrue(res.getBody().contains("\"successful\" : 1"));
        
        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, (res = rh.executePostRequest("/deals/deals/0/_update?pretty", "{\"doc\": {\"zip\": \"98765000\"}}", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager_fls", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("Update is not supported"));
    }
    
    @Test
    public void testFlsUpdateIndex() throws Exception {
        
        setup();

        HttpResponse res = null;
              
        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, (res = rh.executePostRequest("/deals/deals/0/_update?pretty", "{\"doc\": {\"zip\": \"98765000\"}}", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager_fls", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("Update is not supported"));
    }
}