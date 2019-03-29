/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.media.cts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * A class that implements IConnectionStatus interface
 * to report and test connection status.
 */
public class ConnectionStatus implements IConnectionStatus {

    private static final String TAG = "ConnectionStatus";

    private ConnectivityManager mConnectivityManager;

    public ConnectionStatus(Context context) {
        mConnectivityManager =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public String getNotConnectedReason() {
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            return networkInfo.getReason();
        } else {
            return "Network info is not available.";
        }
    }

    public boolean isAvailable() {
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        return (networkInfo != null) && networkInfo.isAvailable();
    }

    public boolean isConnected() {
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        return (networkInfo != null) && networkInfo.isConnected();
    }

    /**
     * Print lines.
     *
     * Print lines in ArrayList<String>
     *
     * @param lines ArrayList<String>
     */
    private void printLines(final ArrayList<String> lines) {
        for (String line : lines) {
            Log.d(TAG, line);
        }
    }

    /**
     * Perform ping test.
     *
     * @param server Server to ping
     * @return true for success, false for failure
     */
    private boolean pingTest(String server) {
        final long PING_RETRIES = 10;
        if (server == null || server.isEmpty()) {
            Log.e(TAG, "Null or empty server name to ping.");
            return false;
        }

        int retries = 0;
        while (retries++ <= PING_RETRIES) {
            try {
                Log.d(TAG, "Try pinging " + server);
                // -c: ping 5 times, -w: limit to 20 seconds
                Process p = Runtime.getRuntime().exec("ping -c 5 -w 20 " + server);
                ArrayList<String> lines = new ArrayList<String>();
                String line;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                printLines(lines);
                lines.clear();

                // print error if any
                reader = new BufferedReader(
                        new InputStreamReader(p.getErrorStream()));
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                printLines(lines);
                if (p.waitFor() == 0) {
                    return true;
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ping not run: Unknown Host");
                break;
            } catch (IOException e) {
                // e.g. if ping is not on the device
                Log.e(TAG, "ping not found: IOException");
                break;
            } catch (InterruptedException e) {
                Log.e(TAG, "ping failed: InterruptedException");
                break;
            }
        }

        // ping test timeout
        return false;
    }

    public void testConnection(Uri uri) {
        if (pingTest(uri.getHost())) {
            Log.d(TAG, "Successfully pinged " + uri.getHost());
        } else {
            Log.e(TAG, "Failed to ping " + uri.getHost());
        }
    }
}

