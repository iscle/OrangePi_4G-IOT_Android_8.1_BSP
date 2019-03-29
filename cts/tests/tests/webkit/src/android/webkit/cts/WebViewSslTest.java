/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.webkit.cts;

import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.util.Log;
import android.webkit.ClientCertRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.cts.WebViewOnUiThread.WaitForLoadedClient;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Principal;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Callable;

import javax.net.ssl.X509TrustManager;

public class WebViewSslTest extends ActivityInstrumentationTestCase2<WebViewCtsActivity> {
    private static final String LOGTAG = "WebViewSslTest";

    /**
     * Taken verbatim from AndroidKeyStoreTest.java. Copying the build notes here for reference.
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

    private WebView mWebView;
    private CtsTestServer mWebServer;
    private WebViewOnUiThread mOnUiThread;

    public WebViewSslTest() {
        super("android.webkit.cts", WebViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final WebViewCtsActivity activity = getActivity();
        mWebView = activity.getWebView();
        if (mWebView != null) {
            new PollingCheck() {
                @Override
                    protected boolean check() {
                        return activity.hasWindowFocus();
                }
            }.run();
            File f = activity.getFileStreamPath("snapshot");
            if (f.exists()) {
                f.delete();
            }

            mOnUiThread = new WebViewOnUiThread(this, mWebView);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        if (mWebServer != null) {
            stopWebServer();
        }
        super.tearDown();
    }

    private void startWebServer(boolean secure) throws Exception {
        assertNull(mWebServer);
        mWebServer = new CtsTestServer(getActivity(), secure);
    }

    private void stopWebServer() throws Exception {
        assertNotNull(mWebServer);
        ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
        ThreadPolicy tmpPolicy = new ThreadPolicy.Builder(oldPolicy)
                .permitNetwork()
                .build();
        StrictMode.setThreadPolicy(tmpPolicy);
        mWebServer.shutdown();
        mWebServer = null;
        StrictMode.setThreadPolicy(oldPolicy);
    }

    @UiThreadTest
    public void testInsecureSiteClearsCertificate() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        }

        startWebServer(true);
        mOnUiThread.setWebViewClient(new MockWebViewClient());
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        SslCertificate cert = mWebView.getCertificate();
        assertNotNull(cert);
        assertEquals("Android", cert.getIssuedTo().getUName());

        stopWebServer();

        startWebServer(false);
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        assertNull(mWebView.getCertificate());
    }

    @UiThreadTest
    public void testSecureSiteSetsCertificate() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        }

        startWebServer(false);
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        assertNull(mWebView.getCertificate());

        stopWebServer();

        startWebServer(true);
        mOnUiThread.setWebViewClient(new MockWebViewClient());
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        SslCertificate cert = mWebView.getCertificate();
        assertNotNull(cert);
        assertEquals("Android", cert.getIssuedTo().getUName());
    }

    @UiThreadTest
    public void testClearSslPreferences() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Load the first page. We expect a call to
        // WebViewClient.onReceivedSslError().
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient(mOnUiThread);
        startWebServer(true);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());

        // Load the page again. We expect another call to
        // WebViewClient.onReceivedSslError() since we cleared sslpreferences.
        mOnUiThread.clearSslPreferences();
        webViewClient.resetWasOnReceivedSslErrorCalled();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());

        // Load the page once again, without clearing the sslpreferences.
        // Make sure we do not get the callback.
        webViewClient.resetWasOnReceivedSslErrorCalled();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());
    }

    public void testOnReceivedSslError() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            private String mErrorUrl;
            private WebView mWebView;

            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                mWebView = view;
                mErrorUrl = error.getUrl();
                handler.proceed();
            }
            public String errorUrl() {
                return mErrorUrl;
            }
            public WebView webView() {
                return mWebView;
            }
        }

        startWebServer(true);
        final String errorUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(errorUrl);

        assertEquals(mWebView, webViewClient.webView());
        assertEquals(errorUrl, webViewClient.errorUrl());
    }

    public void testOnReceivedSslErrorProceed() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        }

        startWebServer(true);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.setWebViewClient(new MockWebViewClient());
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());
    }

    public void testOnReceivedSslErrorCancel() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        }

        startWebServer(true);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.setWebViewClient(new MockWebViewClient());
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
    }

    public void testSslErrorProceedResponseReusedForSameHost() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Load the first page. We expect a call to
        // WebViewClient.onReceivedSslError().
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient(mOnUiThread);
        startWebServer(true);
        final String firstUrl = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(firstUrl);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());

        // Load the second page. We don't expect a call to
        // WebViewClient.onReceivedSslError(), but the page should load.
        webViewClient.resetWasOnReceivedSslErrorCalled();
        final String sameHostUrl = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
        mOnUiThread.loadUrlAndWaitForCompletion(sameHostUrl);
        assertFalse(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals("Second page", mOnUiThread.getTitle());
    }

    public void testSslErrorProceedResponseNotReusedForDifferentHost() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Load the first page. We expect a call to
        // WebViewClient.onReceivedSslError().
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient(mOnUiThread);
        startWebServer(true);
        final String firstUrl = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(firstUrl);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());

        // Load the second page. We expect another call to
        // WebViewClient.onReceivedSslError().
        webViewClient.resetWasOnReceivedSslErrorCalled();
        // The test server uses the host "localhost". "127.0.0.1" works as an
        // alias, but will be considered unique by the WebView.
        final String differentHostUrl = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2).replace(
                "localhost", "127.0.0.1");
        mOnUiThread.loadUrlAndWaitForCompletion(differentHostUrl);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals("Second page", mOnUiThread.getTitle());
    }

    public void testSecureServerRequestingClientCertDoesNotCancelRequest() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.WANTS_CLIENT_AUTH);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient(mOnUiThread);
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // Page loaded OK...
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());
        assertEquals(0, webViewClient.onReceivedErrorCode());
    }

    public void testSecureServerRequiringClientCertDoesCancelRequest() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient(mOnUiThread);
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // Page NOT loaded OK...
        // In this case, we must NOT have received the onReceivedSslError callback as that is for
        // recoverable (e.g. server auth) errors, whereas failing mandatory client auth is non-
        // recoverable and should drop straight through to a load error.
        assertFalse(webViewClient.wasOnReceivedSslErrorCalled());
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());
    }

    public void testProceedClientCertRequest() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient(mOnUiThread);
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));

        // Test that the user's response for this server is kept in cache. Load a different
        // page from the same server and make sure we don't receive a client cert request callback.
        int callCount = webViewClient.getClientCertRequestCount();
        url = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HTML_URL1_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(callCount, webViewClient.getClientCertRequestCount());

        // Now clear the cache and reload the page. We should receive a new callback.
        clearClientCertPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HTML_URL1_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(callCount + 1, webViewClient.getClientCertRequestCount());
    }

    public void testProceedClientCertRequestKeyWithAndroidKeystoreKey() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient(
                mOnUiThread,
                true // use an Android Keystore backed private key
                );
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));

        // Test that the user's response for this server is kept in cache. Load a different
        // page from the same server and make sure we don't receive a client cert request callback.
        int callCount = webViewClient.getClientCertRequestCount();
        url = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HTML_URL1_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(callCount, webViewClient.getClientCertRequestCount());

        // Now clear the cache and reload the page. We should receive a new callback.
        clearClientCertPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HTML_URL1_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(callCount + 1, webViewClient.getClientCertRequestCount());
    }

    public void testIgnoreClientCertRequest() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient(mOnUiThread);
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        // Ignore the request. Load should fail.
        webViewClient.setAction(ClientCertWebViewClient.IGNORE);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());

        // Load a different page from the same domain, ignoring the request. We should get a callback,
        // and load should fail.
        int callCount = webViewClient.getClientCertRequestCount();
        url = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(TestHtmlConstants.HTML_URL1_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());
        assertEquals(callCount + 1, webViewClient.getClientCertRequestCount());

        // Reload, proceeding the request. Load should succeed.
        webViewClient.setAction(ClientCertWebViewClient.PROCEED);
        url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
    }

    public void testCancelClientCertRequest() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient(mOnUiThread);
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        // Cancel the request. Load should fail.
        webViewClient.setAction(ClientCertWebViewClient.CANCEL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());

        // Reload. The request should fail without generating a new callback.
        int callCount = webViewClient.getClientCertRequestCount();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(callCount, webViewClient.getClientCertRequestCount());
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());
    }

    /**
     * {@link X509TrustManager} that trusts everybody.
     */
    private static class TrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Trust the CtSTestServer's client...
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Trust the CtSTestServer...
        }

        public X509Certificate[] getAcceptedIssuers() {
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                return new X509Certificate[] {
                        (X509Certificate) certFactory.generateCertificate(
                                new ByteArrayInputStream(FAKE_RSA_CA_1))
                        };
            } catch (Exception ex) {
                Log.e(LOGTAG, "failed creating certificate chain" + ex);
                return null;
            }
        }
    }

    public void testClientCertIssuersReceivedCorrectly() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH,
                new TrustManager());
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient(mOnUiThread);
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // Verify that issuers sent by the server are received correctly
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate  cert = (X509Certificate) certFactory.generateCertificate(
                                new ByteArrayInputStream(FAKE_RSA_CA_1));
        Principal[] principals = webViewClient.getPrincipals();
        assertEquals(1, principals.length);
        // TODO: should we issue getIssuerX500Principal instead?
        assertEquals(cert.getIssuerDN(), principals[0]);
    }

    private void clearClientCertPreferences() {
       final AtomicBoolean cleared = new AtomicBoolean(false);
        mOnUiThread.clearClientCertPreferences(new Runnable() {
            @Override
            public void run() {
                cleared.set(true);
            }
        });
        // Wait until clearclientcertpreferences clears the preferences. Generally this is just a
        // thread hopping.
        new PollingCheck(WebViewTest.TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return cleared.get();
            }
        }.run();
    }

    // Note that this class is not thread-safe.
    static class SslErrorWebViewClient extends WaitForLoadedClient {
        private boolean mWasOnReceivedSslErrorCalled;
        private String mErrorUrl;
        private int mErrorCode;

        public SslErrorWebViewClient(WebViewOnUiThread onUiThread) {
            super(onUiThread);
        }
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            mWasOnReceivedSslErrorCalled = true;
            mErrorUrl = error.getUrl();
            handler.proceed();
        }
        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                String failingUrl) {
            mErrorCode = errorCode;
        }
        public void resetWasOnReceivedSslErrorCalled() {
            mWasOnReceivedSslErrorCalled = false;
        }
        public boolean wasOnReceivedSslErrorCalled() {
            return mWasOnReceivedSslErrorCalled;
        }
        public String errorUrl() {
            return mErrorUrl;
        }
        public int onReceivedErrorCode() {
            return mErrorCode;
        }
    }

    // Modifies the default behavior of SslErrorWebViewClient to accept the request, and provide
    // certs.
    static class ClientCertWebViewClient extends SslErrorWebViewClient {
        // User Actions
        public static final int PROCEED = 1;
        public static final int CANCEL = 2;
        public static final int IGNORE = 3;

        private final boolean mKeyFromAndroidKeystore;

        private int mClientCertRequests;
        private int mAction = PROCEED;
        private Principal[] mPrincipals;

        public ClientCertWebViewClient(WebViewOnUiThread onUiThread) {
            this(onUiThread, false);
        }

        public ClientCertWebViewClient(WebViewOnUiThread onUiThread,
                boolean keyFromAndroidKeystore) {
            super(onUiThread);
            mKeyFromAndroidKeystore = keyFromAndroidKeystore;
        }

        public int getClientCertRequestCount() {
            return mClientCertRequests;
        }

        public Principal[] getPrincipals() {
            return mPrincipals;
        }

        public void resetClientCertRequestCount() {
            mClientCertRequests = 0;
        }

        public void setAction(int action) {
            mAction = action;
        }

        @Override
        public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
            mClientCertRequests++;
            mPrincipals = request.getPrincipals();
            if (mAction == IGNORE) {
                request.ignore();
                return;
            }
            if (mAction == CANCEL) {
                request.cancel();
                return;
            }
            if (mAction == PROCEED) {
                try {
                    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    X509Certificate[] certChain =  new X509Certificate[] {
                            (X509Certificate) certFactory.generateCertificate(
                                    new ByteArrayInputStream(FAKE_RSA_USER_1)),
                            (X509Certificate) certFactory.generateCertificate(
                                    new ByteArrayInputStream(FAKE_RSA_CA_1))
                    };
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    PrivateKey key = keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));

                    if (mKeyFromAndroidKeystore) {
                        // Key needs to be backed by Android Keystore -- import it there.
                        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                        keyStore.load(null);
                        Log.d(LOGTAG, "Importing private key into Android Keystore...");
                        keyStore.setEntry(
                                "fake1",
                                new KeyStore.PrivateKeyEntry(key, certChain),
                                null);

                        key = (PrivateKey) keyStore.getKey("fake1", null);
                        Log.i(LOGTAG, "Imported private key into Android Keystore. key: " + key);
                    }

                    request.proceed(key, certChain);
                    return;
                } catch (Exception e) {
                    Log.e(LOGTAG,  "Fatal error" + e);
                }
            }
            throw new IllegalStateException("unknown action");
        }
    }
}
