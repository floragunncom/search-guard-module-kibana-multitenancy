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

import java.io.IOException;

import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.transport.TransportClient;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class FlsFieldsTest extends AbstractDlsFlsTest{
    
    
    protected void populate(TransportClient tc) {

        tc.index(new IndexRequest("searchguard").type("config").id("0").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("config", FileHelper.readYamlContent("sg_config.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("internalusers").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("0")
                .source("internalusers", FileHelper.readYamlContent("sg_internal_users.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("roles").id("0").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("roles", FileHelper.readYamlContent("sg_roles.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("rolesmapping").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("0")
                .source("rolesmapping", FileHelper.readYamlContent("sg_roles_mapping.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("actiongroups").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("0")
                .source("actiongroups", FileHelper.readYamlContent("sg_action_groups.yml"))).actionGet();
        
        tc.admin().indices().create(new CreateIndexRequest("deals")
        .mapping("deals", "timestamp","type=date","@timestamp","type=date")).actionGet();
        
        try {
            String doc = FileHelper.loadFile("doc1.json");

            for (int i = 0; i < 10; i++) {
                final String moddoc = doc.replace("<name>", "cust" + i).replace("<employees>", "" + i).replace("<date>", "1970-01-02");
                tc.index(new IndexRequest("deals").type("deals").id("0" + i).setRefreshPolicy(RefreshPolicy.IMMEDIATE).source(moddoc)).actionGet();
            }

        } catch (IOException e) {
            Assert.fail(e.toString());
        }

    }
    
    
    @Test
    public void testFields() throws Exception {        
        setup();

        String query = FileHelper.loadFile("flsquery.json");
        
        HttpResponse res;        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query, new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("secret"));
        Assert.assertTrue(res.getBody().contains("@timestamp"));
        Assert.assertTrue(res.getBody().contains("\"timestamp"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query, new BasicHeader("Authorization", "Basic "+encodeBasicHeader("fls_fields", "password")))).getStatusCode());
        Assert.assertFalse(res.getBody().contains("customer"));
        Assert.assertFalse(res.getBody().contains("secret"));
        Assert.assertFalse(res.getBody().contains("timestamp"));
        Assert.assertFalse(res.getBody().contains("numfield5"));
    }
    
    @Test
    public void testFields2() throws Exception {        
        setup();

        String query = FileHelper.loadFile("flsquery2.json");
        
        HttpResponse res;        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query, new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("secret"));
        Assert.assertTrue(res.getBody().contains("@timestamp"));
        Assert.assertTrue(res.getBody().contains("\"timestamp"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query, new BasicHeader("Authorization", "Basic "+encodeBasicHeader("fls_fields", "password")))).getStatusCode());
        Assert.assertFalse(res.getBody().contains("customer"));
        Assert.assertFalse(res.getBody().contains("secret"));
        Assert.assertFalse(res.getBody().contains("timestamp"));
        Assert.assertTrue(res.getBody().contains("numfield5"));
    }
}