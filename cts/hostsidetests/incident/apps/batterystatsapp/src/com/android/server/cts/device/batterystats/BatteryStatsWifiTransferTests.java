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
package com.android.server.cts.device.batterystats;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

public class BatteryStatsWifiTransferTests {
    private static final String TAG = "BatteryStatsWifiTransferTests";

    private static final int READ_BUFFER_SIZE = 4096;

    /** Server to send requests to. */
    private static final String SERVER_URL = "https://developer.android.com/index.html";

    public static String download(String requestCode) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SERVER_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept-Encoding", "identity"); // Disable compression.

            InputStream in = new BufferedInputStream(conn.getInputStream());
            byte[] data = new byte[READ_BUFFER_SIZE];

            int total = 0;
            int count;
            while ((count = in.read(data)) != -1) {
                total += count;
            }
            Log.i(TAG, String.format("request %s d=%d", requestCode, total));
        } catch (IOException e) {
            Log.i(TAG, e.toString());
            return "Caught exception";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
   }


   public static String upload() {
        HttpURLConnection conn = null;
        try {
            // Append a long query string.
            char[] queryChars = new char[2*1024];
            Arrays.fill(queryChars, 'a');
            URL url = new URL(SERVER_URL + "?" + new String(queryChars));
            conn = (HttpURLConnection) url.openConnection();
            InputStream in = conn.getInputStream();
            in.close();
        } catch (IOException e) {
            return "IO exception";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
   }
}
