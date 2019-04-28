/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.util;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A utility class to check the connectivity.
 */
@WorkerThread
public class NetworkUtils {
    private static final String GENERATE_204 = "http://clients3.google.com/generate_204";

    /**
     * Checks if the internet connection is available.
     */
    public static boolean isNetworkAvailable(@Nullable ConnectivityManager connectivityManager) {
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            return false;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(GENERATE_204).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setDefaultUseCaches(false);
            connection.setUseCaches(false);
            if (connection.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                return true;
            }
        } catch (IOException e) {
            // Does nothing.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return false;
    }

    private NetworkUtils() { }
}
