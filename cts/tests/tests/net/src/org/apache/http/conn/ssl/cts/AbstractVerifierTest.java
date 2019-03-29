/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.http.conn.ssl.cts;

import javax.security.auth.x500.X500Principal;
import junit.framework.TestCase;

import org.apache.http.conn.ssl.AbstractVerifier;

import java.lang.Override;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

/**
 * See also {@link libcore.javax.security.auth.x500.X500PrincipalTest} as it shows some cases
 * we are not checking as they are not allowed by the X500 principal in the first place.
 */
public final class AbstractVerifierTest extends TestCase {

    public void testGetCns() {
        assertCns("");
        assertCns("ou=xxx");
        assertCns("ou=xxx,cn=xxx", "xxx");
        assertCns("ou=xxx+cn=yyy,cn=zzz+cn=abc", "yyy", "zzz", "abc");
        assertCns("cn=a,cn=b", "a", "b");
        assertCns("cn=a   c,cn=b", "a   c", "b");
        assertCns("cn=a   ,cn=b", "a", "b");
        assertCns("cn=Cc,cn=Bb,cn=Aa", "Cc", "Bb", "Aa");
        assertCns("cn=imap.gmail.com", "imap.gmail.com");
        assertCns("l=\"abcn=a,b\", cn=c", "c");
        assertCns("l=\"abcn=a,b\", cn=c", "c");
        assertCns("l=\"abcn=a,b\", cn= c", "c");
        assertCns("cn=<", "<");
        assertCns("cn=>", ">");
        assertCns("cn= >", ">");
        assertCns("cn=a b", "a b");
        assertCns("cn   =a b", "a b");
        assertCns("Cn=a b", "a b");
        assertCns("cN=a b", "a b");
        assertCns("CN=a b", "a b");
        assertCns("cn=a#b", "a#b");
        assertCns("cn=#130161", "a");
        assertCns("l=q\t+cn=p", "p");
        assertCns("l=q\n+cn=p", "p");
        assertCns("l=q\n,cn=p", "p");
        assertCns("l=,cn=p", "p");
        assertCns("l=\tq\n,cn=\tp", "\tp");
    }

    /** A cn=, generates an empty value, unless it's at the very end */
    public void testEmptyValues() {
        assertCns("l=,cn=+cn=q", "", "q");
        assertCns("l=,cn=,cn=q", "", "q");
        assertCns("l=,cn=");
        assertCns("l=,cn=q,cn=   ", "q");
        assertCns("l=,cn=q  ,cn=   ", "q");
        assertCns("l=,cn=\"\"");
        assertCns("l=,cn=\"  \",cn=\"  \"", "  ", "  ");
        assertCns("l=,cn=  ,cn=  ","");
        assertCns("l=,cn=,cn=  ,cn=  ", "", "");
    }


    public void testGetCns_escapedChars() {
        assertCns("cn=\\,", ",");
        assertCns("cn=\\#", "#");
        assertCns("cn=\\+", "+");
        assertCns("cn=\\\"", "\"");
        assertCns("cn=\\\\", "\\");
        assertCns("cn=\\<", "<");
        assertCns("cn=\\>", ">");
        assertCns("cn=\\;", ";");
        assertCns("cn=\\+", "+");
        assertCns("cn=\"\\+\"", "+");
        assertCns("cn=\"\\,\"", ",");
        assertCns("cn= a =", "a =");
        assertCns("cn==", "=");
    }

    public void testGetCns_whitespace() {
        assertCns("cn= p", "p");
        assertCns("cn=\np", "p");
        assertCns("cn=\tp", "\tp");
    }

    public void testGetCnsWithOid() {
        assertCns("2.5.4.3=a,ou=xxx", "a");
        assertCns("2.5.4.3=\" a \",ou=xxx", " a ");
        assertCns("2.5.5.3=a,ou=xxx,cn=b", "b");
    }

    public void testGetCnsWithQuotedStrings() {
        assertCns("cn=\"\\\" a ,=<>#;\"", "\" a ,=<>#;");
        assertCns("cn=abc\\,def", "abc,def");
        assertCns("cn=\"\\\" a ,\\=<>\\#;\"", "\" a ,=<>#;");
    }

    public void testGetCnsWithUtf8() {
        assertCns("cn=\"Lu\\C4\\8Di\\C4\\87\"", "\u004c\u0075\u010d\u0069\u0107");
        assertCns("cn=Lu\\C4\\8Di\\C4\\87", "\u004c\u0075\u010d\u0069\u0107");
        assertCns("cn=Lu\\C4\\8di\\c4\\87", "\u004c\u0075\u010d\u0069\u0107");
        assertCns("cn=\"Lu\\C4\\8di\\c4\\87\"", "\u004c\u0075\u010d\u0069\u0107");
        assertCns("cn=\u004c\u0075\u010d\u0069\u0107", "\u004c\u0075\u010d\u0069\u0107");
        // \63=c
        assertExceptionInPrincipal("\\63n=ab");
        assertExceptionInPrincipal("cn=\\a");
    }

    public void testGetCnsWithWhitespace() {
        assertCns("ou=a, cn=  a  b  ,o=x", "a  b");
        assertCns("cn=\"  a  b  \" ,o=x", "  a  b  ");
    }

    private static void assertCns(String dn, String... expected) {
        String[] result = AbstractVerifier.getCNs(createStubCertificate(dn));
        if (expected.length == 0) {
            assertNull(result);
        } else {
            assertNotNull(dn, result);
            assertEquals(dn, Arrays.asList(expected), Arrays.asList(result));
        }
    }

    private static void assertExceptionInPrincipal(String dn) {
        try {
            X500Principal principal = new X500Principal(dn);
            fail("Expected " + IllegalArgumentException.class.getName()
                    + " because of incorrect input name");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    private static X509Certificate createStubCertificate(final String subjectName) {
        return new X509Certificate() {
            @Override
            public X500Principal getSubjectX500Principal() {
                return new X500Principal(subjectName);
            }

            @Override
            public Set<String> getCriticalExtensionOIDs() {
                return null;
            }

            @Override
            public byte[] getExtensionValue(String oid) {
                return new byte[0];
            }

            @Override
            public Set<String> getNonCriticalExtensionOIDs() {
                return null;
            }

            @Override
            public boolean hasUnsupportedCriticalExtension() {
                return false;
            }

            @Override
            public byte[] getEncoded() throws CertificateEncodingException {
                return new byte[0];
            }

            @Override
            public void verify(PublicKey key)
                    throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
                    NoSuchProviderException, SignatureException {

            }

            @Override
            public void verify(PublicKey key, String sigProvider)
                    throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
                    NoSuchProviderException, SignatureException {

            }

            @Override
            public String toString() {
                return null;
            }

            @Override
            public PublicKey getPublicKey() {
                return null;
            }

            @Override
            public void checkValidity()
                    throws CertificateExpiredException, CertificateNotYetValidException {

            }

            @Override
            public void checkValidity(Date date)
                    throws CertificateExpiredException, CertificateNotYetValidException {

            }

            @Override
            public int getVersion() {
                return 0;
            }

            @Override
            public BigInteger getSerialNumber() {
                return null;
            }

            @Override
            public Principal getIssuerDN() {
                return null;
            }

            @Override
            public Principal getSubjectDN() {
                return null;
            }

            @Override
            public Date getNotBefore() {
                return null;
            }

            @Override
            public Date getNotAfter() {
                return null;
            }

            @Override
            public byte[] getTBSCertificate() throws CertificateEncodingException {
                return new byte[0];
            }

            @Override
            public byte[] getSignature() {
                return new byte[0];
            }

            @Override
            public String getSigAlgName() {
                return null;
            }

            @Override
            public String getSigAlgOID() {
                return null;
            }

            @Override
            public byte[] getSigAlgParams() {
                return new byte[0];
            }

            @Override
            public boolean[] getIssuerUniqueID() {
                return new boolean[0];
            }

            @Override
            public boolean[] getSubjectUniqueID() {
                return new boolean[0];
            }

            @Override
            public boolean[] getKeyUsage() {
                return new boolean[0];
            }

            @Override
            public int getBasicConstraints() {
                return 0;
            }
        };
    }
}

