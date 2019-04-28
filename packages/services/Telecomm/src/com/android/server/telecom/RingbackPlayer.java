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
 * limitations under the License
 */

package com.android.server.telecom;

import com.android.internal.util.Preconditions;
import android.telecom.Log;

/**
 * Plays ringback tones. Ringback is different from other tones because it operates as the current
 * audio for a call, whereas most tones play as simple timed events. This means ringback must be
 * able to turn off and on as the user switches between calls. This is why it is implemented as its
 * own class.
 */
public class RingbackPlayer {

    private final InCallTonePlayer.Factory mPlayerFactory;

    /**
     * The current call for which the ringback tone is being played.
     */
    private Call mCall;

    /**
     * The currently active player.
     */
    private InCallTonePlayer mTonePlayer;

    RingbackPlayer(InCallTonePlayer.Factory playerFactory) {
        mPlayerFactory = playerFactory;
    }

    /**
     * Starts ringback for the specified dialing call as needed.
     *
     * @param call The call for which to ringback.
     */
    public void startRingbackForCall(Call call) {
        Preconditions.checkState(call.getState() == CallState.DIALING);

        if (mCall == call) {
            Log.w(this, "Ignoring duplicate requests to ring for %s.", call);
            return;
        }

        if (mCall != null) {
            // We only get here for the foreground call so, there's no reason why there should
            // exist a current dialing call.
            Log.wtf(this, "Ringback player thinks there are two foreground-dialing calls.");
        }

        mCall = call;
        if (mTonePlayer == null) {
            Log.d(this, "Playing the ringback tone for %s.", call);
            mTonePlayer = mPlayerFactory.createPlayer(InCallTonePlayer.TONE_RING_BACK);
            mTonePlayer.startTone();
        }
    }

    /**
     * Stops the ringback for the specified dialing call as needed.
     *
     * @param call The call for which to stop ringback.
     */
    public void stopRingbackForCall(Call call) {
        if (mCall == call) {
            // The foreground call is no longer dialing or is no longer the foreground call. In
            // either case, stop the ringback tone.
            mCall = null;

            if (mTonePlayer == null) {
                Log.w(this, "No player found to stop.");
            } else {
                Log.i(this, "Stopping the ringback tone for %s.", call);
                mTonePlayer.stopTone();
                mTonePlayer = null;
            }
        }
    }
}