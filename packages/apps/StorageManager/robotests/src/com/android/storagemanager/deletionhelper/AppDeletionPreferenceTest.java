/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.deletionhelper;

import android.content.Context;
import com.android.storagemanager.deletionhelper.AppsAsyncLoader.PackageInfo;
import com.android.storagemanager.testing.TestingConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=TestingConstants.MANIFEST, sdk=TestingConstants.SDK_VERSION)
public class AppDeletionPreferenceTest {

    private static final String TEST_PACKAGE_LABEL = "App";
    private static final String TEST_PACKAGE_NAME = "com.package.mcpackageface";
    public static final long KILOBYTE = 1024L;
    public static final long HUNDRED_BYTES = 100L;
    public static final String KB_STRING = "1.00KB";
    public static final String HUNDRED_BYTE_STRING = "100B";
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
    }

    @Test
    public void testPreferenceSummary() {
        PackageInfo app =
                new PackageInfo.Builder()
                        .setDaysSinceLastUse(30)
                        .setDaysSinceFirstInstall(30)
                        .setPackageName(TEST_PACKAGE_NAME)
                        .setSize(KILOBYTE)
                        .setLabel(TEST_PACKAGE_LABEL)
                        .build();
        AppDeletionPreference preference = new AppDeletionPreference(mContext, app);
        preference.updateSummary();

        assertThat(preference.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(preference.getTitle()).isEqualTo(TEST_PACKAGE_LABEL);
        assertThat(preference.getSummary().toString()).isEqualTo("30 days ago");
        assertThat(preference.getItemSize()).isEqualTo(KB_STRING);
    }

    @Test
    public void testTwoDayPreferenceSummary() {
        PackageInfo app =
                new PackageInfo.Builder()
                        .setDaysSinceLastUse(2)
                        .setDaysSinceFirstInstall(30)
                        .setPackageName(TEST_PACKAGE_NAME)
                        .setSize(KILOBYTE)
                        .setLabel(TEST_PACKAGE_LABEL)
                        .build();
        AppDeletionPreference preference = new AppDeletionPreference(mContext, app);
        preference.updateSummary();

        assertThat(preference.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(preference.getTitle()).isEqualTo(TEST_PACKAGE_LABEL);
        assertThat(preference.getSummary().toString()).isEqualTo("2 days ago");
        assertThat(preference.getItemSize()).isEqualTo(KB_STRING);
    }

    @Test
    public void testYesterdayPreferenceSummary() {
        PackageInfo app =
                new PackageInfo.Builder()
                        .setDaysSinceLastUse(1)
                        .setDaysSinceFirstInstall(30)
                        .setPackageName(TEST_PACKAGE_NAME)
                        .setSize(KILOBYTE)
                        .setLabel(TEST_PACKAGE_LABEL)
                        .build();
        AppDeletionPreference preference = new AppDeletionPreference(mContext, app);
        preference.updateSummary();

        assertThat(preference.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(preference.getTitle()).isEqualTo(TEST_PACKAGE_LABEL);
        assertThat(preference.getSummary().toString()).isEqualTo("Yesterday");
        assertThat(preference.getItemSize()).isEqualTo(KB_STRING);
    }

    @Test
    public void testTodayPreferenceSummary() {
        PackageInfo app =
                new PackageInfo.Builder()
                        .setDaysSinceLastUse(0)
                        .setDaysSinceFirstInstall(30)
                        .setPackageName(TEST_PACKAGE_NAME)
                        .setSize(KILOBYTE)
                        .setLabel(TEST_PACKAGE_LABEL)
                        .build();
        AppDeletionPreference preference = new AppDeletionPreference(mContext, app);
        preference.updateSummary();

        assertThat(preference.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(preference.getTitle()).isEqualTo(TEST_PACKAGE_LABEL);
        assertThat(preference.getSummary().toString()).isEqualTo("Today");
        assertThat(preference.getItemSize()).isEqualTo(KB_STRING);
    }

    @Test
    public void testNeverUsedPreferenceSummary() {
        PackageInfo app =
                new PackageInfo.Builder()
                        .setDaysSinceLastUse(AppsAsyncLoader.NEVER_USED)
                        .setDaysSinceFirstInstall(30)
                        .setPackageName(TEST_PACKAGE_NAME)
                        .setSize(KILOBYTE)
                        .setLabel(TEST_PACKAGE_LABEL)
                        .build();
        AppDeletionPreference preference = new AppDeletionPreference(mContext, app);
        preference.updateSummary();

        assertThat(preference.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(preference.getTitle()).isEqualTo(TEST_PACKAGE_LABEL);
        assertThat(preference.getSummary().toString()).isEqualTo("Not used in last year");
        assertThat(preference.getItemSize()).isEqualTo(KB_STRING);
    }

    @Test
    public void testUnknownLastUsePreferenceSummary() {
        PackageInfo app =
                new PackageInfo.Builder()
                        .setDaysSinceLastUse(AppsAsyncLoader.UNKNOWN_LAST_USE)
                        .setDaysSinceFirstInstall(30)
                        .setPackageName(TEST_PACKAGE_NAME)
                        .setSize(KILOBYTE)
                        .setLabel(TEST_PACKAGE_LABEL)
                        .build();

        AppDeletionPreference preference = new AppDeletionPreference(mContext, app);
        preference.updateSummary();

        assertThat(preference.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(preference.getTitle()).isEqualTo(TEST_PACKAGE_LABEL);
        assertThat(preference.getSummary().toString()).isEqualTo("Not sure when last used");
        assertThat(preference.getItemSize()).isEqualTo(KB_STRING);
    }

    @Test
    public void testSizeSummary() {
        PackageInfo app =
                new PackageInfo.Builder()
                        .setDaysSinceLastUse(30)
                        .setDaysSinceFirstInstall(30)
                        .setPackageName(TEST_PACKAGE_NAME)
                        .setSize(HUNDRED_BYTES)
                        .setLabel(TEST_PACKAGE_LABEL)
                        .build();

        AppDeletionPreference preference = new AppDeletionPreference(mContext, app);
        preference.updateSummary();

        assertThat(preference.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(preference.getTitle()).isEqualTo(TEST_PACKAGE_LABEL);
        assertThat(preference.getSummary().toString()).isEqualTo("30 days ago");
        assertThat(preference.getItemSize()).isEqualTo(HUNDRED_BYTE_STRING);
    }
}
