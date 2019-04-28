/*******************************************************************************
 *      Copyright (C) 2013 Google Inc.
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

package com.android.mail.analytics;

import android.app.Activity;
import android.app.ActivityManager;

/**
 * Mail wrapper for analytics libraries. Libraries should implement {@link Tracker}, and app
 * configurations that want to enable analytics should call {@link #setTracker(Tracker)} as soon
 * as possible upon application start.
 * <p>
 * {@link #getInstance()} will always return an object, but if the app has not yet (or will not
 * ever) set its own tracker instance, method calls on that tracker will be stubbed out.
 *
 */
public final class Analytics {

    public static final String EVENT_CATEGORY_MENU_ITEM = "menu_item";

    /**
     * The email provider for this account.
     */
    public static final int CD_INDEX_ACCOUNT_EMAIL_PROVIDER = 1;

    public static final int CD_INDEX_ACCOUNT_COUNT = 2;

    public static final int CD_INDEX_SENDER_IMAGES_ENABLED = 3;

    /** @deprecated Attachment Previews entirely removed from Conversation List */
    public static final int CD_INDEX_ATTACHMENT_PREVIEWS_ENABLED = 4;

    public static final int CD_INDEX_INBOX_TYPE = 5;

    public static final int CD_INDEX_INBOX_SECTIONS_ENABLED = 6;

    public static final int CD_INDEX_REPLY_ALL_SETTING = 7;

    public static final int CD_INDEX_AUTO_ADVANCE = 8;

    /**
     * Custom dimension in analytics to describe if the user already interacted with the app.
     * The value is one of
     * <ul>
     *     <li>{@link #CD_VALUE_USER_RETENTION_TYPE_NEW},</li>
     *     <li>{@link #CD_VALUE_USER_RETENTION_TYPE_UPGRADING} or</li>
     *     <li>{@link @CD_VALUE_USER_RETENTION_TYPE_RETURNING}</li>
     * </ul>
     */
    public static final int CD_INDEX_USER_RETENTION_TYPE = 9;

    /**
     * Value for {@link #CD_INDEX_USER_RETENTION_TYPE} meaning that the user has never used the
     * Mail app anywhere before.
     */
    public static final String CD_VALUE_USER_RETENTION_TYPE_NEW = "new";
    /**
     * Value for {@link #CD_INDEX_USER_RETENTION_TYPE} meaning that the user has used an older
     * version of the Mail app before.
     */
    public static final String CD_VALUE_USER_RETENTION_TYPE_UPGRADING = "upgrading";

    /**
     * Value for {@link #CD_INDEX_USER_RETENTION_TYPE} meaning that the user has already this
     * version of the Mail app before.
     */
    public static final String CD_VALUE_USER_RETENTION_TYPE_RETURNING = "returning";

    private static Tracker sInstance;

    private Analytics() {
    }

    public static Tracker getInstance() {
        synchronized (Analytics.class) {
            if (sInstance == null) {
                sInstance = new StubTracker();
            }
        }
        return sInstance;
    }

    public static void setTracker(Tracker t) {
        synchronized (Analytics.class) {
            sInstance = t;
        }
    }

    public static boolean isLoggable() {
        return !ActivityManager.isUserAMonkey() && !ActivityManager.isRunningInTestHarness();
    }

    private static final class StubTracker implements Tracker {

        @Override
        public void activityStart(Activity a) {}

        @Override
        public void activityStop(Activity a) {}

        @Override
        public void sendEvent(String category, String action, String label, long value) {}

        @Override
        public void sendTiming(String category, long millis, String name, String label) {}

        @Override
        public void sendMenuItemEvent(String category, int itemResId, String label, long value) {}

        @Override
        public void sendView(String view) {}

        @Override
        public void setCustomDimension(int index, String value) {}

        @Override
        public void setCustomMetric(int index, Long value) {}

        @Override
        public void debugDispatchNow() {}

        @Override
        public void setEmail(String emailAddress, String accountManagerType) {}
    }

}
