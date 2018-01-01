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

package com.floragunn.dlic.auth.http.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.user.AuthCredentials;
import com.google.common.io.BaseEncoding;

public class HTTPJwtAuthenticatorTest {

    
    @Test
    public void testNoKey() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder().build();
        
        String jwsToken = Jwts.builder().setSubject("Leonard McCoy").signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth =new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer "+jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }

    @Test
    public void testEmptyKey() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder().put("signing_key", "").build();
        
        String jwsToken = Jwts.builder().setSubject("Leonard McCoy").signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer "+jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }
    
    @Test
    public void testBadKey() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder().put("signing_key", BaseEncoding.base64().encode(new byte[]{1,3,3,4,3,6,7,8,3,10})).build();
        
        String jwsToken = Jwts.builder().setSubject("Leonard McCoy").signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer "+jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }

    @Test
    public void testTokenMissing() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder().put("signing_key", BaseEncoding.base64().encode(secretKey)).build();
                
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
                
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }
    
    @Test
    public void testInvalid() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder().put("signing_key", BaseEncoding.base64().encode(secretKey)).build();
        
        String jwsToken = "123invalidtoken..";
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer "+jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }
    
    @Test
    public void testBearer() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder().put("signing_key", BaseEncoding.base64().encode(secretKey)).build();
        
        String jwsToken = Jwts.builder().setSubject("Leonard McCoy").setAudience("myaud").signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer "+jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
        Assert.assertEquals(2, creds.getAttributes().size());
    }

    @Test
    public void testBearerWrongPosition() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder().put("signing_key", BaseEncoding.base64().encode(secretKey)).build();
        
        String jwsToken = Jwts.builder().setSubject("Leonard McCoy").signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken + "Bearer " + " 123");
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }
    
    @Test
    public void testNonBearer() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder().put("signing_key", BaseEncoding.base64().encode(secretKey)).build();
        
        String jwsToken = Jwts.builder().setSubject("Leonard McCoy").signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
    }
    
    @Test
    public void testRoles() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("roles_key", "roles")
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Leonard McCoy")
                .claim("roles", "role1,role2")
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(2, creds.getBackendRoles().size());
    }

    @Test
    public void testNullClaim() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("roles_key", "roles")
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Leonard McCoy")
                .claim("roles", null)
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
    } 

    @Test
    public void testNonStringClaim() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("roles_key", "roles")
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Leonard McCoy")
                .claim("roles", 123L)
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(1, creds.getBackendRoles().size());
        Assert.assertTrue( creds.getBackendRoles().contains("123"));
    }
    
    @Test
    public void testRolesMissing() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("roles_key", "roles")
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Leonard McCoy")                
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
    }    
    
    @Test
    public void testWrongSubjectKey() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("subject_key", "missing")
                .build();
        
        String jwsToken = Jwts.builder()
                .claim("roles", "role1,role2")
                .claim("asub", "Dr. Who")
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }
    
    @Test
    public void testAlternativeSubject() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("subject_key", "asub")
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Leonard McCoy")
                .claim("roles", "role1,role2")
                .claim("asub", "Dr. Who")
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Dr. Who", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
    }

    @Test
    public void testNonStringAlternativeSubject() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("subject_key", "asub")
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Leonard McCoy")
                .claim("roles", "role1,role2")
                .claim("asub", false)
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("false", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
    }
    
    @Test
    public void testUrlParam() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("jwt_url_parameter", "abc")
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Leonard McCoy")
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        FakeRestRequest req = new FakeRestRequest(headers, new HashMap<String, String>());
        req.params().put("abc", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(req, null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
    }
    
    @Test
    public void testExp() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Expired")
                .setExpiration(new Date(100))
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }
    
    @Test
    public void testNbf() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .build();
        
        String jwsToken = Jwts.builder()
                .setSubject("Expired")
                .setNotBefore(new Date(System.currentTimeMillis()+(1000*36000)))
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNull(creds);
    }
    
    @Test
    public void testRS256() throws Exception {
        
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        KeyPair pair = keyGen.generateKeyPair();
        PrivateKey priv = pair.getPrivate();
        PublicKey pub = pair.getPublic();
    
        String jwsToken = Jwts.builder().setSubject("Leonard McCoy").signWith(SignatureAlgorithm.RS256, priv).compact();
        Settings settings = Settings.builder().put("signing_key", "-----BEGIN PUBLIC KEY-----\n"+BaseEncoding.base64().encode(pub.getEncoded())+"-----END PUBLIC KEY-----").build();

        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer "+jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
    }
    
    @Test
    public void testES512() throws Exception {
        
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        KeyPair pair = keyGen.generateKeyPair();
        PrivateKey priv = pair.getPrivate();
        PublicKey pub = pair.getPublic();
    
        String jwsToken = Jwts.builder().setSubject("Leonard McCoy").signWith(SignatureAlgorithm.ES512, priv).compact();
        Settings settings = Settings.builder().put("signing_key", BaseEncoding.base64().encode(pub.getEncoded())).build();

        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer "+jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("Leonard McCoy", creds.getUsername());
        Assert.assertEquals(0, creds.getBackendRoles().size());
    }
    
    @Test
    public void rolesArray() throws Exception {
        
        byte[] secretKey = new byte[]{1,2,3,4,5,6,7,8,9,10};
        
        Settings settings = Settings.builder()
                .put("signing_key", BaseEncoding.base64().encode(secretKey))
                .put("roles_key", "roles")
                .build();
        
        String jwsToken = Jwts.builder()
                .setPayload("{"+
                    "\"sub\": \"John Doe\","+
                    "\"roles\": [\"a\",\"b\",\"3rd\"]"+
                  "}")
                .signWith(SignatureAlgorithm.HS512, secretKey).compact();
        
        HTTPJwtAuthenticator jwtAuth = new HTTPJwtAuthenticator(settings, null);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer "+jwsToken);
        
        AuthCredentials creds = jwtAuth.extractCredentials(new FakeRestRequest(headers, new HashMap<String, String>()), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("John Doe", creds.getUsername());
        Assert.assertEquals(3, creds.getBackendRoles().size());
        Assert.assertTrue(creds.getBackendRoles().contains("a"));
        Assert.assertTrue(creds.getBackendRoles().contains("b"));
        Assert.assertTrue(creds.getBackendRoles().contains("3rd"));
    }
       
}
