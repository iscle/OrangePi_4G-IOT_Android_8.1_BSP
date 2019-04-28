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

import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.SearchIndexablesContract.XmlResource;
import android.provider.SearchIndexableResource;
import android.provider.SearchIndexablesProvider;

import com.android.emergency.edit.EditInfoActivity;
import com.android.emergency.edit.EditMedicalInfoActivity;

import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;

public class EmergencySearchIndexablesProvider extends SearchIndexablesProvider {
    private static final String TAG = "EmergencySearchIndexablesProvider";
    private static final int IGNORED_RANK = 2112;
    private static final int NO_ICON_ID = 0;

    private static SearchIndexableResource[] INDEXABLE_RES = new SearchIndexableResource[] {
            new SearchIndexableResource(IGNORED_RANK, R.xml.edit_emergency_info,
                    EditInfoActivity.class.getName(),
                    NO_ICON_ID),
            new SearchIndexableResource(IGNORED_RANK, R.xml.edit_medical_info,
                    EditMedicalInfoActivity.class.getName(),
                    NO_ICON_ID),
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryXmlResources(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_XML_RES_COLUMNS);
        for (int i = 0, length = INDEXABLE_RES.length; i < length; i++) {
            cursor.newRow()
                    .add(XmlResource.COLUMN_RANK, INDEXABLE_RES[i].rank)
                    .add(XmlResource.COLUMN_XML_RESID, INDEXABLE_RES[i].xmlResId)
                    .add(XmlResource.COLUMN_CLASS_NAME, null)
                    .add(XmlResource.COLUMN_ICON_RESID, INDEXABLE_RES[i].iconResId)
                    .add(XmlResource.COLUMN_INTENT_ACTION, "android.intent.action.MAIN")
                    .add(XmlResource.COLUMN_INTENT_TARGET_PACKAGE, "com.android.emergency")
                    .add(XmlResource.COLUMN_INTENT_TARGET_CLASS, INDEXABLE_RES[i].className);
        }
        return cursor;
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_RAW_COLUMNS);
        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);
        return cursor;
    }
}
