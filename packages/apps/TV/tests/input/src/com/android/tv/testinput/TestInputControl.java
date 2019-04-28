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
package com.android.tv.testinput;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import android.util.LongSparseArray;

import com.android.tv.testing.ChannelInfo;
import com.android.tv.testing.ChannelUtils;
import com.android.tv.testing.testinput.ChannelState;
import com.android.tv.testing.testinput.ChannelStateData;
import com.android.tv.testing.testinput.ITestInputControl;

import java.util.Map;

/**
 * Maintains state for the {@link TestTvInputService}.
 *
 * <p>Maintains the current state for every channel.  A default is sent if the state is not
 * explicitly set. The state is versioned so TestTvInputService can tell if onNotifyXXX events need
 * to be sent.
 *
 * <p> Test update the state using @{link ITestInputControl} via {@link TestInputControlService}.
 */
class TestInputControl extends ITestInputControl.Stub {

    private final static String TAG = "TestInputControl";
    private final static TestInputControl INSTANCE = new TestInputControl();

    private final LongSparseArray<ChannelInfo> mId2ChannelInfoMap = new LongSparseArray<>();
    private final LongSparseArray<ChannelState> mOrigId2StateMap = new LongSparseArray<>();

    private java.lang.String mInputId;
    private boolean initialized;

    private TestInputControl() {
    }

    public static TestInputControl getInstance() {
        return INSTANCE;
    }

    public synchronized void init(Context context, String inputId) {
        if (!initialized) {
            // TODO run initialization in a separate thread.
            mInputId = inputId;
            updateChannelMap(context);
            initialized = true;
        }
    }

    private void updateChannelMap(Context context) {
        mId2ChannelInfoMap.clear();
        Map<Long, ChannelInfo> channelIdToInfoMap =
                ChannelUtils.queryChannelInfoMapForTvInput(context, mInputId);
        for (Long channelId : channelIdToInfoMap.keySet()) {
            mId2ChannelInfoMap.put(channelId, channelIdToInfoMap.get(channelId));
        }
        Log.i(TAG, "Initialized channel map for " + mInputId + " with " + mId2ChannelInfoMap.size()
                + " channels");
    }

    public ChannelInfo getChannelInfo(Uri channelUri) {
        return mId2ChannelInfoMap.get(ContentUris.parseId(channelUri));
    }

    public ChannelState getChannelState(int originalNetworkId) {
        return mOrigId2StateMap.get(originalNetworkId, ChannelState.DEFAULT);
    }

    @Override
    public synchronized void updateChannelState(int origId, ChannelStateData data)
            throws RemoteException {
        ChannelState state;
        ChannelState orig = getChannelState(origId);
        state = orig.next(data);
        mOrigId2StateMap.put(origId, state);

        Log.i(TAG, "Setting channel " + origId + " state to " + state);
    }
}
