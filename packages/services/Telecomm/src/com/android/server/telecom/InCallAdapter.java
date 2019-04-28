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

import android.os.Binder;
import android.os.Bundle;
import android.telecom.Log;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telecom.IInCallAdapter;

import java.util.List;

/**
 * Receives call commands and updates from in-call app and passes them through to CallsManager.
 * {@link InCallController} creates an instance of this class and passes it to the in-call app after
 * binding to it. This adapter can receive commands and updates until the in-call app is unbound.
 */
class InCallAdapter extends IInCallAdapter.Stub {
    private final CallsManager mCallsManager;
    private final CallIdMapper mCallIdMapper;
    private final TelecomSystem.SyncRoot mLock;
    private final String mOwnerComponentName;

    /** Persists the specified parameters. */
    public InCallAdapter(CallsManager callsManager, CallIdMapper callIdMapper,
            TelecomSystem.SyncRoot lock, String ownerComponentName) {
        mCallsManager = callsManager;
        mCallIdMapper = callIdMapper;
        mLock = lock;
        mOwnerComponentName = ownerComponentName;
    }

    @Override
    public void answerCall(String callId, int videoState) {
        try {
            Log.startSession(LogUtils.Sessions.ICA_ANSWER_CALL, mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.d(this, "answerCall(%s,%d)", callId, videoState);
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.answerCall(call, videoState);
                    } else {
                        Log.w(this, "answerCall, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void rejectCall(String callId, boolean rejectWithMessage, String textMessage) {
        try {
            Log.startSession(LogUtils.Sessions.ICA_REJECT_CALL, mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.d(this, "rejectCall(%s,%b,%s)", callId, rejectWithMessage, textMessage);
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.rejectCall(call, rejectWithMessage, textMessage);
                    } else {
                        Log.w(this, "setRingback, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void playDtmfTone(String callId, char digit) {
        try {
            Log.startSession("ICA.pDT", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.d(this, "playDtmfTone(%s,%c)", callId, digit);
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.playDtmfTone(call, digit);
                    } else {
                        Log.w(this, "playDtmfTone, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void stopDtmfTone(String callId) {
        try {
            Log.startSession("ICA.sDT", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.d(this, "stopDtmfTone(%s)", callId);
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.stopDtmfTone(call);
                    } else {
                        Log.w(this, "stopDtmfTone, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void postDialContinue(String callId, boolean proceed) {
        try {
            Log.startSession("ICA.pDC", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.d(this, "postDialContinue(%s)", callId);
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.postDialContinue(call, proceed);
                    } else {
                        Log.w(this, "postDialContinue, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void disconnectCall(String callId) {
        try {
            Log.startSession(LogUtils.Sessions.ICA_DISCONNECT_CALL, mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Log.v(this, "disconnectCall: %s", callId);
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.disconnectCall(call);
                    } else {
                        Log.w(this, "disconnectCall, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void holdCall(String callId) {
        try {
            Log.startSession(LogUtils.Sessions.ICA_HOLD_CALL, mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.holdCall(call);
                    } else {
                        Log.w(this, "holdCall, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void unholdCall(String callId) {
        try {
            Log.startSession(LogUtils.Sessions.ICA_UNHOLD_CALL, mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.unholdCall(call);
                    } else {
                        Log.w(this, "unholdCall, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void phoneAccountSelected(String callId, PhoneAccountHandle accountHandle,
            boolean setDefault) {
        try {
            Log.startSession("ICA.pAS", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        mCallsManager.phoneAccountSelected(call, accountHandle, setDefault);
                    } else {
                        Log.w(this, "phoneAccountSelected, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void mute(boolean shouldMute) {
        try {
            Log.startSession(LogUtils.Sessions.ICA_MUTE, mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    mCallsManager.mute(shouldMute);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void setAudioRoute(int route) {
        try {
            Log.startSession(LogUtils.Sessions.ICA_SET_AUDIO_ROUTE, mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    mCallsManager.setAudioRoute(route);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void conference(String callId, String otherCallId) {
        try {
            Log.startSession(LogUtils.Sessions.ICA_CONFERENCE, mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    Call otherCall = mCallIdMapper.getCall(otherCallId);
                    if (call != null && otherCall != null) {
                        mCallsManager.conference(call, otherCall);
                    } else {
                        Log.w(this, "conference, unknown call id: %s or %s", callId, otherCallId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void splitFromConference(String callId) {
        try {
            Log.startSession("ICA.sFC", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.splitFromConference();
                    } else {
                        Log.w(this, "splitFromConference, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void mergeConference(String callId) {
        try {
            Log.startSession("ICA.mC", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.mergeConference();
                    } else {
                        Log.w(this, "mergeConference, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void swapConference(String callId) {
        try {
            Log.startSession("ICA.sC", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.swapConference();
                    } else {
                        Log.w(this, "swapConference, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void pullExternalCall(String callId) {
        try {
            Log.startSession("ICA.pEC", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.pullExternalCall();
                    } else {
                        Log.w(this, "pullExternalCall, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void sendCallEvent(String callId, String event, Bundle extras) {
        try {
            Log.startSession("ICA.sCE", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.sendCallEvent(event, extras);
                    } else {
                        Log.w(this, "sendCallEvent, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void putExtras(String callId, Bundle extras) {
        try {
            Log.startSession("ICA.pE", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.putExtras(Call.SOURCE_INCALL_SERVICE, extras);
                    } else {
                        Log.w(this, "putExtras, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void removeExtras(String callId, List<String> keys) {
        try {
            Log.startSession("ICA.rE", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.removeExtras(Call.SOURCE_INCALL_SERVICE, keys);
                    } else {
                        Log.w(this, "removeExtra, unknown call id: %s", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void turnOnProximitySensor() {
        try {
            Log.startSession("ICA.tOnPS", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    mCallsManager.turnOnProximitySensor();
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void turnOffProximitySensor(boolean screenOnImmediately) {
        try {
            Log.startSession("ICA.tOffPS", mOwnerComponentName);
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    mCallsManager.turnOffProximitySensor(screenOnImmediately);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
             Log.endSession();
        }
    }

    @Override
    public void sendRttRequest(String callId) {
        try {
            Log.startSession("ICA.sRR");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.sendRttRequest();
                    } else {
                        Log.w(this, "stopRtt(): call %s not found", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void respondToRttRequest(String callId, int id, boolean accept) {
        try {
            Log.startSession("ICA.rTRR");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.handleRttRequestResponse(id, accept);
                    } else {
                        Log.w(this, "respondToRttRequest(): call %s not found", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void stopRtt(String callId) {
        try {
            Log.startSession("ICA.sRTT");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    Call call = mCallIdMapper.getCall(callId);
                    if (call != null) {
                        call.stopRtt();
                    } else {
                        Log.w(this, "stopRtt(): call %s not found", callId);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void setRttMode(String callId, int mode) {
        try {
            Log.startSession("ICA.sRM");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    // TODO
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } finally {
            Log.endSession();
        }
    }

    @Override
    public void doMtkAction(Bundle bundle) {
        // do nothing for AOSP behavior
    }
}
