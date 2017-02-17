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
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

@Ignore
public class FlsPerfTest extends AbstractDlsFlsTest{
    
    
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
                
        tc.admin().indices().create(new CreateIndexRequest("deals")
        .settings("index.mapping.total_fields.limit",50000
                , "number_of_shards", 10
                ,"number_of_replicas", 0))
        .actionGet();
        
        try {
            
            IndexRequest ir =  new IndexRequest("deals").type("deals2").id("idx1");
            XContentBuilder b = XContentBuilder.builder(JsonXContent.jsonXContent);
            b.startObject();

            b.field("amount",1000);

            b.startObject("xyz");
            b.field("abc","val");
            b.endObject();
        
            b.endObject();
            ir.source(b);
            
            tc.index(ir).actionGet();

            for(int i=0; i<1500; i++) {
                
                ir =  new IndexRequest("deals").type("deals").id("id"+i);
                b = XContentBuilder.builder(JsonXContent.jsonXContent);
                b.startObject();
                for(int j=0; j<2000;j++) {
                    b.field("field"+j,"val"+j);
                }
            
                b.endObject();
                ir.source(b);
                
                tc.index(ir).actionGet();
            
            }
            
            tc.admin().indices().refresh(new RefreshRequest("deals")).actionGet();
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail(e.toString());
        }
        
    }
    
    @Test
    public void testFlsPerfNamed() throws Exception {
        
        setup();

        HttpResponse res;

        StopWatch sw = new StopWatch("testFlsPerfNamed");
        sw.start("non fls");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        sw.stop();
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        sw.start("with fls");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_named_only", "password")))).getStatusCode());
        sw.stop();
        Assert.assertFalse(res.getBody().contains("field1\""));
        Assert.assertFalse(res.getBody().contains("field2\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        sw.start("with fls 2 after warmup");
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_named_only", "password")))).getStatusCode());
        sw.stop();
        
        Assert.assertFalse(res.getBody().contains("field1\""));
        Assert.assertFalse(res.getBody().contains("field2\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        sw.start("with fls 3 after warmup");

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_named_only", "password")))).getStatusCode());
        sw.stop();
        
        Assert.assertFalse(res.getBody().contains("field1\""));
        Assert.assertFalse(res.getBody().contains("field2\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        System.out.println(sw.prettyPrint());
    }
    
    @Test
    public void testFlsPerfWcEx() throws Exception {
        
        setup();

        HttpResponse res;

        StopWatch sw = new StopWatch("testFlsPerfWcEx");
        sw.start("non fls");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        sw.stop();
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        sw.start("with fls");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_wc_ex", "password")))).getStatusCode());
        sw.stop();
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertFalse(res.getBody().contains("field50\""));
        Assert.assertFalse(res.getBody().contains("field997\""));
        
        sw.start("with fls 2 after warmup");
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_wc_ex", "password")))).getStatusCode());
        sw.stop();
        
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertFalse(res.getBody().contains("field50\""));
        Assert.assertFalse(res.getBody().contains("field997\""));
        
        sw.start("with fls 3 after warmup");

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_wc_ex", "password")))).getStatusCode());
        sw.stop();
        
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertFalse(res.getBody().contains("field50\""));
        Assert.assertFalse(res.getBody().contains("field997\""));
        
        System.out.println(sw.prettyPrint());
    }
    
    @Test
    public void testFlsPerfNamedEx() throws Exception {
        
        setup();

        HttpResponse res;

        StopWatch sw = new StopWatch("testFlsPerfNamedEx");
        sw.start("non fls");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        sw.stop();
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        sw.start("with fls");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_named_ex", "password")))).getStatusCode());
        sw.stop();
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertFalse(res.getBody().contains("field50\""));
        Assert.assertFalse(res.getBody().contains("field997\""));
        
        sw.start("with fls 2 after warmup");
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_named_ex", "password")))).getStatusCode());
        sw.stop();
        
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertFalse(res.getBody().contains("field50\""));
        Assert.assertFalse(res.getBody().contains("field997\""));
        
        sw.start("with fls 3 after warmup");

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_named_ex", "password")))).getStatusCode());
        sw.stop();
        
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertFalse(res.getBody().contains("field50\""));
        Assert.assertFalse(res.getBody().contains("field997\""));
        
        System.out.println(sw.prettyPrint());
    }
    
    @Test
    public void testFlsWcIn() throws Exception {
        
        setup();

        HttpResponse res;

        StopWatch sw = new StopWatch("testFlsWcIn");
        sw.start("non fls");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin")))).getStatusCode());
        sw.stop();
        Assert.assertTrue(res.getBody().contains("field1\""));
        Assert.assertTrue(res.getBody().contains("field2\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        sw.start("with fls");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_wc_in", "password")))).getStatusCode());
        sw.stop();
        Assert.assertFalse(res.getBody().contains("field0\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        sw.start("with fls 2 after warmup");
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_wc_in", "password")))).getStatusCode());
        sw.stop();
        
        Assert.assertFalse(res.getBody().contains("field0\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        sw.start("with fls 3 after warmup");

        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/deals/_search?pretty&size=1000", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("perf_wc_in", "password")))).getStatusCode());
        sw.stop();
        
        Assert.assertFalse(res.getBody().contains("field0\""));
        Assert.assertTrue(res.getBody().contains("field50\""));
        Assert.assertTrue(res.getBody().contains("field997\""));
        
        System.out.println(sw.prettyPrint());
    }
}