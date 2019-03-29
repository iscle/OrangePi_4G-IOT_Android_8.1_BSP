/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts.netstats;

import android.net.TrafficStats;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Used by NetstatsIncidentTest.  Makes some network requests so "dumpsys netstats" will have
 * something to show.
 */
@RunWith(AndroidJUnit4.class)
public class NetstatsDeviceTest {
    private static final String TAG = "NetstatsDeviceTest";

    private static final int NET_TAG = 123123123;

    @Test
    public void testDoNetworkWithoutTagging() throws Exception {
        Log.i(TAG, "testDoNetworkWithoutTagging");

        makeNetworkRequest();
    }

    @Test
    public void testDoNetworkWithTagging() throws Exception {
        Log.i(TAG, "testDoNetworkWithTagging");

        TrafficStats.getAndSetThreadStatsTag(NET_TAG);
        makeNetworkRequest();
    }

    private void makeNetworkRequest() throws Exception {
        final URL url = new URL("http://www.android.com/");
        final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        HttpURLConnection.setFollowRedirects(true);
        try {
            final int status = urlConnection.getResponseCode();

            Log.i(TAG, "Response code from " + url + ": " + status);

            // Doesn't matter what response code we got.  We touched the network, which is enough.
        } finally {
            urlConnection.disconnect();
        }
    }
}
