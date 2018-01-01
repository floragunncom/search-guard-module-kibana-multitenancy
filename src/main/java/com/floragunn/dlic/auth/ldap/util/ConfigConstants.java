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

public final class ConfigConstants {

    public static final String LDAP_AUTHC_USERBASE = "userbase";
    public static final String LDAP_AUTHC_USERNAME_ATTRIBUTE = "username_attribute";
    public static final String LDAP_AUTHC_USERSEARCH = "usersearch";
    
    public static final String LDAP_AUTHZ_RESOLVE_NESTED_ROLES = "resolve_nested_roles";
    public static final String LDAP_AUTHZ_ROLEBASE = "rolebase";
    public static final String LDAP_AUTHZ_ROLENAME = "rolename";
    public static final String LDAP_AUTHZ_ROLESEARCH = "rolesearch";
    public static final String LDAP_AUTHZ_USERROLEATTRIBUTE = "userroleattribute";
    public static final String LDAP_AUTHZ_USERROLENAME = "userrolename";
    public static final String LDAP_AUTHZ_SKIP_USERS = "skip_users";
    public static final String LDAP_AUTHZ_ROLESEARCH_ENABLED = "rolesearch_enabled";
    public static final String LDAP_AUTHZ_NESTEDROLEFILTER = "nested_role_filter";
    
    public static final String LDAP_HOSTS = "hosts";
    public static final String LDAP_BIND_DN = "bind_dn";
    public static final String LDAP_PASSWORD = "password";
    public static final String LDAP_FAKE_LOGIN_ENABLED = "fakelogin_enabled";
    public static final String LDAP_FAKE_LOGIN_DN = "fakelogin_dn";
    public static final String LDAP_FAKE_LOGIN_PASSWORD = "fakelogin_password";
    
    //ssl
    public static final String LDAPS_VERIFY_HOSTNAMES = "verify_hostnames";
    public static final boolean LDAPS_VERIFY_HOSTNAMES_DEFAULT = true;
    public static final String LDAPS_ENABLE_SSL = "enable_ssl";
    public static final String LDAPS_ENABLE_START_TLS = "enable_start_tls";
    public static final String LDAPS_ENABLE_SSL_CLIENT_AUTH = "enable_ssl_client_auth";
    public static final boolean LDAPS_ENABLE_SSL_CLIENT_AUTH_DEFAULT = false;
    
    public static final String LDAPS_JKS_CERT_ALIAS = "cert_alias";
    public static final String LDAPS_JKS_TRUST_ALIAS = "ca_alias";
    
    public static final String LDAPS_PEMKEY_FILEPATH = "pemkey_filepath";
    public static final String LDAPS_PEMKEY_CONTENT = "pemkey_content";
    public static final String LDAPS_PEMKEY_PASSWORD = "pemkey_password";
    public static final String LDAPS_PEMCERT_FILEPATH = "pemcert_filepath";
    public static final String LDAPS_PEMCERT_CONTENT = "pemcert_content";
    public static final String LDAPS_PEMTRUSTEDCAS_FILEPATH = "pemtrustedcas_filepath";
    public static final String LDAPS_PEMTRUSTEDCAS_CONTENT = "pemtrustedcas_content";

    
    
    public static final String LDAPS_ENABLED_SSL_CIPHERS = "enabled_ssl_ciphers";
    public static final String LDAPS_ENABLED_SSL_PROTOCOLS = "enabled_ssl_protocols";

    private ConfigConstants() {

    }

}
