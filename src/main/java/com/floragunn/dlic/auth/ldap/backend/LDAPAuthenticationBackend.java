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

package com.floragunn.dlic.auth.ldap.backend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.SpecialPermission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;
import org.ldaptive.BindRequest;
import org.ldaptive.Connection;
import org.ldaptive.Credential;
import org.ldaptive.LdapEntry;
import org.ldaptive.LdapException;
import org.ldaptive.Response;
import org.ldaptive.SearchScope;

import com.floragunn.dlic.auth.ldap.LdapUser;
import com.floragunn.dlic.auth.ldap.util.ConfigConstants;
import com.floragunn.dlic.auth.ldap.util.LdapHelper;
import com.floragunn.dlic.auth.ldap.util.Utils;
import com.floragunn.searchguard.auth.AuthenticationBackend;
import com.floragunn.searchguard.user.AuthCredentials;
import com.floragunn.searchguard.user.User;

public class LDAPAuthenticationBackend implements AuthenticationBackend {

    static final String ZERO_PLACEHOLDER = "{0}";
    static final String DEFAULT_USERBASE = "";
    static final String DEFAULT_USERSEARCH_PATTERN = "(sAMAccountName={0})";

    static {
        Utils.init();
    }

    protected static final Logger log = LogManager.getLogger(LDAPAuthenticationBackend.class);

    private final Settings settings;
    private final Path configPath;
    
    public LDAPAuthenticationBackend(final Settings settings, final Path configPath) {
        this.settings = settings;
        this.configPath = configPath;
    }
    

    @Override
    public User authenticate(final AuthCredentials credentials) throws ElasticsearchSecurityException {

        Connection ldapConnection = null;
        final String user = Utils.escapeStringRfc2254(credentials.getUsername());
        byte[] password = credentials.getPassword();

        try {

            ldapConnection = LDAPAuthorizationBackend.getConnection(settings, configPath);

            LdapEntry entry = exists(user, ldapConnection, settings);

            //fake a user that no exists
            //makes guessing if a user exists or not harder when looking on the authentication delay time
            if(entry == null && settings.getAsBoolean(ConfigConstants.LDAP_FAKE_LOGIN_ENABLED, false)) {                
                String fakeLognDn = settings.get(ConfigConstants.LDAP_FAKE_LOGIN_DN, "CN=faketomakebindfail,DC="+UUID.randomUUID().toString());
                entry = new LdapEntry(fakeLognDn);
                password = settings.get(ConfigConstants.LDAP_FAKE_LOGIN_PASSWORD, "fakeLoginPwd123").getBytes(StandardCharsets.UTF_8);
            } else if(entry == null) {
                throw new ElasticsearchSecurityException("No user " + user + " found");
            }
            
            final String dn = entry.getDn();

            if(log.isTraceEnabled()) {
                log.trace("Try to authenticate dn {}", dn);
            }

            final BindRequest br = new BindRequest(dn, new Credential(password));
            final SecurityManager sm = System.getSecurityManager();

            if (sm != null) {
                sm.checkPermission(new SpecialPermission());
            }
            
            final Connection _con = ldapConnection;
            
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Response<Void>>() {
                    @Override
                    public Response<Void> run() throws LdapException {
                        return _con.reopen(br);
                    }
                });
            } catch (PrivilegedActionException e) {
                throw e.getException();
            }

            final String usernameAttribute = settings.get(ConfigConstants.LDAP_AUTHC_USERNAME_ATTRIBUTE, null);
            String username = dn;

            if (usernameAttribute != null && entry.getAttribute(usernameAttribute) != null) {
                username = entry.getAttribute(usernameAttribute).getStringValue();
            }

            if(log.isDebugEnabled()) {
                log.debug("Authenticated username {}", username);
            }

            return new LdapUser(username, user, entry, credentials);

        } catch (final Exception e) {
            if(log.isDebugEnabled()) {
                log.debug("Unable to authenticate user due to ", e);
            }
            throw new ElasticsearchSecurityException(e.toString(), e);
        } finally {
            Arrays.fill(password, (byte) '\0');
            password = null;
            Utils.unbindAndCloseSilently(ldapConnection);
        }

    }

    @Override
    public String getType() {
        return "ldap";
    }

    @Override
    public boolean exists(final User user) {
        Connection ldapConnection = null;
        String userName = user.getName();
        
        if(user instanceof LdapUser) {
            userName = ((LdapUser) user).getUserEntry().getDn(); 
        }

        try {
            ldapConnection = LDAPAuthorizationBackend.getConnection(settings, configPath);
            return exists(userName, ldapConnection, settings) != null; 
        } catch (final Exception e) {
            log.warn("User {} does not exist due to "+e, userName);
            if(log.isDebugEnabled()) {
                log.debug("User does not exist due to ", e);
            }
            return false;
        } finally {
            Utils.unbindAndCloseSilently(ldapConnection);
        }
    }
    
    static LdapEntry exists(final String user, Connection ldapConnection, Settings settings) throws Exception {
        final String username = Utils.escapeStringRfc2254(user);

        final List<LdapEntry> result = LdapHelper.search(ldapConnection,
                settings.get(ConfigConstants.LDAP_AUTHC_USERBASE, DEFAULT_USERBASE),
                settings.get(ConfigConstants.LDAP_AUTHC_USERSEARCH, DEFAULT_USERSEARCH_PATTERN).replace(ZERO_PLACEHOLDER, username),
                SearchScope.SUBTREE);

        if (result == null || result.isEmpty()) {
            log.debug("No user " + username + " found");
            return null;
        }

        if (result.size() > 1) {
            log.debug("More than one user for '" + username + "' found");
            return null;
        }

        return result.get(0);
    }

}
