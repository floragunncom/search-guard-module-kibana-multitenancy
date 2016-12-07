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

package com.floragunn.searchguard.test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.xml.bind.DatatypeConverter;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;

import com.floragunn.searchguard.test.helper.cluster.ClusterHelper;
import com.floragunn.searchguard.test.helper.cluster.ClusterInfo;
import com.floragunn.searchguard.test.helper.rest.RestHelper;
import com.floragunn.searchguard.test.helper.rules.SGTestWatcher;

import io.netty.handler.ssl.OpenSsl;

public abstract class AbstractSGUnitTest {

	static {

		System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + " "
				+ System.getProperty("os.version"));
		System.out.println(
				"Java Version: " + System.getProperty("java.version") + " " + System.getProperty("java.vendor"));
		System.out.println("JVM Impl.: " + System.getProperty("java.vm.version") + " "
				+ System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.name"));
		System.out.println("Open SSL available: " + OpenSsl.isAvailable());
		System.out.println("Open SSL version: " + OpenSsl.versionString());
	}
	
	protected ClusterHelper ch = new ClusterHelper();
	protected RestHelper rh;
	protected ClusterInfo ci;
	
	protected final ESLogger log = Loggers.getLogger(this.getClass());
	
	@Rule
	public TestName name = new TestName();

	@Rule
	public final TestWatcher testWatcher = new SGTestWatcher();

	@After
	public void tearDown() throws Exception {
		ch.stopCluster();
	}

	public static String encodeBasicHeader(final String username, final String password) {
		return new String(DatatypeConverter.printBase64Binary(
				(username + ":" + Objects.requireNonNull(password)).getBytes(StandardCharsets.UTF_8)));
	}
}
