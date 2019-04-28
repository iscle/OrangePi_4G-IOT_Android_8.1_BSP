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

package com.android.tv.data;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.test.filters.SmallTest;

import com.android.tv.testing.ComparatorTester;
import com.android.tv.util.TvInputManagerHelper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Comparator;

/**
 * Tests for {@link Channel}.
 */
@SmallTest
public class ChannelTest {
    // Used for testing TV inputs with invalid input package. This could happen when a TV input is
    // uninstalled while drawing an app link card.
    private static final String INVALID_TV_INPUT_PACKAGE_NAME =
            "com.android.tv.invalid_tv_input";
    // Used for testing TV inputs defined inside of Live TV.
    private static final String LIVE_CHANNELS_PACKAGE_NAME = "com.android.tv";
    // Used for testing a TV input which doesn't have its leanback launcher activity.
    private static final String NONE_LEANBACK_TV_INPUT_PACKAGE_NAME =
            "com.android.tv.none_leanback_tv_input";
    // Used for testing a TV input which has its leanback launcher activity.
    private static final String LEANBACK_TV_INPUT_PACKAGE_NAME =
            "com.android.tv.leanback_tv_input";
    private static final String TEST_APP_LINK_TEXT = "test_app_link_text";
    private static final String PARTNER_INPUT_ID = "partner";
    private static final ActivityInfo TEST_ACTIVITY_INFO = new ActivityInfo();

    private Context mMockContext;
    private Intent mInvalidIntent;
    private Intent mValidIntent;

    @Before
    public void setUp() throws NameNotFoundException {
        mInvalidIntent = new Intent(Intent.ACTION_VIEW);
        mInvalidIntent.setComponent(new ComponentName(INVALID_TV_INPUT_PACKAGE_NAME, ".test"));
        mValidIntent = new Intent(Intent.ACTION_VIEW);
        mValidIntent.setComponent(new ComponentName(LEANBACK_TV_INPUT_PACKAGE_NAME, ".test"));
        Intent liveChannelsIntent = new Intent(Intent.ACTION_VIEW);
        liveChannelsIntent.setComponent(
                new ComponentName(LIVE_CHANNELS_PACKAGE_NAME, ".MainActivity"));
        Intent leanbackTvInputIntent = new Intent(Intent.ACTION_VIEW);
        leanbackTvInputIntent.setComponent(
                new ComponentName(LEANBACK_TV_INPUT_PACKAGE_NAME, ".test"));

        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        Mockito.when(mockPackageManager.getLeanbackLaunchIntentForPackage(
                INVALID_TV_INPUT_PACKAGE_NAME)).thenReturn(null);
        Mockito.when(mockPackageManager.getLeanbackLaunchIntentForPackage(
                LIVE_CHANNELS_PACKAGE_NAME)).thenReturn(liveChannelsIntent);
        Mockito.when(mockPackageManager.getLeanbackLaunchIntentForPackage(
                NONE_LEANBACK_TV_INPUT_PACKAGE_NAME)).thenReturn(null);
        Mockito.when(mockPackageManager.getLeanbackLaunchIntentForPackage(
                LEANBACK_TV_INPUT_PACKAGE_NAME)).thenReturn(leanbackTvInputIntent);

        // Channel.getAppLinkIntent() calls initAppLinkTypeAndIntent() which calls
        // Intent.resolveActivityInfo() which calls PackageManager.getActivityInfo().
        Mockito.doAnswer(new Answer<ActivityInfo>() {
            @Override
            public ActivityInfo answer(InvocationOnMock invocation) {
                // We only check the package name, since the class name can be changed
                // when an intent is changed to an uri and created from the uri.
                // (ex, ".className" -> "packageName.className")
                return mValidIntent.getComponent().getPackageName().equals(
                        ((ComponentName)invocation.getArguments()[0]).getPackageName())
                        ? TEST_ACTIVITY_INFO : null;
            }
        }).when(mockPackageManager).getActivityInfo(Mockito.<ComponentName>any(), Mockito.anyInt());

        mMockContext = Mockito.mock(Context.class);
        Mockito.when(mMockContext.getApplicationContext()).thenReturn(mMockContext);
        Mockito.when(mMockContext.getPackageName()).thenReturn(LIVE_CHANNELS_PACKAGE_NAME);
        Mockito.when(mMockContext.getPackageManager()).thenReturn(mockPackageManager);
    }

    @Test
    public void testGetAppLinkType_NoText_NoIntent() {
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, INVALID_TV_INPUT_PACKAGE_NAME, null, null);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, LIVE_CHANNELS_PACKAGE_NAME, null, null);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, NONE_LEANBACK_TV_INPUT_PACKAGE_NAME, null,
                null);
        assertAppLinkType(Channel.APP_LINK_TYPE_APP, LEANBACK_TV_INPUT_PACKAGE_NAME, null, null);
    }

    public void testGetAppLinkType_NoText_InvalidIntent() {
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, INVALID_TV_INPUT_PACKAGE_NAME, null,
                mInvalidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, LIVE_CHANNELS_PACKAGE_NAME, null,
                mInvalidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, NONE_LEANBACK_TV_INPUT_PACKAGE_NAME, null,
                mInvalidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_APP, LEANBACK_TV_INPUT_PACKAGE_NAME, null,
                mInvalidIntent);
    }

    public void testGetAppLinkType_NoText_ValidIntent() {
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, INVALID_TV_INPUT_PACKAGE_NAME, null,
                mValidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, LIVE_CHANNELS_PACKAGE_NAME, null,
                mValidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, NONE_LEANBACK_TV_INPUT_PACKAGE_NAME, null,
                mValidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_APP, LEANBACK_TV_INPUT_PACKAGE_NAME, null,
                mValidIntent);
    }

    public void testGetAppLinkType_HasText_NoIntent() {
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, INVALID_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, null);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, LIVE_CHANNELS_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, null);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, NONE_LEANBACK_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, null);
        assertAppLinkType(Channel.APP_LINK_TYPE_APP, LEANBACK_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, null);
    }

    public void testGetAppLinkType_HasText_InvalidIntent() {
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, INVALID_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, mInvalidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, LIVE_CHANNELS_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, mInvalidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_NONE, NONE_LEANBACK_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, mInvalidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_APP, LEANBACK_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, mInvalidIntent);
    }

    public void testGetAppLinkType_HasText_ValidIntent() {
        assertAppLinkType(Channel.APP_LINK_TYPE_CHANNEL, INVALID_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, mValidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_CHANNEL, LIVE_CHANNELS_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, mValidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_CHANNEL, NONE_LEANBACK_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, mValidIntent);
        assertAppLinkType(Channel.APP_LINK_TYPE_CHANNEL, LEANBACK_TV_INPUT_PACKAGE_NAME,
                TEST_APP_LINK_TEXT, mValidIntent);
    }

    private void assertAppLinkType(int expectedType, String inputPackageName, String appLinkText,
            Intent appLinkIntent) {
        // In Channel, Intent.URI_INTENT_SCHEME is used to parse the URI. So the same flag should be
        // used when the URI is created.
        Channel testChannel = new Channel.Builder()
                .setPackageName(inputPackageName)
                .setAppLinkText(appLinkText)
                .setAppLinkIntentUri(appLinkIntent == null ? null : appLinkIntent.toUri(
                        Intent.URI_INTENT_SCHEME))
                .build();
        assertEquals("Unexpected app-link type for for " + testChannel,
                expectedType, testChannel.getAppLinkType(mMockContext));
    }

    public void testComparator() {

        TvInputManagerHelper manager = Mockito.mock(TvInputManagerHelper.class);
        Mockito.when(manager.isPartnerInput(Matchers.anyString())).thenAnswer(
                new Answer<Boolean>() {
                    @Override
                    public Boolean answer(InvocationOnMock invocation) throws Throwable {
                        String inputId = (String) invocation.getArguments()[0];
                        return PARTNER_INPUT_ID.equals(inputId);
                    }
                });
        Comparator<Channel> comparator = new TestChannelComparator(manager);
        ComparatorTester<Channel> comparatorTester =
                ComparatorTester.withoutEqualsTest(comparator);
        comparatorTester.addComparableGroup(
                new Channel.Builder().setInputId(PARTNER_INPUT_ID).build());
        comparatorTester.addComparableGroup(
                new Channel.Builder().setInputId("1").build());
        comparatorTester.addComparableGroup(
                new Channel.Builder().setInputId("1").setDisplayNumber("2").build());
        comparatorTester.addComparableGroup(
                new Channel.Builder().setInputId("2").setDisplayNumber("1.0").build());

        // display name does not affect comparator
        comparatorTester.addComparableGroup(
                new Channel.Builder().setInputId("2").setDisplayNumber("1.62")
                        .setDisplayName("test1").build(),
                new Channel.Builder().setInputId("2").setDisplayNumber("1.62")
                        .setDisplayName("test2").build(),
                new Channel.Builder().setInputId("2").setDisplayNumber("1.62")
                        .setDisplayName("test3").build());
        comparatorTester.addComparableGroup(
                new Channel.Builder().setInputId("2").setDisplayNumber("2.0").build());
        // Numeric display number sorting
        comparatorTester.addComparableGroup(
                new Channel.Builder().setInputId("2").setDisplayNumber("12.2").build());
        comparatorTester.test();
    }

    /**
     * Test Input Label handled by {@link com.android.tv.data.Channel.DefaultComparator}.
     *
     * <p>Sort partner inputs first, then sort by input label, then by input id.
     * See <a href="http://b/23031603">b/23031603</a>.
     */
    public void testComparatorLabel() {
        TvInputManagerHelper manager = Mockito.mock(TvInputManagerHelper.class);
        Mockito.when(manager.isPartnerInput(Matchers.anyString())).thenAnswer(
                new Answer<Boolean>() {
                    @Override
                    public Boolean answer(InvocationOnMock invocation) throws Throwable {
                        String inputId = (String) invocation.getArguments()[0];
                        return PARTNER_INPUT_ID.equals(inputId);
                    }
                });
        Comparator<Channel> comparator = new ChannelComparatorWithDescriptionAsLabel(manager);
        ComparatorTester<Channel> comparatorTester =
                ComparatorTester.withoutEqualsTest(comparator);

        comparatorTester.addComparableGroup(
                new Channel.Builder().setInputId(PARTNER_INPUT_ID).setDescription("A").build());

        // The description is used as a label for this test.
        comparatorTester.addComparableGroup(
                new Channel.Builder().setDescription("A").setInputId("1").build());
        comparatorTester.addComparableGroup(
                new Channel.Builder().setDescription("A").setInputId("2").build());
        comparatorTester.addComparableGroup(
                new Channel.Builder().setDescription("B").setInputId("1").build());

        comparatorTester.test();
    }

    public void testNormalizeChannelNumber() {
        assertNormalizedDisplayNumber(null, null);
        assertNormalizedDisplayNumber("", "");
        assertNormalizedDisplayNumber("1", "1");
        assertNormalizedDisplayNumber("abcde", "abcde");
        assertNormalizedDisplayNumber("1-1", "1-1");
        assertNormalizedDisplayNumber("1.1", "1-1");
        assertNormalizedDisplayNumber("1 1", "1-1");
        assertNormalizedDisplayNumber("1\u058a1", "1-1");
        assertNormalizedDisplayNumber("1\u05be1", "1-1");
        assertNormalizedDisplayNumber("1\u14001", "1-1");
        assertNormalizedDisplayNumber("1\u18061", "1-1");
        assertNormalizedDisplayNumber("1\u20101", "1-1");
        assertNormalizedDisplayNumber("1\u20111", "1-1");
        assertNormalizedDisplayNumber("1\u20121", "1-1");
        assertNormalizedDisplayNumber("1\u20131", "1-1");
        assertNormalizedDisplayNumber("1\u20141", "1-1");
    }

    private void assertNormalizedDisplayNumber(String displayNumber, String normalized) {
        assertEquals(normalized, Channel.normalizeDisplayNumber(displayNumber));
    }

    private class TestChannelComparator extends Channel.DefaultComparator {
        public TestChannelComparator(TvInputManagerHelper manager) {
            super(null, manager);
        }

        @Override
        public String getInputLabelForChannel(Channel channel) {
            return channel.getInputId();
        }
    }

    private static class ChannelComparatorWithDescriptionAsLabel extends Channel.DefaultComparator {
        public ChannelComparatorWithDescriptionAsLabel(TvInputManagerHelper manager) {
            super(null, manager);
        }

        @Override
        public String getInputLabelForChannel(Channel channel) {
            return channel.getDescription();
        }
    }
}
