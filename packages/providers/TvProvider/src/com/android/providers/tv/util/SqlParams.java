/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.providers.tv.util;

import android.database.DatabaseUtils;

public class SqlParams {
    private String mTables;
    private String mSelection;
    private String[] mSelectionArgs;

    public SqlParams(String tables, String selection, String... selectionArgs) {
        setTables(tables);
        setWhere(selection, selectionArgs);
    }

    public String getTables() {
        return mTables;
    }

    public String getSelection() {
        return mSelection;
    }

    public String[] getSelectionArgs() {
        return mSelectionArgs;
    }

    public void setTables(String tables) {
        mTables = tables;
    }

    public void setWhere(String selection, String... selectionArgs) {
        mSelection = selection;
        mSelectionArgs = selectionArgs;
    }

    public void appendWhere(String selection, String... selectionArgs) {
        mSelection = DatabaseUtils.concatenateWhere(mSelection, selection);
        if (selectionArgs != null) {
            mSelectionArgs = DatabaseUtils.appendSelectionArgs(mSelectionArgs, selectionArgs);
        }
    }
}
