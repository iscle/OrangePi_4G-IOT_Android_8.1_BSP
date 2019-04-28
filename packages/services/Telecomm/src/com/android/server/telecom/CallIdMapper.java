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

package com.android.server.telecom;

import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Map;

/** Utility to map {@link Call} objects to unique IDs. IDs are generated when a call is added. */
@VisibleForTesting
public class CallIdMapper {
    /**
     * A very basic bidirectional map.
     */
    static class BiMap<K, V> {
        private Map<K, V> mPrimaryMap = new ArrayMap<>();
        private Map<V, K> mSecondaryMap = new ArrayMap<>();

        public boolean put(K key, V value) {
            if (key == null || value == null || mPrimaryMap.containsKey(key) ||
                    mSecondaryMap.containsKey(value)) {
                return false;
            }

            mPrimaryMap.put(key, value);
            mSecondaryMap.put(value, key);
            return true;
        }

        public boolean remove(K key) {
            if (key == null) {
                return false;
            }
            if (mPrimaryMap.containsKey(key)) {
                V value = getValue(key);
                mPrimaryMap.remove(key);
                mSecondaryMap.remove(value);
                return true;
            }
            return false;
        }

        public boolean removeValue(V value) {
            if (value == null) {
                return false;
            }
            return remove(getKey(value));
        }

        public V getValue(K key) {
            return mPrimaryMap.get(key);
        }

        public K getKey(V value) {
            return mSecondaryMap.get(value);
        }

        public void clear() {
            mPrimaryMap.clear();
            mSecondaryMap.clear();
        }
    }

    public interface ICallInfo {
        String getCallId(Call call);
    }

    private final BiMap<String, Call> mCalls = new BiMap<>();
    private ICallInfo mCallInfo;

    public CallIdMapper(ICallInfo callInfo) {
        mCallInfo = callInfo;
    }

    void replaceCall(Call newCall, Call callToReplace) {
        // Use the old call's ID for the new call.
        String callId = getCallId(callToReplace);
        mCalls.put(callId, newCall);
    }

    void addCall(Call call, String id) {
        if (call == null) {
            return;
        }
        mCalls.put(id, call);
    }

    void addCall(Call call) {
        addCall(call, mCallInfo.getCallId(call));
    }

    void removeCall(Call call) {
        if (call == null) {
            return;
        }
        mCalls.removeValue(call);
    }

    void removeCall(String callId) {
        mCalls.remove(callId);
    }

    String getCallId(Call call) {
        if (call == null || mCalls.getKey(call) == null) {
            return null;
        }
        return mCallInfo.getCallId(call);
    }

    Call getCall(Object objId) {
        String callId = null;
        if (objId instanceof String) {
            callId = (String) objId;
        }

        return mCalls.getValue(callId);
    }

    void clear() {
        mCalls.clear();
    }
}
