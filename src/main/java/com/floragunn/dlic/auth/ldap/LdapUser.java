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

package com.floragunn.dlic.auth.ldap;

import java.util.Map;

import org.ldaptive.LdapAttribute;
import org.ldaptive.LdapEntry;

import com.floragunn.searchguard.user.AuthCredentials;
import com.floragunn.searchguard.user.User;

public class LdapUser extends User {

    private static final long serialVersionUID = 1L;
    private final LdapEntry userEntry;
    private final String originalUsername;

    public LdapUser(final String name, String originalUsername, final LdapEntry userEntry, final AuthCredentials credentials) {
        super(name, null, credentials);
        this.originalUsername = originalUsername;
        this.userEntry = userEntry;
        Map<String, String> attributes = getCustomAttributesMap();
        attributes.put("ldap.original.username", originalUsername);
        attributes.put("ldap.dn", userEntry.getDn());
        
        for(LdapAttribute attr: userEntry.getAttributes()) {
            attributes.put("attr.ldap."+attr.getName(), attr.getStringValue());
        }
    }

    public LdapEntry getUserEntry() {
        return userEntry;
    }
    
    public String getDn() {
        return userEntry.getDn();
    }

    public String getOriginalUsername() {
        return originalUsername;
    }
}
