/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.verifier.sensors.sixdof.Fragments;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.sixdof.Activities.TestActivity;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * Provides the instructions for a particular phase before it starts.
 */
public class PhaseStartFragment extends Fragment {
    // Identifier for setting and retrieving the phase this Fragment was designed for.
    private static final String ARG_PHASE = "ArgPhase";

    Button mBtnStart;
    TextView mTvDesc;

    TestActivity.CTSTest mPhase;
    TestActivity mActivity;

    public static PhaseStartFragment newInstance(TestActivity.CTSTest phase) {
        PhaseStartFragment fragment = new PhaseStartFragment();
        Bundle arguments = new Bundle();
        arguments.putSerializable(ARG_PHASE, phase);
        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_start_phase, container, false);
        mBtnStart = (Button) rootView.findViewById(R.id.btnStart);
        mTvDesc = (TextView) rootView.findViewById(R.id.tvDesc);
        mActivity = (TestActivity) getActivity();

        mPhase = (TestActivity.CTSTest) getArguments().getSerializable(ARG_PHASE);

        switch (mPhase) {
            case ACCURACY:
                mTvDesc.setText(getString(R.string.phase1_description));
                getActivity().setTitle(getResources().getStringArray(R.array.phase)[TestActivity.CTSTest.ACCURACY.ordinal()]);
                break;
            case ROBUSTNESS:
                mTvDesc.setText(getString(R.string.phase2_description));
                getActivity().setTitle(getResources().getStringArray(R.array.phase)[TestActivity.CTSTest.ROBUSTNESS.ordinal()]);
                break;
            case COMPLEX_MOVEMENT:
                mTvDesc.setText(getString(R.string.phase3_description));
                getActivity().setTitle(getResources().getStringArray(R.array.phase)[TestActivity.CTSTest.COMPLEX_MOVEMENT.ordinal()]);
                break;
            default:
                throw new AssertionError("Trying to start a test that doesn't exist");
        }

        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mActivity.switchToTestFragment(mPhase);
            }
        });

        return rootView;
    }
}
