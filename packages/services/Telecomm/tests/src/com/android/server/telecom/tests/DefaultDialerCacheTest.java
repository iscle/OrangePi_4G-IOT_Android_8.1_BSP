/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.TelecomSystem;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultDialerCacheTest extends TelecomTestCase {

    private static final String DIALER1 = "com.android.dialer";
    private static final String DIALER2 = "xyz.abc.dialer";
    private static final String DIALER3 = "aaa.bbb.ccc.ddd";
    private static final int USER0 = 0;
    private static final int USER1 = 1;
    private static final int USER2 = 2;

    private DefaultDialerCache mDefaultDialerCache;
    private ContentObserver mDefaultDialerSettingObserver;
    private BroadcastReceiver mPackageChangeReceiver;
    private BroadcastReceiver mUserRemovedReceiver;

    @Mock private DefaultDialerCache.DefaultDialerManagerAdapter mMockDefaultDialerManager;

    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();

        ArgumentCaptor<BroadcastReceiver> packageReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        mDefaultDialerCache = new DefaultDialerCache(
                mContext, mMockDefaultDialerManager, new TelecomSystem.SyncRoot() { });

        verify(mContext, times(2)).registerReceiverAsUser(
            packageReceiverCaptor.capture(), eq(UserHandle.ALL), any(IntentFilter.class),
                isNull(String.class), isNull(Handler.class));
        // Receive the first receiver that was captured, the package change receiver.
        mPackageChangeReceiver = packageReceiverCaptor.getAllValues().get(0);

        ArgumentCaptor<BroadcastReceiver> userRemovedReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(
            userRemovedReceiverCaptor.capture(), any(IntentFilter.class));
        mUserRemovedReceiver = userRemovedReceiverCaptor.getAllValues().get(0);

        mDefaultDialerSettingObserver = mDefaultDialerCache.getContentObserver();

        when(mMockDefaultDialerManager.getDefaultDialerApplication(any(Context.class), eq(USER0)))
                .thenReturn(DIALER1);
        when(mMockDefaultDialerManager.getDefaultDialerApplication(any(Context.class), eq(USER1)))
                .thenReturn(DIALER2);
        when(mMockDefaultDialerManager.getDefaultDialerApplication(any(Context.class), eq(USER2)))
                .thenReturn(DIALER3);
    }

    @SmallTest
    public void testThreeUsers() {
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER3);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER3);

        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER0));
        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER1));
        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER2));
    }

    @SmallTest
    public void testDialer1PackageChanged() {
        // Populate the caches first
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER3);

        Intent packageChangeIntent = new Intent(Intent.ACTION_PACKAGE_CHANGED,
                Uri.fromParts("package", DIALER1, null));
        when(mMockDefaultDialerManager.getDefaultDialerApplication(any(Context.class), eq(USER0)))
                .thenReturn(DIALER2);
        mPackageChangeReceiver.onReceive(mContext, packageChangeIntent);
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER0));
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER1));
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER2));

        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER2);
    }

    @SmallTest
    public void testRandomOtherPackageChanged() {
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER3);

        Intent packageChangeIntent = new Intent(Intent.ACTION_PACKAGE_CHANGED,
                Uri.fromParts("package", "red.orange.blue", null));
        mPackageChangeReceiver.onReceive(mContext, packageChangeIntent);
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER0));
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER1));
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER2));
    }

    @SmallTest
    public void testUserRemoved() {
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);

        Intent userRemovalIntent = new Intent(Intent.ACTION_USER_REMOVED);
        userRemovalIntent.putExtra(Intent.EXTRA_USER_HANDLE, USER0);
        mUserRemovedReceiver.onReceive(mContext, userRemovalIntent);

        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);

        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER0));
        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER1));
    }

    @SmallTest
    public void testPackageRemovedWithoutReplace() {
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER3);

        Intent packageChangeIntent = new Intent(Intent.ACTION_PACKAGE_REMOVED,
                Uri.fromParts("package", DIALER1, null));
        packageChangeIntent.putExtra(Intent.EXTRA_REPLACING, false);

        mPackageChangeReceiver.onReceive(mContext, packageChangeIntent);
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER0));
        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER1));
        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER2));
    }

    @SmallTest
    public void testPackageAdded() {
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER3);

        Intent packageChangeIntent = new Intent(Intent.ACTION_PACKAGE_ADDED,
                Uri.fromParts("package", "ppp.qqq.zzz", null));

        mPackageChangeReceiver.onReceive(mContext, packageChangeIntent);
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER0));
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER1));
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER2));
    }

    @SmallTest
    public void testPackageRemovedWithReplace() {
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER3);

        Intent packageChangeIntent = new Intent(Intent.ACTION_PACKAGE_REMOVED,
                Uri.fromParts("package", DIALER1, null));
        packageChangeIntent.putExtra(Intent.EXTRA_REPLACING, true);

        mPackageChangeReceiver.onReceive(mContext, packageChangeIntent);
        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER0));
        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER1));
        verify(mMockDefaultDialerManager, times(1))
                .getDefaultDialerApplication(any(Context.class), eq(USER2));
    }

    @SmallTest
    public void testDefaultDialerSettingChanged() {
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER1);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER3);

        when(mMockDefaultDialerManager.getDefaultDialerApplication(any(Context.class), eq(USER0)))
                .thenReturn(DIALER2);
        when(mMockDefaultDialerManager.getDefaultDialerApplication(any(Context.class), eq(USER1)))
                .thenReturn(DIALER2);
        when(mMockDefaultDialerManager.getDefaultDialerApplication(any(Context.class), eq(USER2)))
                .thenReturn(DIALER2);
        mDefaultDialerSettingObserver.onChange(false);

        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER0));
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER2));
        verify(mMockDefaultDialerManager, times(2))
                .getDefaultDialerApplication(any(Context.class), eq(USER2));

        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER0), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER1), DIALER2);
        assertEquals(mDefaultDialerCache.getDefaultDialerApplication(USER2), DIALER2);
    }
}
