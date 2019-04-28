/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.hfp;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;


// Note:
// All methods in this class are not thread safe, donot call them from
// multiple threads. Call them from the HeadsetPhoneStateMachine message
// handler only.
class HeadsetPhoneState {
    private static final String TAG = "HeadsetPhoneState";

    private HeadsetStateMachine mStateMachine;
    private TelephonyManager mTelephonyManager;
    private ServiceState mServiceState;

    // HFP 1.6 CIND service
    private int mService = HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE;

    // Check this before sending out service state to the device -- if the SIM isn't fully
    // loaded, don't expose that the network is available.
    private boolean mIsSimStateLoaded = false;

    // Number of active (foreground) calls
    private int mNumActive = 0;

    // Current Call Setup State
    private int mCallState = HeadsetHalConstants.CALL_STATE_IDLE;

    // Number of held (background) calls
    private int mNumHeld = 0;

    // HFP 1.6 CIND signal
    private int mSignal = 0;

    // HFP 1.6 CIND roam
    private int mRoam = HeadsetHalConstants.SERVICE_TYPE_HOME;

    // HFP 1.6 CIND battchg
    private int mBatteryCharge = 0;

    private int mSpeakerVolume = 0;

    private int mMicVolume = 0;

    private boolean mListening = false;

    // when HFP Service Level Connection is established
    private boolean mSlcReady = false;

    private Context mContext = null;

    private PhoneStateListener mPhoneStateListener = null;

    private SubscriptionManager mSubMgr;

    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            listenForPhoneState(false);
            listenForPhoneState(true);
        }
    };


    HeadsetPhoneState(Context context, HeadsetStateMachine stateMachine) {
        mStateMachine = stateMachine;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (mTelephonyManager == null) {
            Log.e(TAG, "getSystemService(Context.TELEPHONY_SERVICE) failed, "
                  + "cannot register for SubscriptionInfo changes");
        }
        mContext = context;

        // Register for SubscriptionInfo list changes which is guaranteed
        // to invoke onSubscriptionInfoChanged and which in turns calls
        // loadInBackgroud.
        mSubMgr = SubscriptionManager.from(mContext);
        mSubMgr.addOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);
    }

    public void cleanup() {
        listenForPhoneState(false);
        mSubMgr.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);

        mTelephonyManager = null;
        mStateMachine = null;
    }

    void listenForPhoneState(boolean start) {

        mSlcReady = start;

        if (start) {
            startListenForPhoneState();
        } else {
            stopListenForPhoneState();
        }

    }

    private void startListenForPhoneState() {
        if (!mListening && mSlcReady && mTelephonyManager != null) {

            int subId = SubscriptionManager.getDefaultSubscriptionId();

            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                mPhoneStateListener = getPhoneStateListener(subId);
                if (mTelephonyManager == null) {
                    Log.e(TAG, "mTelephonyManager is null, "
                         + "cannot start listening for phone state changes");
                } else {
                    mTelephonyManager.listen(mPhoneStateListener,
                                             PhoneStateListener.LISTEN_SERVICE_STATE |
                                             PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
                    mListening = true;
                }
            }
        }
    }

    private void stopListenForPhoneState() {
        if (mListening && mTelephonyManager != null) {

            if (mTelephonyManager == null) {
                Log.e(TAG, "mTelephonyManager is null, "
                     + "cannot send request to stop listening for phone state changes");
            } else {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
                mListening = false;
            }
        }
    }

    int getService() {
        return mService;
    }

    int getNumActiveCall() {
        return mNumActive;
    }

    void setNumActiveCall(int numActive) {
        mNumActive = numActive;
    }

    int getCallState() {
        return mCallState;
    }

    void setCallState(int callState) {
        mCallState = callState;
    }

    int getNumHeldCall() {
        return mNumHeld;
    }

    void setNumHeldCall(int numHeldCall) {
        mNumHeld = numHeldCall;
    }

    int getSignal() {
        return mSignal;
    }

    int getRoam() {
        return mRoam;
    }

    void setRoam(int roam) {
        if (mRoam != roam) {
            mRoam = roam;
            sendDeviceStateChanged();
        }
    }

    void setBatteryCharge(int batteryLevel) {
        if (mBatteryCharge != batteryLevel) {
            mBatteryCharge = batteryLevel;
            sendDeviceStateChanged();
        }
    }

    int getBatteryCharge() {
        return mBatteryCharge;
    }

    void setSpeakerVolume(int volume) {
        mSpeakerVolume = volume;
    }

    int getSpeakerVolume() {
        return mSpeakerVolume;
    }

    void setMicVolume(int volume) {
        mMicVolume = volume;
    }

    int getMicVolume() {
        return mMicVolume;
    }

    boolean isInCall() {
        return (mNumActive >= 1);
    }

    void sendDeviceStateChanged()
    {
        int service =
                mIsSimStateLoaded ? mService : HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE;
        // When out of service, send signal strength as 0. Some devices don't
        // use the service indicator, but only the signal indicator
        int signal = service == HeadsetHalConstants.NETWORK_STATE_AVAILABLE ? mSignal : 0;

        Log.d(TAG, "sendDeviceStateChanged. mService="+ mService +
                   " mIsSimStateLoaded=" + mIsSimStateLoaded +
                   " mSignal=" + signal +" mRoam="+ mRoam +
                   " mBatteryCharge=" + mBatteryCharge);
        HeadsetStateMachine sm = mStateMachine;
        if (sm != null) {
            sm.sendMessage(HeadsetStateMachine.DEVICE_STATE_CHANGED,
                new HeadsetDeviceState(service, mRoam, signal, mBatteryCharge));
        }
    }

    private PhoneStateListener getPhoneStateListener(int subId) {
        PhoneStateListener mPhoneStateListener = new PhoneStateListener(subId) {
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                mServiceState = serviceState;
                int newService = (serviceState.getState() == ServiceState.STATE_IN_SERVICE) ?
                    HeadsetHalConstants.NETWORK_STATE_AVAILABLE :
                    HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE;
                int newRoam = serviceState.getRoaming() ? HeadsetHalConstants.SERVICE_TYPE_ROAMING
                                                  : HeadsetHalConstants.SERVICE_TYPE_HOME;

                if (newService == mService && newRoam == mRoam) {
                    // Debounce the state change
                    return;
                }
                mService = newService;
                mRoam = newRoam;

                // If this is due to a SIM insertion, we want to defer sending device state changed
                // until all the SIM config is loaded.
                if (newService == HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE) {
                    mIsSimStateLoaded = false;
                    sendDeviceStateChanged();
                    return;
                }
                IntentFilter simStateChangedFilter =
                        new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
                mContext.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                            // This is a sticky broadcast, so if it's already been loaded,
                            // this'll execute immediately.
                            if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(
                                    intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE))) {
                                mIsSimStateLoaded = true;
                                sendDeviceStateChanged();
                                /// M: catch the exception Receiver not registered @{
                                try {
                                    mContext.unregisterReceiver(this);
                                } catch (Exception e) {
                                    Log.w(TAG, "Unable to unregister SIM state receiver", e);
                                }
                                /// @}
                            }
                        }
                    }
                }, simStateChangedFilter);
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {

                int prevSignal = mSignal;
                if (mService == HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE) {
                    mSignal = 0;
                } else if (signalStrength.isGsm()) {
                    mSignal = signalStrength.getLteLevel();
                    if (mSignal == SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
                        mSignal = gsmAsuToSignal(signalStrength);
                    } else {
                        // SignalStrength#getLteLevel returns the scale from 0-4
                        // Bluetooth signal scales at 0-5
                        // Let's match up the larger side
                        mSignal++;
                    }
                } else {
                    mSignal = cdmaDbmEcioToSignal(signalStrength);
                }

                // network signal strength is scaled to BT 1-5 levels.
                // This results in a lot of duplicate messages, hence this check
                if (prevSignal != mSignal) {
                    sendDeviceStateChanged();
                }
            }

            /* convert [0,31] ASU signal strength to the [0,5] expected by
             * bluetooth devices. Scale is similar to status bar policy
             */
            private int gsmAsuToSignal(SignalStrength signalStrength) {
                int asu = signalStrength.getGsmSignalStrength();
                if      (asu == 99) return 0;
                else if (asu >= 16) return 5;
                else if (asu >= 8)  return 4;
                else if (asu >= 4)  return 3;
                else if (asu >= 2)  return 2;
                else if (asu >= 1)  return 1;
                else                return 0;
            }

            /**
             * Convert the cdma / evdo db levels to appropriate icon level.
             * The scale is similar to the one used in status bar policy.
             *
             * @param signalStrength
             * @return the icon level
             */
            private int cdmaDbmEcioToSignal(SignalStrength signalStrength) {
                int levelDbm = 0;
                int levelEcio = 0;
                int cdmaIconLevel = 0;
                int evdoIconLevel = 0;
                int cdmaDbm = signalStrength.getCdmaDbm();
                int cdmaEcio = signalStrength.getCdmaEcio();

                if (cdmaDbm >= -75) levelDbm = 4;
                else if (cdmaDbm >= -85) levelDbm = 3;
                else if (cdmaDbm >= -95) levelDbm = 2;
                else if (cdmaDbm >= -100) levelDbm = 1;
                else levelDbm = 0;

                // Ec/Io are in dB*10
                if (cdmaEcio >= -90) levelEcio = 4;
                else if (cdmaEcio >= -110) levelEcio = 3;
                else if (cdmaEcio >= -130) levelEcio = 2;
                else if (cdmaEcio >= -150) levelEcio = 1;
                else levelEcio = 0;

                cdmaIconLevel = (levelDbm < levelEcio) ? levelDbm : levelEcio;

                // STOPSHIP: Change back to getRilVoiceRadioTechnology
                if (mServiceState != null &&
                      (mServiceState.getRadioTechnology() ==
                          ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0 ||
                       mServiceState.getRadioTechnology() ==
                           ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A)) {
                      int evdoEcio = signalStrength.getEvdoEcio();
                      int evdoSnr = signalStrength.getEvdoSnr();
                      int levelEvdoEcio = 0;
                      int levelEvdoSnr = 0;

                      // Ec/Io are in dB*10
                      if (evdoEcio >= -650) levelEvdoEcio = 4;
                      else if (evdoEcio >= -750) levelEvdoEcio = 3;
                      else if (evdoEcio >= -900) levelEvdoEcio = 2;
                      else if (evdoEcio >= -1050) levelEvdoEcio = 1;
                      else levelEvdoEcio = 0;

                      if (evdoSnr > 7) levelEvdoSnr = 4;
                      else if (evdoSnr > 5) levelEvdoSnr = 3;
                      else if (evdoSnr > 3) levelEvdoSnr = 2;
                      else if (evdoSnr > 1) levelEvdoSnr = 1;
                      else levelEvdoSnr = 0;

                      evdoIconLevel = (levelEvdoEcio < levelEvdoSnr) ? levelEvdoEcio : levelEvdoSnr;
                }
                // TODO(): There is a bug open regarding what should be sent.
                return (cdmaIconLevel > evdoIconLevel) ?  cdmaIconLevel : evdoIconLevel;
            }
        };
        return mPhoneStateListener;
    }

}

class HeadsetDeviceState {
    int mService;
    int mRoam;
    int mSignal;
    int mBatteryCharge;

    HeadsetDeviceState(int service, int roam, int signal, int batteryCharge) {
        mService = service;
        mRoam = roam;
        mSignal = signal;
        mBatteryCharge = batteryCharge;
    }
}

class HeadsetCallState {
    int mNumActive;
    int mNumHeld;
    int mCallState;
    String mNumber;
    int mType;

    public HeadsetCallState(int numActive, int numHeld, int callState, String number, int type) {
        mNumActive = numActive;
        mNumHeld = numHeld;
        mCallState = callState;
        mNumber = number;
        mType = type;
    }
}

class HeadsetClccResponse {
    int mIndex;
    int mDirection;
    int mStatus;
    int mMode;
    boolean mMpty;
    String mNumber;
    int mType;

    public HeadsetClccResponse(int index, int direction, int status, int mode, boolean mpty,
                               String number, int type) {
        mIndex = index;
        mDirection = direction;
        mStatus = status;
        mMode = mode;
        mMpty = mpty;
        mNumber = number;
        mType = type;
    }
}

class HeadsetVendorSpecificResultCode {
    BluetoothDevice mDevice;
    String mCommand;
    String mArg;

    public HeadsetVendorSpecificResultCode(BluetoothDevice device, String command, String arg) {
        mDevice = device;
        mCommand = command;
        mArg = arg;
    }
}
