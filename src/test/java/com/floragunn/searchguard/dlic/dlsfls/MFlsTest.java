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

package com.floragunn.searchguard.dlic.dlsfls;

import org.apache.http.HttpStatus;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class MFlsTest extends AbstractDlsFlsTest{
    
    
    protected void populate(TransportClient tc) {

        tc.index(new IndexRequest("searchguard").type("sg").id("config").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("config", FileHelper.readYamlContent("dlsfls/sg_config.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("sg").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("internalusers")
                .source("internalusers", FileHelper.readYamlContent("dlsfls/sg_internal_users.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("sg").id("roles").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("roles", FileHelper.readYamlContent("dlsfls/sg_roles.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("sg").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("rolesmapping")
                .source("rolesmapping", FileHelper.readYamlContent("dlsfls/sg_roles_mapping.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("sg").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("actiongroups")
                .source("actiongroups", FileHelper.readYamlContent("dlsfls/sg_action_groups.yml"))).actionGet();
                
        tc.index(new IndexRequest("deals").type("deals").id("0").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("{\"customer\": {\"name\":\"cust1\"}, \"zip\": \"12345\",\"secret\": \"tellnoone\",\"amount\": 10}", XContentType.JSON)).actionGet();
        tc.index(new IndexRequest("finance").type("finance").id("1").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("{\"finfield2\":\"fff\",\"xcustomer\": {\"name\":\"cust2\", \"ctype\":\"industry\"}, \"famount\": 1500}", XContentType.JSON)).actionGet();
    }
    
    @Test
    public void testFlsMGetSearch() throws Exception {
        
        setup();
        
        HttpResponse res;
        
        System.out.println("### normal search");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("deals,finance/_search?pretty", encodeBasicHeader("dept_manager_fls", "password"))).getStatusCode());
        Assert.assertFalse(res.getBody().contains("_sg_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertFalse(res.getBody().contains("xception"));
        Assert.assertTrue(res.getBody().contains("cust1"));
        Assert.assertTrue(res.getBody().contains("zip"));
        Assert.assertTrue(res.getBody().contains("finfield2"));
        Assert.assertFalse(res.getBody().contains("amount"));
        Assert.assertFalse(res.getBody().contains("secret")); 
        
        //mget
        //msearch
        String msearchBody = 
                "{\"index\":\"deals\", \"type\":\"deals\", \"ignore_unavailable\": true}"+System.lineSeparator()+
                "{\"size\":10, \"query\":{\"bool\":{\"must\":{\"match_all\":{}}}}}"+System.lineSeparator()+
                "{\"index\":\"finance\", \"type\":\"finance\", \"ignore_unavailable\": true}"+System.lineSeparator()+
                "{\"size\":10, \"query\":{\"bool\":{\"must\":{\"match_all\":{}}}}}"+System.lineSeparator();
        
        System.out.println("### msearch");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("_msearch?pretty", msearchBody, encodeBasicHeader("dept_manager_fls", "password"))).getStatusCode());
        Assert.assertFalse(res.getBody().contains("_sg_"));
        Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
        Assert.assertFalse(res.getBody().contains("xception"));
        Assert.assertTrue(res.getBody().contains("cust1"));
        Assert.assertTrue(res.getBody().contains("zip"));
        Assert.assertTrue(res.getBody().contains("finfield2"));
        Assert.assertFalse(res.getBody().contains("amount"));
        Assert.assertFalse(res.getBody().contains("secret"));
        
        
        String mgetBody = "{"+
                "\"docs\" : ["+
                    "{"+
                         "\"_index\" : \"deals\","+
                        "\"_type\" : \"deals\","+
                        "\"_id\" : \"0\""+
                   " },"+
                   " {"+
                       "\"_index\" : \"finance\","+
                       " \"_type\" : \"finance\","+
                       " \"_id\" : \"1\""+
                    "}"+
                "]"+
            "}"; 
        
        System.out.println("### mget");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("_mget?pretty", mgetBody, encodeBasicHeader("dept_manager_fls", "password"))).getStatusCode());
        Assert.assertFalse(res.getBody().contains("_sg_"));
        Assert.assertTrue(res.getBody().contains("\"found\" : true"));
        Assert.assertFalse(res.getBody().contains("\"found\" : false"));
        Assert.assertFalse(res.getBody().contains("xception"));
        Assert.assertTrue(res.getBody().contains("cust1"));
        Assert.assertTrue(res.getBody().contains("zip"));
        Assert.assertTrue(res.getBody().contains("finfield2"));
        Assert.assertFalse(res.getBody().contains("amount"));
        Assert.assertFalse(res.getBody().contains("secret")); 
    }
}