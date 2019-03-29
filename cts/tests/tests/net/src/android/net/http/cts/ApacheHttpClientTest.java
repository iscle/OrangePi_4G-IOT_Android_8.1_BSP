/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.net.http.cts;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.net.Uri;
import android.test.AndroidTestCase;
import android.webkit.cts.CtsTestServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ApacheHttpClientTest extends AndroidTestCase {

    private static final int NUM_DOWNLOADS = 20;

    private static final int SMALL_DOWNLOAD_SIZE = 100 * 1024;

    private CtsTestServer mWebServer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWebServer = new CtsTestServer(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mWebServer.shutdown();
    }

    public void testExecute() throws Exception {
        downloadMultipleFiles();
    }

    private void downloadMultipleFiles() throws ClientProtocolException, IOException {
        List<HttpResponse> responses = new ArrayList<HttpResponse>();
        for (int i = 0; i < NUM_DOWNLOADS; i++) {
            HttpClient httpClient = new DefaultHttpClient();
            HttpGet request = new HttpGet(getSmallDownloadUrl(i).toString());
            HttpResponse response = httpClient.execute(request);
            responses.add(response);
        }

        for (int i = 0; i < NUM_DOWNLOADS; i++) {
            assertDownloadResponse("Download " + i, SMALL_DOWNLOAD_SIZE, responses.get(i));
        }
    }

    private Uri getSmallDownloadUrl(int index) {
        return Uri.parse(mWebServer.getTestDownloadUrl("cts-small-download-" + index,
                SMALL_DOWNLOAD_SIZE));
    }

    private void assertDownloadResponse(String message, int expectedNumBytes, HttpResponse response)
            throws IllegalStateException, IOException {
        byte[] buffer = new byte[4096];
        assertEquals(200, response.getStatusLine().getStatusCode());

        InputStream stream = response.getEntity().getContent();
        int numBytes = 0;
        while (true) {
            int bytesRead = stream.read(buffer);
            if (bytesRead < 0) {
                break;
            } else {
                numBytes += bytesRead;
            }
        }
        assertEquals(message, expectedNumBytes, numBytes);
    }
}
