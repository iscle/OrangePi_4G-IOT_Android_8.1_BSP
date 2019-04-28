/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.bluetooth.gatt;

/** @hide */
public class FilterParams {
    private int mClientIf;
    private int mFiltIndex;
    private int mFeatSeln;
    private int mListLogicType;
    private int mFiltLogicType;
    private int mRssiHighValue;
    private int mRssiLowValue;
    private int mDelyMode;
    private int mFoundTimeOut;
    private int mLostTimeOut;
    private int mFoundTimeOutCnt;
    private int mNumOfTrackEntries;

    public FilterParams(int client_if, int filt_index,
        int feat_seln, int list_logic_type, int filt_logic_type,
        int rssi_high_thres, int rssi_low_thres, int dely_mode,
        int found_timeout, int lost_timeout, int found_timeout_cnt,
        int num_of_tracking_entries) {

        mClientIf = client_if;
        mFiltIndex = filt_index;
        mFeatSeln = feat_seln;
        mListLogicType = list_logic_type;
        mFiltLogicType = filt_logic_type;
        mRssiHighValue = rssi_high_thres;
        mRssiLowValue = rssi_low_thres;
        mDelyMode = dely_mode;
        mFoundTimeOut = found_timeout;
        mLostTimeOut = lost_timeout;
        mFoundTimeOutCnt = found_timeout_cnt;
        mNumOfTrackEntries = num_of_tracking_entries;
    }

    public int getClientIf () {
        return mClientIf;
    }

    public int getFiltIndex () {
        return mFiltIndex;
    }

    public int getFeatSeln () {
        return mFeatSeln;
    }

    public int getDelyMode () {
        return mDelyMode;
    }

    public int getListLogicType () {
        return mListLogicType;
    }

    public int getFiltLogicType () {
        return mFiltLogicType;
    }

    public int getRSSIHighValue () {
        return mRssiHighValue;
    }

    public int getRSSILowValue () {
        return mRssiLowValue;
    }

    public int getFoundTimeout () {
        return mFoundTimeOut;
    }

    public int getFoundTimeOutCnt () {
        return mFoundTimeOutCnt;
    }

    public int getLostTimeout () {
        return mLostTimeOut;
    }

    public int getNumOfTrackEntries () {
        return mNumOfTrackEntries;
    }

}

