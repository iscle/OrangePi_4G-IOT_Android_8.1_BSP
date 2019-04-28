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
package libcore.tzdata.update_test_app.installupdatetestapp;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import libcore.icu.ICU;
import libcore.util.ZoneInfoDB;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String EXTRA_REQUIRED_HASH = "REQUIRED_HASH";
    private static final String EXTRA_VERSION_NUMBER = "VERSION";

    private EditText actionEditText;
    private EditText versionEditText;
    private EditText contentPathEditText;
    private EditText requiredHashEditText;
    private TextView logView;

    private ExecutorService executor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button triggerInstallButton = (Button) findViewById(R.id.trigger_install_button);
        triggerInstallButton.setOnClickListener(this);

        actionEditText = (EditText) findViewById(R.id.action);
        versionEditText = (EditText) findViewById(R.id.version);
        contentPathEditText = (EditText) findViewById(R.id.content_path);
        requiredHashEditText = (EditText) findViewById(R.id.required_hash);
        logView = (TextView) findViewById(R.id.log);
        executor = Executors.newFixedThreadPool(1);
        logString("Active libcore version: "
                + ZoneInfoDB.getInstance().getVersion());
        logString("Active icu4c version: "
                + ICU.getTZDataVersion());
        logString("Active icu4j version: "
                + android.icu.util.TimeZone.getTZDataVersion());
    }

    @Override
    public void onClick(View v) {
        final String action = actionEditText.getText().toString();
        final String contentPath = contentPathEditText.getText().toString();
        final String version = versionEditText.getText().toString();
        final String requiredHash = requiredHashEditText.getText().toString();

        new AsyncTask<Void, String, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                final File contentFile = new File(contentPath);
                File tempDir = new File(getFilesDir(), "temp");
                if (!tempDir.exists() && !tempDir.mkdir()) {
                    publishProgress("Unable to create: " + tempDir);
                    return null;
                }

                File copyOfContentFile;
                try {
                    copyOfContentFile = File.createTempFile("content", ".tmp", tempDir);
                    copyFile(contentFile, copyOfContentFile);
                } catch (IOException e) {
                    publishProgress("Error", exceptionToString(e));
                    return null;
                }
                publishProgress("Created copy of " + contentFile + " at " + copyOfContentFile);

                try {
                    sendIntent(copyOfContentFile, action, version, requiredHash);
                } catch (Exception e) {
                    publishProgress("Error", exceptionToString(e));
                }
                publishProgress("Update intent sent successfully");
                return null;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                for (String message : values) {
                    addToLog(message, null);
                }
            }
        }.executeOnExecutor(executor);
    }

    private void sleep(long millisDelay) {
        try {
            Thread.sleep(millisDelay);
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    private void sendIntent(
            File contentFile, String action, String version, String required) {
        Intent i = new Intent();
        i.setAction(action);
        Uri contentUri =
                FileProvider.getUriForFile(
                        getApplicationContext(), "libcore.tzdata.update_test_app.fileprovider",
                        contentFile);
        i.setData(contentUri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.putExtra(EXTRA_VERSION_NUMBER, version);
        i.putExtra(EXTRA_REQUIRED_HASH, required);
        sendBroadcast(i);
    }

    private void addToLog(String message, Exception e) {
        logString(message);
        if (e != null) {
            String text = exceptionToString(e);
            logString(text);
        }
    }

    private void logString(String value) {
        logView.append(new Date() + " " + value + "\n");
        android.text.Layout layout = logView.getLayout();
        if (layout != null) {
            int scrollAmount =
                    layout.getLineTop(logView.getLineCount()) - logView.getHeight();
            logView.scrollTo(0, scrollAmount);
        }
    }

    private static String exceptionToString(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.getBuffer().toString();
    }

    private static void copyFile(File from, File to) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        try (
                FileInputStream in = new FileInputStream(from);
                FileOutputStream out = new FileOutputStream(to)
        ) {
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        }
    }
}
