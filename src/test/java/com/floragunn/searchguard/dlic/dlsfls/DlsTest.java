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

public class DlsTest extends AbstractDlsFlsTest{
    
    
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
                .source("{\"amount\": 10}")).actionGet();
        tc.index(new IndexRequest("deals").type("deals").id("1").refresh(true)
                .source("{\"amount\": 1500}")).actionGet();
    }
    
    @Test
    public void testDlsAggregations() throws Exception {
        
        setup();
        
        
        String query = "{"+
            "\"query\" : {"+
                 "\"match_all\": {}"+
            "},"+
            "\"aggs\" : {"+
                "\"thesum\" : { \"sum\" : { \"field\" : \"amount\" } }"+
            "}"+
        "}";
        
        HttpResponse res;
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query, new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"value\" : 1500.0"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query, new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"value\" : 1510.0"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
    }
    
    @Test
    public void testDlsTermVectors() throws Exception {
        
        setup();
        
        HttpResponse res;
        res = rh.executeGetRequest("/deals/deals/0/_termvectors?pretty=true", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")));
        Assert.assertTrue(res.getBody().contains("\"found\" : false"));
        
        res = rh.executeGetRequest("/deals/deals/0/_termvectors?pretty=true", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")));
        Assert.assertTrue(res.getBody().contains("\"found\" : true"));
    }
    
    @Test
    public void testDls() throws Exception {
        
        setup();
        
        HttpResponse res;
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=0", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=0", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        
        String query =
                
            "{"+
                "\"query\": {"+
                   "\"range\" : {"+
                      "\"amount\" : {"+
                           "\"gte\" : 8,"+
                            "\"lte\" : 20,"+
                            "\"boost\" : 3.0"+
                        "}"+
                    "}"+
                "}"+
            "}";
        
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query,new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 0,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        query =
                
                "{"+
                    "\"query\": {"+
                       "\"range\" : {"+
                          "\"amount\" : {"+
                               "\"gte\" : 100,"+
                                "\"lte\" : 2000,"+
                                "\"boost\" : 2.0"+
                            "}"+
                        "}"+
                    "}"+
                "}";
            
            
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query,new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));      
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query,new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));  
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?q=amount:10&pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 0,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        res = rh.executeGetRequest("/deals/deals/0?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")));
        Assert.assertTrue(res.getBody().contains("\"found\" : false"));
        
        res = rh.executeGetRequest("/deals/deals/0?realtime=true&pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")));
        Assert.assertTrue(res.getBody().contains("\"found\" : false"));
        
        res = rh.executeGetRequest("/deals/deals/1?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")));
        Assert.assertTrue(res.getBody().contains("\"found\" : true"));
     
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_count?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"count\" : 2,"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));  
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_count?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"count\" : 1,"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));  
        
        //mget
        //msearch
        String msearchBody = 
                "{\"index\":\"deals\", \"type\":\"deals\", \"ignore_unavailable\": true}"+System.lineSeparator()+
                "{\"size\":10, \"query\":{\"bool\":{\"must\":{\"match_all\":{}}}}}"+System.lineSeparator()+
                "{\"index\":\"deals\", \"type\":\"deals\", \"ignore_unavailable\": true}"+System.lineSeparator()+
                "{\"size\":10, \"query\":{\"bool\":{\"must\":{\"match_all\":{}}}}}"+System.lineSeparator();
        
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("_msearch?pretty", msearchBody, new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertFalse(res.getBody().contains("_sg_dls_query"));
        Assert.assertFalse(res.getBody().contains("_sg_fls_fields"));
        Assert.assertTrue(res.getBody().contains("\"amount\" : 1500")); 
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));  
        
        
        String mgetBody = "{"+
                "\"docs\" : ["+
                    "{"+
                         "\"_index\" : \"deals\","+
                        "\"_type\" : \"deals\","+
                        "\"_id\" : \"1\""+
                   " },"+
                   " {"+
                       "\"_index\" : \"deals\","+
                       " \"_type\" : \"deals\","+
                       " \"_id\" : \"2\""+
                    "}"+
                "]"+
            "}"; 
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("_mget?pretty", mgetBody, new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertFalse(res.getBody().contains("_sg_dls_query"));
        Assert.assertFalse(res.getBody().contains("_sg_fls_fields"));
        Assert.assertTrue(res.getBody().contains("amount"));
        Assert.assertTrue(res.getBody().contains("\"found\" : false")); 
        
        
        
    }
    
    @Test
    public void testNonDls() throws Exception {
        
        setup();
        
        HttpResponse res;
        String query =
                
                "{"+
                    "\"query\": {"+
                       "\"range\" : {"+
                          "\"amount\" : {"+
                               "\"gte\" : 100,"+
                                "\"lte\" : 2000,"+
                                "\"boost\" : 2.0"+
                            "}"+
                        "}"+
                    "}"+
                "}";
            
            
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/_search?pretty", query,new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
      
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?q=amount:10&pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        res = rh.executeGetRequest("/deals/deals/0?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")));
        Assert.assertTrue(res.getBody().contains("\"found\" : true"));

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/deals/_count?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"count\" : 2,"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_count?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"count\" : 2,"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
    }
    
    @Test
    public void testDlsCache() throws Exception {
        
        setup();
        
        HttpResponse res;
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")))).getStatusCode());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        
        res = rh.executeGetRequest("/deals/deals/0?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")));
        Assert.assertTrue(res.getBody().contains("\"found\" : true"));
        
        res = rh.executeGetRequest("/deals/deals/0?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("dept_manager", "password")));
        Assert.assertTrue(res.getBody().contains("\"found\" : false"));
    }
}