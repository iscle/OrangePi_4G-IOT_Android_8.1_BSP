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

package com.android.tv.settings.connectivity;

import com.android.tv.settings.connectivity.setup.MessageWizardFragment;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * Activity to wait a few seconds before returning
 */
public class TimedMessageWizardFragment extends MessageWizardFragment {

    public interface Listener {
        void onTimedMessageCompleted();
    }

    private static final int MSG_TIME_OUT = 1;
    private static final int DEFAULT_TIME_OUT_MS = 3 * 1000;
    private static final String KEY_TIME_OUT_DURATION = "time_out_duration";

    public static TimedMessageWizardFragment newInstance(String title) {
        return newInstance(title, DEFAULT_TIME_OUT_MS);
    }

    public static TimedMessageWizardFragment newInstance(String title, int durationMs) {
        TimedMessageWizardFragment fragment = new TimedMessageWizardFragment();
        Bundle args = new Bundle();
        args.putInt(KEY_TIME_OUT_DURATION, durationMs);
        MessageWizardFragment.addArguments(args, title, false);
        fragment.setArguments(args);
        return fragment;
    }


    private Handler mTimeoutHandler;
    private Listener mListener;

    @Override
    public void onAttach(Activity activity) {
        if (activity instanceof Listener) {
            mListener = (Listener) activity;
        } else {
            throw new IllegalArgumentException("Activity must implement "
                    + "TimedMessageWizardFragment.Listener to use this fragment.");
        }
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mTimeoutHandler = new Handler(getActivity().getMainLooper()) {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_TIME_OUT:
                        if (mListener != null) {
                            mListener.onTimedMessageCompleted();
                        }
                        break;
                    default:
                        break;
                }
            }
        };
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mTimeoutHandler.sendEmptyMessageDelayed(MSG_TIME_OUT,
                getArguments().getInt(KEY_TIME_OUT_DURATION, DEFAULT_TIME_OUT_MS));
    }

    @Override
    public void onPause() {
        super.onPause();
        mTimeoutHandler.removeMessages(MSG_TIME_OUT);
    }
}
