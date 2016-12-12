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

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;

import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.Assert;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;
import com.floragunn.searchguard.test.AbstractSGUnitTest;
import com.floragunn.searchguard.test.helper.cluster.ClusterConfiguration;
import com.floragunn.searchguard.test.helper.cluster.ClusterHelper;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper;

public abstract class AbstractDlsFlsTest extends AbstractSGUnitTest {

    protected void setup() throws Exception {
        setup(ClusterConfiguration.SINGLENODE);
    }

    protected void setup(ClusterConfiguration configuration) throws Exception {
        final Settings nodeSettings = defaultNodeSettings(true);

        log.debug("Starting nodes");
        this.ci = ch.startCluster(nodeSettings, configuration);
        log.debug("Started nodes");

        log.debug("Setup index");
        setupSearchGuardIndex();
        log.debug("Setup done");

        RestHelper rh = new RestHelper(ci);

        this.rh = rh;
    }

    protected void setupSearchGuardIndex() {
        Settings tcSettings = Settings.builder().put("cluster.name", ClusterHelper.clustername)
                .put(defaultNodeSettings(false))
                .put("searchguard.ssl.transport.keystore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("kirk-keystore.jks"))
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "kirk").put("path.home", ".").build();

        try (TransportClient tc = new TransportClientImpl(tcSettings, asCollection(SearchGuardPlugin.class, Netty4Plugin.class))) {
            log.debug("Start transport client to init");

            tc.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress(ci.nodeHost, ci.nodePort)));
            Assert.assertEquals(ci.numNodes,
                    tc.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet().getNodes().size());

            tc.admin().indices().create(new CreateIndexRequest("searchguard")).actionGet();
            
            populate(tc);
            
            ConfigUpdateResponse cur = tc
                    .execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(new String[]{"config","internalusers", "roles","rolesmapping", "actiongroups" }))
                    .actionGet();
            Assert.assertEquals(ci.numNodes, cur.getNodes().size());

        }
    }

    protected Settings defaultNodeSettings(boolean enableRestSSL) {
        Settings.Builder builder = Settings.builder().put("searchguard.ssl.transport.enabled", true)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_ENABLE_OPENSSL_IF_AVAILABLE, false)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLE_OPENSSL_IF_AVAILABLE, false)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "node-0")
                .put("searchguard.ssl.transport.keystore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
                .put("searchguard.ssl.transport.truststore_filepath",
                        FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"))
                .put("searchguard.ssl.transport.enforce_hostname_verification", false)
                .put("searchguard.ssl.transport.resolve_hostname", false)
                .putArray("searchguard.authcz.admin_dn", "CN=kirk,OU=client,O=client,l=tEst, C=De");

        if (enableRestSSL) {
            builder.put("searchguard.ssl.http.enabled", true)
                    .put("searchguard.ssl.http.keystore_filepath",
                            FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
                    .put("searchguard.ssl.http.truststore_filepath",
                            FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"));
        }
        return builder.build();
    }
    
    
    protected static class TransportClientImpl extends TransportClient {

        public TransportClientImpl(Settings settings, Collection<Class<? extends Plugin>> plugins) {
            super(settings, plugins);
        }

        public TransportClientImpl(Settings settings, Settings defaultSettings, Collection<Class<? extends Plugin>> plugins) {
            super(settings, defaultSettings, plugins, null);
        }       
    }
    
    protected static Collection<Class<? extends Plugin>> asCollection(Class<? extends Plugin>... plugins) {
        return Arrays.asList(plugins);
    }
    
    protected void populate(TransportClient tc) {
        
    }
}