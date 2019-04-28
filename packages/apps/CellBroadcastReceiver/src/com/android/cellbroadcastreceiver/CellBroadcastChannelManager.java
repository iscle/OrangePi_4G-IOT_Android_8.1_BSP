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

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.util.Log;

import com.android.cellbroadcastreceiver.CellBroadcastAlertService.AlertType;

import java.util.ArrayList;

/**
 * CellBroadcastChannelManager handles the additional cell broadcast channels that
 * carriers might enable through resources.
 * Syntax: "<channel id range>:[type=<alert type>], [emergency=true/false]"
 * For example,
 * <string-array name="additional_cbs_channels_strings" translatable="false">
 *     <item>"43008:type=earthquake, emergency=true"</item>
 *     <item>"0xAFEE:type=tsunami, emergency=true"</item>
 *     <item>"0xAC00-0xAFED:type=other"</item>
 *     <item>"1234-5678"</item>
 * </string-array>
 * If no tones are specified, the alert type will be set to CMAS_DEFAULT. If emergency is not set,
 * by default it's not emergency.
 */
public class CellBroadcastChannelManager {

    private static final String TAG = "CellBroadcastChannelManager";

    private static CellBroadcastChannelManager sInstance = null;

    /**
     * Cell broadcast channel range
     * A range is consisted by starting channel id, ending channel id, and the alert type
     */
    public static class CellBroadcastChannelRange {

        private static final String KEY_TYPE = "type";
        private static final String KEY_EMERGENCY = "emergency";

        public int mStartId;
        public int mEndId;
        public AlertType mAlertType;
        public boolean mIsEmergency;

        public CellBroadcastChannelRange(String channelRange) throws Exception {

            mAlertType = AlertType.CMAS_DEFAULT;
            mIsEmergency = false;

            int colonIndex = channelRange.indexOf(':');
            if (colonIndex != -1) {
                // Parse the alert type and emergency flag
                String[] pairs = channelRange.substring(colonIndex + 1).trim().split(",");
                for (String pair : pairs) {
                    pair = pair.trim();
                    String[] tokens = pair.split("=");
                    if (tokens.length == 2) {
                        String key = tokens[0].trim();
                        String value = tokens[1].trim();
                        switch (key) {
                            case KEY_TYPE:
                                mAlertType = AlertType.valueOf(value.toUpperCase());
                                break;
                            case KEY_EMERGENCY:
                                mIsEmergency = value.equalsIgnoreCase("true");
                                break;
                        }
                    }
                }
                channelRange = channelRange.substring(0, colonIndex).trim();
            }

            // Parse the channel range
            int dashIndex = channelRange.indexOf('-');
            if (dashIndex != -1) {
                // range that has start id and end id
                mStartId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                mEndId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
            } else {
                // Not a range, only a single id
                mStartId = mEndId = Integer.decode(channelRange);
            }
        }
    }

    /**
     * Get the instance of the cell broadcast other channel manager
     * @return The singleton instance
     */
    public static CellBroadcastChannelManager getInstance() {
        if (sInstance == null) {
            sInstance = new CellBroadcastChannelManager();
        }
        return sInstance;
    }

    /**
     * Get cell broadcast channels enabled by the carriers.
     * @param context Application context
     * @return The list of channel ranges enabled by the carriers.
     */
    public ArrayList<CellBroadcastChannelRange> getCellBroadcastChannelRanges(Context context) {

        ArrayList<CellBroadcastChannelRange> result = new ArrayList<>();
        String[] ranges = context.getResources().getStringArray(
                R.array.additional_cbs_channels_strings);

        if (ranges != null) {
            for (String range : ranges) {
                try {
                    result.add(new CellBroadcastChannelRange(range));
                } catch (Exception e) {
                    loge("Failed to parse \"" + range + "\". e=" + e);
                }
            }
        }

        return result;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
