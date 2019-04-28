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

package com.android.car.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represent listeners for a sensor grouped by their rate.
 * @hide
 */
public class CarRatedListeners<EventListenerType> {
    private final Map<EventListenerType, Integer> mListenersToRate = new HashMap<>(4);

    private int mUpdateRate;

    protected long mLastUpdateTime = -1;

    protected CarRatedListeners(int rate) {
        mUpdateRate = rate;
    }

    public boolean contains(EventListenerType listener) {
        return mListenersToRate.containsKey(listener);
    }

    public int getRate() {
        return mUpdateRate;
    }

    /**
     * Remove given listener from the list and update rate if necessary.
     *
     * @param listener
     * @return true if rate was updated. Otherwise, returns false.
     */
    public boolean remove(EventListenerType listener) {
        mListenersToRate.remove(listener);
        if (mListenersToRate.isEmpty()) {
            return false;
        }
        Integer updateRate = Collections.min(mListenersToRate.values());
        if (updateRate != mUpdateRate) {
            mUpdateRate = updateRate;
            return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return mListenersToRate.isEmpty();
    }

    /**
     * Add given listener to the list and update rate if necessary.
     *
     * @param listener if null, add part is skipped.
     * @param updateRate
     * @return true if rate was updated. Otherwise, returns false.
     */
    public boolean addAndUpdateRate(EventListenerType listener, int updateRate) {
        Integer oldUpdateRate = mListenersToRate.put(listener, updateRate);
        if (mUpdateRate > updateRate) {
            mUpdateRate = updateRate;
            return true;
        } else if (oldUpdateRate != null && oldUpdateRate == mUpdateRate) {
            mUpdateRate = Collections.min(mListenersToRate.values());
        }
        return false;
    }

    public Collection<EventListenerType> getListeners() {
        return mListenersToRate.keySet();
    }
}
