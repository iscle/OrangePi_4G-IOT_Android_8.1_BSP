/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * <p>
 * A complete Mail activity instance. This is the toplevel class that creates the views and handles
 * the activity lifecycle.</p>
 *
 * <p>This class is abstract to allow many other activities to be quickly created by subclassing
 * this activity and overriding a small subset of the life cycle methods: for example
 * ComposeActivity and CreateShortcutActivity.</p>
 *
 * <p>In the Gmail codebase, this was called GmailBaseActivity</p>
 *
 */
public abstract class AbstractMailActivity extends AppCompatActivity implements RestrictedActivity {

    private final UiHandler mUiHandler = new UiHandler();

    // STOPSHIP: ship with false
    private static final boolean STRICT_MODE = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build());
        }

        super.onCreate(savedInstanceState);
        mUiHandler.setEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mUiHandler.setEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mUiHandler.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mUiHandler.setEnabled(true);
    }

    @Override
    public Context getActivityContext() {
        return this;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        // Supplementally dump the contents of the OS LoaderManager and FragmentManager.
        // Both are still possible to use, and the supportlib dump reads from neither.
        getLoaderManager().dump(prefix, fd, writer, args);
        getFragmentManager().dump(prefix, fd, writer, args);
    }

}
