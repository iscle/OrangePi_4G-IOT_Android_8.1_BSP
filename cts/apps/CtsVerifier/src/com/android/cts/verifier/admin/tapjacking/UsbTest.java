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

package com.android.cts.verifier.admin.tapjacking;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsbTest extends PassFailButtons.Activity {

    private View mOverlay;
    private Button mEscalateBtn;
    private boolean auth = false;
    private boolean first_attempt = true;

    public static final String LOG_TAG = "UsbTest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tapjacking);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.usb_tapjacking_test,
                R.string.usb_tapjacking_test_info, -1);

        //initialise the escalate button and set a listener
        mEscalateBtn = (Button) findViewById(R.id.tapjacking_btn);
        mEscalateBtn.setEnabled(true);
        mEscalateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(v.getContext())) {
                    // show settings permission
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                }

                if (!Settings.canDrawOverlays(v.getContext())) {
                    Toast.makeText(v.getContext(), R.string.usb_tapjacking_error_toast2,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if(!first_attempt && !auth){
                    Toast.makeText(v.getContext(),
                            R.string.usb_tapjacking_error_toast,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                first_attempt = false;
                escalatePriv();
            }
        });

        // Ensure there is a binary at ie: cts/apps/CtsVerifier/assets/adb
        AssetManager assetManager = getAssets();
        try {
            //if the adb doesn't exist add it
            File adb = new File(this.getFilesDir() + "/adb");
            InputStream myInput = assetManager.open("adb");
            OutputStream myOutput = new FileOutputStream(adb);

            byte[] buffer = new byte[1024];
            int length;

            while ((length = myInput.read(buffer)) > 0) {
                myOutput.write(buffer, 0, length);
            }
            myInput.close();
            myOutput.flush();
            myOutput.close();

            //Set execute bit
            adb.setExecutable(true);
        } catch (Exception e) {
            Log.e(LOG_TAG, "onCreate " + e.toString());
        }
    }

    private void escalatePriv() {
        try {
            File adb = new File(this.getFilesDir() + "/adb");
            //Check for unauthorised devices to connect to
            ProcessBuilder builder = new ProcessBuilder(
                    adb.getAbsolutePath(), "devices");
            builder.directory(this.getFilesDir());

            Map<String, String> env = builder.environment();
            env.put("HOME", this.getFilesDir().toString());
            env.put("TMPDIR", this.getFilesDir().toString());

            Process adb_devices = builder.start();

            String output = getDevices(adb_devices.getInputStream());
            Log.d(LOG_TAG, output);
            int rc = adb_devices.waitFor();

            //CASE: USB debugging not enabled and/or adbd not listening on a tcp port
            if (output.isEmpty()) {
                Log.d(LOG_TAG,
                        "USB debugging not enabled and/or adbd not listening on a tcp port");
            }

            //CASE: We have a tcp port, however the device hasn't been authorized
            if (output.toLowerCase().contains("unauthorized".toLowerCase())) {
                //If we're here, then we most likely we have a RSA prompt
                showOverlay();
                Log.d(LOG_TAG, "We haven't been authorized yet...");
            } else if(output.toLowerCase().contains("device".toLowerCase())){
                Log.d(LOG_TAG, "We have authorization");
                hideOverlay();
                auth = true;
            } else {
                hideOverlay();
                Log.d(LOG_TAG, "The port is probably in use by another process");
                auth = false;
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "escalatePriv " + e.toString());
        }

        //Check if we have been authorized and set auth
        if(!auth){
            Log.d(LOG_TAG, "We're still not authenticated yet");
        }
    }

    private static String getDevices(InputStream s) throws Exception {
        String[] terms = {"device", // We are authorized to use this device
                "unauthorized", // We need to authenticate adb server to this daemon
                "offline" }; // Device is most probably in use


        BufferedReader br = new BufferedReader(new InputStreamReader(s));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null)
        {
            sb.append(line).append("\n");
        }

        br.close();
        Log.d(LOG_TAG, sb.toString());
        for(String t : terms) {
            String pattern = "\\b" + t + "\\b";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(sb.toString());
            if (m.find()){
                return sb.toString();
            }
        }

        return "";
    }

    private void showOverlay() {
        if (mOverlay != null)
            return;

        WindowManager windowManager = (WindowManager) getApplicationContext().
                getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.x = 0;
        layoutParams.y = dipToPx(-46);
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.windowAnimations = 0;

        mOverlay = View.inflate(getApplicationContext(), R.layout.usb_tapjacking_overlay,
                null);
        windowManager.addView(mOverlay, layoutParams);
    }

    private void hideOverlay() {
        if (mOverlay != null) {
            WindowManager windowManager = (WindowManager) getApplicationContext().getSystemService(
                    WINDOW_SERVICE);
            windowManager.removeViewImmediate(mOverlay);
            mOverlay = null;
        }
    }

    private int dipToPx(int dip) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip,
                getResources().getDisplayMetrics()));
    }

    @Override
    public void onResume(){
        super.onResume();
        hideOverlay();

        //Check if we've been authorized
        if(!first_attempt) {
            try {
                File adb = new File(this.getFilesDir() + "/adb");
                //Check for unauthorised devices to connect to
                ProcessBuilder builder = new ProcessBuilder(adb.getAbsolutePath(),
                        "devices");
                builder.directory(this.getFilesDir());

                Map<String, String> env = builder.environment();
                env.put("HOME", this.getFilesDir().toString());
                env.put("TMPDIR", this.getFilesDir().toString());

                Process adb_devices = builder.start();

                String output = getDevices(adb_devices.getInputStream());
                Log.d(LOG_TAG, output);
                int rc = adb_devices.waitFor();

                if (output.toLowerCase().contains("unauthorized".toLowerCase())) {
                    //The user didn't authorize the app, prompt for a app restart
                    auth = false;
                }else if(output.toLowerCase().contains("device".toLowerCase())){
                    //The user has authorized the app
                    auth = true;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, e.toString());
            } finally {
                escalatePriv();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        hideOverlay();
    }
}
