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

package com.android.server.wifi;

import java.util.HashMap;

public class MockResources extends android.test.mock.MockResources {

    private HashMap<Integer, Boolean> mBooleanValues;
    private HashMap<Integer, Integer> mIntegerValues;
    private HashMap<Integer, String>  mStringValues;

    public MockResources() {
        mBooleanValues = new HashMap<Integer, Boolean>();
        mIntegerValues = new HashMap<Integer, Integer>();
        mStringValues  = new HashMap<Integer, String>();
    }

    @Override
    public boolean getBoolean(int id) {
        if (mBooleanValues.containsKey(id)) {
            return mBooleanValues.get(id);
        } else {
            return false;
        }
    }

    @Override
    public int getInteger(int id) {
        if (mIntegerValues.containsKey(id)) {
            return mIntegerValues.get(id);
        } else {
            return 0;
        }
    }

    @Override
    public String getString(int id) {
        if (mStringValues.containsKey(id)) {
            return mStringValues.get(id);
        } else {
            return null;
        }
    }

    public void setBoolean(int id, boolean value) {
        mBooleanValues.put(id, value);
    }

    public void setInteger(int id, int value) {
        mIntegerValues.put(id, value);
    }

    public void setString(int id, String value) {
        mStringValues.put(id, value);
    }
}
