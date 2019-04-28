/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream.telecom;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.TelecomManager;
import android.util.Log;
import com.android.car.stream.StreamCard;
import com.android.car.stream.StreamProducer;
import com.android.car.stream.telecom.StreamInCallService.StreamInCallServiceBinder;

/**
 * A {@link StreamProducer} that listens for active call events and produces a {@link StreamCard}
 */
public class CurrentCallStreamProducer extends StreamProducer
        implements StreamInCallService.InCallServiceCallback {
    private static final String TAG = "CurrentCallProducer";

    private StreamInCallService mInCallService;
    private PhoneCallback mPhoneCallback;
    private CurrentCallActionReceiver mCallActionReceiver;
    private Call mCurrentCall;
    private long mCurrentCallStartTime;

    private CurrentCallConverter mConverter;
    private AsyncTask mUpdateStreamItemTask;

    private String mDialerPackage;
    private TelecomManager mTelecomManager;

    public CurrentCallStreamProducer(Context context) {
        super(context);
    }

    @Override
    public void start() {
        super.start();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "current call producer started");
        }
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        mDialerPackage = mTelecomManager.getDefaultDialerPackage();
        mConverter = new CurrentCallConverter(mContext);
        mPhoneCallback = new PhoneCallback();

        Intent inCallServiceIntent = new Intent(mContext, StreamInCallService.class);
        inCallServiceIntent.setAction(StreamInCallService.LOCAL_INCALL_SERVICE_BIND_ACTION);
        mContext.bindService(inCallServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void stop() {
        mContext.unbindService(mServiceConnection);
        super.stop();
    }

    private void acceptCall() {
        synchronized (mTelecomManager) {
            if (mCurrentCall != null && mCurrentCall.getState() == Call.STATE_RINGING) {
                mCurrentCall.answer(0 /* videoState */);
            }
        }
    }

    private void disconnectCall() {
        synchronized (mTelecomManager) {
            if (mCurrentCall != null) {
                mCurrentCall.disconnect();
            }
        }
    }

    @Override
    public void onCallAdded(Call call) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "on call added, state: " + call.getState());
        }
        mCurrentCall = call;
        updateStreamCard(mCurrentCall, mContext);
        call.registerCallback(mPhoneCallback);
    }

    @Override
    public void onCallRemoved(Call call) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "on call removed, state: " + call.getState());
        }
        call.unregisterCallback(mPhoneCallback);
        updateStreamCard(call, mContext);
        mCurrentCall = null;
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        if (mCurrentCall != null && audioState != null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "audio state changed, is muted? " + audioState.isMuted());
            }
            updateStreamCard(mCurrentCall, mContext);
        }
    }

    private void clearUpdateStreamItemTask() {
        if (mUpdateStreamItemTask != null) {
            mUpdateStreamItemTask.cancel(false);
            mUpdateStreamItemTask = null;
        }
    }

    private void updateStreamCard(final Call call, final Context context) {
        // Only one update may be active at a time.
        clearUpdateStreamItemTask();

        mUpdateStreamItemTask = new AsyncTask<Void, Void, StreamCard>() {
            @Override
            protected StreamCard doInBackground(Void... voids) {
                try {
                    return mConverter.convert(call, context, mInCallService.isMuted(),
                            mCurrentCallStartTime, mDialerPackage);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create StreamItem.", e);
                    throw e;
                }
            }

            @Override
            protected void onPostExecute(StreamCard card) {
                if (call.getState() == Call.STATE_DISCONNECTED) {
                    removeCard(card);
                } else {
                    postCard(card);
                }
            }
        }.execute();
    }

    private class PhoneCallback extends Call.Callback {
        @Override
        public void onStateChanged(Call call, int state) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onStateChanged call: " + call + ", state: " + state);
            }

            if (state == Call.STATE_ACTIVE) {
                mCurrentCallStartTime = SystemClock.elapsedRealtime();
            } else {
                mCurrentCallStartTime = 0;
            }

            switch (state) {
                // TODO: Determine if a HUD or stream card should be displayed.
                case Call.STATE_RINGING: // Incoming call is ringing.
                case Call.STATE_DIALING: // Outgoing call that is dialing.
                case Call.STATE_ACTIVE:  // Call is connected
                case Call.STATE_DISCONNECTING: // Call is being disconnected
                case Call.STATE_DISCONNECTED:  // Call has finished.
                    updateStreamCard(call, mContext);
                    mCurrentCall = call;
                    break;
                default:
            }
        }
    }

    private class CurrentCallActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (!TelecomConstants.INTENT_ACTION_STREAM_CALL_CONTROL.equals(intentAction)) {
                return;
            }

            String action = intent.getStringExtra(TelecomConstants.EXTRA_STREAM_CALL_ACTION);
            switch (action) {
                case TelecomConstants.ACTION_MUTE:
                    mInCallService.setMuted(true);
                    break;
                case TelecomConstants.ACTION_UNMUTE:
                    mInCallService.setMuted(false);
                    break;
                case TelecomConstants.ACTION_ACCEPT_CALL:
                    acceptCall();
                    break;
                case TelecomConstants.ACTION_HANG_UP_CALL:
                    disconnectCall();
                    break;
                default:
            }
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StreamInCallServiceBinder binder = (StreamInCallServiceBinder) service;
            mInCallService = binder.getService();
            mInCallService.setCallback(CurrentCallStreamProducer.this);

            if (mCallActionReceiver == null) {
                mCallActionReceiver = new CurrentCallActionReceiver();
                mContext.registerReceiver(mCallActionReceiver,
                        new IntentFilter(TelecomConstants.INTENT_ACTION_STREAM_CALL_CONTROL));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mInCallService = null;
        }
    };
}
