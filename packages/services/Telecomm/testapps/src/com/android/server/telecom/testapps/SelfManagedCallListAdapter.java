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

import android.telecom.CallAudioState;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.server.telecom.testapps.R;

import java.util.List;

public class SelfManagedCallListAdapter extends BaseAdapter {

    private static final String TAG = "SelfMgCallListAd";
    /**
     * Listener used to handle tap of the "disconnect" button for a connection.
     */
    private View.OnClickListener mDisconnectListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View parent = (View) v.getParent().getParent();
            SelfManagedConnection connection = (SelfManagedConnection) parent.getTag();
            connection.setConnectionDisconnected(DisconnectCause.LOCAL);
            SelfManagedCallList.getInstance().removeConnection(connection);
        }
    };

    /**
     * Listener used to handle tap of the "active" button for a connection.
     */
    private View.OnClickListener mActiveListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View parent = (View) v.getParent().getParent();
            SelfManagedConnection connection = (SelfManagedConnection) parent.getTag();
            connection.setConnectionActive();
            notifyDataSetChanged();
        }
    };

    /**
     * Listener used to handle tap of the "held" button for a connection.
     */
    private View.OnClickListener mHeldListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View parent = (View) v.getParent().getParent();
            SelfManagedConnection connection = (SelfManagedConnection) parent.getTag();
            connection.setConnectionHeld();
            notifyDataSetChanged();
        }
    };

    private View.OnClickListener mSpeakerListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View parent = (View) v.getParent().getParent();
            SelfManagedConnection connection = (SelfManagedConnection) parent.getTag();
            connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
            notifyDataSetChanged();
        }
    };

    private View.OnClickListener mEarpieceListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View parent = (View) v.getParent().getParent();
            SelfManagedConnection connection = (SelfManagedConnection) parent.getTag();
            connection.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
            notifyDataSetChanged();
        }
    };

    private final LayoutInflater mLayoutInflater;

    private List<SelfManagedConnection> mConnections;

    public SelfManagedCallListAdapter(LayoutInflater layoutInflater,
                                      List<SelfManagedConnection> connections) {

        mLayoutInflater = layoutInflater;
        mConnections = connections;
    }

    @Override
    public int getCount() {
        return mConnections.size();
    }

    @Override
    public Object getItem(int position) {
        return mConnections.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View result = convertView == null
                ? mLayoutInflater.inflate(R.layout.self_managed_call_list_item, parent, false)
                : convertView;
        SelfManagedConnection connection = mConnections.get(position);
        PhoneAccountHandle phoneAccountHandle = connection.getExtras().getParcelable(
                SelfManagedConnection.EXTRA_PHONE_ACCOUNT_HANDLE);
        if (phoneAccountHandle.getId().equals(SelfManagedCallList.SELF_MANAGED_ACCOUNT_1)) {
            result.setBackgroundColor(result.getContext().getColor(R.color.test_call_a_color));
        } else {
            result.setBackgroundColor(result.getContext().getColor(R.color.test_call_b_color));
        }

        CallAudioState audioState = connection.getCallAudioState();
        String audioRoute = "?";
        if (audioState != null) {
            switch (audioState.getRoute()) {
                case CallAudioState.ROUTE_BLUETOOTH:
                    audioRoute = "BT";
                    break;
                case CallAudioState.ROUTE_SPEAKER:
                    audioRoute = "\uD83D\uDD0A";
                    break;
                case CallAudioState.ROUTE_EARPIECE:
                    audioRoute = "\uD83D\uDC42";
                    break;
                case CallAudioState.ROUTE_WIRED_HEADSET:
                    audioRoute = "\uD83D\uDD0C";
                    break;
                default:
                    audioRoute = "?";
                    break;
            }
        }
        String callType;
        if (connection.isIncomingCall()) {
            if (connection.isIncomingCallUiShowing()) {
                callType = "Incoming(our ux) ";
            } else {
                callType = "Incoming(sys ux) ";
            }
        } else {
            callType = "Outgoing";
        }
        setInfoForRow(result, phoneAccountHandle.getId(), connection.getAddress().toString(),
                android.telecom.Connection.stateToString(connection.getState()), audioRoute,
                callType);
        result.setTag(connection);
        return result;
    }

    public void updateConnections() {
        Log.i(TAG, "updateConnections "+ mConnections.size());

        notifyDataSetChanged();
    }

    private void setInfoForRow(View view, String accountName, String number,
                               String status, String audioRoute, String callType) {

        TextView numberTextView = (TextView) view.findViewById(R.id.phoneNumber);
        TextView statusTextView = (TextView) view.findViewById(R.id.callState);
        View activeButton = view.findViewById(R.id.setActiveButton);
        activeButton.setOnClickListener(mActiveListener);
        View disconnectButton = view.findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(mDisconnectListener);
        View setHeldButton = view.findViewById(R.id.setHeldButton);
        setHeldButton.setOnClickListener(mHeldListener);
        View speakerButton = view.findViewById(R.id.speakerButton);
        speakerButton.setOnClickListener(mSpeakerListener);
        View earpieceButton = view.findViewById(R.id.earpieceButton);
        earpieceButton.setOnClickListener(mEarpieceListener);
        numberTextView.setText(accountName + " - " + number + " (" + audioRoute + ")");
        statusTextView.setText(callType + " - Status: " + status);
    }
}
