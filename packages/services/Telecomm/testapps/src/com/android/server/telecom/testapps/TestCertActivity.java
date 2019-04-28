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
package com.android.server.telecom.testapps;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.telephony.ImsiEncryptionInfo;
import android.text.TextUtils;
import android.util.Log;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

import android.util.Base64;

public class TestCertActivity extends Activity {

    private EditText mCertUrlView;
    public static final String LOG_TAG = "TestCertActivity";

    private ProgressDialog progressDialog;
    private ArrayList<String> keyList = new ArrayList<String>();

    // URL to get the json
    private String mURL = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.testcert_main);
        findViewById(R.id.get_key_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new GetKeys().execute();
            }
        });

        mCertUrlView = (EditText) findViewById(R.id.text);
        mCertUrlView.setText(mURL);
    }

    /**
     * Class to get json by making HTTP call
     */
    private class GetKeys extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(TestCertActivity.this);
            progressDialog.setMessage("Downloading...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        public String getCertificateList() {
            String response = null;
            String mURL = mCertUrlView.getText().toString();
            try {
                URL url = new URL(mURL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                // read the response
                InputStream in = new BufferedInputStream(conn.getInputStream());
                response = convertToString(in);
            } catch (ProtocolException e) {
                Log.e(LOG_TAG, "ProtocolException: " + e.getMessage());
            } catch (MalformedURLException e) {
                Log.e(LOG_TAG, "MalformedURLException: " + e.getMessage());
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException: " + e.getMessage());
            } catch (Exception e) {
                Log.e(LOG_TAG, "Exception: " + e.getMessage());
            }
            return response;
        }

        private String convertToString(InputStream is) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();

            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return sb.toString();
        }

        private void savePublicKey(String key, int type, String identifier) {
            byte[] keyBytes = Base64.decode(key.getBytes(), Base64.DEFAULT);
            final TelephonyManager telephonyManager =
                    (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

            String mcc = "";
            String mnc = "";
            String networkOperator = telephonyManager.getNetworkOperator();

            if (!TextUtils.isEmpty(networkOperator)) {
                mcc = networkOperator.substring(0, 3);
                mnc = networkOperator.substring(3);
                Log.i(LOG_TAG, "using values for mnc, mcc: " + mnc + "," + mcc);
            }

            ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo(mcc,
                    mnc, type, identifier, keyBytes, new Date());
            telephonyManager.setCarrierInfoForImsiEncryption(imsiEncryptionInfo);
            keyList.add(imsiEncryptionInfo.getKeyType() + "," +
                    imsiEncryptionInfo.getKeyIdentifier());
            Log.i(LOG_TAG,"calling telephonymanager complete");
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            // Making a request to url and getting response
            String jsonStr = getCertificateList();
            Log.d(LOG_TAG, "Response from url: " + jsonStr);

            if (jsonStr != null) {
                try {
                    JSONObject jsonObj = new JSONObject(jsonStr);
                    // Getting JSON Array node
                    JSONArray certificates = jsonObj.getJSONArray("certificates");

                    // looping through the certificates
                    for (int i = 0; i < certificates.length(); i++) {
                        JSONObject cert = certificates.getJSONObject(i);
                        String key = cert.getString("key");
                        int type = cert.getInt("type");
                        String identifier = cert.getString("identifier");
                        savePublicKey(key, type, identifier);
                    }
                } catch (final JSONException e) {
                    Log.e(LOG_TAG, "Json parsing error: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    "Json parsing error: " + e.getMessage(),
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }
            } else {
                Log.e(LOG_TAG, "Unable to get JSON from server " + mURL);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                "Unable to get JSON from server!",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            super.onPostExecute(result);
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            ListView listView = (ListView) findViewById(R.id.keylist);
            ArrayAdapter arrayAdapter =
                    new ArrayAdapter(TestCertActivity.this, R.layout.key_list, keyList);
            listView.setAdapter(arrayAdapter);
        }
    }
}


