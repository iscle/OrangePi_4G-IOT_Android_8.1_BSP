/*
 * Copyright 2013 The Android Open Source Project
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

package android.keystore.cts;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.security.KeyPairGeneratorSpec;
import android.security.KeyStoreParameter;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import android.keystore.cts.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.security.auth.x500.X500Principal;

public class AndroidKeyStoreTest extends AndroidTestCase {
    private static final String TAG = AndroidKeyStoreTest.class.getSimpleName();

    private KeyStore mKeyStore;

    private static final String TEST_ALIAS_1 = "test1";

    private static final String TEST_ALIAS_2 = "test2";

    private static final String TEST_ALIAS_3 = "test3";

    private long mMaxTestDurationMillis;

    /*
     * The keys and certificates below are generated with:
     *
     * openssl req -new -x509 -days 3650 -extensions v3_ca -keyout cakey.pem -out cacert.pem
     * openssl req -newkey rsa:1024 -keyout userkey.pem -nodes -days 3650 -out userkey.req
     * mkdir -p demoCA/newcerts
     * touch demoCA/index.txt
     * echo "01" > demoCA/serial
     * openssl ca -out usercert.pem -in userkey.req -cert cacert.pem -keyfile cakey.pem -days 3650
     */

    /**
     * Generated from above and converted with:
     *
     * openssl x509 -outform d -in cacert.pem | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_RSA_CA_1 = {
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0xce, (byte) 0x30, (byte) 0x82,
            (byte) 0x02, (byte) 0x37, (byte) 0xa0, (byte) 0x03, (byte) 0x02, (byte) 0x01,
            (byte) 0x02, (byte) 0x02, (byte) 0x09, (byte) 0x00, (byte) 0xe1, (byte) 0x6a,
            (byte) 0xa2, (byte) 0xf4, (byte) 0x2e, (byte) 0x55, (byte) 0x48, (byte) 0x0a,
            (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01,
            (byte) 0x05, (byte) 0x05, (byte) 0x00, (byte) 0x30, (byte) 0x4f, (byte) 0x31,
            (byte) 0x0b, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x06, (byte) 0x13, (byte) 0x02, (byte) 0x55, (byte) 0x53,
            (byte) 0x31, (byte) 0x0b, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x04, (byte) 0x08, (byte) 0x13, (byte) 0x02, (byte) 0x43,
            (byte) 0x41, (byte) 0x31, (byte) 0x16, (byte) 0x30, (byte) 0x14, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x07, (byte) 0x13, (byte) 0x0d,
            (byte) 0x4d, (byte) 0x6f, (byte) 0x75, (byte) 0x6e, (byte) 0x74, (byte) 0x61,
            (byte) 0x69, (byte) 0x6e, (byte) 0x20, (byte) 0x56, (byte) 0x69, (byte) 0x65,
            (byte) 0x77, (byte) 0x31, (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x0a, (byte) 0x13, (byte) 0x12,
            (byte) 0x41, (byte) 0x6e, (byte) 0x64, (byte) 0x72, (byte) 0x6f, (byte) 0x69,
            (byte) 0x64, (byte) 0x20, (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74,
            (byte) 0x20, (byte) 0x43, (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x73,
            (byte) 0x30, (byte) 0x1e, (byte) 0x17, (byte) 0x0d, (byte) 0x31, (byte) 0x32,
            (byte) 0x30, (byte) 0x38, (byte) 0x31, (byte) 0x34, (byte) 0x31, (byte) 0x36,
            (byte) 0x35, (byte) 0x35, (byte) 0x34, (byte) 0x34, (byte) 0x5a, (byte) 0x17,
            (byte) 0x0d, (byte) 0x32, (byte) 0x32, (byte) 0x30, (byte) 0x38, (byte) 0x31,
            (byte) 0x32, (byte) 0x31, (byte) 0x36, (byte) 0x35, (byte) 0x35, (byte) 0x34,
            (byte) 0x34, (byte) 0x5a, (byte) 0x30, (byte) 0x4f, (byte) 0x31, (byte) 0x0b,
            (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x06, (byte) 0x13, (byte) 0x02, (byte) 0x55, (byte) 0x53, (byte) 0x31,
            (byte) 0x0b, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x08, (byte) 0x13, (byte) 0x02, (byte) 0x43, (byte) 0x41,
            (byte) 0x31, (byte) 0x16, (byte) 0x30, (byte) 0x14, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x04, (byte) 0x07, (byte) 0x13, (byte) 0x0d, (byte) 0x4d,
            (byte) 0x6f, (byte) 0x75, (byte) 0x6e, (byte) 0x74, (byte) 0x61, (byte) 0x69,
            (byte) 0x6e, (byte) 0x20, (byte) 0x56, (byte) 0x69, (byte) 0x65, (byte) 0x77,
            (byte) 0x31, (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x04, (byte) 0x0a, (byte) 0x13, (byte) 0x12, (byte) 0x41,
            (byte) 0x6e, (byte) 0x64, (byte) 0x72, (byte) 0x6f, (byte) 0x69, (byte) 0x64,
            (byte) 0x20, (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x20,
            (byte) 0x43, (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x73, (byte) 0x30,
            (byte) 0x81, (byte) 0x9f, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09,
            (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d,
            (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x03,
            (byte) 0x81, (byte) 0x8d, (byte) 0x00, (byte) 0x30, (byte) 0x81, (byte) 0x89,
            (byte) 0x02, (byte) 0x81, (byte) 0x81, (byte) 0x00, (byte) 0xa3, (byte) 0x72,
            (byte) 0xab, (byte) 0xd0, (byte) 0xe4, (byte) 0xad, (byte) 0x2f, (byte) 0xe7,
            (byte) 0xe2, (byte) 0x79, (byte) 0x07, (byte) 0x36, (byte) 0x3d, (byte) 0x0c,
            (byte) 0x8d, (byte) 0x42, (byte) 0x9a, (byte) 0x0a, (byte) 0x33, (byte) 0x64,
            (byte) 0xb3, (byte) 0xcd, (byte) 0xb2, (byte) 0xd7, (byte) 0x3a, (byte) 0x42,
            (byte) 0x06, (byte) 0x77, (byte) 0x45, (byte) 0x29, (byte) 0xe9, (byte) 0xcb,
            (byte) 0xb7, (byte) 0x4a, (byte) 0xd6, (byte) 0xee, (byte) 0xad, (byte) 0x01,
            (byte) 0x91, (byte) 0x9b, (byte) 0x0c, (byte) 0x59, (byte) 0xa1, (byte) 0x03,
            (byte) 0xfa, (byte) 0xf0, (byte) 0x5a, (byte) 0x7c, (byte) 0x4f, (byte) 0xf7,
            (byte) 0x8d, (byte) 0x36, (byte) 0x0f, (byte) 0x1f, (byte) 0x45, (byte) 0x7d,
            (byte) 0x1b, (byte) 0x31, (byte) 0xa1, (byte) 0x35, (byte) 0x0b, (byte) 0x00,
            (byte) 0xed, (byte) 0x7a, (byte) 0xb6, (byte) 0xc8, (byte) 0x4e, (byte) 0xa9,
            (byte) 0x86, (byte) 0x4c, (byte) 0x7b, (byte) 0x99, (byte) 0x57, (byte) 0x41,
            (byte) 0x12, (byte) 0xef, (byte) 0x6b, (byte) 0xbc, (byte) 0x3d, (byte) 0x60,
            (byte) 0xf2, (byte) 0x99, (byte) 0x1a, (byte) 0xcd, (byte) 0xed, (byte) 0x56,
            (byte) 0xa4, (byte) 0xe5, (byte) 0x36, (byte) 0x9f, (byte) 0x24, (byte) 0x1f,
            (byte) 0xdc, (byte) 0x89, (byte) 0x40, (byte) 0xc8, (byte) 0x99, (byte) 0x92,
            (byte) 0xab, (byte) 0x4a, (byte) 0xb5, (byte) 0x61, (byte) 0x45, (byte) 0x62,
            (byte) 0xff, (byte) 0xa3, (byte) 0x45, (byte) 0x65, (byte) 0xaf, (byte) 0xf6,
            (byte) 0x27, (byte) 0x30, (byte) 0x51, (byte) 0x0e, (byte) 0x0e, (byte) 0xeb,
            (byte) 0x79, (byte) 0x0c, (byte) 0xbe, (byte) 0xb3, (byte) 0x0a, (byte) 0x6f,
            (byte) 0x29, (byte) 0x06, (byte) 0xdc, (byte) 0x2f, (byte) 0x6b, (byte) 0x51,
            (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0xa3,
            (byte) 0x81, (byte) 0xb1, (byte) 0x30, (byte) 0x81, (byte) 0xae, (byte) 0x30,
            (byte) 0x1d, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x0e,
            (byte) 0x04, (byte) 0x16, (byte) 0x04, (byte) 0x14, (byte) 0x33, (byte) 0x05,
            (byte) 0xee, (byte) 0xfe, (byte) 0x6f, (byte) 0x60, (byte) 0xc7, (byte) 0xf9,
            (byte) 0xa9, (byte) 0xd2, (byte) 0x73, (byte) 0x5c, (byte) 0x8f, (byte) 0x6d,
            (byte) 0xa2, (byte) 0x2f, (byte) 0x97, (byte) 0x8e, (byte) 0x5d, (byte) 0x51,
            (byte) 0x30, (byte) 0x7f, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d,
            (byte) 0x23, (byte) 0x04, (byte) 0x78, (byte) 0x30, (byte) 0x76, (byte) 0x80,
            (byte) 0x14, (byte) 0x33, (byte) 0x05, (byte) 0xee, (byte) 0xfe, (byte) 0x6f,
            (byte) 0x60, (byte) 0xc7, (byte) 0xf9, (byte) 0xa9, (byte) 0xd2, (byte) 0x73,
            (byte) 0x5c, (byte) 0x8f, (byte) 0x6d, (byte) 0xa2, (byte) 0x2f, (byte) 0x97,
            (byte) 0x8e, (byte) 0x5d, (byte) 0x51, (byte) 0xa1, (byte) 0x53, (byte) 0xa4,
            (byte) 0x51, (byte) 0x30, (byte) 0x4f, (byte) 0x31, (byte) 0x0b, (byte) 0x30,
            (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x06,
            (byte) 0x13, (byte) 0x02, (byte) 0x55, (byte) 0x53, (byte) 0x31, (byte) 0x0b,
            (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x08, (byte) 0x13, (byte) 0x02, (byte) 0x43, (byte) 0x41, (byte) 0x31,
            (byte) 0x16, (byte) 0x30, (byte) 0x14, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x07, (byte) 0x13, (byte) 0x0d, (byte) 0x4d, (byte) 0x6f,
            (byte) 0x75, (byte) 0x6e, (byte) 0x74, (byte) 0x61, (byte) 0x69, (byte) 0x6e,
            (byte) 0x20, (byte) 0x56, (byte) 0x69, (byte) 0x65, (byte) 0x77, (byte) 0x31,
            (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x0a, (byte) 0x13, (byte) 0x12, (byte) 0x41, (byte) 0x6e,
            (byte) 0x64, (byte) 0x72, (byte) 0x6f, (byte) 0x69, (byte) 0x64, (byte) 0x20,
            (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x20, (byte) 0x43,
            (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x73, (byte) 0x82, (byte) 0x09,
            (byte) 0x00, (byte) 0xe1, (byte) 0x6a, (byte) 0xa2, (byte) 0xf4, (byte) 0x2e,
            (byte) 0x55, (byte) 0x48, (byte) 0x0a, (byte) 0x30, (byte) 0x0c, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x13, (byte) 0x04, (byte) 0x05,
            (byte) 0x30, (byte) 0x03, (byte) 0x01, (byte) 0x01, (byte) 0xff, (byte) 0x30,
            (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86, (byte) 0x48,
            (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01, (byte) 0x05,
            (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x81, (byte) 0x81, (byte) 0x00,
            (byte) 0x8c, (byte) 0x30, (byte) 0x42, (byte) 0xfa, (byte) 0xeb, (byte) 0x1a,
            (byte) 0x26, (byte) 0xeb, (byte) 0xda, (byte) 0x56, (byte) 0x32, (byte) 0xf2,
            (byte) 0x9d, (byte) 0xa5, (byte) 0x24, (byte) 0xd8, (byte) 0x3a, (byte) 0xda,
            (byte) 0x30, (byte) 0xa6, (byte) 0x8b, (byte) 0x46, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xdb, (byte) 0xf1, (byte) 0xe6, (byte) 0xe1, (byte) 0x7c, (byte) 0x1b,
            (byte) 0xe7, (byte) 0x77, (byte) 0x00, (byte) 0xa1, (byte) 0x1c, (byte) 0x19,
            (byte) 0x17, (byte) 0x73, (byte) 0xb0, (byte) 0xf0, (byte) 0x9d, (byte) 0xf3,
            (byte) 0x4f, (byte) 0xb6, (byte) 0xbc, (byte) 0xc7, (byte) 0x47, (byte) 0x85,
            (byte) 0x2a, (byte) 0x4a, (byte) 0xa1, (byte) 0xa5, (byte) 0x58, (byte) 0xf5,
            (byte) 0xc5, (byte) 0x1a, (byte) 0x51, (byte) 0xb1, (byte) 0x04, (byte) 0x80,
            (byte) 0xee, (byte) 0x3a, (byte) 0xec, (byte) 0x2f, (byte) 0xe1, (byte) 0xfd,
            (byte) 0x58, (byte) 0xeb, (byte) 0xed, (byte) 0x82, (byte) 0x9e, (byte) 0x38,
            (byte) 0xa3, (byte) 0x24, (byte) 0x75, (byte) 0xf7, (byte) 0x3e, (byte) 0xc2,
            (byte) 0xc5, (byte) 0x27, (byte) 0xeb, (byte) 0x6f, (byte) 0x7b, (byte) 0x50,
            (byte) 0xda, (byte) 0x43, (byte) 0xdc, (byte) 0x3b, (byte) 0x0b, (byte) 0x6f,
            (byte) 0x78, (byte) 0x8f, (byte) 0xb0, (byte) 0x66, (byte) 0xe1, (byte) 0x12,
            (byte) 0x87, (byte) 0x5f, (byte) 0x97, (byte) 0x7b, (byte) 0xca, (byte) 0x14,
            (byte) 0x79, (byte) 0xf7, (byte) 0xe8, (byte) 0x6c, (byte) 0x72, (byte) 0xdb,
            (byte) 0x91, (byte) 0x65, (byte) 0x17, (byte) 0x54, (byte) 0xe0, (byte) 0x74,
            (byte) 0x1d, (byte) 0xac, (byte) 0x47, (byte) 0x04, (byte) 0x12, (byte) 0xe0,
            (byte) 0xc3, (byte) 0x66, (byte) 0x19, (byte) 0x05, (byte) 0x2e, (byte) 0x7e,
            (byte) 0xf1, (byte) 0x61
    };

    /**
     * Generated from above and converted with:
     *
     * openssl pkcs8 -topk8 -outform d -in userkey.pem -nocrypt | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_RSA_KEY_1 = new byte[] {
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x78, (byte) 0x02, (byte) 0x01,
            (byte) 0x00, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a,
            (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01,
            (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x04, (byte) 0x82,
            (byte) 0x02, (byte) 0x62, (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x5e,
            (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x81, (byte) 0x81,
            (byte) 0x00, (byte) 0xce, (byte) 0x29, (byte) 0xeb, (byte) 0xf6, (byte) 0x5b,
            (byte) 0x25, (byte) 0xdc, (byte) 0xa1, (byte) 0xa6, (byte) 0x2c, (byte) 0x66,
            (byte) 0xcb, (byte) 0x20, (byte) 0x90, (byte) 0x27, (byte) 0x86, (byte) 0x8a,
            (byte) 0x44, (byte) 0x71, (byte) 0x50, (byte) 0xda, (byte) 0xd3, (byte) 0x02,
            (byte) 0x77, (byte) 0x55, (byte) 0xe9, (byte) 0xe8, (byte) 0x08, (byte) 0xf3,
            (byte) 0x36, (byte) 0x9a, (byte) 0xae, (byte) 0xab, (byte) 0x04, (byte) 0x6d,
            (byte) 0x00, (byte) 0x99, (byte) 0xbf, (byte) 0x7d, (byte) 0x0f, (byte) 0x67,
            (byte) 0x8b, (byte) 0x1d, (byte) 0xd4, (byte) 0x2b, (byte) 0x7c, (byte) 0xcb,
            (byte) 0xcd, (byte) 0x33, (byte) 0xc7, (byte) 0x84, (byte) 0x30, (byte) 0xe2,
            (byte) 0x45, (byte) 0x21, (byte) 0xb3, (byte) 0x75, (byte) 0xf5, (byte) 0x79,
            (byte) 0x02, (byte) 0xda, (byte) 0x50, (byte) 0xa3, (byte) 0x8b, (byte) 0xce,
            (byte) 0xc3, (byte) 0x8e, (byte) 0x0f, (byte) 0x25, (byte) 0xeb, (byte) 0x08,
            (byte) 0x2c, (byte) 0xdd, (byte) 0x1c, (byte) 0xcf, (byte) 0xff, (byte) 0x3b,
            (byte) 0xde, (byte) 0xb6, (byte) 0xaa, (byte) 0x2a, (byte) 0xa9, (byte) 0xc4,
            (byte) 0x8a, (byte) 0x24, (byte) 0x24, (byte) 0xe6, (byte) 0x29, (byte) 0x0d,
            (byte) 0x98, (byte) 0x4c, (byte) 0x32, (byte) 0xa1, (byte) 0x7b, (byte) 0x23,
            (byte) 0x2b, (byte) 0x42, (byte) 0x30, (byte) 0xee, (byte) 0x78, (byte) 0x08,
            (byte) 0x47, (byte) 0xad, (byte) 0xf2, (byte) 0x96, (byte) 0xd5, (byte) 0xf1,
            (byte) 0x62, (byte) 0x42, (byte) 0x2d, (byte) 0x35, (byte) 0x19, (byte) 0xb4,
            (byte) 0x3c, (byte) 0xc9, (byte) 0xc3, (byte) 0x5f, (byte) 0x03, (byte) 0x16,
            (byte) 0x3a, (byte) 0x23, (byte) 0xac, (byte) 0xcb, (byte) 0xce, (byte) 0x9e,
            (byte) 0x51, (byte) 0x2e, (byte) 0x6d, (byte) 0x02, (byte) 0x03, (byte) 0x01,
            (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x81, (byte) 0x80, (byte) 0x16,
            (byte) 0x59, (byte) 0xc3, (byte) 0x24, (byte) 0x1d, (byte) 0x33, (byte) 0x98,
            (byte) 0x9c, (byte) 0xc9, (byte) 0xc8, (byte) 0x2c, (byte) 0x88, (byte) 0xbf,
            (byte) 0x0a, (byte) 0x01, (byte) 0xce, (byte) 0xfb, (byte) 0x34, (byte) 0x7a,
            (byte) 0x58, (byte) 0x7a, (byte) 0xb0, (byte) 0xbf, (byte) 0xa6, (byte) 0xb2,
            (byte) 0x60, (byte) 0xbe, (byte) 0x70, (byte) 0x21, (byte) 0xf5, (byte) 0xfc,
            (byte) 0x85, (byte) 0x0d, (byte) 0x33, (byte) 0x58, (byte) 0xa1, (byte) 0xe5,
            (byte) 0x09, (byte) 0x36, (byte) 0x84, (byte) 0xb2, (byte) 0x04, (byte) 0x0a,
            (byte) 0x02, (byte) 0xd3, (byte) 0x88, (byte) 0x1f, (byte) 0x0c, (byte) 0x2b,
            (byte) 0x1d, (byte) 0xe9, (byte) 0x3d, (byte) 0xe7, (byte) 0x79, (byte) 0xf9,
            (byte) 0x32, (byte) 0x5c, (byte) 0x8a, (byte) 0x75, (byte) 0x49, (byte) 0x12,
            (byte) 0xe4, (byte) 0x05, (byte) 0x26, (byte) 0xd4, (byte) 0x2e, (byte) 0x9e,
            (byte) 0x1f, (byte) 0xcc, (byte) 0x54, (byte) 0xad, (byte) 0x33, (byte) 0x8d,
            (byte) 0x99, (byte) 0x00, (byte) 0xdc, (byte) 0xf5, (byte) 0xb4, (byte) 0xa2,
            (byte) 0x2f, (byte) 0xba, (byte) 0xe5, (byte) 0x62, (byte) 0x30, (byte) 0x6d,
            (byte) 0xe6, (byte) 0x3d, (byte) 0xeb, (byte) 0x24, (byte) 0xc2, (byte) 0xdc,
            (byte) 0x5f, (byte) 0xb7, (byte) 0x16, (byte) 0x35, (byte) 0xa3, (byte) 0x98,
            (byte) 0x98, (byte) 0xa8, (byte) 0xef, (byte) 0xe8, (byte) 0xc4, (byte) 0x96,
            (byte) 0x6d, (byte) 0x38, (byte) 0xab, (byte) 0x26, (byte) 0x6d, (byte) 0x30,
            (byte) 0xc2, (byte) 0xa0, (byte) 0x44, (byte) 0xe4, (byte) 0xff, (byte) 0x7e,
            (byte) 0xbe, (byte) 0x7c, (byte) 0x33, (byte) 0xa5, (byte) 0x10, (byte) 0xad,
            (byte) 0xd7, (byte) 0x1e, (byte) 0x13, (byte) 0x20, (byte) 0xb3, (byte) 0x1f,
            (byte) 0x41, (byte) 0x02, (byte) 0x41, (byte) 0x00, (byte) 0xf1, (byte) 0x89,
            (byte) 0x07, (byte) 0x0f, (byte) 0xe8, (byte) 0xcf, (byte) 0xab, (byte) 0x13,
            (byte) 0x2a, (byte) 0x8f, (byte) 0x88, (byte) 0x80, (byte) 0x11, (byte) 0x9a,
            (byte) 0x79, (byte) 0xb6, (byte) 0x59, (byte) 0x3a, (byte) 0x50, (byte) 0x6e,
            (byte) 0x57, (byte) 0x37, (byte) 0xab, (byte) 0x2a, (byte) 0xd2, (byte) 0xaa,
            (byte) 0xd9, (byte) 0x72, (byte) 0x73, (byte) 0xff, (byte) 0x8b, (byte) 0x47,
            (byte) 0x76, (byte) 0xdd, (byte) 0xdc, (byte) 0xf5, (byte) 0x97, (byte) 0x44,
            (byte) 0x3a, (byte) 0x78, (byte) 0xbe, (byte) 0x17, (byte) 0xb4, (byte) 0x22,
            (byte) 0x6f, (byte) 0xe5, (byte) 0x23, (byte) 0x70, (byte) 0x1d, (byte) 0x10,
            (byte) 0x5d, (byte) 0xba, (byte) 0x16, (byte) 0x81, (byte) 0xf1, (byte) 0x45,
            (byte) 0xce, (byte) 0x30, (byte) 0xb4, (byte) 0xab, (byte) 0x80, (byte) 0xe4,
            (byte) 0x98, (byte) 0x31, (byte) 0x02, (byte) 0x41, (byte) 0x00, (byte) 0xda,
            (byte) 0x82, (byte) 0x9d, (byte) 0x3f, (byte) 0xca, (byte) 0x2f, (byte) 0xe1,
            (byte) 0xd4, (byte) 0x86, (byte) 0x77, (byte) 0x48, (byte) 0xa6, (byte) 0xab,
            (byte) 0xab, (byte) 0x1c, (byte) 0x42, (byte) 0x5c, (byte) 0xd5, (byte) 0xc7,
            (byte) 0x46, (byte) 0x59, (byte) 0x91, (byte) 0x3f, (byte) 0xfc, (byte) 0xcc,
            (byte) 0xec, (byte) 0xc2, (byte) 0x40, (byte) 0x12, (byte) 0x2c, (byte) 0x8d,
            (byte) 0x1f, (byte) 0xa2, (byte) 0x18, (byte) 0x88, (byte) 0xee, (byte) 0x82,
            (byte) 0x4a, (byte) 0x5a, (byte) 0x5e, (byte) 0x88, (byte) 0x20, (byte) 0xe3,
            (byte) 0x7b, (byte) 0xe0, (byte) 0xd8, (byte) 0x3a, (byte) 0x52, (byte) 0x9a,
            (byte) 0x26, (byte) 0x6a, (byte) 0x04, (byte) 0xec, (byte) 0xe8, (byte) 0xb9,
            (byte) 0x48, (byte) 0x40, (byte) 0xe1, (byte) 0xe1, (byte) 0x83, (byte) 0xa6,
            (byte) 0x67, (byte) 0xa6, (byte) 0xfd, (byte) 0x02, (byte) 0x41, (byte) 0x00,
            (byte) 0x89, (byte) 0x72, (byte) 0x3e, (byte) 0xb0, (byte) 0x90, (byte) 0xfd,
            (byte) 0x4c, (byte) 0x0e, (byte) 0xd6, (byte) 0x13, (byte) 0x63, (byte) 0xcb,
            (byte) 0xed, (byte) 0x38, (byte) 0x88, (byte) 0xb6, (byte) 0x79, (byte) 0xc4,
            (byte) 0x33, (byte) 0x6c, (byte) 0xf6, (byte) 0xf8, (byte) 0xd8, (byte) 0xd0,
            (byte) 0xbf, (byte) 0x9d, (byte) 0x35, (byte) 0xac, (byte) 0x69, (byte) 0xd2,
            (byte) 0x2b, (byte) 0xc1, (byte) 0xf9, (byte) 0x24, (byte) 0x7b, (byte) 0xce,
            (byte) 0xcd, (byte) 0xcb, (byte) 0xa7, (byte) 0xb2, (byte) 0x7a, (byte) 0x0a,
            (byte) 0x27, (byte) 0x19, (byte) 0xc9, (byte) 0xaf, (byte) 0x0d, (byte) 0x21,
            (byte) 0x89, (byte) 0x88, (byte) 0x7c, (byte) 0xad, (byte) 0x9e, (byte) 0x8d,
            (byte) 0x47, (byte) 0x6d, (byte) 0x3f, (byte) 0xce, (byte) 0x7b, (byte) 0xa1,
            (byte) 0x74, (byte) 0xf1, (byte) 0xa0, (byte) 0xa1, (byte) 0x02, (byte) 0x41,
            (byte) 0x00, (byte) 0xd9, (byte) 0xa8, (byte) 0xf5, (byte) 0xfe, (byte) 0xce,
            (byte) 0xe6, (byte) 0x77, (byte) 0x6b, (byte) 0xfe, (byte) 0x2d, (byte) 0xe0,
            (byte) 0x1e, (byte) 0xb6, (byte) 0x2e, (byte) 0x12, (byte) 0x4e, (byte) 0x40,
            (byte) 0xaf, (byte) 0x6a, (byte) 0x7b, (byte) 0x37, (byte) 0x49, (byte) 0x2a,
            (byte) 0x96, (byte) 0x25, (byte) 0x83, (byte) 0x49, (byte) 0xd4, (byte) 0x0c,
            (byte) 0xc6, (byte) 0x78, (byte) 0x25, (byte) 0x24, (byte) 0x90, (byte) 0x90,
            (byte) 0x06, (byte) 0x15, (byte) 0x9e, (byte) 0xfe, (byte) 0xf9, (byte) 0xdf,
            (byte) 0x5b, (byte) 0xf3, (byte) 0x7e, (byte) 0x38, (byte) 0x70, (byte) 0xeb,
            (byte) 0x57, (byte) 0xd0, (byte) 0xd9, (byte) 0xa7, (byte) 0x0e, (byte) 0x14,
            (byte) 0xf7, (byte) 0x95, (byte) 0x68, (byte) 0xd5, (byte) 0xc8, (byte) 0xab,
            (byte) 0x9d, (byte) 0x3a, (byte) 0x2b, (byte) 0x51, (byte) 0xf9, (byte) 0x02,
            (byte) 0x41, (byte) 0x00, (byte) 0x96, (byte) 0xdf, (byte) 0xe9, (byte) 0x67,
            (byte) 0x6c, (byte) 0xdc, (byte) 0x90, (byte) 0x14, (byte) 0xb4, (byte) 0x1d,
            (byte) 0x22, (byte) 0x33, (byte) 0x4a, (byte) 0x31, (byte) 0xc1, (byte) 0x9d,
            (byte) 0x2e, (byte) 0xff, (byte) 0x9a, (byte) 0x2a, (byte) 0x95, (byte) 0x4b,
            (byte) 0x27, (byte) 0x74, (byte) 0xcb, (byte) 0x21, (byte) 0xc3, (byte) 0xd2,
            (byte) 0x0b, (byte) 0xb2, (byte) 0x46, (byte) 0x87, (byte) 0xf8, (byte) 0x28,
            (byte) 0x01, (byte) 0x8b, (byte) 0xd8, (byte) 0xb9, (byte) 0x4b, (byte) 0xcd,
            (byte) 0x9a, (byte) 0x96, (byte) 0x41, (byte) 0x0e, (byte) 0x36, (byte) 0x6d,
            (byte) 0x40, (byte) 0x42, (byte) 0xbc, (byte) 0xd9, (byte) 0xd3, (byte) 0x7b,
            (byte) 0xbc, (byte) 0xa7, (byte) 0x92, (byte) 0x90, (byte) 0xdd, (byte) 0xa1,
            (byte) 0x9c, (byte) 0xce, (byte) 0xa1, (byte) 0x87, (byte) 0x11, (byte) 0x51
    };

    /**
     * Generated from above and converted with:
     *
     * openssl x509 -outform d -in usercert.pem | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_RSA_USER_1 = new byte[] {
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x95, (byte) 0x30, (byte) 0x82,
            (byte) 0x01, (byte) 0xfe, (byte) 0xa0, (byte) 0x03, (byte) 0x02, (byte) 0x01,
            (byte) 0x02, (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x30, (byte) 0x0d,
            (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86,
            (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x05,
            (byte) 0x00, (byte) 0x30, (byte) 0x4f, (byte) 0x31, (byte) 0x0b, (byte) 0x30,
            (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x06,
            (byte) 0x13, (byte) 0x02, (byte) 0x55, (byte) 0x53, (byte) 0x31, (byte) 0x0b,
            (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x08, (byte) 0x13, (byte) 0x02, (byte) 0x43, (byte) 0x41, (byte) 0x31,
            (byte) 0x16, (byte) 0x30, (byte) 0x14, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x07, (byte) 0x13, (byte) 0x0d, (byte) 0x4d, (byte) 0x6f,
            (byte) 0x75, (byte) 0x6e, (byte) 0x74, (byte) 0x61, (byte) 0x69, (byte) 0x6e,
            (byte) 0x20, (byte) 0x56, (byte) 0x69, (byte) 0x65, (byte) 0x77, (byte) 0x31,
            (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x0a, (byte) 0x13, (byte) 0x12, (byte) 0x41, (byte) 0x6e,
            (byte) 0x64, (byte) 0x72, (byte) 0x6f, (byte) 0x69, (byte) 0x64, (byte) 0x20,
            (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x20, (byte) 0x43,
            (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x73, (byte) 0x30, (byte) 0x1e,
            (byte) 0x17, (byte) 0x0d, (byte) 0x31, (byte) 0x32, (byte) 0x30, (byte) 0x38,
            (byte) 0x31, (byte) 0x34, (byte) 0x32, (byte) 0x33, (byte) 0x32, (byte) 0x35,
            (byte) 0x34, (byte) 0x38, (byte) 0x5a, (byte) 0x17, (byte) 0x0d, (byte) 0x32,
            (byte) 0x32, (byte) 0x30, (byte) 0x38, (byte) 0x31, (byte) 0x32, (byte) 0x32,
            (byte) 0x33, (byte) 0x32, (byte) 0x35, (byte) 0x34, (byte) 0x38, (byte) 0x5a,
            (byte) 0x30, (byte) 0x55, (byte) 0x31, (byte) 0x0b, (byte) 0x30, (byte) 0x09,
            (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x06, (byte) 0x13,
            (byte) 0x02, (byte) 0x55, (byte) 0x53, (byte) 0x31, (byte) 0x0b, (byte) 0x30,
            (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x08,
            (byte) 0x13, (byte) 0x02, (byte) 0x43, (byte) 0x41, (byte) 0x31, (byte) 0x1b,
            (byte) 0x30, (byte) 0x19, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x0a, (byte) 0x13, (byte) 0x12, (byte) 0x41, (byte) 0x6e, (byte) 0x64,
            (byte) 0x72, (byte) 0x6f, (byte) 0x69, (byte) 0x64, (byte) 0x20, (byte) 0x54,
            (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x20, (byte) 0x43, (byte) 0x61,
            (byte) 0x73, (byte) 0x65, (byte) 0x73, (byte) 0x31, (byte) 0x1c, (byte) 0x30,
            (byte) 0x1a, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x03,
            (byte) 0x13, (byte) 0x13, (byte) 0x73, (byte) 0x65, (byte) 0x72, (byte) 0x76,
            (byte) 0x65, (byte) 0x72, (byte) 0x31, (byte) 0x2e, (byte) 0x65, (byte) 0x78,
            (byte) 0x61, (byte) 0x6d, (byte) 0x70, (byte) 0x6c, (byte) 0x65, (byte) 0x2e,
            (byte) 0x63, (byte) 0x6f, (byte) 0x6d, (byte) 0x30, (byte) 0x81, (byte) 0x9f,
            (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01,
            (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x81, (byte) 0x8d,
            (byte) 0x00, (byte) 0x30, (byte) 0x81, (byte) 0x89, (byte) 0x02, (byte) 0x81,
            (byte) 0x81, (byte) 0x00, (byte) 0xce, (byte) 0x29, (byte) 0xeb, (byte) 0xf6,
            (byte) 0x5b, (byte) 0x25, (byte) 0xdc, (byte) 0xa1, (byte) 0xa6, (byte) 0x2c,
            (byte) 0x66, (byte) 0xcb, (byte) 0x20, (byte) 0x90, (byte) 0x27, (byte) 0x86,
            (byte) 0x8a, (byte) 0x44, (byte) 0x71, (byte) 0x50, (byte) 0xda, (byte) 0xd3,
            (byte) 0x02, (byte) 0x77, (byte) 0x55, (byte) 0xe9, (byte) 0xe8, (byte) 0x08,
            (byte) 0xf3, (byte) 0x36, (byte) 0x9a, (byte) 0xae, (byte) 0xab, (byte) 0x04,
            (byte) 0x6d, (byte) 0x00, (byte) 0x99, (byte) 0xbf, (byte) 0x7d, (byte) 0x0f,
            (byte) 0x67, (byte) 0x8b, (byte) 0x1d, (byte) 0xd4, (byte) 0x2b, (byte) 0x7c,
            (byte) 0xcb, (byte) 0xcd, (byte) 0x33, (byte) 0xc7, (byte) 0x84, (byte) 0x30,
            (byte) 0xe2, (byte) 0x45, (byte) 0x21, (byte) 0xb3, (byte) 0x75, (byte) 0xf5,
            (byte) 0x79, (byte) 0x02, (byte) 0xda, (byte) 0x50, (byte) 0xa3, (byte) 0x8b,
            (byte) 0xce, (byte) 0xc3, (byte) 0x8e, (byte) 0x0f, (byte) 0x25, (byte) 0xeb,
            (byte) 0x08, (byte) 0x2c, (byte) 0xdd, (byte) 0x1c, (byte) 0xcf, (byte) 0xff,
            (byte) 0x3b, (byte) 0xde, (byte) 0xb6, (byte) 0xaa, (byte) 0x2a, (byte) 0xa9,
            (byte) 0xc4, (byte) 0x8a, (byte) 0x24, (byte) 0x24, (byte) 0xe6, (byte) 0x29,
            (byte) 0x0d, (byte) 0x98, (byte) 0x4c, (byte) 0x32, (byte) 0xa1, (byte) 0x7b,
            (byte) 0x23, (byte) 0x2b, (byte) 0x42, (byte) 0x30, (byte) 0xee, (byte) 0x78,
            (byte) 0x08, (byte) 0x47, (byte) 0xad, (byte) 0xf2, (byte) 0x96, (byte) 0xd5,
            (byte) 0xf1, (byte) 0x62, (byte) 0x42, (byte) 0x2d, (byte) 0x35, (byte) 0x19,
            (byte) 0xb4, (byte) 0x3c, (byte) 0xc9, (byte) 0xc3, (byte) 0x5f, (byte) 0x03,
            (byte) 0x16, (byte) 0x3a, (byte) 0x23, (byte) 0xac, (byte) 0xcb, (byte) 0xce,
            (byte) 0x9e, (byte) 0x51, (byte) 0x2e, (byte) 0x6d, (byte) 0x02, (byte) 0x03,
            (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0xa3, (byte) 0x7b, (byte) 0x30,
            (byte) 0x79, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x1d, (byte) 0x13, (byte) 0x04, (byte) 0x02, (byte) 0x30, (byte) 0x00,
            (byte) 0x30, (byte) 0x2c, (byte) 0x06, (byte) 0x09, (byte) 0x60, (byte) 0x86,
            (byte) 0x48, (byte) 0x01, (byte) 0x86, (byte) 0xf8, (byte) 0x42, (byte) 0x01,
            (byte) 0x0d, (byte) 0x04, (byte) 0x1f, (byte) 0x16, (byte) 0x1d, (byte) 0x4f,
            (byte) 0x70, (byte) 0x65, (byte) 0x6e, (byte) 0x53, (byte) 0x53, (byte) 0x4c,
            (byte) 0x20, (byte) 0x47, (byte) 0x65, (byte) 0x6e, (byte) 0x65, (byte) 0x72,
            (byte) 0x61, (byte) 0x74, (byte) 0x65, (byte) 0x64, (byte) 0x20, (byte) 0x43,
            (byte) 0x65, (byte) 0x72, (byte) 0x74, (byte) 0x69, (byte) 0x66, (byte) 0x69,
            (byte) 0x63, (byte) 0x61, (byte) 0x74, (byte) 0x65, (byte) 0x30, (byte) 0x1d,
            (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x0e, (byte) 0x04,
            (byte) 0x16, (byte) 0x04, (byte) 0x14, (byte) 0x32, (byte) 0xa1, (byte) 0x1e,
            (byte) 0x6b, (byte) 0x69, (byte) 0x04, (byte) 0xfe, (byte) 0xb3, (byte) 0xcd,
            (byte) 0xf8, (byte) 0xbb, (byte) 0x14, (byte) 0xcd, (byte) 0xff, (byte) 0xd4,
            (byte) 0x16, (byte) 0xc3, (byte) 0xab, (byte) 0x44, (byte) 0x2f, (byte) 0x30,
            (byte) 0x1f, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x23,
            (byte) 0x04, (byte) 0x18, (byte) 0x30, (byte) 0x16, (byte) 0x80, (byte) 0x14,
            (byte) 0x33, (byte) 0x05, (byte) 0xee, (byte) 0xfe, (byte) 0x6f, (byte) 0x60,
            (byte) 0xc7, (byte) 0xf9, (byte) 0xa9, (byte) 0xd2, (byte) 0x73, (byte) 0x5c,
            (byte) 0x8f, (byte) 0x6d, (byte) 0xa2, (byte) 0x2f, (byte) 0x97, (byte) 0x8e,
            (byte) 0x5d, (byte) 0x51, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09,
            (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d,
            (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x05, (byte) 0x00, (byte) 0x03,
            (byte) 0x81, (byte) 0x81, (byte) 0x00, (byte) 0x46, (byte) 0x42, (byte) 0xef,
            (byte) 0x56, (byte) 0x89, (byte) 0x78, (byte) 0x90, (byte) 0x38, (byte) 0x24,
            (byte) 0x9f, (byte) 0x8c, (byte) 0x7a, (byte) 0xce, (byte) 0x7a, (byte) 0xa5,
            (byte) 0xb5, (byte) 0x1e, (byte) 0x74, (byte) 0x96, (byte) 0x34, (byte) 0x49,
            (byte) 0x8b, (byte) 0xed, (byte) 0x44, (byte) 0xb3, (byte) 0xc9, (byte) 0x05,
            (byte) 0xd7, (byte) 0x48, (byte) 0x55, (byte) 0x52, (byte) 0x59, (byte) 0x15,
            (byte) 0x0b, (byte) 0xaa, (byte) 0x16, (byte) 0x86, (byte) 0xd2, (byte) 0x8e,
            (byte) 0x16, (byte) 0x99, (byte) 0xe8, (byte) 0x5f, (byte) 0x11, (byte) 0x71,
            (byte) 0x42, (byte) 0x55, (byte) 0xd1, (byte) 0xc4, (byte) 0x6f, (byte) 0x2e,
            (byte) 0xa9, (byte) 0x64, (byte) 0x6f, (byte) 0xd8, (byte) 0xfd, (byte) 0x43,
            (byte) 0x13, (byte) 0x24, (byte) 0xaa, (byte) 0x67, (byte) 0xe6, (byte) 0xf5,
            (byte) 0xca, (byte) 0x80, (byte) 0x5e, (byte) 0x3a, (byte) 0x3e, (byte) 0xcc,
            (byte) 0x4f, (byte) 0xba, (byte) 0x87, (byte) 0xe6, (byte) 0xae, (byte) 0xbf,
            (byte) 0x8f, (byte) 0xd5, (byte) 0x28, (byte) 0x38, (byte) 0x58, (byte) 0x30,
            (byte) 0x24, (byte) 0xf6, (byte) 0x53, (byte) 0x5b, (byte) 0x41, (byte) 0x53,
            (byte) 0xe6, (byte) 0x45, (byte) 0xbc, (byte) 0xbe, (byte) 0xe6, (byte) 0xbb,
            (byte) 0x5d, (byte) 0xd8, (byte) 0xa7, (byte) 0xf9, (byte) 0x64, (byte) 0x99,
            (byte) 0x04, (byte) 0x43, (byte) 0x75, (byte) 0xd7, (byte) 0x2d, (byte) 0x32,
            (byte) 0x0a, (byte) 0x94, (byte) 0xaf, (byte) 0x06, (byte) 0x34, (byte) 0xae,
            (byte) 0x46, (byte) 0xbd, (byte) 0xda, (byte) 0x00, (byte) 0x0e, (byte) 0x25,
            (byte) 0xc2, (byte) 0xf7, (byte) 0xc9, (byte) 0xc3, (byte) 0x65, (byte) 0xd2,
            (byte) 0x08, (byte) 0x41, (byte) 0x0a, (byte) 0xf3, (byte) 0x72
    };

    /*
     * The keys and certificates below are generated with:
     *
     * openssl req -new -x509 -days 3650 -extensions v3_ca -keyout cakey.pem -out cacert.pem
     * openssl ecparam -name prime256v1 -out ecparam.pem
     * openssl req -newkey ec:ecparam.pem -keyout userkey.pem -nodes -days 3650 -out userkey.req
     * mkdir -p demoCA/newcerts
     * touch demoCA/index.txt
     * echo "01" > demoCA/serial
     * openssl ca -out usercert.pem -in userkey.req -cert cacert.pem -keyfile cakey.pem -days 3650
     */

    /**
     * Generated from above and converted with:
     *
     * openssl x509 -outform d -in cacert.pem | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_EC_CA_1 = {
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x58, (byte) 0x30, (byte) 0x82,
            (byte) 0x01, (byte) 0xc1, (byte) 0xa0, (byte) 0x03, (byte) 0x02, (byte) 0x01,
            (byte) 0x02, (byte) 0x02, (byte) 0x09, (byte) 0x00, (byte) 0xe1, (byte) 0xb2,
            (byte) 0x8c, (byte) 0x04, (byte) 0x95, (byte) 0xeb, (byte) 0x10, (byte) 0xcb,
            (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01,
            (byte) 0x05, (byte) 0x05, (byte) 0x00, (byte) 0x30, (byte) 0x45, (byte) 0x31,
            (byte) 0x0b, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x06, (byte) 0x13, (byte) 0x02, (byte) 0x41, (byte) 0x55,
            (byte) 0x31, (byte) 0x13, (byte) 0x30, (byte) 0x11, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x04, (byte) 0x08, (byte) 0x0c, (byte) 0x0a, (byte) 0x53,
            (byte) 0x6f, (byte) 0x6d, (byte) 0x65, (byte) 0x2d, (byte) 0x53, (byte) 0x74,
            (byte) 0x61, (byte) 0x74, (byte) 0x65, (byte) 0x31, (byte) 0x21, (byte) 0x30,
            (byte) 0x1f, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x0a,
            (byte) 0x0c, (byte) 0x18, (byte) 0x49, (byte) 0x6e, (byte) 0x74, (byte) 0x65,
            (byte) 0x72, (byte) 0x6e, (byte) 0x65, (byte) 0x74, (byte) 0x20, (byte) 0x57,
            (byte) 0x69, (byte) 0x64, (byte) 0x67, (byte) 0x69, (byte) 0x74, (byte) 0x73,
            (byte) 0x20, (byte) 0x50, (byte) 0x74, (byte) 0x79, (byte) 0x20, (byte) 0x4c,
            (byte) 0x74, (byte) 0x64, (byte) 0x30, (byte) 0x1e, (byte) 0x17, (byte) 0x0d,
            (byte) 0x31, (byte) 0x33, (byte) 0x30, (byte) 0x38, (byte) 0x32, (byte) 0x37,
            (byte) 0x31, (byte) 0x36, (byte) 0x32, (byte) 0x38, (byte) 0x32, (byte) 0x38,
            (byte) 0x5a, (byte) 0x17, (byte) 0x0d, (byte) 0x32, (byte) 0x33, (byte) 0x30,
            (byte) 0x38, (byte) 0x32, (byte) 0x35, (byte) 0x31, (byte) 0x36, (byte) 0x32,
            (byte) 0x38, (byte) 0x32, (byte) 0x38, (byte) 0x5a, (byte) 0x30, (byte) 0x45,
            (byte) 0x31, (byte) 0x0b, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x04, (byte) 0x06, (byte) 0x13, (byte) 0x02, (byte) 0x41,
            (byte) 0x55, (byte) 0x31, (byte) 0x13, (byte) 0x30, (byte) 0x11, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x08, (byte) 0x0c, (byte) 0x0a,
            (byte) 0x53, (byte) 0x6f, (byte) 0x6d, (byte) 0x65, (byte) 0x2d, (byte) 0x53,
            (byte) 0x74, (byte) 0x61, (byte) 0x74, (byte) 0x65, (byte) 0x31, (byte) 0x21,
            (byte) 0x30, (byte) 0x1f, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x0a, (byte) 0x0c, (byte) 0x18, (byte) 0x49, (byte) 0x6e, (byte) 0x74,
            (byte) 0x65, (byte) 0x72, (byte) 0x6e, (byte) 0x65, (byte) 0x74, (byte) 0x20,
            (byte) 0x57, (byte) 0x69, (byte) 0x64, (byte) 0x67, (byte) 0x69, (byte) 0x74,
            (byte) 0x73, (byte) 0x20, (byte) 0x50, (byte) 0x74, (byte) 0x79, (byte) 0x20,
            (byte) 0x4c, (byte) 0x74, (byte) 0x64, (byte) 0x30, (byte) 0x81, (byte) 0x9f,
            (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01,
            (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x81, (byte) 0x8d,
            (byte) 0x00, (byte) 0x30, (byte) 0x81, (byte) 0x89, (byte) 0x02, (byte) 0x81,
            (byte) 0x81, (byte) 0x00, (byte) 0xb5, (byte) 0xf6, (byte) 0x08, (byte) 0x0f,
            (byte) 0xc4, (byte) 0x4d, (byte) 0xe4, (byte) 0x0d, (byte) 0x34, (byte) 0x1d,
            (byte) 0xe2, (byte) 0x23, (byte) 0x18, (byte) 0x63, (byte) 0x03, (byte) 0xf7,
            (byte) 0x14, (byte) 0x0e, (byte) 0x98, (byte) 0xcd, (byte) 0x45, (byte) 0x1f,
            (byte) 0xfe, (byte) 0xfb, (byte) 0x09, (byte) 0x3f, (byte) 0x5d, (byte) 0x36,
            (byte) 0x3b, (byte) 0x0f, (byte) 0xf9, (byte) 0x5e, (byte) 0x86, (byte) 0x56,
            (byte) 0x64, (byte) 0xd7, (byte) 0x3f, (byte) 0xae, (byte) 0x33, (byte) 0x09,
            (byte) 0xd3, (byte) 0xdd, (byte) 0x06, (byte) 0x17, (byte) 0x26, (byte) 0xdc,
            (byte) 0xa2, (byte) 0x8c, (byte) 0x3c, (byte) 0x65, (byte) 0xed, (byte) 0x03,
            (byte) 0x82, (byte) 0x78, (byte) 0x9b, (byte) 0xee, (byte) 0xe3, (byte) 0x98,
            (byte) 0x58, (byte) 0xe1, (byte) 0xf1, (byte) 0xa0, (byte) 0x85, (byte) 0xae,
            (byte) 0x63, (byte) 0x84, (byte) 0x41, (byte) 0x46, (byte) 0xa7, (byte) 0x4f,
            (byte) 0xdc, (byte) 0xbb, (byte) 0x1c, (byte) 0x6e, (byte) 0xec, (byte) 0x7b,
            (byte) 0xd5, (byte) 0xab, (byte) 0x3d, (byte) 0x6a, (byte) 0x05, (byte) 0x58,
            (byte) 0x0f, (byte) 0x9b, (byte) 0x6a, (byte) 0x67, (byte) 0x4b, (byte) 0xe9,
            (byte) 0x2a, (byte) 0x6d, (byte) 0x96, (byte) 0x11, (byte) 0x53, (byte) 0x95,
            (byte) 0x78, (byte) 0xaa, (byte) 0xd1, (byte) 0x91, (byte) 0x4a, (byte) 0xf8,
            (byte) 0x54, (byte) 0x52, (byte) 0x6d, (byte) 0xb9, (byte) 0xca, (byte) 0x74,
            (byte) 0x81, (byte) 0xf8, (byte) 0x99, (byte) 0x64, (byte) 0xd1, (byte) 0x4f,
            (byte) 0x01, (byte) 0x38, (byte) 0x4f, (byte) 0x08, (byte) 0x5c, (byte) 0x31,
            (byte) 0xcb, (byte) 0x7c, (byte) 0x5c, (byte) 0x78, (byte) 0x5d, (byte) 0x47,
            (byte) 0xd9, (byte) 0xf0, (byte) 0x1a, (byte) 0xeb, (byte) 0x02, (byte) 0x03,
            (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0xa3, (byte) 0x50, (byte) 0x30,
            (byte) 0x4e, (byte) 0x30, (byte) 0x1d, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x1d, (byte) 0x0e, (byte) 0x04, (byte) 0x16, (byte) 0x04, (byte) 0x14,
            (byte) 0x5f, (byte) 0x5b, (byte) 0x5e, (byte) 0xac, (byte) 0x29, (byte) 0xfa,
            (byte) 0xa1, (byte) 0x9f, (byte) 0x9e, (byte) 0xad, (byte) 0x46, (byte) 0xe1,
            (byte) 0xbc, (byte) 0x20, (byte) 0x72, (byte) 0xcf, (byte) 0x4a, (byte) 0xd4,
            (byte) 0xfa, (byte) 0xe3, (byte) 0x30, (byte) 0x1f, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x1d, (byte) 0x23, (byte) 0x04, (byte) 0x18, (byte) 0x30,
            (byte) 0x16, (byte) 0x80, (byte) 0x14, (byte) 0x5f, (byte) 0x5b, (byte) 0x5e,
            (byte) 0xac, (byte) 0x29, (byte) 0xfa, (byte) 0xa1, (byte) 0x9f, (byte) 0x9e,
            (byte) 0xad, (byte) 0x46, (byte) 0xe1, (byte) 0xbc, (byte) 0x20, (byte) 0x72,
            (byte) 0xcf, (byte) 0x4a, (byte) 0xd4, (byte) 0xfa, (byte) 0xe3, (byte) 0x30,
            (byte) 0x0c, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x13,
            (byte) 0x04, (byte) 0x05, (byte) 0x30, (byte) 0x03, (byte) 0x01, (byte) 0x01,
            (byte) 0xff, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a,
            (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01,
            (byte) 0x01, (byte) 0x05, (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x81,
            (byte) 0x81, (byte) 0x00, (byte) 0xa1, (byte) 0x4a, (byte) 0xe6, (byte) 0xfc,
            (byte) 0x7f, (byte) 0x17, (byte) 0xaa, (byte) 0x65, (byte) 0x4a, (byte) 0x34,
            (byte) 0xde, (byte) 0x69, (byte) 0x67, (byte) 0x54, (byte) 0x4d, (byte) 0xa2,
            (byte) 0xc2, (byte) 0x98, (byte) 0x02, (byte) 0x43, (byte) 0x6a, (byte) 0x0e,
            (byte) 0x0b, (byte) 0x7f, (byte) 0xa4, (byte) 0x46, (byte) 0xaf, (byte) 0xa4,
            (byte) 0x65, (byte) 0xa0, (byte) 0xdb, (byte) 0xf1, (byte) 0x5b, (byte) 0xd5,
            (byte) 0x09, (byte) 0xbc, (byte) 0xee, (byte) 0x37, (byte) 0x51, (byte) 0x19,
            (byte) 0x36, (byte) 0xc0, (byte) 0x90, (byte) 0xd3, (byte) 0x5f, (byte) 0xf3,
            (byte) 0x4f, (byte) 0xb9, (byte) 0x08, (byte) 0x45, (byte) 0x0e, (byte) 0x01,
            (byte) 0x8a, (byte) 0x95, (byte) 0xef, (byte) 0x92, (byte) 0x95, (byte) 0x33,
            (byte) 0x78, (byte) 0xdd, (byte) 0x90, (byte) 0xbb, (byte) 0xf3, (byte) 0x06,
            (byte) 0x75, (byte) 0xd0, (byte) 0x66, (byte) 0xe6, (byte) 0xd0, (byte) 0x18,
            (byte) 0x6e, (byte) 0xeb, (byte) 0x1c, (byte) 0x52, (byte) 0xc3, (byte) 0x2e,
            (byte) 0x57, (byte) 0x7d, (byte) 0xa9, (byte) 0x03, (byte) 0xdb, (byte) 0xf4,
            (byte) 0x57, (byte) 0x5f, (byte) 0x6c, (byte) 0x7e, (byte) 0x00, (byte) 0x0d,
            (byte) 0x8f, (byte) 0xe8, (byte) 0x91, (byte) 0xf7, (byte) 0xae, (byte) 0x24,
            (byte) 0x35, (byte) 0x07, (byte) 0xb5, (byte) 0x48, (byte) 0x2d, (byte) 0x36,
            (byte) 0x30, (byte) 0x5d, (byte) 0xe9, (byte) 0x49, (byte) 0x2d, (byte) 0xd1,
            (byte) 0x5d, (byte) 0xc5, (byte) 0xf4, (byte) 0x33, (byte) 0x77, (byte) 0x3c,
            (byte) 0x71, (byte) 0xad, (byte) 0x90, (byte) 0x65, (byte) 0xa9, (byte) 0xc1,
            (byte) 0x0b, (byte) 0x5c, (byte) 0x62, (byte) 0x55, (byte) 0x50, (byte) 0x6f,
            (byte) 0x9b, (byte) 0xc9, (byte) 0x0d, (byte) 0xee
    };

    /**
     * Generated from above and converted with:
     *
     * openssl pkcs8 -topk8 -outform d -in userkey.pem -nocrypt | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_EC_KEY_1 = new byte[] {
            (byte) 0x30, (byte) 0x81, (byte) 0x87, (byte) 0x02, (byte) 0x01, (byte) 0x00,
            (byte) 0x30, (byte) 0x13, (byte) 0x06, (byte) 0x07, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0xce, (byte) 0x3d, (byte) 0x02, (byte) 0x01, (byte) 0x06,
            (byte) 0x08, (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0xce, (byte) 0x3d,
            (byte) 0x03, (byte) 0x01, (byte) 0x07, (byte) 0x04, (byte) 0x6d, (byte) 0x30,
            (byte) 0x6b, (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x04, (byte) 0x20,
            (byte) 0x3a, (byte) 0x8a, (byte) 0x02, (byte) 0xdc, (byte) 0xde, (byte) 0x70,
            (byte) 0x84, (byte) 0x45, (byte) 0x34, (byte) 0xaf, (byte) 0xbd, (byte) 0xd5,
            (byte) 0x02, (byte) 0x17, (byte) 0x69, (byte) 0x90, (byte) 0x65, (byte) 0x1e,
            (byte) 0x87, (byte) 0xf1, (byte) 0x3d, (byte) 0x17, (byte) 0xb6, (byte) 0xf4,
            (byte) 0x31, (byte) 0x94, (byte) 0x86, (byte) 0x76, (byte) 0x55, (byte) 0xf7,
            (byte) 0xcc, (byte) 0xba, (byte) 0xa1, (byte) 0x44, (byte) 0x03, (byte) 0x42,
            (byte) 0x00, (byte) 0x04, (byte) 0xd9, (byte) 0xcf, (byte) 0xe7, (byte) 0x9b,
            (byte) 0x23, (byte) 0xc8, (byte) 0xa3, (byte) 0xb8, (byte) 0x33, (byte) 0x14,
            (byte) 0xa4, (byte) 0x4d, (byte) 0x75, (byte) 0x90, (byte) 0xf3, (byte) 0xcd,
            (byte) 0x43, (byte) 0xe5, (byte) 0x1b, (byte) 0x05, (byte) 0x1d, (byte) 0xf3,
            (byte) 0xd0, (byte) 0xa3, (byte) 0xb7, (byte) 0x32, (byte) 0x5f, (byte) 0x79,
            (byte) 0xdc, (byte) 0x88, (byte) 0xb8, (byte) 0x4d, (byte) 0xb3, (byte) 0xd1,
            (byte) 0x6d, (byte) 0xf7, (byte) 0x75, (byte) 0xf3, (byte) 0xbf, (byte) 0x50,
            (byte) 0xa1, (byte) 0xbc, (byte) 0x03, (byte) 0x64, (byte) 0x22, (byte) 0xe6,
            (byte) 0x1a, (byte) 0xa1, (byte) 0xe1, (byte) 0x06, (byte) 0x68, (byte) 0x3b,
            (byte) 0xbc, (byte) 0x9f, (byte) 0xd3, (byte) 0xae, (byte) 0x77, (byte) 0x5e,
            (byte) 0x88, (byte) 0x0c, (byte) 0x5e, (byte) 0x0c, (byte) 0xb2, (byte) 0x38
    };

    /**
     * Generated from above and converted with:
     *
     * openssl x509 -outform d -in usercert.pem | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_EC_USER_1 = new byte[] {
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x51, (byte) 0x30, (byte) 0x82,
            (byte) 0x01, (byte) 0xba, (byte) 0xa0, (byte) 0x03, (byte) 0x02, (byte) 0x01,
            (byte) 0x02, (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x30, (byte) 0x0d,
            (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86,
            (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x05,
            (byte) 0x00, (byte) 0x30, (byte) 0x45, (byte) 0x31, (byte) 0x0b, (byte) 0x30,
            (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x06,
            (byte) 0x13, (byte) 0x02, (byte) 0x41, (byte) 0x55, (byte) 0x31, (byte) 0x13,
            (byte) 0x30, (byte) 0x11, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x08, (byte) 0x0c, (byte) 0x0a, (byte) 0x53, (byte) 0x6f, (byte) 0x6d,
            (byte) 0x65, (byte) 0x2d, (byte) 0x53, (byte) 0x74, (byte) 0x61, (byte) 0x74,
            (byte) 0x65, (byte) 0x31, (byte) 0x21, (byte) 0x30, (byte) 0x1f, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x0a, (byte) 0x0c, (byte) 0x18,
            (byte) 0x49, (byte) 0x6e, (byte) 0x74, (byte) 0x65, (byte) 0x72, (byte) 0x6e,
            (byte) 0x65, (byte) 0x74, (byte) 0x20, (byte) 0x57, (byte) 0x69, (byte) 0x64,
            (byte) 0x67, (byte) 0x69, (byte) 0x74, (byte) 0x73, (byte) 0x20, (byte) 0x50,
            (byte) 0x74, (byte) 0x79, (byte) 0x20, (byte) 0x4c, (byte) 0x74, (byte) 0x64,
            (byte) 0x30, (byte) 0x1e, (byte) 0x17, (byte) 0x0d, (byte) 0x31, (byte) 0x33,
            (byte) 0x30, (byte) 0x38, (byte) 0x32, (byte) 0x37, (byte) 0x31, (byte) 0x36,
            (byte) 0x33, (byte) 0x30, (byte) 0x30, (byte) 0x38, (byte) 0x5a, (byte) 0x17,
            (byte) 0x0d, (byte) 0x32, (byte) 0x33, (byte) 0x30, (byte) 0x38, (byte) 0x32,
            (byte) 0x35, (byte) 0x31, (byte) 0x36, (byte) 0x33, (byte) 0x30, (byte) 0x30,
            (byte) 0x38, (byte) 0x5a, (byte) 0x30, (byte) 0x62, (byte) 0x31, (byte) 0x0b,
            (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x06, (byte) 0x13, (byte) 0x02, (byte) 0x41, (byte) 0x55, (byte) 0x31,
            (byte) 0x13, (byte) 0x30, (byte) 0x11, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x08, (byte) 0x0c, (byte) 0x0a, (byte) 0x53, (byte) 0x6f,
            (byte) 0x6d, (byte) 0x65, (byte) 0x2d, (byte) 0x53, (byte) 0x74, (byte) 0x61,
            (byte) 0x74, (byte) 0x65, (byte) 0x31, (byte) 0x21, (byte) 0x30, (byte) 0x1f,
            (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x0a, (byte) 0x0c,
            (byte) 0x18, (byte) 0x49, (byte) 0x6e, (byte) 0x74, (byte) 0x65, (byte) 0x72,
            (byte) 0x6e, (byte) 0x65, (byte) 0x74, (byte) 0x20, (byte) 0x57, (byte) 0x69,
            (byte) 0x64, (byte) 0x67, (byte) 0x69, (byte) 0x74, (byte) 0x73, (byte) 0x20,
            (byte) 0x50, (byte) 0x74, (byte) 0x79, (byte) 0x20, (byte) 0x4c, (byte) 0x74,
            (byte) 0x64, (byte) 0x31, (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x03, (byte) 0x0c, (byte) 0x12,
            (byte) 0x73, (byte) 0x65, (byte) 0x72, (byte) 0x76, (byte) 0x65, (byte) 0x72,
            (byte) 0x2e, (byte) 0x65, (byte) 0x78, (byte) 0x61, (byte) 0x6d, (byte) 0x70,
            (byte) 0x6c, (byte) 0x65, (byte) 0x2e, (byte) 0x63, (byte) 0x6f, (byte) 0x6d,
            (byte) 0x30, (byte) 0x59, (byte) 0x30, (byte) 0x13, (byte) 0x06, (byte) 0x07,
            (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0xce, (byte) 0x3d, (byte) 0x02,
            (byte) 0x01, (byte) 0x06, (byte) 0x08, (byte) 0x2a, (byte) 0x86, (byte) 0x48,
            (byte) 0xce, (byte) 0x3d, (byte) 0x03, (byte) 0x01, (byte) 0x07, (byte) 0x03,
            (byte) 0x42, (byte) 0x00, (byte) 0x04, (byte) 0xd9, (byte) 0xcf, (byte) 0xe7,
            (byte) 0x9b, (byte) 0x23, (byte) 0xc8, (byte) 0xa3, (byte) 0xb8, (byte) 0x33,
            (byte) 0x14, (byte) 0xa4, (byte) 0x4d, (byte) 0x75, (byte) 0x90, (byte) 0xf3,
            (byte) 0xcd, (byte) 0x43, (byte) 0xe5, (byte) 0x1b, (byte) 0x05, (byte) 0x1d,
            (byte) 0xf3, (byte) 0xd0, (byte) 0xa3, (byte) 0xb7, (byte) 0x32, (byte) 0x5f,
            (byte) 0x79, (byte) 0xdc, (byte) 0x88, (byte) 0xb8, (byte) 0x4d, (byte) 0xb3,
            (byte) 0xd1, (byte) 0x6d, (byte) 0xf7, (byte) 0x75, (byte) 0xf3, (byte) 0xbf,
            (byte) 0x50, (byte) 0xa1, (byte) 0xbc, (byte) 0x03, (byte) 0x64, (byte) 0x22,
            (byte) 0xe6, (byte) 0x1a, (byte) 0xa1, (byte) 0xe1, (byte) 0x06, (byte) 0x68,
            (byte) 0x3b, (byte) 0xbc, (byte) 0x9f, (byte) 0xd3, (byte) 0xae, (byte) 0x77,
            (byte) 0x5e, (byte) 0x88, (byte) 0x0c, (byte) 0x5e, (byte) 0x0c, (byte) 0xb2,
            (byte) 0x38, (byte) 0xa3, (byte) 0x7b, (byte) 0x30, (byte) 0x79, (byte) 0x30,
            (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x13,
            (byte) 0x04, (byte) 0x02, (byte) 0x30, (byte) 0x00, (byte) 0x30, (byte) 0x2c,
            (byte) 0x06, (byte) 0x09, (byte) 0x60, (byte) 0x86, (byte) 0x48, (byte) 0x01,
            (byte) 0x86, (byte) 0xf8, (byte) 0x42, (byte) 0x01, (byte) 0x0d, (byte) 0x04,
            (byte) 0x1f, (byte) 0x16, (byte) 0x1d, (byte) 0x4f, (byte) 0x70, (byte) 0x65,
            (byte) 0x6e, (byte) 0x53, (byte) 0x53, (byte) 0x4c, (byte) 0x20, (byte) 0x47,
            (byte) 0x65, (byte) 0x6e, (byte) 0x65, (byte) 0x72, (byte) 0x61, (byte) 0x74,
            (byte) 0x65, (byte) 0x64, (byte) 0x20, (byte) 0x43, (byte) 0x65, (byte) 0x72,
            (byte) 0x74, (byte) 0x69, (byte) 0x66, (byte) 0x69, (byte) 0x63, (byte) 0x61,
            (byte) 0x74, (byte) 0x65, (byte) 0x30, (byte) 0x1d, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x1d, (byte) 0x0e, (byte) 0x04, (byte) 0x16, (byte) 0x04,
            (byte) 0x14, (byte) 0xd5, (byte) 0xc4, (byte) 0x72, (byte) 0xbd, (byte) 0xd2,
            (byte) 0x4e, (byte) 0x90, (byte) 0x1b, (byte) 0x14, (byte) 0x32, (byte) 0xdb,
            (byte) 0x03, (byte) 0xae, (byte) 0xfa, (byte) 0x27, (byte) 0x7d, (byte) 0x8d,
            (byte) 0xe4, (byte) 0x80, (byte) 0x58, (byte) 0x30, (byte) 0x1f, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x23, (byte) 0x04, (byte) 0x18,
            (byte) 0x30, (byte) 0x16, (byte) 0x80, (byte) 0x14, (byte) 0x5f, (byte) 0x5b,
            (byte) 0x5e, (byte) 0xac, (byte) 0x29, (byte) 0xfa, (byte) 0xa1, (byte) 0x9f,
            (byte) 0x9e, (byte) 0xad, (byte) 0x46, (byte) 0xe1, (byte) 0xbc, (byte) 0x20,
            (byte) 0x72, (byte) 0xcf, (byte) 0x4a, (byte) 0xd4, (byte) 0xfa, (byte) 0xe3,
            (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01,
            (byte) 0x05, (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x81, (byte) 0x81,
            (byte) 0x00, (byte) 0x43, (byte) 0x99, (byte) 0x9f, (byte) 0x67, (byte) 0x08,
            (byte) 0x43, (byte) 0xd5, (byte) 0x6b, (byte) 0x6f, (byte) 0xd7, (byte) 0x05,
            (byte) 0xd6, (byte) 0x75, (byte) 0x34, (byte) 0x30, (byte) 0xca, (byte) 0x20,
            (byte) 0x47, (byte) 0x61, (byte) 0xa1, (byte) 0x89, (byte) 0xb6, (byte) 0xf1,
            (byte) 0x49, (byte) 0x7b, (byte) 0xd9, (byte) 0xb9, (byte) 0xe8, (byte) 0x1e,
            (byte) 0x29, (byte) 0x74, (byte) 0x0a, (byte) 0x67, (byte) 0xc0, (byte) 0x7d,
            (byte) 0xb8, (byte) 0xe6, (byte) 0x39, (byte) 0xa8, (byte) 0x5e, (byte) 0xc3,
            (byte) 0xb0, (byte) 0xa1, (byte) 0x30, (byte) 0x6a, (byte) 0x1f, (byte) 0x1d,
            (byte) 0xfc, (byte) 0x11, (byte) 0x59, (byte) 0x0b, (byte) 0xb9, (byte) 0xad,
            (byte) 0x3a, (byte) 0x4e, (byte) 0x50, (byte) 0x0a, (byte) 0x61, (byte) 0xdb,
            (byte) 0x75, (byte) 0x6b, (byte) 0xe5, (byte) 0x3f, (byte) 0x8d, (byte) 0xde,
            (byte) 0x28, (byte) 0x68, (byte) 0xb1, (byte) 0x29, (byte) 0x9a, (byte) 0x18,
            (byte) 0x8a, (byte) 0xfc, (byte) 0x3f, (byte) 0x13, (byte) 0x93, (byte) 0x29,
            (byte) 0xed, (byte) 0x22, (byte) 0x7c, (byte) 0xb4, (byte) 0x50, (byte) 0xd5,
            (byte) 0x4d, (byte) 0x32, (byte) 0x4d, (byte) 0x42, (byte) 0x2b, (byte) 0x29,
            (byte) 0x97, (byte) 0x86, (byte) 0xc0, (byte) 0x01, (byte) 0x00, (byte) 0x25,
            (byte) 0xf6, (byte) 0xd3, (byte) 0x2a, (byte) 0xd8, (byte) 0xda, (byte) 0x13,
            (byte) 0x94, (byte) 0x12, (byte) 0x78, (byte) 0x14, (byte) 0x0b, (byte) 0x51,
            (byte) 0xc0, (byte) 0x45, (byte) 0xb4, (byte) 0x02, (byte) 0x37, (byte) 0x98,
            (byte) 0x42, (byte) 0x3c, (byte) 0xcb, (byte) 0x2e, (byte) 0xe4, (byte) 0x38,
            (byte) 0x69, (byte) 0x1b, (byte) 0x72, (byte) 0xf0, (byte) 0xaa, (byte) 0x89,
            (byte) 0x7e, (byte) 0xde, (byte) 0xb2
    };

    /**
     * The amount of time to allow before and after expected time for variance
     * in timing tests.
     */
    private static final long SLOP_TIME_MILLIS = 15000L;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Wipe any existing entries in the KeyStore
        KeyStore ksTemp = KeyStore.getInstance("AndroidKeyStore");
        ksTemp.load(null, null);
        Enumeration<String> aliases = ksTemp.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            ksTemp.deleteEntry(alias);
        }

        // Get a new instance because some tests need it uninitialized
        mKeyStore = KeyStore.getInstance("AndroidKeyStore");

        // Use a longer timeout on watches, which are generally less performant.
        mMaxTestDurationMillis =
                getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                        ? LARGE_NUMBER_OF_KEYS_TEST_MAX_DURATION_WATCH_MILLIS
                        : LARGE_NUMBER_OF_KEYS_TEST_MAX_DURATION_MILLIS;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null, null);
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                keyStore.deleteEntry(alias);
            }
        } finally {
            super.tearDown();
        }
    }

    private PrivateKey generatePrivateKey(String keyType, byte[] fakeKey1) throws Exception {
        KeyFactory kf = KeyFactory.getInstance(keyType);
        return kf.generatePrivate(new PKCS8EncodedKeySpec(fakeKey1));
    }

    private Certificate generateCertificate(byte[] fakeUser1) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(new ByteArrayInputStream(fakeUser1));
    }

    private PrivateKeyEntry makeUserEcKey1() throws Exception {
        return new KeyStore.PrivateKeyEntry(generatePrivateKey("EC", FAKE_EC_KEY_1),
                new Certificate[] {
                        generateCertificate(FAKE_EC_USER_1), generateCertificate(FAKE_EC_CA_1)
                });
    }

    private PrivateKeyEntry makeUserRsaKey1() throws Exception {
        return new KeyStore.PrivateKeyEntry(generatePrivateKey("RSA", FAKE_RSA_KEY_1),
                new Certificate[] {
                        generateCertificate(FAKE_RSA_USER_1), generateCertificate(FAKE_RSA_CA_1)
                });
    }

    private Entry makeCa1() throws Exception {
        return new KeyStore.TrustedCertificateEntry(generateCertificate(FAKE_RSA_CA_1));
    }

    private void assertAliases(final String[] expectedAliases) throws KeyStoreException {
        final Enumeration<String> aliases = mKeyStore.aliases();
        int count = 0;

        final Set<String> expectedSet = new HashSet<String>();
        expectedSet.addAll(Arrays.asList(expectedAliases));

        while (aliases.hasMoreElements()) {
            count++;
            final String alias = aliases.nextElement();
            assertTrue("The alias should be in the expected set", expectedSet.contains(alias));
            expectedSet.remove(alias);
        }
        assertTrue("The expected set and actual set should be exactly equal", expectedSet.isEmpty());
        assertEquals("There should be the correct number of keystore entries",
                expectedAliases.length, count);
    }

    public void testKeyStore_Aliases_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        assertAliases(new String[] {});

        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        assertAliases(new String[] { TEST_ALIAS_1 });

        mKeyStore.setEntry(TEST_ALIAS_2, makeCa1(), null);

        assertAliases(new String[] { TEST_ALIAS_1, TEST_ALIAS_2 });
    }

    public void testKeyStore_Aliases_NotInitialized_Unencrypted_Failure() throws Exception {
        try {
            mKeyStore.aliases();
            fail("KeyStore should throw exception when not initialized");
        } catch (KeyStoreException success) {
        }
    }

    public void testKeyStore_ContainsAliases_PrivateAndCA_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        assertAliases(new String[] {});

        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        assertTrue("Should contain generated private key", mKeyStore.containsAlias(TEST_ALIAS_1));

        mKeyStore.setEntry(TEST_ALIAS_2, makeCa1(), null);

        assertTrue("Should contain added CA certificate", mKeyStore.containsAlias(TEST_ALIAS_2));

        assertFalse("Should not contain unadded certificate alias",
                mKeyStore.containsAlias(TEST_ALIAS_3));
    }

    public void testKeyStore_ContainsAliases_CAOnly_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        mKeyStore.setEntry(TEST_ALIAS_2, makeCa1(), null);

        assertTrue("Should contain added CA certificate", mKeyStore.containsAlias(TEST_ALIAS_2));
    }

    public void testKeyStore_ContainsAliases_NonExistent_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        assertFalse("Should contain added CA certificate", mKeyStore.containsAlias(TEST_ALIAS_1));
    }

    public void testKeyStore_DeleteEntry_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        // TEST_ALIAS_2
        mKeyStore.setCertificateEntry(TEST_ALIAS_2, generateCertificate(FAKE_RSA_CA_1));

        // TEST_ALIAS_3
        mKeyStore.setCertificateEntry(TEST_ALIAS_3, generateCertificate(FAKE_RSA_CA_1));

        assertAliases(new String[] { TEST_ALIAS_1, TEST_ALIAS_2, TEST_ALIAS_3 });

        mKeyStore.deleteEntry(TEST_ALIAS_1);

        assertAliases(new String[] { TEST_ALIAS_2, TEST_ALIAS_3 });

        mKeyStore.deleteEntry(TEST_ALIAS_3);

        assertAliases(new String[] { TEST_ALIAS_2 });

        mKeyStore.deleteEntry(TEST_ALIAS_2);

        assertAliases(new String[] { });
    }

    public void testKeyStore_DeleteEntry_EmptyStore_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // Should not throw when a non-existent entry is requested for delete.
        mKeyStore.deleteEntry(TEST_ALIAS_1);
    }

    public void testKeyStore_DeleteEntry_NonExistent_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        // Should not throw when a non-existent entry is requested for delete.
        mKeyStore.deleteEntry(TEST_ALIAS_2);
    }

    public void testKeyStore_GetCertificate_Single_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        mKeyStore.setCertificateEntry(TEST_ALIAS_1, generateCertificate(FAKE_RSA_CA_1));

        assertAliases(new String[] { TEST_ALIAS_1 });

        assertNull("Certificate should not exist in keystore",
                mKeyStore.getCertificate(TEST_ALIAS_2));

        Certificate retrieved = mKeyStore.getCertificate(TEST_ALIAS_1);

        assertNotNull("Retrieved certificate should not be null", retrieved);

        CertificateFactory f = CertificateFactory.getInstance("X.509");
        Certificate actual = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        assertEquals("Actual and retrieved certificates should be the same", actual, retrieved);
    }

    public void testKeyStore_GetCertificate_NonExist_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        assertNull("Certificate should not exist in keystore",
                mKeyStore.getCertificate(TEST_ALIAS_1));
    }

    public void testKeyStore_GetCertificateAlias_CAEntry_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        Certificate cert = generateCertificate(FAKE_RSA_CA_1);
        mKeyStore.setCertificateEntry(TEST_ALIAS_1, cert);

        assertEquals("Stored certificate alias should be found", TEST_ALIAS_1,
                mKeyStore.getCertificateAlias(cert));
    }

    public void testKeyStore_GetCertificateAlias_PrivateKeyEntry_Unencrypted_Success()
            throws Exception {
        mKeyStore.load(null, null);

        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        CertificateFactory f = CertificateFactory.getInstance("X.509");
        Certificate actual = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));

        assertEquals("Stored certificate alias should be found", TEST_ALIAS_1,
                mKeyStore.getCertificateAlias(actual));
    }

    public void testKeyStore_GetCertificateAlias_CAEntry_WithPrivateKeyUsingCA_Unencrypted_Success()
            throws Exception {
        mKeyStore.load(null, null);

        Certificate actual = generateCertificate(FAKE_RSA_CA_1);

        // Insert TrustedCertificateEntry with CA name
        mKeyStore.setCertificateEntry(TEST_ALIAS_2, actual);

        // Insert PrivateKeyEntry that uses the same CA
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        assertEquals("Stored certificate alias should be found", TEST_ALIAS_2,
                mKeyStore.getCertificateAlias(actual));
    }

    public void testKeyStore_GetCertificateAlias_NonExist_Empty_Unencrypted_Failure()
            throws Exception {
        mKeyStore.load(null, null);

        CertificateFactory f = CertificateFactory.getInstance("X.509");
        Certificate actual = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        assertNull("Stored certificate alias should not be found",
                mKeyStore.getCertificateAlias(actual));
    }

    public void testKeyStore_GetCertificateAlias_NonExist_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        Certificate ca = generateCertificate(FAKE_RSA_CA_1);

        // Insert TrustedCertificateEntry with CA name
        mKeyStore.setCertificateEntry(TEST_ALIAS_1, ca);

        Certificate userCert = generateCertificate(FAKE_RSA_USER_1);

        assertNull("Stored certificate alias should be found",
                mKeyStore.getCertificateAlias(userCert));
    }

    public void testKeyStore_GetCertificateChain_SingleLength_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        Certificate[] expected = new Certificate[2];
        expected[0] = generateCertificate(FAKE_RSA_USER_1);
        expected[1] = generateCertificate(FAKE_RSA_CA_1);

        Certificate[] actual = mKeyStore.getCertificateChain(TEST_ALIAS_1);

        assertNotNull("Returned certificate chain should not be null", actual);
        assertEquals("Returned certificate chain should be correct size", expected.length,
                actual.length);
        assertEquals("First certificate should be user certificate", expected[0], actual[0]);
        assertEquals("Second certificate should be CA certificate", expected[1], actual[1]);

        // Negative test when keystore is populated.
        assertNull("Stored certificate alias should not be found",
                mKeyStore.getCertificateChain(TEST_ALIAS_2));
    }

    public void testKeyStore_GetCertificateChain_NonExist_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        assertNull("Stored certificate alias should not be found",
                mKeyStore.getCertificateChain(TEST_ALIAS_1));
    }

    public void testKeyStore_GetCreationDate_PrivateKeyEntry_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        Date now = new Date();
        Date actual = mKeyStore.getCreationDate(TEST_ALIAS_1);

        Date expectedAfter = new Date(now.getTime() - SLOP_TIME_MILLIS);
        Date expectedBefore = new Date(now.getTime() + SLOP_TIME_MILLIS);

        assertTrue("Time should be close to current time", actual.before(expectedBefore));
        assertTrue("Time should be close to current time", actual.after(expectedAfter));
    }

    public void testKeyStore_GetCreationDate_CAEntry_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // Insert TrustedCertificateEntry with CA name
        mKeyStore.setCertificateEntry(TEST_ALIAS_1, generateCertificate(FAKE_RSA_CA_1));

        Date now = new Date();
        Date actual = mKeyStore.getCreationDate(TEST_ALIAS_1);
        assertNotNull("Certificate should be found", actual);

        Date expectedAfter = new Date(now.getTime() - SLOP_TIME_MILLIS);
        Date expectedBefore = new Date(now.getTime() + SLOP_TIME_MILLIS);

        assertTrue("Time should be close to current time", actual.before(expectedBefore));
        assertTrue("Time should be close to current time", actual.after(expectedAfter));
    }

    public void testKeyStore_GetEntry_NullParams_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        Entry entry = mKeyStore.getEntry(TEST_ALIAS_1, null);
        assertNotNull("Entry should exist", entry);

        assertTrue("Should be a PrivateKeyEntry", entry instanceof PrivateKeyEntry);

        PrivateKeyEntry keyEntry = (PrivateKeyEntry) entry;

        assertPrivateKeyEntryEquals(keyEntry, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1, FAKE_RSA_CA_1);
    }

    public void testKeyStore_GetEntry_EC_NullParams_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserEcKey1(), null);

        Entry entry = mKeyStore.getEntry(TEST_ALIAS_1, null);
        assertNotNull("Entry should exist", entry);

        assertTrue("Should be a PrivateKeyEntry", entry instanceof PrivateKeyEntry);

        PrivateKeyEntry keyEntry = (PrivateKeyEntry) entry;

        assertPrivateKeyEntryEquals(keyEntry, "EC", FAKE_EC_KEY_1, FAKE_EC_USER_1, FAKE_EC_CA_1);
    }

    public void testKeyStore_GetEntry_RSA_NullParams_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        Entry entry = mKeyStore.getEntry(TEST_ALIAS_1, null);
        assertNotNull("Entry should exist", entry);

        assertTrue("Should be a PrivateKeyEntry", entry instanceof PrivateKeyEntry);

        PrivateKeyEntry keyEntry = (PrivateKeyEntry) entry;

        assertPrivateKeyEntryEquals(keyEntry, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                FAKE_RSA_CA_1);
    }

    @SuppressWarnings("unchecked")
    private void assertPrivateKeyEntryEquals(PrivateKeyEntry keyEntry, String keyType, byte[] key,
            byte[] cert, byte[] ca) throws Exception {
        KeyFactory keyFact = KeyFactory.getInstance(keyType);
        PrivateKey expectedKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(key));

        CertificateFactory certFact = CertificateFactory.getInstance("X.509");
        Certificate expectedCert = certFact.generateCertificate(new ByteArrayInputStream(cert));

        final Collection<Certificate> expectedChain;
        if (ca != null) {
            expectedChain = (Collection<Certificate>) certFact
                    .generateCertificates(new ByteArrayInputStream(ca));
        } else {
            expectedChain = null;
        }

        assertPrivateKeyEntryEquals(keyEntry, expectedKey, expectedCert, expectedChain);
    }

    private void assertPrivateKeyEntryEquals(PrivateKeyEntry keyEntry, PrivateKey expectedKey,
            Certificate expectedCert, Collection<Certificate> expectedChain) throws Exception {
        final PrivateKey privKey = keyEntry.getPrivateKey();
        final PublicKey pubKey = keyEntry.getCertificate().getPublicKey();

        if (expectedKey instanceof ECKey) {
            assertTrue("Returned PrivateKey " + privKey.getClass() + " should be instanceof ECKey",
                    privKey instanceof ECKey);
            assertEquals("Returned PrivateKey should be what we inserted",
                    ((ECKey) expectedKey).getParams().getCurve(),
                    ((ECKey) privKey).getParams().getCurve());
        } else if (expectedKey instanceof RSAKey) {
            assertTrue("Returned PrivateKey " + privKey.getClass() + " should be instanceof RSAKey",
                    privKey instanceof RSAKey);
            assertEquals("Returned PrivateKey should be what we inserted",
                    ((RSAKey) expectedKey).getModulus(),
                    ((RSAKey) privKey).getModulus());
        }

        assertNull("getFormat() should return null", privKey.getFormat());
        assertNull("getEncoded() should return null", privKey.getEncoded());

        assertEquals("Public keys should be in X.509 format", "X.509", pubKey.getFormat());
        assertNotNull("Public keys should be encodable", pubKey.getEncoded());

        assertEquals("Returned Certificate should be what we inserted", expectedCert,
                keyEntry.getCertificate());

        Certificate[] actualChain = keyEntry.getCertificateChain();

        assertEquals("First certificate in chain should be user cert", expectedCert, actualChain[0]);

        if (expectedChain == null) {
            assertEquals("Certificate chain should not include CAs", 1, actualChain.length);
        } else {
            assertEquals("Chains should be the same size", expectedChain.size() + 1,
                    actualChain.length);
            int i = 1;
            final Iterator<Certificate> it = expectedChain.iterator();
            while (it.hasNext() && i < actualChain.length) {
                assertEquals("CA chain certificate should equal what we put in", it.next(),
                        actualChain[i++]);
            }
        }
    }

    public void testKeyStore_GetEntry_Nonexistent_NullParams_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        assertNull("A non-existent entry should return null",
                mKeyStore.getEntry(TEST_ALIAS_1, null));
    }

    public void testKeyStore_GetKey_NoPassword_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        Key key = mKeyStore.getKey(TEST_ALIAS_1, null);
        assertNotNull("Key should exist", key);

        assertTrue("Should be a PrivateKey", key instanceof PrivateKey);
        assertTrue("Should be a RSAKey", key instanceof RSAKey);

        RSAKey actualKey = (RSAKey) key;

        KeyFactory keyFact = KeyFactory.getInstance("RSA");
        PrivateKey expectedKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));

        assertEquals("Inserted key should be same as retrieved key",
                ((RSAKey) expectedKey).getModulus(), actualKey.getModulus());
    }

    public void testKeyStore_GetKey_Certificate_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        // Insert TrustedCertificateEntry with CA name
        mKeyStore.setCertificateEntry(TEST_ALIAS_1, generateCertificate(FAKE_RSA_CA_1));

        assertNull("Certificate entries should return null", mKeyStore.getKey(TEST_ALIAS_1, null));
    }

    public void testKeyStore_GetKey_NonExistent_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        assertNull("A non-existent entry should return null", mKeyStore.getKey(TEST_ALIAS_1, null));
    }

    public void testKeyStore_GetProvider_Unencrypted_Success() throws Exception {
        assertEquals("AndroidKeyStore", mKeyStore.getProvider().getName());
    }

    public void testKeyStore_GetType_Unencrypted_Success() throws Exception {
        assertEquals("AndroidKeyStore", mKeyStore.getType());
    }

    public void testKeyStore_IsCertificateEntry_CA_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // Insert TrustedCertificateEntry with CA name
        mKeyStore.setCertificateEntry(TEST_ALIAS_1, generateCertificate(FAKE_RSA_CA_1));

        assertTrue("Should return true for CA certificate",
                mKeyStore.isCertificateEntry(TEST_ALIAS_1));
    }

    public void testKeyStore_IsCertificateEntry_PrivateKey_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        assertFalse("Should return false for PrivateKeyEntry",
                mKeyStore.isCertificateEntry(TEST_ALIAS_1));
    }

    public void testKeyStore_IsCertificateEntry_NonExist_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        assertFalse("Should return false for non-existent entry",
                mKeyStore.isCertificateEntry(TEST_ALIAS_1));
    }

    public void testKeyStore_IsKeyEntry_PrivateKey_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // TEST_ALIAS_1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        assertTrue("Should return true for PrivateKeyEntry", mKeyStore.isKeyEntry(TEST_ALIAS_1));
    }

    public void testKeyStore_IsKeyEntry_CA_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        mKeyStore.setCertificateEntry(TEST_ALIAS_1, generateCertificate(FAKE_RSA_CA_1));

        assertFalse("Should return false for CA certificate", mKeyStore.isKeyEntry(TEST_ALIAS_1));
    }

    public void testKeyStore_IsKeyEntry_NonExist_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        assertFalse("Should return false for non-existent entry",
                mKeyStore.isKeyEntry(TEST_ALIAS_1));
    }

    public void testKeyStore_SetCertificate_CA_Unencrypted_Success() throws Exception {
        final Certificate actual = generateCertificate(FAKE_RSA_CA_1);

        mKeyStore.load(null, null);

        mKeyStore.setCertificateEntry(TEST_ALIAS_1, actual);
        assertAliases(new String[] { TEST_ALIAS_1 });

        Certificate retrieved = mKeyStore.getCertificate(TEST_ALIAS_1);

        assertEquals("Retrieved certificate should be the same as the one inserted", actual,
                retrieved);
    }

    public void testKeyStore_SetCertificate_CAExists_Overwrite_Unencrypted_Success()
            throws Exception {
        mKeyStore.load(null, null);

        mKeyStore.setCertificateEntry(TEST_ALIAS_1, generateCertificate(FAKE_RSA_CA_1));

        assertAliases(new String[] { TEST_ALIAS_1 });

        final Certificate cert = generateCertificate(FAKE_RSA_CA_1);

        // TODO have separate FAKE_CA for second test
        mKeyStore.setCertificateEntry(TEST_ALIAS_1, cert);

        assertAliases(new String[] { TEST_ALIAS_1 });
    }

    public void testKeyStore_SetCertificate_PrivateKeyExists_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        assertAliases(new String[] { TEST_ALIAS_1 });

        final Certificate cert = generateCertificate(FAKE_RSA_CA_1);

        try {
            mKeyStore.setCertificateEntry(TEST_ALIAS_1, cert);
            fail("Should throw when trying to overwrite a PrivateKey entry with a Certificate");
        } catch (KeyStoreException success) {
        }
    }

    public void testKeyStore_SetEntry_PrivateKeyEntry_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        KeyFactory keyFact = KeyFactory.getInstance("RSA");
        PrivateKey expectedKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        final Certificate[] expectedChain = new Certificate[2];
        expectedChain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
        expectedChain[1] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        PrivateKeyEntry expected = new PrivateKeyEntry(expectedKey, expectedChain);

        mKeyStore.setEntry(TEST_ALIAS_1, expected, null);

        Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
        assertNotNull("Retrieved entry should exist", actualEntry);

        assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                actualEntry instanceof PrivateKeyEntry);

        PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;

        assertPrivateKeyEntryEquals(actual, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1, FAKE_RSA_CA_1);
    }

    public void testKeyStore_SetEntry_PrivateKeyEntry_Params_Unencrypted_Failure() throws Exception {
        // The Android Keystore requires encrypted storage which is only decryptable with a key
        // bound to a credential provided by the user. By default, the Keystore waits for the user
        // to set a lock screen PIN or password and uses this credential to set up an encrypted
        // storage space itself. In that implementation, the Keystore should not be initialized when
        // no lock screen PIN or password has been set. This is what the test verifies.
        //
        // If your environment already provides encrypted storage which is only decryptable with a
        // key bound to another credential provided by the user, you may initialize the Keystore
        // immediately and get a waiver for this test.
        KeyguardManager keyguardManager =
                (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
        assertNotNull(keyguardManager);
        assertFalse("Secure lock screen must not be configured", keyguardManager.isDeviceSecure());

        mKeyStore.load(null, null);

        KeyFactory keyFact = KeyFactory.getInstance("RSA");
        PrivateKey expectedKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        final Certificate[] expectedChain = new Certificate[2];
        expectedChain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
        expectedChain[1] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        PrivateKeyEntry entry = new PrivateKeyEntry(expectedKey, expectedChain);

        try {
            mKeyStore.setEntry(TEST_ALIAS_1, entry,
                    new KeyStoreParameter.Builder(getContext())
                    .setEncryptionRequired(true)
                    .build());
            fail("Shouldn't be able to insert encrypted entry when KeyStore uninitialized");
        } catch (KeyStoreException expected) {
        }

        assertNull(mKeyStore.getEntry(TEST_ALIAS_1, null));
    }

    public void testKeyStore_SetEntry_PrivateKeyEntry_Overwrites_PrivateKeyEntry_Unencrypted_Success()
            throws Exception {
        mKeyStore.load(null, null);

        final KeyFactory keyFact = KeyFactory.getInstance("RSA");
        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        // Start with PrivateKeyEntry
        {
            PrivateKey expectedKey = keyFact
                    .generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));

            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
            expectedChain[1] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

            PrivateKeyEntry expected = new PrivateKeyEntry(expectedKey, expectedChain);

            mKeyStore.setEntry(TEST_ALIAS_1, expected, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);

            assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;

            assertPrivateKeyEntryEquals(actual, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                    FAKE_RSA_CA_1);
        }

        // TODO make entirely new test vector for the overwrite
        // Replace with PrivateKeyEntry
        {
            PrivateKey expectedKey = keyFact
                    .generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));

            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
            expectedChain[1] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

            PrivateKeyEntry expected = new PrivateKeyEntry(expectedKey, expectedChain);

            mKeyStore.setEntry(TEST_ALIAS_1, expected, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);

            assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;

            assertPrivateKeyEntryEquals(actual, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                    FAKE_RSA_CA_1);
        }
    }

    public void testKeyStore_SetEntry_CAEntry_Overwrites_PrivateKeyEntry_Unencrypted_Success()
            throws Exception {
        mKeyStore.load(null, null);

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        // Start with TrustedCertificateEntry
        {
            final Certificate caCert = f
                    .generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

            TrustedCertificateEntry expectedCertEntry = new TrustedCertificateEntry(caCert);
            mKeyStore.setEntry(TEST_ALIAS_1, expectedCertEntry, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue("Retrieved entry should be of type TrustedCertificateEntry",
                    actualEntry instanceof TrustedCertificateEntry);
            TrustedCertificateEntry actualCertEntry = (TrustedCertificateEntry) actualEntry;
            assertEquals("Stored and retrieved certificates should be the same",
                    expectedCertEntry.getTrustedCertificate(),
                    actualCertEntry.getTrustedCertificate());
        }

        // Replace with PrivateKeyEntry
        {
            KeyFactory keyFact = KeyFactory.getInstance("RSA");
            PrivateKey expectedKey = keyFact
                    .generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
            expectedChain[1] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

            PrivateKeyEntry expectedPrivEntry = new PrivateKeyEntry(expectedKey, expectedChain);

            mKeyStore.setEntry(TEST_ALIAS_1, expectedPrivEntry, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actualPrivEntry = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(actualPrivEntry, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                    FAKE_RSA_CA_1);
        }
    }

    public void testKeyStore_SetEntry_PrivateKeyEntry_Overwrites_CAEntry_Unencrypted_Success()
            throws Exception {
        mKeyStore.load(null, null);

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        final Certificate caCert = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        // Start with PrivateKeyEntry
        {
            KeyFactory keyFact = KeyFactory.getInstance("RSA");
            PrivateKey expectedKey = keyFact
                    .generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
            expectedChain[1] = caCert;

            PrivateKeyEntry expectedPrivEntry = new PrivateKeyEntry(expectedKey, expectedChain);

            mKeyStore.setEntry(TEST_ALIAS_1, expectedPrivEntry, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actualPrivEntry = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(actualPrivEntry, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                    FAKE_RSA_CA_1);
        }

        // Replace with TrustedCertificateEntry
        {
            TrustedCertificateEntry expectedCertEntry = new TrustedCertificateEntry(caCert);
            mKeyStore.setEntry(TEST_ALIAS_1, expectedCertEntry, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue("Retrieved entry should be of type TrustedCertificateEntry",
                    actualEntry instanceof TrustedCertificateEntry);
            TrustedCertificateEntry actualCertEntry = (TrustedCertificateEntry) actualEntry;
            assertEquals("Stored and retrieved certificates should be the same",
                    expectedCertEntry.getTrustedCertificate(),
                    actualCertEntry.getTrustedCertificate());
        }
    }

    public void testKeyStore_SetEntry_PrivateKeyEntry_Overwrites_ShortPrivateKeyEntry_Unencrypted_Success()
            throws Exception {
        mKeyStore.load(null, null);

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        final Certificate caCert = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        // Start with PrivateKeyEntry
        {
            KeyFactory keyFact = KeyFactory.getInstance("RSA");
            PrivateKey expectedKey = keyFact
                    .generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
            expectedChain[1] = caCert;

            PrivateKeyEntry expectedPrivEntry = new PrivateKeyEntry(expectedKey, expectedChain);

            mKeyStore.setEntry(TEST_ALIAS_1, expectedPrivEntry, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actualPrivEntry = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(actualPrivEntry, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                    FAKE_RSA_CA_1);
        }

        // Replace with PrivateKeyEntry that has no chain
        {
            KeyFactory keyFact = KeyFactory.getInstance("RSA");
            PrivateKey expectedKey = keyFact
                    .generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
            final Certificate[] expectedChain = new Certificate[1];
            expectedChain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));

            PrivateKeyEntry expectedPrivEntry = new PrivateKeyEntry(expectedKey, expectedChain);

            mKeyStore.setEntry(TEST_ALIAS_1, expectedPrivEntry, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actualPrivEntry = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(actualPrivEntry, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                    null);
        }
    }

    public void testKeyStore_SetEntry_CAEntry_Overwrites_CAEntry_Unencrypted_Success()
            throws Exception {
        mKeyStore.load(null, null);

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        // Insert TrustedCertificateEntry
        {
            final Certificate caCert = f
                    .generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

            TrustedCertificateEntry expectedCertEntry = new TrustedCertificateEntry(caCert);
            mKeyStore.setEntry(TEST_ALIAS_1, expectedCertEntry, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue("Retrieved entry should be of type TrustedCertificateEntry",
                    actualEntry instanceof TrustedCertificateEntry);
            TrustedCertificateEntry actualCertEntry = (TrustedCertificateEntry) actualEntry;
            assertEquals("Stored and retrieved certificates should be the same",
                    expectedCertEntry.getTrustedCertificate(),
                    actualCertEntry.getTrustedCertificate());
        }

        // Replace with TrustedCertificateEntry of USER
        {
            final Certificate userCert = f.generateCertificate(new ByteArrayInputStream(
                    FAKE_RSA_USER_1));

            TrustedCertificateEntry expectedUserEntry = new TrustedCertificateEntry(userCert);
            mKeyStore.setEntry(TEST_ALIAS_1, expectedUserEntry, null);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue("Retrieved entry should be of type TrustedCertificateEntry",
                    actualEntry instanceof TrustedCertificateEntry);
            TrustedCertificateEntry actualUserEntry = (TrustedCertificateEntry) actualEntry;
            assertEquals("Stored and retrieved certificates should be the same",
                    expectedUserEntry.getTrustedCertificate(),
                    actualUserEntry.getTrustedCertificate());
        }
    }

    public void testKeyStore_SetKeyEntry_ProtectedKey_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        final Certificate caCert = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        KeyFactory keyFact = KeyFactory.getInstance("RSA");
        PrivateKey privKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
        final Certificate[] chain = new Certificate[2];
        chain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
        chain[1] = caCert;

        try {
            mKeyStore.setKeyEntry(TEST_ALIAS_1, privKey, "foo".toCharArray(), chain);
            fail("Should fail when a password is specified");
        } catch (KeyStoreException success) {
        }
    }

    public void testKeyStore_SetKeyEntry_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        final Certificate caCert = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        KeyFactory keyFact = KeyFactory.getInstance("RSA");
        PrivateKey privKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
        final Certificate[] chain = new Certificate[2];
        chain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
        chain[1] = caCert;

        mKeyStore.setKeyEntry(TEST_ALIAS_1, privKey, null, chain);

        Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
        assertNotNull("Retrieved entry should exist", actualEntry);

        assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                actualEntry instanceof PrivateKeyEntry);

        PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;

        assertPrivateKeyEntryEquals(actual, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1, FAKE_RSA_CA_1);
    }

    public void testKeyStore_SetKeyEntry_Replaced_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        final Certificate caCert = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_CA_1));

        // Insert initial key
        {
            KeyFactory keyFact = KeyFactory.getInstance("RSA");
            PrivateKey privKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
            final Certificate[] chain = new Certificate[2];
            chain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
            chain[1] = caCert;

            mKeyStore.setKeyEntry(TEST_ALIAS_1, privKey, null, chain);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);

            assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;

            assertPrivateKeyEntryEquals(actual, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                    FAKE_RSA_CA_1);
        }

        // TODO make a separate key
        // Replace key
        {
            KeyFactory keyFact = KeyFactory.getInstance("RSA");
            PrivateKey privKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
            final Certificate[] chain = new Certificate[2];
            chain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_RSA_USER_1));
            chain[1] = caCert;

            mKeyStore.setKeyEntry(TEST_ALIAS_1, privKey, null, chain);

            Entry actualEntry = mKeyStore.getEntry(TEST_ALIAS_1, null);
            assertNotNull("Retrieved entry should exist", actualEntry);

            assertTrue("Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;

            assertPrivateKeyEntryEquals(actual, "RSA", FAKE_RSA_KEY_1, FAKE_RSA_USER_1,
                    FAKE_RSA_CA_1);
        }
    }

    public void testKeyStore_SetKeyEntry_ReplacedChain_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        // Create key #1
        {
            KeyStore.PrivateKeyEntry privEntry = makeUserRsaKey1();
            mKeyStore.setEntry(TEST_ALIAS_1, privEntry, null);

            Entry entry = mKeyStore.getEntry(TEST_ALIAS_1, null);

            assertTrue(entry instanceof PrivateKeyEntry);

            PrivateKeyEntry keyEntry = (PrivateKeyEntry) entry;

            ArrayList<Certificate> chain = new ArrayList<Certificate>();
            chain.add(generateCertificate(FAKE_RSA_CA_1));
            assertPrivateKeyEntryEquals(keyEntry, privEntry.getPrivateKey(),
                    privEntry.getCertificate(), chain);
        }

        // Replace key #1 with new chain
        {
            Key key = mKeyStore.getKey(TEST_ALIAS_1, null);

            assertTrue(key instanceof PrivateKey);

            PrivateKey expectedKey = (PrivateKey) key;

            Certificate expectedCert = generateCertificate(FAKE_RSA_USER_1);

            mKeyStore.setKeyEntry(TEST_ALIAS_1, expectedKey, null,
                    new Certificate[] { expectedCert });

            Entry entry = mKeyStore.getEntry(TEST_ALIAS_1, null);

            assertTrue(entry instanceof PrivateKeyEntry);

            PrivateKeyEntry keyEntry = (PrivateKeyEntry) entry;

            assertPrivateKeyEntryEquals(keyEntry, expectedKey, expectedCert, null);
        }
    }

    public void testKeyStore_SetKeyEntry_ReplacedChain_DifferentPrivateKey_Unencrypted_Failure()
            throws Exception {
        mKeyStore.load(null, null);

        // Create key #1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        // Create key #2
        mKeyStore.setEntry(TEST_ALIAS_2, makeUserRsaKey1(), null);


        // Replace key #1 with key #2
        {
            Key key1 = mKeyStore.getKey(TEST_ALIAS_2, null);

            Certificate cert = generateCertificate(FAKE_RSA_USER_1);

            try {
                mKeyStore.setKeyEntry(TEST_ALIAS_1, key1, null, new Certificate[] { cert });
                fail("Should not allow setting of KeyEntry with wrong PrivaetKey");
            } catch (KeyStoreException success) {
            }
        }
    }

    public void testKeyStore_SetKeyEntry_ReplacedWithSame_UnencryptedToUnencrypted_Failure()
            throws Exception {
        mKeyStore.load(null, null);

        // Create key #1
        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        // Replace with same
        Entry entry = mKeyStore.getEntry(TEST_ALIAS_1, null);
        mKeyStore.setEntry(TEST_ALIAS_1, entry, null);
    }

    public void testKeyStore_Size_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        mKeyStore.setCertificateEntry(TEST_ALIAS_1, generateCertificate(FAKE_RSA_CA_1));

        assertEquals("The keystore size should match expected", 1, mKeyStore.size());
        assertAliases(new String[] { TEST_ALIAS_1 });

        mKeyStore.setCertificateEntry(TEST_ALIAS_2, generateCertificate(FAKE_RSA_CA_1));

        assertEquals("The keystore size should match expected", 2, mKeyStore.size());
        assertAliases(new String[] { TEST_ALIAS_1, TEST_ALIAS_2 });

        mKeyStore.setEntry(TEST_ALIAS_3, makeUserRsaKey1(), null);

        assertEquals("The keystore size should match expected", 3, mKeyStore.size());
        assertAliases(new String[] { TEST_ALIAS_1, TEST_ALIAS_2, TEST_ALIAS_3 });

        mKeyStore.deleteEntry(TEST_ALIAS_1);

        assertEquals("The keystore size should match expected", 2, mKeyStore.size());
        assertAliases(new String[] { TEST_ALIAS_2, TEST_ALIAS_3 });

        mKeyStore.deleteEntry(TEST_ALIAS_3);

        assertEquals("The keystore size should match expected", 1, mKeyStore.size());
        assertAliases(new String[] { TEST_ALIAS_2 });
    }

    public void testKeyStore_Store_LoadStoreParam_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        try {
            mKeyStore.store(null);
            fail("Should throw UnsupportedOperationException when trying to store");
        } catch (UnsupportedOperationException success) {
        }
    }

    public void testKeyStore_Load_InputStreamSupplied_Unencrypted_Failure() throws Exception {
        byte[] buf = "FAKE KEYSTORE".getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(buf);

        try {
            mKeyStore.load(is, null);
            fail("Should throw IllegalArgumentException when InputStream is supplied");
        } catch (IllegalArgumentException success) {
        }
    }

    public void testKeyStore_Load_PasswordSupplied_Unencrypted_Failure() throws Exception {
        try {
            mKeyStore.load(null, "password".toCharArray());
            fail("Should throw IllegalArgumentException when password is supplied");
        } catch (IllegalArgumentException success) {
        }
    }

    public void testKeyStore_Store_OutputStream_Unencrypted_Failure() throws Exception {
        mKeyStore.load(null, null);

        OutputStream sink = new ByteArrayOutputStream();
        try {
            mKeyStore.store(sink, null);
            fail("Should throw UnsupportedOperationException when trying to store");
        } catch (UnsupportedOperationException success) {
        }

        try {
            mKeyStore.store(sink, "blah".toCharArray());
            fail("Should throw UnsupportedOperationException when trying to store");
        } catch (UnsupportedOperationException success) {
        }
    }

    public void testKeyStore_KeyOperations_Wrap_Unencrypted_Success() throws Exception {
        mKeyStore.load(null, null);

        mKeyStore.setEntry(TEST_ALIAS_1, makeUserRsaKey1(), null);

        // Test key usage
        Entry e = mKeyStore.getEntry(TEST_ALIAS_1, null);
        assertNotNull(e);
        assertTrue(e instanceof PrivateKeyEntry);

        PrivateKeyEntry privEntry = (PrivateKeyEntry) e;
        PrivateKey privKey = privEntry.getPrivateKey();
        assertNotNull(privKey);

        PublicKey pubKey = privEntry.getCertificate().getPublicKey();

        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.WRAP_MODE, pubKey);

        byte[] expectedKey = new byte[] {
                0x00, 0x05, (byte) 0xAA, (byte) 0x0A5, (byte) 0xFF, 0x55, 0x0A
        };

        SecretKey expectedSecret = new TransparentSecretKey(expectedKey, "AES");

        byte[] wrappedExpected = c.wrap(expectedSecret);

        c.init(Cipher.UNWRAP_MODE, privKey);
        SecretKey actualSecret = (SecretKey) c.unwrap(wrappedExpected, "AES", Cipher.SECRET_KEY);

        assertEquals(Arrays.toString(expectedSecret.getEncoded()),
                Arrays.toString(actualSecret.getEncoded()));
    }

    public void testKeyStore_Encrypting_RSA_NONE_NOPADDING() throws Exception {

        String alias = "MyKey";
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        assertNotNull(ks);
        ks.load(null);

        Calendar cal = Calendar.getInstance();
        cal.set(1944, 5, 6);
        Date now = cal.getTime();
        cal.clear();

        cal.set(1945, 8, 2);
        Date end = cal.getTime();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        assertNotNull(kpg);
        kpg.initialize(new KeyPairGeneratorSpec.Builder(mContext)
                .setAlias(alias)
                .setStartDate(now)
                .setEndDate(end)
                .setSerialNumber(BigInteger.valueOf(1))
                .setSubject(new X500Principal("CN=test1"))
                .build());

        kpg.generateKeyPair();

        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, null);
        assertNotNull(privateKey);
        PublicKey publicKey = ks.getCertificate(alias).getPublicKey();
        assertNotNull(publicKey);
        String cipher = privateKey.getAlgorithm() + "/NONE/NOPADDING";
        Cipher encrypt = Cipher.getInstance(cipher);
        assertNotNull(encrypt);
        encrypt.init(Cipher.ENCRYPT_MODE, privateKey);

        int modulusSizeBytes = (((RSAKey) publicKey).getModulus().bitLength() + 7) / 8;
        byte[] plainText = new byte[modulusSizeBytes];
        Arrays.fill(plainText, (byte) 0xFF);

        // We expect a BadPaddingException here as the message size (plaintext)
        // is bigger than the modulus.
        try {
            encrypt.doFinal(plainText);
            fail("Expected BadPaddingException");
        } catch (BadPaddingException e) {
            // pass on exception as it is expected
        }
    }

    public void testKeyStore_PrivateKeyEntry_RSA_PublicKeyWorksWithCrypto()
            throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setKeyEntry(TEST_ALIAS_2,
                KeyFactory.getInstance("RSA").generatePrivate(
                        new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1)),
                null, // no password (it's not even supported)
                new Certificate[] {generateCertificate(FAKE_RSA_USER_1)});
        PublicKey publicKey = mKeyStore.getCertificate(TEST_ALIAS_2).getPublicKey();
        assertNotNull(publicKey);

        Signature.getInstance("SHA256withRSA").initVerify(publicKey);
        Signature.getInstance("NONEwithRSA").initVerify(publicKey);
        Signature.getInstance("SHA256withRSA/PSS").initVerify(publicKey);

        Cipher.getInstance("RSA/ECB/PKCS1Padding").init(Cipher.ENCRYPT_MODE, publicKey);
        Cipher.getInstance("RSA/ECB/NoPadding").init(Cipher.ENCRYPT_MODE, publicKey);
        Cipher.getInstance("RSA/ECB/OAEPPadding").init(Cipher.ENCRYPT_MODE, publicKey);
    }

    public void testKeyStore_PrivateKeyEntry_EC_PublicKeyWorksWithCrypto()
            throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setKeyEntry(TEST_ALIAS_1,
                KeyFactory.getInstance("EC").generatePrivate(
                        new PKCS8EncodedKeySpec(FAKE_EC_KEY_1)),
                null, // no password (it's not even supported)
                new Certificate[] {generateCertificate(FAKE_EC_USER_1)});
        PublicKey publicKey = mKeyStore.getCertificate(TEST_ALIAS_1).getPublicKey();
        assertNotNull(publicKey);

        Signature.getInstance("SHA256withECDSA").initVerify(publicKey);
        Signature.getInstance("NONEwithECDSA").initVerify(publicKey);
    }

    public void testKeyStore_TrustedCertificateEntry_RSA_PublicKeyWorksWithCrypto()
            throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(TEST_ALIAS_2, generateCertificate(FAKE_RSA_USER_1));
        PublicKey publicKey = mKeyStore.getCertificate(TEST_ALIAS_2).getPublicKey();
        assertNotNull(publicKey);

        Signature.getInstance("SHA256withRSA").initVerify(publicKey);
        Signature.getInstance("NONEwithRSA").initVerify(publicKey);

        Cipher.getInstance("RSA/ECB/PKCS1Padding").init(Cipher.ENCRYPT_MODE, publicKey);
        Cipher.getInstance("RSA/ECB/NoPadding").init(Cipher.ENCRYPT_MODE, publicKey);
    }

    public void testKeyStore_TrustedCertificateEntry_EC_PublicKeyWorksWithCrypto()
            throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(TEST_ALIAS_1, generateCertificate(FAKE_EC_USER_1));
        PublicKey publicKey = mKeyStore.getCertificate(TEST_ALIAS_1).getPublicKey();
        assertNotNull(publicKey);

        Signature.getInstance("SHA256withECDSA").initVerify(publicKey);
        Signature.getInstance("NONEwithECDSA").initVerify(publicKey);
    }

    private static final int MIN_SUPPORTED_KEY_COUNT = 1500;
    private static final long MINUTE_IN_MILLIS = 1000 * 60;
    private static final long LARGE_NUMBER_OF_KEYS_TEST_MAX_DURATION_MILLIS = 2 * MINUTE_IN_MILLIS;
    private static final long LARGE_NUMBER_OF_KEYS_TEST_MAX_DURATION_WATCH_MILLIS = 3 * MINUTE_IN_MILLIS;

    private static boolean isDeadlineReached(long startTimeMillis, long durationMillis) {
        long nowMillis = System.currentTimeMillis();
        if (nowMillis < startTimeMillis) {
            return true;
        }
        return nowMillis - startTimeMillis > durationMillis;
    }

    @LargeTest
    public void testKeyStore_LargeNumberOfKeysSupported_RSA() throws Exception {
        // This test imports key1, then lots of other keys, then key2, and then confirms that
        // key1 and key2 backed by Android Keystore work fine. The assumption is that if the
        // underlying implementation has a limit on the number of keys, it'll either delete the
        // oldest key (key1), or will refuse to add keys (key2).
        // The test imports as many keys as it can in a fixed amount of time instead of stopping
        // at MIN_SUPPORTED_KEY_COUNT to balance the desire to support an unlimited number of keys
        // with the constraints on how long the test can run and performance differences of hardware
        // under test.

        long testStartTimeMillis = System.currentTimeMillis();

        Certificate cert1 = TestUtils.getRawResX509Certificate(getContext(), R.raw.rsa_key1_cert);
        PrivateKey privateKey1 = TestUtils.getRawResPrivateKey(getContext(), R.raw.rsa_key1_pkcs8);
        String entryName1 = "test0";

        Certificate cert2 = TestUtils.getRawResX509Certificate(getContext(), R.raw.rsa_key2_cert);
        PrivateKey privateKey2 = TestUtils.getRawResPrivateKey(getContext(), R.raw.rsa_key2_pkcs8);

        Certificate cert3 = generateCertificate(FAKE_RSA_USER_1);
        PrivateKey privateKey3 = generatePrivateKey("RSA", FAKE_RSA_KEY_1);

        mKeyStore.load(null);
        int latestImportedEntryNumber = 0;
        try {
            KeyProtection protectionParams = new KeyProtection.Builder(
                    KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build();
            mKeyStore.setEntry(entryName1,
                    new KeyStore.PrivateKeyEntry(privateKey1, new Certificate[] {cert1}),
                    protectionParams);

            // Import key3 lots of times, under different aliases.
            while (!isDeadlineReached(testStartTimeMillis, mMaxTestDurationMillis)) {
                latestImportedEntryNumber++;
                if ((latestImportedEntryNumber % 1000) == 0) {
                    Log.i(TAG, "Imported " + latestImportedEntryNumber + " keys");
                }
                String entryAlias = "test" + latestImportedEntryNumber;
                try {
                    mKeyStore.setEntry(entryAlias,
                            new KeyStore.PrivateKeyEntry(privateKey3, new Certificate[] {cert3}),
                            protectionParams);
                } catch (Throwable e) {
                    throw new RuntimeException("Entry " + entryAlias + " import failed", e);
                }
            }
            Log.i(TAG, "Imported " + latestImportedEntryNumber + " keys");
            if (latestImportedEntryNumber < MIN_SUPPORTED_KEY_COUNT) {
                fail("Failed to import " + MIN_SUPPORTED_KEY_COUNT + " keys in "
                        + (System.currentTimeMillis() - testStartTimeMillis)
                        + " ms. Imported: " + latestImportedEntryNumber + " keys");
            }

            latestImportedEntryNumber++;
            String entryName2 = "test" + latestImportedEntryNumber;
            mKeyStore.setEntry(entryName2,
                    new KeyStore.PrivateKeyEntry(privateKey2, new Certificate[] {cert2}),
                    protectionParams);
            PrivateKey keystorePrivateKey2 = (PrivateKey) mKeyStore.getKey(entryName2, null);
            PrivateKey keystorePrivateKey1 = (PrivateKey) mKeyStore.getKey(entryName1, null);

            byte[] message = "This is a test".getBytes("UTF-8");

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(keystorePrivateKey1);
            sig.update(message);
            byte[] signature = sig.sign();
            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initVerify(cert1.getPublicKey());
            sig.update(message);
            assertTrue(sig.verify(signature));

            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initSign(keystorePrivateKey2);
            sig.update(message);
            signature = sig.sign();
            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initVerify(cert2.getPublicKey());
            sig.update(message);
            assertTrue(sig.verify(signature));
        } finally {
            // Clean up Keystore without using KeyStore.aliases() which can't handle this many
            // entries.
            Log.i(TAG, "Deleting imported keys");
            for (int i = 0; i <= latestImportedEntryNumber; i++) {
                if ((i > 0) && ((i % 1000) == 0)) {
                    Log.i(TAG, "Deleted " + i + " keys");
                }
                mKeyStore.deleteEntry("test" + i);
            }
            Log.i(TAG, "Deleted " + (latestImportedEntryNumber + 1) + " keys");
        }
    }

    @LargeTest
    public void testKeyStore_LargeNumberOfKeysSupported_EC() throws Exception {
        // This test imports key1, then lots of other keys, then key2, and then confirms that
        // key1 and key2 backed by Android Keystore work fine. The assumption is that if the
        // underlying implementation has a limit on the number of keys, it'll either delete the
        // oldest key (key1), or will refuse to add keys (key2).
        // The test imports as many keys as it can in a fixed amount of time instead of stopping
        // at MIN_SUPPORTED_KEY_COUNT to balance the desire to support an unlimited number of keys
        // with the constraints on how long the test can run and performance differences of hardware
        // under test.

        long testStartTimeMillis = System.currentTimeMillis();

        Certificate cert1 = TestUtils.getRawResX509Certificate(getContext(), R.raw.ec_key1_cert);
        PrivateKey privateKey1 = TestUtils.getRawResPrivateKey(getContext(), R.raw.ec_key1_pkcs8);
        String entryName1 = "test0";

        Certificate cert2 = TestUtils.getRawResX509Certificate(getContext(), R.raw.ec_key2_cert);
        PrivateKey privateKey2 = TestUtils.getRawResPrivateKey(getContext(), R.raw.ec_key2_pkcs8);

        Certificate cert3 = generateCertificate(FAKE_EC_USER_1);
        PrivateKey privateKey3 = generatePrivateKey("EC", FAKE_EC_KEY_1);

        mKeyStore.load(null);
        int latestImportedEntryNumber = 0;
        try {
            KeyProtection protectionParams = new KeyProtection.Builder(
                    KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build();
            mKeyStore.setEntry(entryName1,
                    new KeyStore.PrivateKeyEntry(privateKey1, new Certificate[] {cert1}),
                    protectionParams);

            // Import key3 lots of times, under different aliases.
            while (!isDeadlineReached(
                    testStartTimeMillis, mMaxTestDurationMillis)) {
                latestImportedEntryNumber++;
                if ((latestImportedEntryNumber % 1000) == 0) {
                    Log.i(TAG, "Imported " + latestImportedEntryNumber + " keys");
                }
                String entryAlias = "test" + latestImportedEntryNumber;
                try {
                    mKeyStore.setEntry(entryAlias,
                            new KeyStore.PrivateKeyEntry(privateKey3, new Certificate[] {cert3}),
                            protectionParams);
                } catch (Throwable e) {
                    throw new RuntimeException("Entry " + entryAlias + " import failed", e);
                }
            }
            Log.i(TAG, "Imported " + latestImportedEntryNumber + " keys");
            if (latestImportedEntryNumber < MIN_SUPPORTED_KEY_COUNT) {
                fail("Failed to import " + MIN_SUPPORTED_KEY_COUNT + " keys in "
                        + (System.currentTimeMillis() - testStartTimeMillis)
                        + " ms. Imported: " + latestImportedEntryNumber + " keys");
            }

            latestImportedEntryNumber++;
            String entryName2 = "test" + latestImportedEntryNumber;
            mKeyStore.setEntry(entryName2,
                    new KeyStore.PrivateKeyEntry(privateKey2, new Certificate[] {cert2}),
                    protectionParams);
            PrivateKey keystorePrivateKey2 = (PrivateKey) mKeyStore.getKey(entryName2, null);
            PrivateKey keystorePrivateKey1 = (PrivateKey) mKeyStore.getKey(entryName1, null);

            byte[] message = "This is a test".getBytes("UTF-8");

            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(keystorePrivateKey1);
            sig.update(message);
            byte[] signature = sig.sign();
            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initVerify(cert1.getPublicKey());
            sig.update(message);
            assertTrue(sig.verify(signature));

            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initSign(keystorePrivateKey2);
            sig.update(message);
            signature = sig.sign();
            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initVerify(cert2.getPublicKey());
            sig.update(message);
            assertTrue(sig.verify(signature));
        } finally {
            // Clean up Keystore without using KeyStore.aliases() which can't handle this many
            // entries.
            Log.i(TAG, "Deleting imported keys");
            for (int i = 0; i <= latestImportedEntryNumber; i++) {
                if ((i > 0) && ((i % 1000) == 0)) {
                    Log.i(TAG, "Deleted " + i + " keys");
                }
                mKeyStore.deleteEntry("test" + i);
            }
            Log.i(TAG, "Deleted " + (latestImportedEntryNumber + 1) + " keys");
        }
    }

    @LargeTest
    public void testKeyStore_LargeNumberOfKeysSupported_AES() throws Exception {
        // This test imports key1, then lots of other keys, then key2, and then confirms that
        // key1 and key2 backed by Android Keystore work fine. The assumption is that if the
        // underlying implementation has a limit on the number of keys, it'll either delete the
        // oldest key (key1), or will refuse to add keys (key2).
        // The test imports as many keys as it can in a fixed amount of time instead of stopping
        // at MIN_SUPPORTED_KEY_COUNT to balance the desire to support an unlimited number of keys
        // with the constraints on how long the test can run and performance differences of hardware
        // under test.

        long testStartTimeMillis = System.currentTimeMillis();

        SecretKey key1 = new TransparentSecretKey(
                HexEncoding.decode("010203040506070809fafbfcfdfeffcc"), "AES");
        String entryName1 = "test0";

        SecretKey key2 = new TransparentSecretKey(
                HexEncoding.decode("808182838485868788897a7b7c7d7e7f"), "AES");

        SecretKey key3 = new TransparentSecretKey(
                HexEncoding.decode("33333333333333333333777777777777"), "AES");

        mKeyStore.load(null);
        int latestImportedEntryNumber = 0;
        try {
            KeyProtection protectionParams = new KeyProtection.Builder(
                    KeyProperties.PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build();
            mKeyStore.setEntry(entryName1, new KeyStore.SecretKeyEntry(key1), protectionParams);

            // Import key3 lots of times, under different aliases.
            while (!isDeadlineReached(testStartTimeMillis, mMaxTestDurationMillis)) {
                latestImportedEntryNumber++;
                if ((latestImportedEntryNumber % 1000) == 0) {
                    Log.i(TAG, "Imported " + latestImportedEntryNumber + " keys");
                }
                String entryAlias = "test" + latestImportedEntryNumber;
                try {
                    mKeyStore.setEntry(entryAlias,
                            new KeyStore.SecretKeyEntry(key3), protectionParams);
                } catch (Throwable e) {
                    throw new RuntimeException("Entry " + entryAlias + " import failed", e);
                }
            }
            Log.i(TAG, "Imported " + latestImportedEntryNumber + " keys");
            if (latestImportedEntryNumber < MIN_SUPPORTED_KEY_COUNT) {
                fail("Failed to import " + MIN_SUPPORTED_KEY_COUNT + " keys in "
                        + (System.currentTimeMillis() - testStartTimeMillis)
                        + " ms. Imported: " + latestImportedEntryNumber + " keys");
            }

            latestImportedEntryNumber++;
            String entryName2 = "test" + latestImportedEntryNumber;
            mKeyStore.setEntry(entryName2, new KeyStore.SecretKeyEntry(key2), protectionParams);
            SecretKey keystoreKey2 = (SecretKey) mKeyStore.getKey(entryName2, null);
            SecretKey keystoreKey1 = (SecretKey) mKeyStore.getKey(entryName1, null);

            byte[] plaintext = "This is a test".getBytes("UTF-8");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey1);
            byte[] ciphertext = cipher.doFinal(plaintext);
            AlgorithmParameters cipherParams = cipher.getParameters();
            cipher = Cipher.getInstance(cipher.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, key1, cipherParams);
            MoreAsserts.assertEquals(plaintext, cipher.doFinal(ciphertext));

            cipher = Cipher.getInstance(cipher.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey2);
            ciphertext = cipher.doFinal(plaintext);
            cipherParams = cipher.getParameters();
            cipher = Cipher.getInstance(cipher.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, key2, cipherParams);
            MoreAsserts.assertEquals(plaintext, cipher.doFinal(ciphertext));
        } finally {
            // Clean up Keystore without using KeyStore.aliases() which can't handle this many
            // entries.
            Log.i(TAG, "Deleting imported keys");
            for (int i = 0; i <= latestImportedEntryNumber; i++) {
                if ((i > 0) && ((i % 1000) == 0)) {
                    Log.i(TAG, "Deleted " + i + " keys");
                }
                mKeyStore.deleteEntry("test" + i);
            }
            Log.i(TAG, "Deleted " + (latestImportedEntryNumber + 1) + " keys");
        }
    }

    @LargeTest
    public void testKeyStore_LargeNumberOfKeysSupported_HMAC() throws Exception {
        // This test imports key1, then lots of other keys, then key2, and then confirms that
        // key1 and key2 backed by Android Keystore work fine. The assumption is that if the
        // underlying implementation has a limit on the number of keys, it'll either delete the
        // oldest key (key1), or will refuse to add keys (key2).
        // The test imports as many keys as it can in a fixed amount of time instead of stopping
        // at MIN_SUPPORTED_KEY_COUNT to balance the desire to support an unlimited number of keys
        // with the constraints on how long the test can run and performance differences of hardware
        // under test.

        long testStartTimeMillis = System.currentTimeMillis();

        SecretKey key1 = new TransparentSecretKey(
                HexEncoding.decode("010203040506070809fafbfcfdfeffcc"), "HmacSHA256");
        String entryName1 = "test0";

        SecretKey key2 = new TransparentSecretKey(
                HexEncoding.decode("808182838485868788897a7b7c7d7e7f"), "HmacSHA256");

        SecretKey key3 = new TransparentSecretKey(
                HexEncoding.decode("33333333333333333333777777777777"), "HmacSHA256");

        mKeyStore.load(null);
        int latestImportedEntryNumber = 0;
        try {
            KeyProtection protectionParams = new KeyProtection.Builder(
                    KeyProperties.PURPOSE_SIGN)
                    .build();
            mKeyStore.setEntry(entryName1, new KeyStore.SecretKeyEntry(key1), protectionParams);

            // Import key3 lots of times, under different aliases.
            while (!isDeadlineReached(testStartTimeMillis, mMaxTestDurationMillis)) {
                latestImportedEntryNumber++;
                if ((latestImportedEntryNumber % 1000) == 0) {
                    Log.i(TAG, "Imported " + latestImportedEntryNumber + " keys");
                }
                String entryAlias = "test" + latestImportedEntryNumber;
                try {
                    mKeyStore.setEntry(entryAlias,
                            new KeyStore.SecretKeyEntry(key3), protectionParams);
                } catch (Throwable e) {
                    throw new RuntimeException("Entry " + entryAlias + " import failed", e);
                }
            }
            Log.i(TAG, "Imported " + latestImportedEntryNumber + " keys");
            if (latestImportedEntryNumber < MIN_SUPPORTED_KEY_COUNT) {
                fail("Failed to import " + MIN_SUPPORTED_KEY_COUNT + " keys in "
                        + (System.currentTimeMillis() - testStartTimeMillis)
                        + " ms. Imported: " + latestImportedEntryNumber + " keys");
            }

            latestImportedEntryNumber++;
            String entryName2 = "test" + latestImportedEntryNumber;
            mKeyStore.setEntry(entryName2, new KeyStore.SecretKeyEntry(key2), protectionParams);
            SecretKey keystoreKey2 = (SecretKey) mKeyStore.getKey(entryName2, null);
            SecretKey keystoreKey1 = (SecretKey) mKeyStore.getKey(entryName1, null);

            byte[] message = "This is a test".getBytes("UTF-8");
            Mac mac = Mac.getInstance(key1.getAlgorithm());
            mac.init(keystoreKey1);
            MoreAsserts.assertEquals(
                    HexEncoding.decode(
                            "905e36f5a175f4ca54ad56b860b46f6502f883a90628dca2d33a953fb7224eaf"),
                    mac.doFinal(message));

            mac = Mac.getInstance(key2.getAlgorithm());
            mac.init(keystoreKey2);
            MoreAsserts.assertEquals(
                    HexEncoding.decode(
                            "59b57e77e4e2cb36b5c7b84af198ac004327bc549de6931a1b5505372dd8c957"),
                    mac.doFinal(message));
        } finally {
            // Clean up Keystore without using KeyStore.aliases() which can't handle this many
            // entries.
            Log.i(TAG, "Deleting imported keys");
            for (int i = 0; i <= latestImportedEntryNumber; i++) {
                if ((i > 0) && ((i % 1000) == 0)) {
                    Log.i(TAG, "Deleted " + i + " keys");
                }
                mKeyStore.deleteEntry("test" + i);
            }
            Log.i(TAG, "Deleted " + (latestImportedEntryNumber + 1) + " keys");
        }
    }

    public void testKeyStore_OnlyOneDigestCanBeAuthorized_HMAC() throws Exception {
        mKeyStore.load(null);

        for (String algorithm : KeyGeneratorTest.EXPECTED_ALGORITHMS) {
            if (!TestUtils.isHmacAlgorithm(algorithm)) {
                continue;
            }
            try {
                String digest = TestUtils.getHmacAlgorithmDigest(algorithm);
                assertNotNull(digest);
                SecretKey keyBeingImported = new TransparentSecretKey(new byte[16], algorithm);

                KeyProtection.Builder goodSpec =
                        new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN);

                // Digests authorization not specified in import parameters
                assertFalse(goodSpec.build().isDigestsSpecified());
                mKeyStore.setEntry(TEST_ALIAS_1,
                        new KeyStore.SecretKeyEntry(keyBeingImported),
                        goodSpec.build());
                SecretKey key = (SecretKey) mKeyStore.getKey(TEST_ALIAS_1, null);
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(TestUtils.getKeyInfo(key).getDigests()), digest);

                // The same digest is specified in import parameters
                mKeyStore.setEntry(TEST_ALIAS_1,
                        new KeyStore.SecretKeyEntry(keyBeingImported),
                        TestUtils.buildUpon(goodSpec).setDigests(digest).build());
                key = (SecretKey) mKeyStore.getKey(TEST_ALIAS_1, null);
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(TestUtils.getKeyInfo(key).getDigests()), digest);

                // Empty set of digests specified in import parameters
                try {
                    mKeyStore.setEntry(TEST_ALIAS_1,
                            new KeyStore.SecretKeyEntry(keyBeingImported),
                            TestUtils.buildUpon(goodSpec).setDigests().build());
                    fail();
                } catch (KeyStoreException expected) {}

                // A different digest specified in import parameters
                String anotherDigest = "SHA-256".equalsIgnoreCase(digest) ? "SHA-384" : "SHA-256";
                try {
                    mKeyStore.setEntry(TEST_ALIAS_1,
                            new KeyStore.SecretKeyEntry(keyBeingImported),
                            TestUtils.buildUpon(goodSpec).setDigests(anotherDigest).build());
                    fail();
                } catch (KeyStoreException expected) {}
                try {
                    mKeyStore.setEntry(TEST_ALIAS_1,
                            new KeyStore.SecretKeyEntry(keyBeingImported),
                            TestUtils.buildUpon(goodSpec)
                                    .setDigests(digest, anotherDigest)
                                    .build());
                    fail();
                } catch (KeyStoreException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testKeyStore_ImportSupportedSizes_AES() throws Exception {
        mKeyStore.load(null);

        KeyProtection params = new KeyProtection.Builder(
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build();
        String alias = "test1";
        mKeyStore.deleteEntry(alias);
        assertFalse(mKeyStore.containsAlias(alias));
        for (int keySizeBytes = 0; keySizeBytes <= 512 / 8; keySizeBytes++) {
            int keySizeBits = keySizeBytes * 8;
            try {
                KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(
                        new TransparentSecretKey(new byte[keySizeBytes], "AES"));
                if (TestUtils.contains(KeyGeneratorTest.AES_SUPPORTED_KEY_SIZES, keySizeBits)) {
                    mKeyStore.setEntry(alias, entry, params);
                    SecretKey key = (SecretKey) mKeyStore.getKey(alias, null);
                    assertEquals("AES", key.getAlgorithm());
                    assertEquals(keySizeBits, TestUtils.getKeyInfo(key).getKeySize());
                } else {
                    mKeyStore.deleteEntry(alias);
                    assertFalse(mKeyStore.containsAlias(alias));
                    try {
                        mKeyStore.setEntry(alias, entry, params);
                        fail();
                    } catch (KeyStoreException expected) {}
                    assertFalse(mKeyStore.containsAlias(alias));
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key size " + keySizeBits, e);
            }
        }
    }

    public void testKeyStore_ImportSupportedSizes_HMAC() throws Exception {
        mKeyStore.load(null);

        KeyProtection params = new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build();
        String alias = "test1";
        mKeyStore.deleteEntry(alias);
        assertFalse(mKeyStore.containsAlias(alias));
        for (String algorithm : KeyGeneratorTest.EXPECTED_ALGORITHMS) {
            if (!TestUtils.isHmacAlgorithm(algorithm)) {
                continue;
            }
            for (int keySizeBytes = 8; keySizeBytes <= 1024 / 8; keySizeBytes++) {
                try {
                    KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(
                            new TransparentSecretKey(new byte[keySizeBytes], algorithm));
                    if (keySizeBytes > 0) {
                        mKeyStore.setEntry(alias, entry, params);
                        SecretKey key = (SecretKey) mKeyStore.getKey(alias, null);
                        assertEquals(algorithm, key.getAlgorithm());
                        assertEquals(keySizeBytes * 8, TestUtils.getKeyInfo(key).getKeySize());
                    } else {
                        mKeyStore.deleteEntry(alias);
                        assertFalse(mKeyStore.containsAlias(alias));
                        try {
                            mKeyStore.setEntry(alias, entry, params);
                            fail();
                        } catch (KeyStoreException expected) {}
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + algorithm + " with key size " + (keySizeBytes * 8), e);
                }
            }
        }
    }

    public void testKeyStore_ImportSupportedSizes_EC() throws Exception {
        mKeyStore.load(null);
        KeyProtection params =
                TestUtils.getMinimalWorkingImportParametersForSigningingWith("SHA256withECDSA");
        checkKeyPairImportSucceeds(
                "secp224r1", R.raw.ec_key3_secp224r1_pkcs8, R.raw.ec_key3_secp224r1_cert, params);
        checkKeyPairImportSucceeds(
                "secp256r1", R.raw.ec_key4_secp256r1_pkcs8, R.raw.ec_key4_secp256r1_cert, params);
        checkKeyPairImportSucceeds(
                "secp384r1", R.raw.ec_key5_secp384r1_pkcs8, R.raw.ec_key5_secp384r1_cert, params);
        checkKeyPairImportSucceeds(
                "secp512r1", R.raw.ec_key6_secp521r1_pkcs8, R.raw.ec_key6_secp521r1_cert, params);
    }

    public void testKeyStore_ImportSupportedSizes_RSA() throws Exception {
        mKeyStore.load(null);
        KeyProtection params =
                TestUtils.getMinimalWorkingImportParametersForSigningingWith("SHA256withRSA");
        checkKeyPairImportSucceeds(
                "512", R.raw.rsa_key5_512_pkcs8, R.raw.rsa_key5_512_cert, params);
        checkKeyPairImportSucceeds(
                "768", R.raw.rsa_key6_768_pkcs8, R.raw.rsa_key6_768_cert, params);
        checkKeyPairImportSucceeds(
                "1024", R.raw.rsa_key3_1024_pkcs8, R.raw.rsa_key3_1024_cert, params);
        checkKeyPairImportSucceeds(
                "2048", R.raw.rsa_key8_2048_pkcs8, R.raw.rsa_key8_2048_cert, params);
        checkKeyPairImportSucceeds(
                "3072", R.raw.rsa_key7_3072_pksc8, R.raw.rsa_key7_3072_cert, params);
        checkKeyPairImportSucceeds(
                "4096", R.raw.rsa_key4_4096_pkcs8, R.raw.rsa_key4_4096_cert, params);
    }

    private void checkKeyPairImportSucceeds(
            String alias, int privateResId, int certResId, KeyProtection params) throws Exception {
        try {
            mKeyStore.deleteEntry(alias);
            TestUtils.importIntoAndroidKeyStore(
                    alias, getContext(), privateResId, certResId, params);
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + alias, e);
        } finally {
            try {
                mKeyStore.deleteEntry(alias);
            } catch (Exception ignored) {}
        }
    }
}
