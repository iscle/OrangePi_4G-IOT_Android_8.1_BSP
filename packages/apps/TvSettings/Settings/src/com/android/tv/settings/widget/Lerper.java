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

package com.android.tv.settings.widget;

/**
 * Lerper model tracks target position by adding (target - source) / divisor to source position
 */
public final class Lerper {

    public static final float DEFAULT_DIVISOR = 2.0f;

    private float mDivisor = DEFAULT_DIVISOR;
    private float mMinDelta = 1 / DEFAULT_DIVISOR;

    public void setDivisor(float divisor) {
        if (divisor < 1f) throw new IllegalArgumentException();
        mDivisor = divisor;
        mMinDelta = 1 / divisor;
    }

    public float getDivisor() {
        return mDivisor;
    }

    public float getMinDelta() {
        return mMinDelta;
    }

    public int getValue(int currentValue, int targetValue) {
        int delta = targetValue - currentValue;
        int retValue;
        if (delta > 0) {
            // make sure change currentValue and not exceeding targetValue
            delta = (int)(Math.ceil(delta / mDivisor));
            if (delta == 0) {
                delta = 1;
            }
            retValue = currentValue + delta;
            if (retValue > targetValue) {
                retValue = targetValue;
            }
        } else if (delta < 0) {
            // make sure change currentValue and not exceeding targetValue
            delta = (int)(Math.floor(delta / mDivisor));
            if (delta == 0) {
                delta = -1;
            }
            retValue = currentValue + delta;
            if (retValue < targetValue) {
                retValue = targetValue;
            }
        } else {
            retValue = targetValue;
        }
        return retValue;
    }

    public float getValue(float currentValue, float targetValue) {
        float delta = targetValue - currentValue;
        float retValue;
        if (delta > mMinDelta) {
            // make sure change currentValue and not exceeding targetValue
            delta = delta / mDivisor;
            retValue = currentValue + delta;
            if (retValue > targetValue) {
                retValue = targetValue;
            }
        } else if (delta < -mMinDelta) {
            // make sure change currentValue and not exceeding targetValue
            delta = delta / mDivisor;
            retValue = currentValue + delta;
            if (retValue < targetValue) {
                retValue = targetValue;
            }
        } else {
            retValue = targetValue;
        }
        return retValue;
    }
}
