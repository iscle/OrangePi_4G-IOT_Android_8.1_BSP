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
package com.android.car.apps.common;

/**
 * @hide
 */
public class RefcountObject<T> {

    public interface RefcountListener {
        void onRefcountZero(RefcountObject<?> object);
    }

    private RefcountObject.RefcountListener mRefcountListener;
    private int mRefcount;
    private T mObject;

    public RefcountObject(T object) {
        mObject = object;
    }

    public void setRefcountListener(RefcountObject.RefcountListener listener) {
        mRefcountListener = listener;
    }

    public synchronized int addRef() {
        mRefcount++;
        return mRefcount;
    }

    public synchronized int releaseRef() {
        mRefcount--;
        if (mRefcount == 0 && mRefcountListener != null) {
            mRefcountListener.onRefcountZero(this);
        }
        return mRefcount;
    }

    public synchronized int getRef() {
        return mRefcount;
    }

    public T getObject() {
        return mObject;
    }
}
