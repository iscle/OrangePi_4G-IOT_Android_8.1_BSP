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
package com.android.phone;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.UserManager;
import android.provider.SearchIndexablesContract;
import android.provider.Settings;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.euicc.EuiccManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link PhoneSearchIndexablesProvider}. */
@RunWith(AndroidJUnit4.class)
public final class PhoneSearchIndexablesProviderTest {
    private PhoneSearchIndexablesTestProvider mProvider;
    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private Context mContext;
    @Mock private Resources mResources;
    @Mock private UserManager mUserManager;
    @Mock private EuiccManager mEuiccManager;
    @Mock private ContentResolver mCr;

    private class PhoneSearchIndexablesTestProvider extends PhoneSearchIndexablesProvider {
        private boolean mIsEuiccSettingsHidden = false;
        private boolean mIsEnhanced4gLteHidden = false;

        @Override boolean isEuiccSettingsHidden() {
            return mIsEuiccSettingsHidden;
        }

        @Override boolean isEnhanced4gLteHidden() {
            return mIsEnhanced4gLteHidden;
        }

        public void setIsEuiccSettingsHidden(boolean isEuiccSettingsHidden) {
            mIsEuiccSettingsHidden = isEuiccSettingsHidden;
        }

        public void setIsEnhanced4gLteHidden(boolean isEnhanced4gLteHidden) {
            mIsEnhanced4gLteHidden = isEnhanced4gLteHidden;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mResources.getString(com.android.phone.R.string.carrier_settings_euicc))
                .thenReturn("");
        when(mResources.getString(com.android.phone.R.string.keywords_carrier_settings_euicc))
                .thenReturn("");

        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(mUserManager);
        when(mContext.getSystemService(Context.EUICC_SERVICE)).thenReturn(mEuiccManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getApplicationInfo()).thenReturn(mApplicationInfo);
        when(mContext.getPackageName()).thenReturn("PhoneSearchIndexablesProviderTest");
        when(mContext.getContentResolver()).thenReturn(mCr);
        when(mCr.getPackageName()).thenReturn("com.android.phone");

        final ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = Settings.AUTHORITY;
        providerInfo.exported = true;
        providerInfo.grantUriPermissions = true;
        providerInfo.readPermission = android.Manifest.permission.READ_SEARCH_INDEXABLES;
        mProvider = new PhoneSearchIndexablesTestProvider();
        mProvider.attachInfo(mContext, providerInfo);
    }

    @Test
    public void testQueryRawData() {
        when(mUserManager.isAdminUser()).thenReturn(true);
        when(mEuiccManager.isEnabled()).thenReturn(true);
        Settings.Global.putInt(mCr, Settings.Global.EUICC_PROVISIONED, 1);
        Settings.Global.getInt(mCr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        Cursor cursor = mProvider.queryRawData(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS);
        assertThat(cursor.getColumnNames()).isEqualTo(
                SearchIndexablesContract.INDEXABLES_RAW_COLUMNS);
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToNext();
        assertThat(cursor.getString(SearchIndexablesContract.COLUMN_INDEX_RAW_KEY))
                .isEqualTo("esim_list_profile");
    }

    @Test
    public void testQueryNonIndexableKeys() {
        mProvider.setIsEnhanced4gLteHidden(false /* isEnhanced4gLteHidden */);
        mProvider.setIsEuiccSettingsHidden(false /* isEuiccSettingsHiden */);
        when(mUserManager.isAdminUser()).thenReturn(false);
        Cursor cursor1 = mProvider.queryNonIndexableKeys(
                SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        assertThat(cursor1.getColumnNames()).isEqualTo(
                SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        assertThat(cursor1.getCount()).isEqualTo(11);

        when(mUserManager.isAdminUser()).thenReturn(true);
        Cursor cursor2 = mProvider
                .queryNonIndexableKeys(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        assertThat(cursor2.getCount()).isEqualTo(1);

        mProvider.setIsEuiccSettingsHidden(true /* isEuiccSettingsHidden */);
        Cursor cursor3 = mProvider
                .queryNonIndexableKeys(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        assertThat(cursor3.getCount()).isEqualTo(2);

        mProvider.setIsEnhanced4gLteHidden(true /* isEnhanced4gLteHidden */);
        Cursor cursor4 = mProvider
                .queryNonIndexableKeys(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        assertThat(cursor4.getCount()).isEqualTo(3);
    }
}
