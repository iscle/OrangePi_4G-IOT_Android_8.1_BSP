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

package com.android.documentsui.services;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import android.app.Notification;

class TestForegroundManager implements FileOperationService.ForegroundManager {

    private int mForegroundId = -1;
    private Notification mForegroundNotification;

    @Override
    public void startForeground(int id, Notification notification) {
        mForegroundId = id;
        mForegroundNotification = notification;
    }

    @Override
    public void stopForeground(boolean cancelNotification) {
        mForegroundId = -1;
        mForegroundNotification = null;
    }

    void assertInForeground() {
        assertNotNull(mForegroundNotification);
    }

    void assertInBackground() {
        assertNull(mForegroundNotification);
    }

    int getForegroundId() {
        return mForegroundId;
    }

    Notification getForegroundNotification() {
        return mForegroundNotification;
    }
}
