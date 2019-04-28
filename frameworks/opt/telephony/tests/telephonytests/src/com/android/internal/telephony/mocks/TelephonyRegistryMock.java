/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.mocks;

import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.VoLteServiceState;
import com.android.internal.telephony.IPhoneStateListener;
import com.android.internal.telephony.IOnSubscriptionsChangedListener;
import com.android.internal.telephony.ITelephonyRegistry;

import java.util.ArrayList;
import java.util.List;

public class TelephonyRegistryMock extends ITelephonyRegistry.Stub {

    private static class Record {
        String callingPackage;

        IBinder binder;

        IPhoneStateListener callback;
        IOnSubscriptionsChangedListener onSubscriptionsChangedListenerCallback;

        int callerUserId;

        int events;

        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;

        boolean canReadPhoneState;

        boolean matchPhoneStateListenerEvent(int events) {
            return (callback != null) && ((events & this.events) != 0);
        }

        boolean matchOnSubscriptionsChangedListener() {
            return (onSubscriptionsChangedListenerCallback != null);
        }

        @Override
        public String toString() {
            return "{callingPackage=" + callingPackage + " binder=" + binder
                    + " callback=" + callback
                    + " onSubscriptionsChangedListenererCallback="
                                            + onSubscriptionsChangedListenerCallback
                    + " callerUserId=" + callerUserId + " subId=" + subId + " phoneId=" + phoneId
                    + " events=" + Integer.toHexString(events)
                    + " canReadPhoneState=" + canReadPhoneState + "}";
        }
    }

    private final ArrayList<IBinder> mRemoveList = new ArrayList<IBinder>();
    private final ArrayList<Record> mRecords = new ArrayList<Record>();
    private boolean hasNotifySubscriptionInfoChangedOccurred = false;

    public TelephonyRegistryMock() {
    }

    private void remove(IBinder binder) {
        synchronized (mRecords) {
            final int recordCount = mRecords.size();
            for (int i = 0; i < recordCount; i++) {
                if (mRecords.get(i).binder == binder) {
                    mRecords.remove(i);
                    return;
                }
            }
        }
    }

    private void handleRemoveListLocked() {
        int size = mRemoveList.size();
        if (size > 0) {
            for (IBinder b: mRemoveList) {
                remove(b);
            }
            mRemoveList.clear();
        }
    }


    @Override
    public void addOnSubscriptionsChangedListener(String callingPackage,
            IOnSubscriptionsChangedListener callback) {
        Record r;

        synchronized (mRecords) {
            // register
            find_and_add: {
                IBinder b = callback.asBinder();
                final int N = mRecords.size();
                for (int i = 0; i < N; i++) {
                    r = mRecords.get(i);
                    if (b == r.binder) {
                        break find_and_add;
                    }
                }
                r = new Record();
                r.binder = b;
                mRecords.add(r);
            }

            r.onSubscriptionsChangedListenerCallback = callback;
            r.callingPackage = callingPackage;
            r.callerUserId = UserHandle.getCallingUserId();
            r.events = 0;
            r.canReadPhoneState = true; // permission has been enforced above
            // Always notify when registration occurs if there has been a notification.
            if (hasNotifySubscriptionInfoChangedOccurred) {
                try {
                    r.onSubscriptionsChangedListenerCallback.onSubscriptionsChanged();
                } catch (RemoteException e) {
                    remove(r.binder);
                }
            } else {
                //log("listen oscl: hasNotifySubscriptionInfoChangedOccurred==false no callback");
            }
        }

    }

    @Override
    public void removeOnSubscriptionsChangedListener(String pkgForDebug,
            IOnSubscriptionsChangedListener callback) {
        remove(callback.asBinder());
    }

    @Override
    public void notifySubscriptionInfoChanged() {
        synchronized (mRecords) {
            if (!hasNotifySubscriptionInfoChangedOccurred) {
                //log("notifySubscriptionInfoChanged: first invocation mRecords.size="
                //        + mRecords.size());
            }
            hasNotifySubscriptionInfoChangedOccurred = true;
            mRemoveList.clear();
            for (Record r : mRecords) {
                if (r.matchOnSubscriptionsChangedListener()) {
                    try {
                        r.onSubscriptionsChangedListenerCallback.onSubscriptionsChanged();
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    @Override
    public void listen(String pkg, IPhoneStateListener callback, int events, boolean notifyNow) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void listenForSubscriber(int subId, String pkg, IPhoneStateListener callback, int events,
                                    boolean notifyNow) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCallState(int state, String incomingNumber) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCallStateForPhoneId(int phoneId, int subId, int state,
                String incomingNumber) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyServiceStateForPhoneId(int phoneId, int subId, ServiceState state) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifySignalStrengthForPhoneId(int phoneId, int subId,
                SignalStrength signalStrength) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyMessageWaitingChangedForPhoneId(int phoneId, int subId, boolean mwi) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCallForwardingChanged(boolean cfi) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCallForwardingChangedForSubscriber(int subId, boolean cfi) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyDataActivity(int state) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyDataActivityForSubscriber(int subId, int state) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyDataConnection(int state, boolean isDataConnectivityPossible,
            String reason, String apn, String apnType, LinkProperties linkProperties,
            NetworkCapabilities networkCapabilities, int networkType, boolean roaming) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyDataConnectionForSubscriber(int subId, int state,
            boolean isDataConnectivityPossible, String reason, String apn, String apnType,
            LinkProperties linkProperties, NetworkCapabilities networkCapabilities,
            int networkType, boolean roaming) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyDataConnectionFailed(String reason, String apnType) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyDataConnectionFailedForSubscriber(int subId, String reason, String apnType) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCellLocation(Bundle cellLocation) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCellLocationForSubscriber(int subId, Bundle cellLocation) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyOtaspChanged(int otaspMode) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCellInfo(List<CellInfo> cellInfo) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyPreciseCallState(int ringingCallState, int foregroundCallState,
            int backgroundCallState) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyDisconnectCause(int disconnectCause, int preciseDisconnectCause) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyPreciseDataConnectionFailed(String reason, String apnType, String apn,
            String failCause) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyPreciseDataConnectionFailedForSubscriber(int subId, String reason,
            String apnType, String apn, String failCause) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCellInfoForSubscriber(int subId, List<CellInfo> cellInfo) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyVoLteServiceStateChanged(VoLteServiceState lteState) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyOemHookRawEventForSubscriber(int subId, byte[] rawData) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifyCarrierNetworkChange(boolean active) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void notifySimActivationStateChangedForPhoneId(int phoneId, int subId,
                                                          int activationType, int state) {
        throw new RuntimeException("Not implemented");
    }
}
