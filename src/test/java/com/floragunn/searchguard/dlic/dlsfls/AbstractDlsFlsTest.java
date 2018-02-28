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

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;

import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.test.SingleClusterTest;
import com.floragunn.searchguard.test.helper.rest.RestHelper;

public abstract class AbstractDlsFlsTest extends SingleClusterTest {

    protected RestHelper rh = null;
    
    @Override
    protected String getResourceFolder() {
        return "dlsfls";
    }
    
    protected final void setup() throws Exception {
        Settings settings = Settings.builder().put(ConfigConstants.SEARCHGUARD_AUDIT_TYPE_DEFAULT, "debug").build();
        setup(Settings.EMPTY, null, settings, false);
        
        try(TransportClient tc = getInternalTransportClient(this.clusterInfo, Settings.EMPTY)) {
            populate(tc);
            ConfigUpdateResponse cur = tc
                    .execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(ConfigConstants.CONFIG_NAMES.toArray(new String[0])))
                    .actionGet();
            Assert.assertEquals(this.clusterInfo.numNodes, cur.getNodes().size());
        }
        
        rh = nonSslRestHelper();
    }
    
    abstract void populate(TransportClient tc);
}