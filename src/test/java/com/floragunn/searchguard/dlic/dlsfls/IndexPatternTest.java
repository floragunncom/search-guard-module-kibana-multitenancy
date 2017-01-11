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

public class IndexPatternTest extends AbstractDlsFlsTest{
    
    
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
        
        tc.index(new IndexRequest("logstash-2016").type("logs").refresh(true)
                .source("{\"message\":\"mymsg1a\", \"ipaddr\": \"10.0.0.0\",\"msgid\": \"12\"}")).actionGet();
        tc.index(new IndexRequest("logstash-2016").type("logs").refresh(true)
                .source("{\"message\":\"mymsg1b\", \"ipaddr\": \"10.0.0.1\",\"msgid\": \"14\"}")).actionGet();
        tc.index(new IndexRequest("logstash-2018").type("logs").refresh(true)
                .source("{\"message\":\"mymsg1c\", \"ipaddr\": \"10.0.0.2\",\"msgid\": \"12\"}")).actionGet();
        tc.index(new IndexRequest("logstash-2018").type("logs").refresh(true)
                .source("{\"message\":\"mymsg1d\", \"ipaddr\": \"10.0.0.3\",\"msgid\": \"14\"}")).actionGet();
            }
    
    @Test
    public void testSearch() throws Exception {
        
        setup();

        HttpResponse res;

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-2016/logs/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertTrue(res.getBody().contains("ipaddr"));
        Assert.assertTrue(res.getBody().contains("message"));
        Assert.assertTrue(res.getBody().contains("mymsg"));
        Assert.assertTrue(res.getBody().contains("msgid"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-2016/logs/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("logstash", "password")))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertFalse(res.getBody().contains("ipaddr"));
        Assert.assertFalse(res.getBody().contains("message"));
        Assert.assertFalse(res.getBody().contains("mymsg"));
        Assert.assertTrue(res.getBody().contains("msgid"));
    }
    
    @Test
    public void testSearchWc() throws Exception {
        
        setup();

        HttpResponse res;

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-20*/logs/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(res.getBody().contains("\"total\" : 4,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertTrue(res.getBody().contains("ipaddr"));
        Assert.assertTrue(res.getBody().contains("message"));
        Assert.assertTrue(res.getBody().contains("mymsg"));
        Assert.assertTrue(res.getBody().contains("msgid"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-20*/logs/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("logstash", "password")))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertFalse(res.getBody().contains("ipaddr"));
        Assert.assertFalse(res.getBody().contains("message"));
        Assert.assertFalse(res.getBody().contains("mymsg"));
        Assert.assertTrue(res.getBody().contains("msgid"));
    }
    
    @Test
    public void testSearchWcRegex() throws Exception {
        
        setup();

        HttpResponse res;

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-20*/logs/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(res.getBody().contains("\"total\" : 4,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertTrue(res.getBody().contains("ipaddr"));
        Assert.assertTrue(res.getBody().contains("message"));
        Assert.assertTrue(res.getBody().contains("mymsg"));
        Assert.assertTrue(res.getBody().contains("msgid"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-20*/logs/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("regex", "password")))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertFalse(res.getBody().contains("ipaddr"));
        Assert.assertFalse(res.getBody().contains("message"));
        Assert.assertFalse(res.getBody().contains("mymsg"));
        Assert.assertTrue(res.getBody().contains("msgid"));
    }
}