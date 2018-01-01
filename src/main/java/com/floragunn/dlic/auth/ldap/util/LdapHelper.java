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

package com.floragunn.dlic.auth.ldap.util;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.SpecialPermission;
import org.ldaptive.Connection;
import org.ldaptive.DerefAliases;
import org.ldaptive.LdapEntry;
import org.ldaptive.LdapException;
import org.ldaptive.Response;
import org.ldaptive.ReturnAttributes;
import org.ldaptive.SearchOperation;
import org.ldaptive.SearchRequest;
import org.ldaptive.SearchResult;
import org.ldaptive.SearchScope;
import org.ldaptive.referral.SearchReferralHandler;

public class LdapHelper {

    public static List<LdapEntry> search(final Connection conn, final String baseDn, final String filter, final SearchScope searchScope) throws LdapException {

        final SecurityManager sm = System.getSecurityManager();

        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
        }
        
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<List<LdapEntry>>() {
                @Override
                public List<LdapEntry> run() throws Exception {
                    final List<LdapEntry> entries = new ArrayList<>();
                    final SearchRequest request = new SearchRequest(baseDn, filter);
                    request.setReferralHandler(new SearchReferralHandler());
                    request.setSearchScope(searchScope);
                    request.setDerefAliases(DerefAliases.ALWAYS);
                    request.setReturnAttributes(ReturnAttributes.ALL.value());
                    final SearchOperation search = new SearchOperation(conn);
                    // referrals will be followed to build the response
                    final Response<SearchResult> r = search.execute(request);
                    final org.ldaptive.SearchResult result = r.getResult();
                    entries.addAll(result.getEntries());
                    return entries;
                }
            });
        } catch (PrivilegedActionException e) {
            throw new LdapException(e);
        }
    }

    public static LdapEntry lookup(final Connection conn, final String dn) throws LdapException {

        final List<LdapEntry> entries = search(conn, dn, "(objectClass=*)", SearchScope.OBJECT);

        if (entries.size() == 1) {
            return entries.get(0);
        } else {
            return null;
        }
    }

}
