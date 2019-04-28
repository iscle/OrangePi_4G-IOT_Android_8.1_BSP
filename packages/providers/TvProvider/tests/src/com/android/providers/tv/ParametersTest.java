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

package com.android.providers.tv;

import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

public class ParametersTest extends AndroidTestCase {
    private static final String FAKE_INPUT_ID = "ParametersTest";
    private static final String PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";
    private static final String PERMISSION_ACCESS_ALL_EPG_DATA =
            "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;
    private MockTvProviderContext mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResolver = new MockContentResolver();
        mResolver.addProvider(Settings.AUTHORITY, new MockContentProvider() {
            @Override
            public Bundle call(String method, String request, Bundle args) {
                return new Bundle();
            }
        });

        mProvider = new TvProviderForTesting();
        mResolver.addProvider(TvContract.AUTHORITY, mProvider);

        mContext = new MockTvProviderContext(mResolver, getContext());
        setContext(mContext);

        final ProviderInfo info = new ProviderInfo();
        info.authority = TvContract.AUTHORITY;
        mProvider.attachInfoForTesting(getContext(), info);
        Utils.clearTvProvider(mResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.shutdown();
        super.tearDown();
    }

    private ContentValues createDummyChannelValues(int searchable, boolean preview) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        values.put(Channels.COLUMN_INTERNAL_PROVIDER_ID, "ID-4321");
        values.put(Channels.COLUMN_TYPE, preview ? Channels.TYPE_PREVIEW : Channels.TYPE_OTHER);
        values.put(Channels.COLUMN_SERVICE_TYPE, Channels.SERVICE_TYPE_AUDIO_VIDEO);
        values.put(Channels.COLUMN_DISPLAY_NUMBER, "1");
        values.put(Channels.COLUMN_VIDEO_FORMAT, Channels.VIDEO_FORMAT_480P);
        values.put(Channels.COLUMN_SEARCHABLE, searchable);

        return values;
    }

    private void verifyChannelCountWithPreview(int expectedCount, boolean preview) {
        Uri channelUri = Channels.CONTENT_URI.buildUpon()
                .appendQueryParameter(TvContract.PARAM_PREVIEW, String.valueOf(preview)).build();
        verifyChannelCount(channelUri, expectedCount);
    }

    private void verifyChannelCount(Uri channelUri, int expectedCount) {
        try (Cursor cursor = mResolver.query(
                channelUri, new String[] {Channels.COLUMN_TYPE}, null, null, null)) {
            assertNotNull(cursor);
            assertEquals("Query:{Uri=" + channelUri + "}", expectedCount, cursor.getCount());
        }
    }

    private void insertChannelWithPackageName(ContentValues values, String packageName) {
        mProvider.callingPackage = packageName;
        mResolver.insert(Channels.CONTENT_URI, values);
        mProvider.callingPackage = null;
    }

    private void verifyChannelQuery(Uri channelsUri, int expectedCount, boolean expectedException) {
        try {
            verifyChannelCount(channelsUri, expectedCount);
            if (expectedException) {
                fail("Query:{Uri=" + channelsUri + "} should throw exception");
            }
        } catch (SecurityException e) {
            if (!expectedException) {
                fail("Query failed due to:" + e);
            }
        }
    }

    private void verifyChannelUpdate(Uri channelsUri, ContentValues values,
            int expectedCount, boolean expectedException) {
        try {
            int count = mResolver.update(channelsUri, values, null, null);
            if (expectedException) {
                fail("Update:{Uri=" + channelsUri + "} should throw exception");
            }
            assertEquals(expectedCount, count);
        } catch (SecurityException e) {
            if (!expectedException) {
                fail("Update failed due to:" + e);
            }
        }
    }

    private void verifyChannelDelete(Uri channelsUri, int expectedCount,
            boolean expectedException) {
        try {
            int count = mResolver.delete(channelsUri, null, null);
            if (expectedException) {
                fail("Delete:{Uri=" + channelsUri + "} should throw exception");
            }
            assertEquals(expectedCount, count);
        } catch (SecurityException e) {
            if (!expectedException) {
                fail("Delete failed due to:" + e);
            }
        }
    }

    public void testTypePreviewQueryChannel() {
        // Check if there is not any preview and non-preview channels.
        verifyChannelCountWithPreview(0, true);
        verifyChannelCountWithPreview(0, false);
        // Insert one preview channel and then check if the count of preview channels is 0 and the
        // count of non-preview channels is 0.
        ContentValues previewChannelContentValue = createDummyChannelValues(1, true);
        mResolver.insert(Channels.CONTENT_URI, previewChannelContentValue);
        verifyChannelCountWithPreview(1, true);
        verifyChannelCountWithPreview(0, false);
        // Insert one non-preview channel and then check if the count of preview channels or
        // non-preview channels are both 1.
        ContentValues nonPreviewChannelContentValue = createDummyChannelValues(1, false);
        mResolver.insert(Channels.CONTENT_URI, nonPreviewChannelContentValue);
        verifyChannelCountWithPreview(1, true);
        verifyChannelCountWithPreview(1, false);
    }

    public void testPackageNameOperateChannels() {
        String packageName = getContext().getPackageName();
        String otherPackageName = packageName + ".other";
        Uri ownPackageChannelsUri = Channels.CONTENT_URI.buildUpon()
                .appendQueryParameter(TvContract.PARAM_PACKAGE, packageName).build();
        Uri otherPackageChannelsUri = Channels.CONTENT_URI.buildUpon()
                .appendQueryParameter(TvContract.PARAM_PACKAGE, otherPackageName).build();

        // Tests with PERMISSION_ACCESS_ALL_EPG_DATA.
        ContentValues values = createDummyChannelValues(1, false);
        insertChannelWithPackageName(values, packageName);
        verifyChannelQuery(ownPackageChannelsUri, 1, false);
        ContentValues otherValues1 = createDummyChannelValues(1, false);
        ContentValues otherValues2 = createDummyChannelValues(0, false);
        insertChannelWithPackageName(otherValues1, otherPackageName);
        verifyChannelQuery(otherPackageChannelsUri, 1, false);
        insertChannelWithPackageName(otherValues2, otherPackageName);
        verifyChannelQuery(otherPackageChannelsUri, 2, false);
        values.remove(Channels.COLUMN_TYPE);
        values.put(Channels.COLUMN_DISPLAY_NUMBER, "2");
        verifyChannelUpdate(ownPackageChannelsUri, values, 1, false);
        verifyChannelDelete(ownPackageChannelsUri, 1, false);
        otherValues1.remove(Channels.COLUMN_TYPE);
        otherValues1.put(Channels.COLUMN_DISPLAY_NUMBER, "2");
        verifyChannelUpdate(otherPackageChannelsUri, otherValues1, 2, false);
        verifyChannelDelete(otherPackageChannelsUri, 2, false);

        // Tests with PERMISSION_READ_TV_LISTINGS, without PERMISSION_ACCESS_ALL_EPG_DATA.
        mContext.grantOrRejectPermission(PERMISSION_ACCESS_ALL_EPG_DATA, false);
        values = createDummyChannelValues(1, false);
        insertChannelWithPackageName(values, packageName);
        verifyChannelQuery(ownPackageChannelsUri, 1, false);
        otherValues1 = createDummyChannelValues(1, false);
        otherValues2 = createDummyChannelValues(1, false);
        ContentValues otherValues3 = createDummyChannelValues(0, false);
        insertChannelWithPackageName(otherValues1, otherPackageName);
        verifyChannelQuery(otherPackageChannelsUri, 1, false);
        insertChannelWithPackageName(otherValues2, otherPackageName);
        verifyChannelQuery(otherPackageChannelsUri, 2, false);
        insertChannelWithPackageName(otherValues3, otherPackageName);
        verifyChannelQuery(otherPackageChannelsUri, 2, false);
        values.remove(Channels.COLUMN_TYPE);
        values.put(Channels.COLUMN_DISPLAY_NUMBER, "2");
        verifyChannelUpdate(ownPackageChannelsUri, values, 1, false);
        verifyChannelDelete(ownPackageChannelsUri, 1, false);
        otherValues1.remove(Channels.COLUMN_TYPE);
        otherValues1.remove(Channels.COLUMN_PACKAGE_NAME);
        otherValues1.put(Channels.COLUMN_DISPLAY_NUMBER, "2");
        verifyChannelUpdate(otherPackageChannelsUri, otherValues1, 0, false);
        verifyChannelDelete(otherPackageChannelsUri, 0, false);

        // Tests without PERMISSION_ACCESS_ALL_EPG_DATA and PERMISSION_READ_TV_LISTINGS.
        mContext.grantOrRejectPermission(PERMISSION_READ_TV_LISTINGS, false);
        values = createDummyChannelValues(1, false);
        insertChannelWithPackageName(values, packageName);
        verifyChannelQuery(ownPackageChannelsUri, 1, false);
        otherValues1 = createDummyChannelValues(1, false);
        insertChannelWithPackageName(otherValues1, otherPackageName);
        verifyChannelQuery(otherPackageChannelsUri, 0, false);
        values.remove(Channels.COLUMN_TYPE);
        values.put(Channels.COLUMN_DISPLAY_NUMBER, "2");
        verifyChannelUpdate(ownPackageChannelsUri, values, 1, false);
        verifyChannelDelete(ownPackageChannelsUri, 1, false);
        otherValues1.remove(Channels.COLUMN_TYPE);
        otherValues1.remove(Channels.COLUMN_PACKAGE_NAME);
        otherValues1.put(Channels.COLUMN_DISPLAY_NUMBER, "2");
        verifyChannelUpdate(otherPackageChannelsUri, otherValues1, 0, false);
        verifyChannelDelete(otherPackageChannelsUri, 0, false);
    }
}
