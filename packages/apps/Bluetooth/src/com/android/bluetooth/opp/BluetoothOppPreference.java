/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import java.util.HashMap;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

/**
 * This class cache Bluetooth device name and channel locally. Its a temp
 * solution which should be replaced by bluetooth_devices in SettingsProvider
 */
public class BluetoothOppPreference {
    private static final String TAG = "BluetoothOppPreference";
    private static final boolean V = Constants.VERBOSE;

    private static BluetoothOppPreference INSTANCE;

    /* Used when obtaining a reference to the singleton instance. */
    private static Object INSTANCE_LOCK = new Object();

    private boolean mInitialized;

    private Context mContext;

    private SharedPreferences mNamePreference;

    private SharedPreferences mChannelPreference;

    private HashMap<String, Integer> mChannels = new HashMap<String, Integer>();

    private HashMap<String, String> mNames = new HashMap<String, String>();

    public static BluetoothOppPreference getInstance(Context context) {
        synchronized (INSTANCE_LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new BluetoothOppPreference();
            }
            if (!INSTANCE.init(context)) {
                return null;
            }
            return INSTANCE;
        }
    }

    private boolean init(Context context) {
        if (mInitialized)
            return true;
        mInitialized = true;

        mContext = context;

        mNamePreference = mContext.getSharedPreferences(Constants.BLUETOOTHOPP_NAME_PREFERENCE,
                Context.MODE_PRIVATE);
        mChannelPreference = mContext.getSharedPreferences(
                Constants.BLUETOOTHOPP_CHANNEL_PREFERENCE, Context.MODE_PRIVATE);

        mNames = (HashMap<String, String>) mNamePreference.getAll();
        mChannels = (HashMap<String, Integer>) mChannelPreference.getAll();

        return true;
    }

    private String getChannelKey(BluetoothDevice remoteDevice, int uuid) {
        return remoteDevice.getAddress() + "_" + Integer.toHexString(uuid);
    }

    public String getName(BluetoothDevice remoteDevice) {
        if (remoteDevice.getAddress().equals("FF:FF:FF:00:00:00")) {
            return "localhost";
        }
        if (!mNames.isEmpty()) {
            String name = mNames.get(remoteDevice.getAddress());
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    public int getChannel(BluetoothDevice remoteDevice, int uuid) {
        String key = getChannelKey(remoteDevice, uuid);
        if (V) Log.v(TAG, "getChannel " + key);
        Integer channel = null;
        if (mChannels != null) {
            channel = mChannels.get(key);
            if (V) Log.v(TAG, "getChannel for " + remoteDevice + "_" + Integer.toHexString(uuid) +
                        " as " + channel);
        }
        return (channel != null) ? channel : -1;
    }

    public void setName(BluetoothDevice remoteDevice, String name) {
        if (V) Log.v(TAG, "Setname for " + remoteDevice + " to " + name);
        if (name != null && !name.equals(getName(remoteDevice))) {
            Editor ed = mNamePreference.edit();
            ed.putString(remoteDevice.getAddress(), name);
            ed.apply();
            mNames.put(remoteDevice.getAddress(), name);
        }
    }

    public void setChannel(BluetoothDevice remoteDevice, int uuid, int channel) {
        if (V) Log.v(TAG, "Setchannel for " + remoteDevice + "_" + Integer.toHexString(uuid) + " to "
                    + channel);
        if (channel != getChannel(remoteDevice, uuid)) {
            String key = getChannelKey(remoteDevice, uuid);
            Editor ed = mChannelPreference.edit();
            ed.putInt(key, channel);
            ed.apply();
            mChannels.put(key, channel);
        }
    }

    public void removeChannel(BluetoothDevice remoteDevice, int uuid) {
        String key = getChannelKey(remoteDevice, uuid);
        Editor ed = mChannelPreference.edit();
        ed.remove(key);
        ed.apply();
        mChannels.remove(key);
    }

    public void dump() {
        Log.d(TAG, "Dumping Names:  ");
        Log.d(TAG, mNames.toString());
        Log.d(TAG, "Dumping Channels:  ");
        Log.d(TAG, mChannels.toString());
    }
}
