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
package com.android.devicehealth.tests;

import org.junit.Assert;
import org.junit.Before;

import android.content.Context;
import android.os.DropBoxManager;
import android.support.test.InstrumentationRegistry;

abstract class HealthCheckBase {

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    /**
     * Check dropbox service for a particular label and assert if found
     */
    protected void checkCrash(String label) {
        DropBoxManager dropbox = (DropBoxManager) mContext
                .getSystemService(Context.DROPBOX_SERVICE);
        Assert.assertNotNull("Unable access the DropBoxManager service", dropbox);

        long timestamp = 0;
        DropBoxManager.Entry entry = null;
        int crashCount = 0;
        StringBuilder errorDetails = new StringBuilder("Error details:\n");
        while (null != (entry = dropbox.getNextEntry(label, timestamp))) {
            try {
                crashCount++;
                errorDetails.append(label);
                errorDetails.append(": ");
                errorDetails.append(entry.getText(70));
                errorDetails.append("    ...\n");
            } finally {
                entry.close();
            }
            timestamp = entry.getTimeMillis();
        }
        Assert.assertEquals(errorDetails.toString(), 0, crashCount);
    }
}
