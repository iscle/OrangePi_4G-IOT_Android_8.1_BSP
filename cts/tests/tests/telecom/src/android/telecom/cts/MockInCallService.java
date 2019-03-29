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

package android.telecom.cts;

import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class MockInCallService extends InCallService {
    private static String LOG_TAG = "MockInCallService";
    private ArrayList<Call> mCalls = new ArrayList<>();
    private ArrayList<Call> mConferenceCalls = new ArrayList<>();
    private static InCallServiceCallbacks sCallbacks;
    private Map<Call, MockVideoCallCallback> mVideoCallCallbacks =
            new ArrayMap<Call, MockVideoCallCallback>();

    private static final Object sLock = new Object();
    private static boolean mIsServiceBound = false;

    public static abstract class InCallServiceCallbacks {
        private MockInCallService mService;
        public Semaphore lock = new Semaphore(0);

        public void onCallAdded(Call call, int numCalls) {};
        public void onCallRemoved(Call call, int numCalls) {};
        public void onCallStateChanged(Call call, int state) {};
        public void onParentChanged(Call call, Call parent) {};
        public void onChildrenChanged(Call call, List<Call> children) {};
        public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {};
        public void onCallDestroyed(Call call) {};
        public void onDetailsChanged(Call call, Call.Details details) {};
        public void onCanAddCallsChanged(boolean canAddCalls) {}
        public void onBringToForeground(boolean showDialpad) {}
        public void onCallAudioStateChanged(CallAudioState audioState) {}
        public void onPostDialWait(Call call, String remainingPostDialSequence) {}
        public void onCannedTextResponsesLoaded(Call call, List<String> cannedTextResponses) {}
        public void onSilenceRinger() {}
        public void onConnectionEvent(Call call, String event, Bundle extras) {}
        public void onRttModeChanged(Call call, int mode) {}
        public void onRttStatusChanged(Call call, boolean enabled, Call.RttCall rttCall) {}
        public void onRttRequest(Call call, int id) {}
        public void onRttInitiationFailure(Call call, int reason) {}

        final public MockInCallService getService() {
            return mService;
        }

        final public void setService(MockInCallService service) {
            mService = service;
        }
    }

    /**
     * Note that the super implementations of the callback methods are all no-ops, but we call
     * them anyway to make sure that the CTS coverage tool detects that we are testing them.
     */
    private Call.Callback mCallCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            if (getCallbacks() != null) {
                getCallbacks().onCallStateChanged(call, state);
            }
        }

        @Override
        public void onVideoCallChanged(Call call, InCallService.VideoCall videoCall) {
            super.onVideoCallChanged(call, videoCall);
            saveVideoCall(call, videoCall);
        }

        @Override
        public void onParentChanged(Call call, Call parent) {
            super.onParentChanged(call, parent);
            if (getCallbacks() != null) {
                getCallbacks().onParentChanged(call, parent);
            }
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            super.onChildrenChanged(call, children);
            if (getCallbacks() != null) {
                getCallbacks().onChildrenChanged(call, children);
            }
        }

        @Override
        public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {
            super.onConferenceableCallsChanged(call, conferenceableCalls);
            if (getCallbacks() != null) {
                getCallbacks().onConferenceableCallsChanged(call, conferenceableCalls);
            }
        }

        @Override
        public void onCallDestroyed(Call call) {
            super.onCallDestroyed(call);
            if (getCallbacks() != null) {
                getCallbacks().onCallDestroyed(call);
            }
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            super.onDetailsChanged(call, details);
            if (getCallbacks() != null) {
                getCallbacks().onDetailsChanged(call, details);
            }
        }

        @Override
        public void onPostDialWait(Call call, String remainingPostDialSequence) {
            super.onPostDialWait(call, remainingPostDialSequence);
            if (getCallbacks() != null) {
                getCallbacks().onPostDialWait(call, remainingPostDialSequence);
            }
        }

        @Override
        public void onCannedTextResponsesLoaded(Call call, List<String> cannedTextResponses) {
            super.onCannedTextResponsesLoaded(call, cannedTextResponses);
            if (getCallbacks() != null) {
                getCallbacks().onCannedTextResponsesLoaded(call, cannedTextResponses);
            }
        }

        @Override
        public void onConnectionEvent(Call call, String event, Bundle extras) {
            super.onConnectionEvent(call, event, extras);
            if (getCallbacks() != null) {
                getCallbacks().onConnectionEvent(call, event, extras);
            }
        }

        @Override
        public void onRttModeChanged(Call call, int mode) {
            super.onRttModeChanged(call, mode);
            if (getCallbacks() != null) {
                getCallbacks().onRttModeChanged(call, mode);
            }
        }

        @Override
        public void onRttStatusChanged(Call call, boolean enabled, Call.RttCall rttCall) {
            super.onRttStatusChanged(call, enabled, rttCall);
            if (getCallbacks() != null) {
                getCallbacks().onRttStatusChanged(call, enabled, rttCall);
            }
        }

        @Override
        public void onRttRequest(Call call, int id) {
            super.onRttRequest(call, id);
            if (getCallbacks() != null) {
                getCallbacks().onRttRequest(call, id);
            }
        }

        @Override
        public void onRttInitiationFailure(Call call, int reason) {
            super.onRttInitiationFailure(call, reason);
            if (getCallbacks() != null) {
                getCallbacks().onRttInitiationFailure(call, reason);
            }
        }
    };

    private void saveVideoCall(Call call, VideoCall videoCall) {
        if (videoCall != null) {
            if (!mVideoCallCallbacks.containsKey(call)) {
                MockVideoCallCallback listener = new MockVideoCallCallback(call);
                videoCall.registerCallback(listener);
                mVideoCallCallbacks.put(call, listener);
            }
        } else {
            mVideoCallCallbacks.remove(call);
        }
    }

    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        Log.i(LOG_TAG, "Service bounded");
        if (getCallbacks() != null) {
            getCallbacks().setService(this);
        }
        mIsServiceBound = true;
        return super.onBind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE) == true) {
            if (!mConferenceCalls.contains(call)) {
                mConferenceCalls.add(call);
                call.registerCallback(mCallCallback);
            }
        } else {
            if (!mCalls.contains(call)) {
                mCalls.add(call);
                call.registerCallback(mCallCallback);
                VideoCall videoCall = call.getVideoCall();
                if (videoCall != null) {
                    saveVideoCall(call, videoCall);
                }
            }
        }
        if (getCallbacks() != null) {
            getCallbacks().onCallAdded(call, mCalls.size() + mConferenceCalls.size());
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE) == true) {
            mConferenceCalls.remove(call);
        } else {
            mCalls.remove(call);
        }
        if (getCallbacks() != null) {
            getCallbacks().onCallRemoved(call, mCalls.size() + mConferenceCalls.size());
            saveVideoCall(call, null /* remove videoCall */);
        }
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        super.onCanAddCallChanged(canAddCall);
        if (getCallbacks() != null) {
            getCallbacks().onCanAddCallsChanged(canAddCall);
        }
    }

    @Override
    public void onBringToForeground(boolean showDialpad) {
        super.onBringToForeground(showDialpad);
        if (getCallbacks() != null) {
            getCallbacks().onBringToForeground(showDialpad);
        }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        super.onCallAudioStateChanged(audioState);
        if (getCallbacks() != null) {
            getCallbacks().onCallAudioStateChanged(audioState);
        }
    }

    @Override
    public void onSilenceRinger(){
        super.onSilenceRinger();
        if(getCallbacks() != null) {
            getCallbacks().onSilenceRinger();
        }
    }

    /**
     * @return the number of calls currently added to the {@code InCallService}.
     */
    public int getCallCount() {
        return mCalls.size();
    }

    /**
     * @return the number of conference calls currently added to the {@code InCallService}.
     */
    public int getConferenceCallCount() {
        return mConferenceCalls.size();
    }

    /**
     * @return the most recently added call that exists inside the {@code InCallService}
     */
    public Call getLastCall() {
        if (!mCalls.isEmpty()) {
            return mCalls.get(mCalls.size() - 1);
        }
        return null;
    }

    /**
     * @return the most recently added conference call that exists inside the {@code InCallService}
     */
    public Call getLastConferenceCall() {
        if (!mConferenceCalls.isEmpty()) {
            return mConferenceCalls.get(mConferenceCalls.size() - 1);
        }
        return null;
    }

    public void disconnectLastCall() {
        final Call call = getLastCall();
        if (call != null) {
            call.disconnect();
        }
    }

    public void disconnectLastConferenceCall() {
        final Call call = getLastConferenceCall();
        if (call != null) {
            call.disconnect();
        }
    }

    public void disconnectAllCalls() {
        for (final Call call: mCalls) {
            call.disconnect();
        }
    }

    public void disconnectAllConferenceCalls() {
        for (final Call call: mConferenceCalls) {
            call.disconnect();
        }
    }

    public static void setCallbacks(InCallServiceCallbacks callbacks) {
        synchronized (sLock) {
            sCallbacks = callbacks;
        }
    }

    private InCallServiceCallbacks getCallbacks() {
        synchronized (sLock) {
            if (sCallbacks != null) {
                sCallbacks.setService(this);
            }
            return sCallbacks;
        }
    }

    /**
     * Determines if a video callback has been registered for the passed in call.
     *
     * @param call The call.
     * @return {@code true} if a video callback has been registered.
     */
    public boolean isVideoCallbackRegistered(Call call) {
        return mVideoCallCallbacks.containsKey(call);
    }

    /**
     * Retrieves the video callbacks associated with a call.
     * @param call The call.
     * @return The {@link MockVideoCallCallback} instance associated with the call.
     */
    public MockVideoCallCallback getVideoCallCallback(Call call) {
        return mVideoCallCallbacks.get(call);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(LOG_TAG, "Service has been unbound");
        assertTrue(mIsServiceBound);
        mIsServiceBound = false;
        return super.onUnbind(intent);
    }

    public static boolean isServiceBound() {
        return mIsServiceBound;
    }
}
