/*
 * Copyright (C) 2015 Android Open Source Project
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

package com.android.server.telecom.testapps;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.telecom.Call;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class CallListAdapter extends BaseAdapter {
    private static final String TAG = "CallListAdapter";

    private final TestCallList.Listener mListener = new TestCallList.Listener() {
        @Override
        public void onCallAdded(Call call) {
            notifyDataSetChanged();
        }

        @Override
        public void onCallRemoved(Call call) {
            notifyDataSetChanged();
            if (mCallList.size() == 0) {
                mCallList.removeListener(this);
            }
        }
    };

    private final LayoutInflater mLayoutInflater;
    private final TestCallList mCallList;
    private final Handler mHandler = new Handler();
    private final Runnable mSecondsRunnable = new Runnable() {
        @Override
        public void run() {
            notifyDataSetChanged();
            if (mCallList.size() > 0) {
                mHandler.postDelayed(this, 1000);
            }
        }
    };

    public CallListAdapter(Context context) {
        mLayoutInflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mCallList = TestCallList.getInstance();
        mCallList.addListener(mListener);
        mHandler.postDelayed(mSecondsRunnable, 1000);
    }


    @Override
    public int getCount() {
        Log.i(TAG, "size reporting: " + mCallList.size());
        return mCallList.size();
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        Log.i(TAG, "getView: " + position);
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.call_list_item, parent, false);
        }

        TextView phoneNumber = (TextView) convertView.findViewById(R.id.phoneNumber);
        TextView duration = (TextView) convertView.findViewById(R.id.duration);
        TextView state = (TextView) convertView.findViewById(R.id.callState);

        Call call = mCallList.getCall(position);
        Uri handle = call.getDetails().getHandle();
        phoneNumber.setText(handle == null ? "No number" : handle.getSchemeSpecificPart());

        long durationMs = System.currentTimeMillis() - call.getDetails().getConnectTimeMillis();
        duration.setText((durationMs / 1000) + " secs");

        state.setText(getStateString(call));

        Log.i(TAG, "Call found: " + ((handle == null) ? "null" : handle.getSchemeSpecificPart())
                + ", " + durationMs);

        return convertView;
    }

    private static String getStateString(Call call) {
        switch (call.getState()) {
            case Call.STATE_ACTIVE:
                return "active";
            case Call.STATE_CONNECTING:
                return "connecting";
            case Call.STATE_DIALING:
                return "dialing";
            case Call.STATE_DISCONNECTED:
                return "disconnected";
            case Call.STATE_DISCONNECTING:
                return "disconnecting";
            case Call.STATE_HOLDING:
                return "on hold";
            case Call.STATE_NEW:
                return "new";
            case Call.STATE_RINGING:
                return "ringing";
            case Call.STATE_SELECT_PHONE_ACCOUNT:
                return "select phone account";
            default:
                return "unknown";
        }
    }
}
