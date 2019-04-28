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

package com.android.tests.connectivity.uid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import java.io.*;
import java.lang.Thread;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConnectivityTestActivity extends Activity {

    ConnectivityManager connectivityManager;
    NetworkInfo netInfo;
    public static final String TAG = "ConnectivityUIDTest";
    private static final String RESULT = "result";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy policy =
                new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    public void onResume() {
        super.onResume();
        boolean conn = checkNow(this.getApplicationContext());
        Intent returnIntent = new Intent();
        returnIntent.putExtra(RESULT, conn);
        setResult(RESULT_OK, returnIntent);
        finish();
    }

    public boolean checkNow(Context con) {
        try{
            connectivityManager = (ConnectivityManager)
                    con.getSystemService(Context.CONNECTIVITY_SERVICE);
            netInfo = connectivityManager.getActiveNetworkInfo();
            return netInfo.isConnected() && httpRequest();
        } catch(Exception e) {
            Log.e(TAG, "CheckConnectivity exception: ", e);
        }

        return false;
    }

    private boolean httpRequest() throws IOException {
        URL targetURL = new URL("http://www.google.com/generate_204");
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) targetURL.openConnection();
            urlConnection.connect();
            int respCode = urlConnection.getResponseCode();
            return (respCode == 204);
        } catch (IOException e) {
            Log.e(TAG, "Checkconnectivity exception: ", e);
        }
        return false;
    }
}
