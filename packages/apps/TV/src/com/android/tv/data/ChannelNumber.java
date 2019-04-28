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

package com.android.tv.data;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.KeyEvent;

import com.android.tv.util.StringUtils;

import java.util.Objects;

/**
 * A convenience class to handle channel number.
 */
public final class ChannelNumber implements Comparable<ChannelNumber> {
    private static final int[] CHANNEL_DELIMITER_KEYCODES = {
        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_PERIOD,
        KeyEvent.KEYCODE_NUMPAD_DOT, KeyEvent.KEYCODE_SPACE
    };

    /** The major part of the channel number. */
    public String majorNumber;
    /** The flag which indicates whether it has a delimiter or not. */
    public boolean hasDelimiter;
    /** The major part of the channel number. */
    public String minorNumber;

    public ChannelNumber() {
        reset();
    }

    public void reset() {
        setChannelNumber("", false, "");
    }

    private void setChannelNumber(String majorNumber, boolean hasDelimiter, String minorNumber) {
        this.majorNumber = majorNumber;
        this.hasDelimiter = hasDelimiter;
        this.minorNumber = minorNumber;
    }

    @Override
    public String toString() {
        if (hasDelimiter) {
            return majorNumber + Channel.CHANNEL_NUMBER_DELIMITER + minorNumber;
        }
        return majorNumber;
    }

    @Override
    public int compareTo(@NonNull ChannelNumber another) {
        int major = Integer.parseInt(majorNumber);
        int minor = hasDelimiter ? Integer.parseInt(minorNumber) : 0;

        int opponentMajor = Integer.parseInt(another.majorNumber);
        int opponentMinor = another.hasDelimiter
                ? Integer.parseInt(another.minorNumber) : 0;
        if (major == opponentMajor) {
            return minor - opponentMinor;
        }
        return major - opponentMajor;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ChannelNumber) {
            ChannelNumber channelNumber = (ChannelNumber) obj;
            return TextUtils.equals(majorNumber, channelNumber.majorNumber)
                    && TextUtils.equals(minorNumber, channelNumber.minorNumber)
                    && hasDelimiter == channelNumber.hasDelimiter;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(majorNumber, hasDelimiter, minorNumber);
    }

    public static boolean isChannelNumberDelimiterKey(int keyCode) {
        for (int delimiterKeyCode : CHANNEL_DELIMITER_KEYCODES) {
            if (delimiterKeyCode == keyCode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the ChannelNumber instance.
     * <p>
     * Note that all the channel number argument should be normalized by
     * {@link Channel#normalizeDisplayNumber}. The channels retrieved from
     * {@link ChannelDataManager} are already normalized.
     */
    public static ChannelNumber parseChannelNumber(String number) {
        if (number == null) {
            return null;
        }
        ChannelNumber ret = new ChannelNumber();
        int indexOfDelimiter = number.indexOf(Channel.CHANNEL_NUMBER_DELIMITER);
        if (indexOfDelimiter == 0 || indexOfDelimiter == number.length() - 1) {
            return null;
        } else if (indexOfDelimiter < 0) {
            ret.majorNumber = number;
            if (!isInteger(ret.majorNumber)) {
                return null;
            }
        } else {
            ret.hasDelimiter = true;
            ret.majorNumber = number.substring(0, indexOfDelimiter);
            ret.minorNumber = number.substring(indexOfDelimiter + 1);
            if (!isInteger(ret.majorNumber) || !isInteger(ret.minorNumber)) {
                return null;
            }
        }
        return ret;
    }

    /**
     * Compares the channel numbers.
     * <p>
     * Note that all the channel number arguments should be normalized by
     * {@link Channel#normalizeDisplayNumber}. The channels retrieved from
     * {@link ChannelDataManager} are already normalized.
     */
    public static int compare(String lhs, String rhs) {
        ChannelNumber lhsNumber = parseChannelNumber(lhs);
        ChannelNumber rhsNumber = parseChannelNumber(rhs);
        // Null first
        if (lhsNumber == null && rhsNumber == null) {
            return StringUtils.compare(lhs, rhs);
        } else if (lhsNumber == null /* && rhsNumber != null */) {
            return -1;
        } else if (rhsNumber == null) {
            return 1;
        }
        return lhsNumber.compareTo(rhsNumber);
    }

    private static boolean isInteger(String string) {
        try {
            Integer.parseInt(string);
        } catch(NumberFormatException | NullPointerException e) {
            return false;
        }
        return true;
    }
}
