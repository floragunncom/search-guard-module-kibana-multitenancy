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

package com.floragunn.searchguard.auditlog.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.transport.TransportAddress;

import com.floragunn.searchguard.auditlog.AuditLog.Origin;
import com.floragunn.searchguard.auditlog.impl.AuditMessage.Category;

public class MockAuditMessageFactory {
	
	public static AuditMessage validAuditMessage() {
  
	    ClusterService cs = mock(ClusterService.class);
	    DiscoveryNode dn = mock(DiscoveryNode.class); 

        when(dn.getHostAddress()).thenReturn("hostaddress");
        when(dn.getId()).thenReturn("hostaddress");
        when(dn.getHostName()).thenReturn("hostaddress");
        when(cs.localNode()).thenReturn(dn);
	   	    
	    //RestRequest holder = createValidRestRequest();
		Category category = Category.FAILED_LOGIN;
		TransportAddress ta = new TransportAddress(new InetSocketAddress("8.8.8.8",80));
		
		//ThreadContext tc = new ThreadContext(Settings.EMPTY);
		//User user = new User("John Doe");
		//tc.putTransient(ConfigConstants.SG_USER, user);
		//tc.putTransient(ConfigConstants.SG_REMOTE_ADDRESS, "8.8.8.8");
		//tc.putTransient(ConfigConstants.SG_SSL_TRANSPORT_PRINCIPAL, "CN=kirk,OU=client,O=client,L=test,C=DE");
		
		AuditMessage msg = new AuditMessage(category, cs, Origin.TRANSPORT, Origin.TRANSPORT);
		msg.addEffectiveUser("John Doe");
		msg.addRemoteAddress(ta);
		msg.addRequestType("IndexRequest");
		return msg;
	}

	//private static RestRequest createValidRestRequest() {
	//	return new MockRestRequest();
	//}
}
