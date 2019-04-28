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
package com.android.emergency;

import static com.google.common.truth.Truth.assertThat;

import android.database.Cursor;
import android.provider.SearchIndexablesContract;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.emergency.R;
import com.android.emergency.TestConfig;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for {@link EmergencySearchIndexablesProvider}. */
@SmallTest
@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public final class EmergencySearchIndexablesProviderTest {
    private EmergencySearchIndexablesProvider mProvider;

    @Before
    public void setUp() {
        mProvider = new EmergencySearchIndexablesProvider();
    }

    @Test
    public void testQueryXmlResources() {
        Cursor cursor = mProvider.queryXmlResources(
                SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS);
        assertThat(cursor.getColumnNames()).isEqualTo(
                SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS);
        assertThat(cursor.getCount()).isNotEqualTo(0);

        Set<Integer> expectedXmlResIds = new HashSet();
        expectedXmlResIds.add(R.xml.edit_emergency_info);
        expectedXmlResIds.add(R.xml.edit_medical_info);

        assertThat(cursor.isBeforeFirst()).isTrue();
        Set<Integer> xmlResIds = new HashSet();
        while (cursor.moveToNext()) {
            xmlResIds.add(cursor.getInt(SearchIndexablesContract.COLUMN_INDEX_XML_RES_RESID));
        }
        assertThat(xmlResIds).isEqualTo(expectedXmlResIds);
    }

    @Test
    public void testQueryRawData() {
        Cursor cursor = mProvider.queryRawData(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS);
        assertThat(cursor.getColumnNames()).isEqualTo(
                SearchIndexablesContract.INDEXABLES_RAW_COLUMNS);
        assertThat(cursor.getCount()).isEqualTo(0);
    }

    @Test
    public void testQueryNonIndexableKeys() {
        Cursor cursor = mProvider.queryNonIndexableKeys(
                SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        assertThat(cursor.getColumnNames()).isEqualTo(
                SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        assertThat(cursor.getCount()).isEqualTo(0);
    }
}
