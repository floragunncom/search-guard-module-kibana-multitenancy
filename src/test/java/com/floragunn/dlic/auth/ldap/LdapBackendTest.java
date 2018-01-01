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

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.TreeSet;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.settings.Settings;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.ldaptive.Connection;
import org.ldaptive.LdapEntry;

import com.floragunn.dlic.auth.ldap.backend.LDAPAuthenticationBackend;
import com.floragunn.dlic.auth.ldap.backend.LDAPAuthorizationBackend;
import com.floragunn.dlic.auth.ldap.srv.EmbeddedLDAPServer;
import com.floragunn.dlic.auth.ldap.util.ConfigConstants;
import com.floragunn.dlic.auth.ldap.util.LdapHelper;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.user.AuthCredentials;
import com.floragunn.searchguard.user.User;

public class LdapBackendTest {
    
    static {
        System.setProperty("sg.display_lic_none", "true");
    }

    protected EmbeddedLDAPServer ldapServer = null;

    public final void startLDAPServer() throws Exception {

        // log.debug("non localhost address: {}", getNonLocalhostAddress());

        ldapServer = new EmbeddedLDAPServer();

        // keytab.delete();
        // ldapServer.createKeytab("krbtgt/EXAMPLE.COM@EXAMPLE.COM", "secret",
        // keytab);
        // ldapServer.createKeytab("HTTP/" + getNonLocalhostAddress() +
        // "@EXAMPLE.COM", "httppwd", keytab);
        // ldapServer.createKeytab("HTTP/localhost@EXAMPLE.COM", "httppwd",
        // keytab);
        // ldapServer.createKeytab("ldap/localhost@EXAMPLE.COM", "randall",
        // keytab);

        ldapServer.start();
        ldapServer.applyLdif("base.ldif");
    }

    @Test
    public void testLdapAuthentication() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "127.0.0.1:4", "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})").build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
    }
    
    @Test(expected=ElasticsearchSecurityException.class)
    public void testLdapAuthenticationFakeLogin() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_FAKE_LOGIN_ENABLED, true)
                .build();

        new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("unknown", "unknown"
                .getBytes(StandardCharsets.UTF_8)));
    }
    
    @Test(expected=ElasticsearchSecurityException.class)
    public void testLdapInjection() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})").build();

        String injectString = "*jack*";

        
        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials(injectString, "secret"
                .getBytes(StandardCharsets.UTF_8)));
    }
    
    @Test
    public void testLdapAuthenticationBindDn() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS,  "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_BIND_DN, "cn=Captain Spock,ou=people,o=TEST")
                .put(ConfigConstants.LDAP_PASSWORD, "spocksecret")
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
    }
    
    @Test(expected=ElasticsearchSecurityException.class)
    public void testLdapAuthenticationWrongBindDn() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS,  "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_BIND_DN, "cn=Captain Spock,ou=people,o=TEST")
                .put(ConfigConstants.LDAP_PASSWORD, "wrong")
                .build();

        new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
    }
    
    @Test(expected=ElasticsearchSecurityException.class)
    public void testLdapAuthenticationBindFail() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS,  "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})").build();

        new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "wrong".getBytes(StandardCharsets.UTF_8)));
    }
    
    @Test(expected=ElasticsearchSecurityException.class)
    public void testLdapAuthenticationNoUser() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS,  "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})").build();

        new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("UNKNOWN", "UNKNOWN".getBytes(StandardCharsets.UTF_8)));
    }

    @Test(expected = ElasticsearchSecurityException.class)
    public void testLdapAuthenticationFail() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "127.0.0.1:4", "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})").build();

        new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "xxxxx".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testLdapAuthenticationSSL() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapsPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAPS_ENABLE_SSL, true)
                .put("searchguard.ssl.transport.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("ldap/truststore.jks"))
                .put("verify_hostnames", false)
                .put("path.home",".")
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
    }
    
    @Test
    public void testLdapAuthenticationSSLPEMFile() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapsPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAPS_ENABLE_SSL, true)
                .put(ConfigConstants.LDAPS_PEMTRUSTEDCAS_FILEPATH, FileHelper.getAbsoluteFilePathFromClassPath("ldap/root-ca.pem").toFile().getName())
                .put("verify_hostnames", false)
                .put("path.home",".")
                .put("path.conf",FileHelper.getAbsoluteFilePathFromClassPath("ldap/root-ca.pem").getParent())
                .build();
        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, Paths.get("src/test/resources/ldap")).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
    }
    
    @Test
    public void testLdapAuthenticationSSLPEMText() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder().loadFromPath(Paths.get(FileHelper.getAbsoluteFilePathFromClassPath("ldap/test1.yml").toFile().getAbsolutePath())).build();
        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
    }
    
    @Test
    public void testLdapAuthenticationSSLSSLv3() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapsPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAPS_ENABLE_SSL, true)
                .put("searchguard.ssl.transport.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("ldap/truststore.jks"))
                .put("verify_hostnames", false)
                .putList("enabled_ssl_protocols", "SSLv3")
                .put("path.home",".")
                .build();

        try {
            new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            Assert.assertEquals(e.getCause().getClass(), org.ldaptive.LdapException.class);
            Assert.assertTrue(e.getCause().getMessage().contains("Unable to connec"));
        }
        
    }
    
    @Test
    public void testLdapAuthenticationSSLUnknowCipher() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapsPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAPS_ENABLE_SSL, true)
                .put("searchguard.ssl.transport.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("ldap/truststore.jks"))
                .put("verify_hostnames", false)
                .putList("enabled_ssl_ciphers", "AAA")
                .put("path.home",".")
                .build();

        try {
            new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            Assert.assertEquals(e.getCause().getClass(), org.ldaptive.LdapException.class);
            Assert.assertTrue(e.getCause().getMessage().contains("Unable to connec"));
        }
        
    }
    
    @Test
    public void testLdapAuthenticationSpecialCipherProtocol() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapsPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAPS_ENABLE_SSL, true)
                .put("searchguard.ssl.transport.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("ldap/truststore.jks"))
                .put("verify_hostnames", false)
                .putList("enabled_ssl_protocols", "TLSv1")
                .putList("enabled_ssl_ciphers", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA")
                .put("path.home",".")
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
        
    }
    
    @Test
    public void testLdapAuthenticationSSLNoKeystore() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapsPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAPS_ENABLE_SSL, true)
                .put("searchguard.ssl.transport.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("ldap/truststore.jks"))
                .put("verify_hostnames", false)
                .put("path.home",".")
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
    }

    @Test
    public void testLdapAuthenticationSSLFailPlain() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAPS_ENABLE_SSL, true).build();

        try {
            new LDAPAuthenticationBackend(settings, null)
                    .authenticate(new AuthCredentials("jacksonm", "secret".getBytes(StandardCharsets.UTF_8)));
        } catch (final Exception e) {
            Assert.assertEquals(org.ldaptive.LdapException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testLdapExists() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "127.0.0.1:4", "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})").build();

        final LDAPAuthenticationBackend lbe = new LDAPAuthenticationBackend(settings, null);
        Assert.assertTrue(lbe.exists(new User("jacksonm")));
        Assert.assertFalse(lbe.exists(new User("doesnotexist")));
    }

    @Test
    public void testLdapAuthorization() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "127.0.0.1:4", "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                // .put("searchguard.authentication.authorization.ldap.userrolename",
                // "(uniqueMember={0})")
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
        Assert.assertEquals(2, user.getRoles().size());
        Assert.assertEquals("ceo", new ArrayList(new TreeSet(user.getRoles())).get(0));
        Assert.assertEquals(user.getName(), user.getUserEntry().getDn());
    }

    @Test
    public void testLdapAuthenticationReferral() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})").build();

        final Connection con = LDAPAuthorizationBackend.getConnection(settings, null);
        try {
            final LdapEntry ref1 = LdapHelper.lookup(con, "cn=Ref1,ou=people,o=TEST");
            Assert.assertEquals("cn=refsolved,ou=people,o=TEST", ref1.getDn());
        } finally {
            con.close();
        }

    }
    
    
    @Test
    public void testLdapEscape() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .put(ConfigConstants.LDAP_AUTHZ_USERROLENAME, "description") // no memberOf OID
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, true)
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("ssign", "ssignsecret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Special\\, Sign,ou=people,o=TEST", user.getName());
        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);
        Assert.assertEquals("cn=Special\\, Sign,ou=people,o=TEST", user.getName());
        Assert.assertEquals(4, user.getRoles().size());
        Assert.assertTrue(user.getRoles().toString().contains("ceo"));
    }
    
    @Test
    public void testLdapAuthorizationRoleSearchUsername() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(cn={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember=cn={1},ou=people,o=TEST)")
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("Michael Jackson", "secret"
                .getBytes(StandardCharsets.UTF_8)));

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("Michael Jackson", user.getOriginalUsername());
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getUserEntry().getDn());
        Assert.assertEquals(2, user.getRoles().size());
        Assert.assertEquals("ceo", new ArrayList(new TreeSet(user.getRoles())).get(0));
        Assert.assertEquals(user.getName(), user.getUserEntry().getDn());
    }
    
    @Test
    public void testLdapAuthorizationOnly() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .build();

        final User user = new User("jacksonm");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("jacksonm", user.getName());
        Assert.assertEquals(2, user.getRoles().size());
        Assert.assertEquals("ceo", new ArrayList(new TreeSet(user.getRoles())).get(0));
    }
    
    @Test
    public void testLdapAuthorizationNested() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, true)
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .build();

        final User user = new User("spock");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("spock", user.getName());
        Assert.assertEquals(4, user.getRoles().size());
        Assert.assertEquals("nested1", new ArrayList(new TreeSet(user.getRoles())).get(1));
    }
    
    @Test
    public void testLdapAuthorizationNestedFilter() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, true)
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .putList(ConfigConstants.LDAP_AUTHZ_NESTEDROLEFILTER, "cn=nested2,ou=groups,o=TEST")
                .build();

        final User user = new User("spock");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("spock", user.getName());
        Assert.assertEquals(2, user.getRoles().size());
        Assert.assertEquals("ceo", new ArrayList(new TreeSet(user.getRoles())).get(0));
        Assert.assertEquals("nested2", new ArrayList(new TreeSet(user.getRoles())).get(1));
    }
    
    @Test
    public void testLdapAuthorizationDnNested() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "dn")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, true)
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .build();

        final User user = new User("spock");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("spock", user.getName());
        Assert.assertEquals(4, user.getRoles().size());
        Assert.assertEquals("cn=nested1,ou=groups,o=TEST", new ArrayList(new TreeSet(user.getRoles())).get(1));
    }
    
    @Test
    public void testLdapAuthorizationDn() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "dn")
                .put(ConfigConstants.LDAP_AUTHC_USERNAME_ATTRIBUTE, "UID")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, false)
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .build();

        final User user = new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret".getBytes()));

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("jacksonm", user.getName());
        Assert.assertEquals(2, user.getRoles().size());
        Assert.assertEquals("cn=ceo,ou=groups,o=TEST", new ArrayList(new TreeSet(user.getRoles())).get(0));
    }

    @Test
    public void testLdapAuthenticationUserNameAttribute() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder().putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST").put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERNAME_ATTRIBUTE, "uid").build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("jacksonm", user.getName());
    }

    @Test
    public void testLdapAuthenticationStartTLS() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAPS_ENABLE_START_TLS, true)
                .put("searchguard.ssl.transport.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("ldap/truststore.jks"))
                .put("verify_hostnames", false).put("path.home", ".")
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
    }
    
    @Test
    public void testLdapAuthorizationSkipUsers() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "127.0.0.1:4", "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .putList(ConfigConstants.LDAP_AUTHZ_SKIP_USERS, "cn=Michael Jackson,ou*people,o=TEST")
                .build();

        final LdapUser user = (LdapUser) new LDAPAuthenticationBackend(settings, null).authenticate(new AuthCredentials("jacksonm", "secret"
                .getBytes(StandardCharsets.UTF_8)));

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("cn=Michael Jackson,ou=people,o=TEST", user.getName());
        Assert.assertEquals(0, user.getRoles().size());
        Assert.assertEquals(user.getName(), user.getUserEntry().getDn());
    }
    
    @Test
    public void testLdapAuthorizationNestedAttr() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, true)
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .put(ConfigConstants.LDAP_AUTHZ_USERROLENAME, "description") // no memberOf OID
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH_ENABLED, true)
                .build();

        final User user = new User("spock");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("spock", user.getName());
        Assert.assertEquals(8, user.getRoles().size());
        Assert.assertEquals("nested3", new ArrayList(new TreeSet(user.getRoles())).get(4));
        Assert.assertEquals("rolemo4", new ArrayList(new TreeSet(user.getRoles())).get(7));
    }
    
    @Test
    public void testLdapAuthorizationNestedAttrFilter() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, true)
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .put(ConfigConstants.LDAP_AUTHZ_USERROLENAME, "description") // no memberOf OID
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH_ENABLED, true)
                .putList(ConfigConstants.LDAP_AUTHZ_NESTEDROLEFILTER, "cn=rolemo4*")
                .build();

        final User user = new User("spock");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("spock", user.getName());
        Assert.assertEquals(6, user.getRoles().size());
        Assert.assertEquals("role2", new ArrayList(new TreeSet(user.getRoles())).get(4));
        Assert.assertEquals("nested1", new ArrayList(new TreeSet(user.getRoles())).get(2));
        
    }
    
    @Test
    public void testLdapAuthorizationNestedAttrFilterAll() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, true)
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .put(ConfigConstants.LDAP_AUTHZ_USERROLENAME, "description") // no memberOf OID
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH_ENABLED, true)
                .putList(ConfigConstants.LDAP_AUTHZ_NESTEDROLEFILTER, "*")
                .build();

        final User user = new User("spock");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("spock", user.getName());
        Assert.assertEquals(4, user.getRoles().size());
        
    }
    
    @Test
    public void testLdapAuthorizationNestedAttrFilterAllEqualsNestedFalse() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "ou=groups,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, false) //-> same like putList(ConfigConstants.LDAP_AUTHZ_NESTEDROLEFILTER, "*")
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(uniqueMember={0})")
                .put(ConfigConstants.LDAP_AUTHZ_USERROLENAME, "description") // no memberOf OID
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH_ENABLED, true)
                .build();

        final User user = new User("spock");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("spock", user.getName());
        Assert.assertEquals(4, user.getRoles().size());
        
    }
    
    @Test
    public void testLdapAuthorizationNestedAttrNoRoleSearch() throws Exception {

        startLDAPServer();

        final Settings settings = Settings.builder()
                .putList(ConfigConstants.LDAP_HOSTS, "localhost:" + EmbeddedLDAPServer.ldapPort)
                .put(ConfigConstants.LDAP_AUTHC_USERSEARCH, "(uid={0})")
                .put(ConfigConstants.LDAP_AUTHC_USERBASE, "ou=people,o=TEST")
                .put(ConfigConstants.LDAP_AUTHZ_ROLEBASE, "unused")
                .put(ConfigConstants.LDAP_AUTHZ_ROLENAME, "cn")
                .put(ConfigConstants.LDAP_AUTHZ_RESOLVE_NESTED_ROLES, true)
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH, "(((unused")
                .put(ConfigConstants.LDAP_AUTHZ_USERROLENAME, "description") // no memberOf OID
                .put(ConfigConstants.LDAP_AUTHZ_ROLESEARCH_ENABLED, false)
                .build();

        final User user = new User("spock");

        new LDAPAuthorizationBackend(settings, null).fillRoles(user, null);

        Assert.assertNotNull(user);
        Assert.assertEquals("spock", user.getName());
        Assert.assertEquals(3, user.getRoles().size());
        Assert.assertEquals("nested3", new ArrayList(new TreeSet(user.getRoles())).get(1));
        Assert.assertEquals("rolemo4", new ArrayList(new TreeSet(user.getRoles())).get(2));
    }
    
    @After
    public void tearDown() throws Exception {

        if (ldapServer != null) {
            ldapServer.stop();
        }

    }
}
