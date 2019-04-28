/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.telecom.testapps;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.ConnectionRequest;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.ArrayMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the list of {@link SelfManagedConnection} active in the sample third-party calling app.
 */
public class SelfManagedCallList {
    public abstract static class Listener {
        public void onCreateIncomingConnectionFailed(ConnectionRequest request) {};
        public void onCreateOutgoingConnectionFailed(ConnectionRequest request) {};
        public void onConnectionListChanged() {};
    }

    public static String SELF_MANAGED_ACCOUNT_1 = "1";
    public static String SELF_MANAGED_ACCOUNT_2 = "2";
    public static String SELF_MANAGED_NAME_1 = "SuperCall";
    public static String SELF_MANAGED_NAME_2 = "Mega Call";

    private static SelfManagedCallList sInstance;
    private static ComponentName COMPONENT_NAME = new ComponentName(
            SelfManagedCallList.class.getPackage().getName(),
            SelfManagedConnectionService.class.getName());
    private static Uri SELF_MANAGED_ADDRESS_1 = Uri.fromParts(PhoneAccount.SCHEME_TEL, "555-1212",
            "");
    private static Uri SELF_MANAGED_ADDRESS_2 = Uri.fromParts(PhoneAccount.SCHEME_SIP,
            "me@test.org", "");
    private static Map<String, PhoneAccountHandle> mPhoneAccounts = new ArrayMap();

    public static SelfManagedCallList getInstance() {
        if (sInstance == null) {
            sInstance = new SelfManagedCallList();
        }
        return sInstance;
    }

    private Listener mListener;

    private List<SelfManagedConnection> mConnections = new ArrayList<>();

    private SelfManagedConnection.Listener mConnectionListener =
            new SelfManagedConnection.Listener() {
                @Override
                public void onConnectionStateChanged(SelfManagedConnection connection) {
                    notifyCallModified();
                }

                @Override
                public void onConnectionRemoved(SelfManagedConnection connection) {
                    removeConnection(connection);
                    notifyCallModified();
                }
    };

    public SelfManagedConnection.Listener getConnectionListener() {
        return mConnectionListener;
    }


    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void registerPhoneAccounts(Context context) {
        registerPhoneAccount(context, SELF_MANAGED_ACCOUNT_1, SELF_MANAGED_ADDRESS_1,
                SELF_MANAGED_NAME_1, true /* areCallsLogged */);
        registerPhoneAccount(context, SELF_MANAGED_ACCOUNT_2, SELF_MANAGED_ADDRESS_2,
                SELF_MANAGED_NAME_2, false /* areCallsLogged */);
    }

    public void registerPhoneAccount(Context context, String id, Uri address, String name,
                                     boolean areCallsLogged) {
        PhoneAccountHandle handle = new PhoneAccountHandle(COMPONENT_NAME, id);
        mPhoneAccounts.put(id, handle);
        Bundle extras = new Bundle();
        extras.putBoolean(PhoneAccount.EXTRA_SUPPORTS_HANDOVER_TO, true);
        if (areCallsLogged) {
            extras.putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, true);
        }
        PhoneAccount.Builder builder = PhoneAccount.builder(handle, name)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                .setAddress(address)
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_VIDEO_CALLING |
                        PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING)
                .setExtras(extras)
                .setShortDescription(name);

        TelecomManager.from(context).registerPhoneAccount(builder.build());
    }

    public PhoneAccountHandle getPhoneAccountHandle(String id) {
        return mPhoneAccounts.get(id);
    }

    public void notifyCreateIncomingConnectionFailed(ConnectionRequest request) {
        if (mListener != null) {
            mListener.onCreateIncomingConnectionFailed(request);
        }
    }

    public void notifyCreateOutgoingConnectionFailed(ConnectionRequest request) {
        if (mListener != null) {
            mListener.onCreateOutgoingConnectionFailed(request);
        }
    }

    public void addConnection(SelfManagedConnection connection) {
        Log.i(this, "addConnection %s", connection);
        mConnections.add(connection);
        if (mListener != null) {
            Log.i(this, "addConnection calling onConnectionListChanged %s", connection);
            mListener.onConnectionListChanged();
        }
    }

    public void removeConnection(SelfManagedConnection connection) {
        Log.i(this, "removeConnection %s", connection);
        mConnections.remove(connection);
        if (mListener != null) {
            Log.i(this, "removeConnection calling onConnectionListChanged %s", connection);
            mListener.onConnectionListChanged();
        }
    }

    public List<SelfManagedConnection> getConnections() {
        return mConnections;
    }

    public SelfManagedConnection getConnectionById(int callId) {
        Optional<SelfManagedConnection> foundOptional = mConnections.stream()
                .filter((c) -> {return c.getCallId() == callId;})
                .findFirst();
        return foundOptional.orElse(null);
    }

    public void notifyCallModified() {
        if (mListener != null) {
            mListener.onConnectionListChanged();
        }
    }
}
