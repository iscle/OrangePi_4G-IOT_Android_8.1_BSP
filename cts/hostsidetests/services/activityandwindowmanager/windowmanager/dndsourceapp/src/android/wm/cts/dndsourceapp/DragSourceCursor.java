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
 * limitations under the License.
 */

package android.wm.cts.dndsourceapp;

import android.database.AbstractCursor;

public class DragSourceCursor extends AbstractCursor {
    private static final String COLUMN_KEY = "key";

    private final String mValue;

    public DragSourceCursor(String value) {
        mValue = value;
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public String[] getColumnNames() {
        return new String[] {COLUMN_KEY};
    }

    @Override
    public String getString(int column) {
        if (getPosition() != 0) {
            throw new IllegalArgumentException("Incorrect position: " + getPosition());
        }
        if (column != 0) {
            throw new IllegalArgumentException("Incorrect column: " + column);
        }
        return mValue;
    }

    @Override
    public short getShort(int column) {
        return 0;
    }

    @Override
    public int getInt(int column) {
        return 0;
    }

    @Override
    public long getLong(int column) {
        return 0;
    }

    @Override
    public float getFloat(int column) {
        return 0;
    }

    @Override
    public double getDouble(int column) {
        return 0;
    }

    @Override
    public boolean isNull(int column) {
        return false;
    }
}
