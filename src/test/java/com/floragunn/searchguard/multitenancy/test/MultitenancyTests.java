package com.floragunn.searchguard.multitenancy.test;

import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.support.WildcardMatcher;
import com.floragunn.searchguard.test.SingleClusterTest;
import com.floragunn.searchguard.test.helper.rest.RestHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class MultitenancyTests extends SingleClusterTest {

    @Test
    public void testMt() throws Exception {
        final Settings settings = Settings.builder()
                .build();
        setup(settings);
        final RestHelper rh = nonSslRestHelper();

        HttpResponse res;
        String body = "{\"buildNum\": 15460, \"defaultIndex\": \"humanresources\", \"tenant\": \"human_resources\"}";
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executePutRequest(".kibana/config/5.6.0?pretty",body, new BasicHeader("sgtenant", "blafasel"), encodeBasicHeader("hr_employee", "hr_employee"))).getStatusCode());
        
        body = "{\"buildNum\": 15460, \"defaultIndex\": \"humanresources\", \"tenant\": \"human_resources\"}";
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executePutRequest(".kibana/config/5.6.0?pretty",body, new BasicHeader("sgtenant", "business_intelligence"), encodeBasicHeader("hr_employee", "hr_employee"))).getStatusCode());

        body = "{\"buildNum\": 15460, \"defaultIndex\": \"humanresources\", \"tenant\": \"human_resources\"}";
        Assert.assertEquals(HttpStatus.SC_CREATED, (res = rh.executePutRequest(".kibana/config/5.6.0?pretty",body, new BasicHeader("sgtenant", "human_resources"), encodeBasicHeader("hr_employee", "hr_employee"))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(WildcardMatcher.match("*.kibana_*_humanresources*", res.getBody()));
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest(".kibana/config/5.6.0?pretty",new BasicHeader("sgtenant", "human_resources"), encodeBasicHeader("hr_employee", "hr_employee"))).getStatusCode());
        System.out.println(res.getBody());
        Assert.assertTrue(WildcardMatcher.match("*human_resources*", res.getBody()));
        
    }

}
