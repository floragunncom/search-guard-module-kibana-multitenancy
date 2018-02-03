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
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class DlsNestedTest extends AbstractDlsFlsTest{
    
    @Override
    protected void populate(TransportClient tc) {

        tc.index(new IndexRequest("searchguard").type("sg").id("config").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("config", FileHelper.readYamlContent("sg_config.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("sg").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("internalusers")
                .source("internalusers", FileHelper.readYamlContent("sg_internal_users.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("sg").id("roles").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("roles", FileHelper.readYamlContent("sg_roles.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("sg").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("rolesmapping")
                .source("rolesmapping", FileHelper.readYamlContent("sg_roles_mapping.yml"))).actionGet();
        tc.index(new IndexRequest("searchguard").type("sg").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("actiongroups")
                .source("actiongroups", FileHelper.readYamlContent("sg_action_groups.yml"))).actionGet();
        
        String mapping = "{" +
                "        \"mytype\" : {" +
                "            \"properties\" : {" +
                "                \"amount\" : {\"type\": \"integer\"}," +
                "                \"owner\" : {\"type\": \"text\"}," +
                "                \"my_nested_object\" : {\"type\" : \"nested\"}" +
                "            }" +
                "        }" +
                "    }" +
                "";
        
        tc.admin().indices().create(new CreateIndexRequest("deals")
        .settings(Settings.builder().put("number_of_shards", 1).put("number_of_replicas", 0).build())
        .mapping("mytype", mapping, XContentType.JSON)).actionGet();
        
        //tc.index(new IndexRequest("deals").type("mytype").id("3").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
        //        .source("{\"amount\": 7,\"owner\": \"a\", \"my_nested_object\" : {\"name\": \"spock\"}}", XContentType.JSON)).actionGet();
        //tc.index(new IndexRequest("deals").type("mytype").id("4").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
        //        .source("{\"amount\": 8, \"my_nested_object\" : {\"name\": \"spock\"}}", XContentType.JSON)).actionGet();
        //tc.index(new IndexRequest("deals").type("mytype").id("5").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
        //        .source("{\"amount\": 1400,\"owner\": \"a\", \"my_nested_object\" : {\"name\": \"spock\"}}", XContentType.JSON)).actionGet();
        tc.index(new IndexRequest("deals").type("mytype").id("1").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .source("{\"amount\": 1500,\"owner\": \"b\", \"my_nested_object\" : {\"name\": \"spock\"}}", XContentType.JSON)).actionGet();
    }
    
    @Test
    public void testNestedQuery() throws Exception {
        
        setup();
        
        
        String query = "{" +
                "  \"query\": {" +
                "    \"nested\": {" +
                "      \"path\": \"my_nested_object\"," +
                "      \"query\": {" +
                "        \"match\": {\"my_nested_object.name\" : \"spock\"}" +
                "      }," +
                "      \"inner_hits\": {} " +
                "    }" +
                "  }" +
                "}";
                        
        
        HttpResponse res;
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/mytype/_search?pretty", query, encodeBasicHeader("dept_manager", "password"))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(res.getBody().contains("\"total\" : 1,\n    \"max_"));
        Assert.assertTrue(res.getBody().contains("\"my_nested_object\" : {"));
        Assert.assertTrue(res.getBody().contains("\"field\" : \"my_nested_object\","));
        Assert.assertTrue(res.getBody().contains("\"offset\" : 0"));
        
        //Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePostRequest("/deals/mytype/_search?pretty", query, encodeBasicHeader("admin", "admin"))).getStatusCode());
        //System.out.println(res.getBody());
        //Assert.assertTrue(res.getBody().contains("\"total\" : 2,\n    \"max_"));
        //Assert.assertTrue(res.getBody().contains("\"value\" : 1510.0"));
        //Assert.assertTrue(res.getBody().contains("\"failed\" : 0"));
    }
    
    
}