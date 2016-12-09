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

package com.floragunn.searchguard.test.helper.cluster;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.PluginAwareNode;
import org.elasticsearch.transport.Netty4Plugin;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import com.floragunn.searchguard.test.helper.cluster.ClusterConfiguration.NodeSettings;

public class ClusterHelper {

	protected final Logger log = LogManager.getLogger(ClusterHelper.class);

	protected List<Node> esNodes = new LinkedList<>();

	public final static String clustername = "searchguard_testcluster";

	/**
	 * Start n Elasticsearch nodes with the provided settings
	 * 
	 * @return
	 */
	public final ClusterInfo startCluster(final Settings settings, ClusterConfiguration clusterConfiguration)
			throws Exception {
		if (!esNodes.isEmpty()) {
			throw new RuntimeException("There are still " + esNodes.size() + " nodes instantiated, close them first.");
		}

		FileUtils.deleteDirectory(new File("data"));

		List<NodeSettings> nodeSettings = clusterConfiguration.getNodeSettings();

		for (int i = 0; i < nodeSettings.size(); i++) {
			NodeSettings setting = nodeSettings.get(i);

			Node node = new PluginAwareNode(
					getDefaultSettingsBuilder(i, setting.masterNode, setting.dataNode, setting.tribeNode)
							.put(settings == null ? Settings.Builder.EMPTY_SETTINGS : settings).build(),
					Netty4Plugin.class, SearchGuardPlugin.class);
			node.start();
			esNodes.add(node);
			Thread.sleep(200);
		}
		ClusterInfo cInfo = waitForGreenClusterState();
		cInfo.numNodes = nodeSettings.size();
		return cInfo;
	}

	public final void stopCluster() throws Exception {
		for (Node node : esNodes) {
			node.close();
			Thread.sleep(500);
		}
		esNodes.clear();
	}

	/**
	 * Waits for a green cluster state.
	 * 
	 * @return
	 */

	protected ClusterInfo waitForGreenClusterState() throws IOException {
		return waitForCluster(ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(10));
	}

	protected ClusterInfo waitForCluster(final ClusterHealthStatus status, final TimeValue timeout) throws IOException {
		if (esNodes.isEmpty()) {
			throw new RuntimeException("List of nodes was empty.");
		}

		ClusterInfo clusterInfo = new ClusterInfo();

		Node node = esNodes.get(0);
		Client client = node.client();
		try {
			log.debug("waiting for cluster state {}", status.name());
			final ClusterHealthResponse healthResponse = client.admin().cluster().prepareHealth()
					.setWaitForStatus(status).setTimeout(timeout).setWaitForNodes("" + esNodes.size()).execute()
					.actionGet();
			if (healthResponse.isTimedOut()) {
				throw new IOException("cluster state is " + healthResponse.getStatus().name() + " with "
						+ healthResponse.getNumberOfNodes() + " nodes");
			} else {
				log.debug("... cluster state ok " + healthResponse.getStatus().name() + " with "
						+ healthResponse.getNumberOfNodes() + " nodes");
			}

			org.junit.Assert.assertEquals(esNodes.size(), healthResponse.getNumberOfNodes());

			final NodesInfoResponse res = client.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet();

			final List<NodeInfo> nodes = res.getNodes();

			// TODO: can be optimized
			for (NodeInfo nodeInfo: nodes) {
				if (nodeInfo.getHttp() != null && nodeInfo.getHttp().address() != null) {
					final InetSocketTransportAddress is = (InetSocketTransportAddress) nodeInfo.getHttp().address()
							.publishAddress();
					clusterInfo.httpPort = is.getPort();
					clusterInfo.httpHost = is.getHost();
					clusterInfo.httpAdresses.add(is);
				}

				final InetSocketTransportAddress is = (InetSocketTransportAddress) nodeInfo.getTransport().getAddress()
						.publishAddress();
				clusterInfo.nodePort = is.getPort();
				clusterInfo.nodeHost = is.getHost();
			}
		} catch (final ElasticsearchTimeoutException e) {
			throw new IOException(
					"timeout, cluster does not respond to health request, cowardly refusing to continue with operations");
		}
		return clusterInfo;
	}

	// @formatter:off
	private Settings.Builder getDefaultSettingsBuilder(final int nodenum, final boolean masterNode,
			final boolean dataNode, final boolean tribeNode) {

		return Settings.builder()
		        .put("node.name", "searchguard_testnode_" + nodenum)
		        .put("node.data", dataNode)
				.put("node.master", masterNode)
				.put("cluster.name", clustername)
				.put("path.data", "data/data")
				.put("path.logs", "data/logs")
				.put("path.conf", "data/config")
				.put("node.max_local_storage_nodes", 3)
				.put("http.enabled", true)
				.put("cluster.routing.allocation.disk.watermark.high", "1mb")
				.put("cluster.routing.allocation.disk.watermark.low", "1mb")
				.put("http.cors.enabled", true)
				.put("node.local", false)
				.put("path.home", ".");
	}
	// @formatter:on
}
