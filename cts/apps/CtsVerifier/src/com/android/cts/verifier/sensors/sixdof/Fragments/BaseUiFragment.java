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
import com.android.cts.verifier.sensors.sixdof.Activities.StartActivity;
import com.android.cts.verifier.sensors.sixdof.Activities.TestActivity;
import com.android.cts.verifier.sensors.sixdof.Interfaces.BaseUiListener;
import com.android.cts.verifier.sensors.sixdof.Renderer.BaseRenderer;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.app.Fragment;
import android.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import java.io.IOException;

/**
 * Abstract class that UI Fragments for each test inherit from,
 */
public abstract class BaseUiFragment extends Fragment implements BaseUiListener {
    private static final String TAG = "BaseUiFragment";
    protected static final long UI_UPDATE_DELAY = 200;

    protected static final int DIALOG_FRAGMENT = 1;

    protected Button mBtnPass;
    protected Button mBtnInfo;
    protected Button mBtnFail;
    protected ImageButton mPlaceWaypointButton;

    protected LinearLayout mLLCameraLayout;

    protected TestActivity mActivity;

    protected Handler mHandler;
    protected Runnable mUIUpdateRunnable;

    protected BaseRenderer mRenderer;

    /**
     * Called when this fragment is attached to an activity. Starts the test if the Pose service is
     * ready.
     */
    @Override
    public void onAttach(Activity context) {
        super.onAttach(context);
        mActivity = (TestActivity) getActivity();

        if (mActivity.isPoseProviderReady()) {
            onPoseProviderReady();
        }
    }

    protected void initUIHandler(Runnable uiRunnable) {
        mHandler = new Handler();
        mUIUpdateRunnable = uiRunnable;
        mHandler.postDelayed(mUIUpdateRunnable, UI_UPDATE_DELAY);
    }

    protected void setupButtons(View fragmentView, TestActivity.CTSTest currentPhase) {
        final int phaseIndex = currentPhase.ordinal();
        mBtnPass = (Button) fragmentView.findViewById(R.id.btnPass);
        mBtnInfo = (Button) fragmentView.findViewById(R.id.btnInfo);
        mBtnFail = (Button) fragmentView.findViewById(R.id.btnFail);

        mBtnInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                builder.setMessage(getResources().getStringArray(R.array.phase_descriptions)[phaseIndex])
                        .setTitle(getResources().getStringArray(R.array.phase)[phaseIndex])
                        .setPositiveButton(R.string.got_it, null);

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        mBtnFail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent resultIntent = getActivity().getIntent();
                String report = "Couldn't create test report.";
                try {
                    report = mActivity.getTestReport().getContents();
                } catch (IOException e) {
                    Log.e(TAG, report);
                }
                resultIntent.putExtra(TestActivity.EXTRA_REPORT, report);
                resultIntent.putExtra(TestActivity.EXTRA_RESULT_ID, StartActivity.ResultCode.FAILED);
                getActivity().setResult(Activity.RESULT_OK, resultIntent);
                getActivity().finish();
            }
        });
    }

    protected abstract void setupUILoop();

    protected abstract void showInitialDialog();

    protected String getObjectiveText(Manager.Lap lap, int waypointCount) {
        String currentObjective = "";
        int lapIndex = lap.ordinal();
        if (lapIndex > 1) lapIndex = 1; // Text is same for indexes 1, 2, 3

        switch (waypointCount) {
            case 0:
                currentObjective = getResources()
                        .getStringArray(R.array.initial_waypoint)[lapIndex];
                break;
            case Manager.MAX_MARKER_NUMBER - 1:
                currentObjective = getString(R.string.obj_return_to_initial_waypoint);
                break;
            case Manager.MAX_MARKER_NUMBER:
                currentObjective = "";
                mPlaceWaypointButton.setVisibility(View.INVISIBLE);
                break;
            default:
                currentObjective = getResources()
                        .getStringArray(R.array.next_waypoint)[lapIndex]
                        .replace('0', Character.forDigit(waypointCount, 10));
                break;
        }

        return currentObjective;
    }

    /**
     * Nullify activity to avoid memory leak.
     */
    @Override
    public void onDetach() {
        super.onDetach();

        mActivity = null;
        mHandler = null;
        mUIUpdateRunnable = null;
    }

    @Override
    public void onDestroyUi() {
        if (mRenderer != null) {
            mRenderer.onDestroy();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mRenderer != null) {
            mRenderer.disconnectCamera();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mRenderer != null) {
            mRenderer.connectCamera(mActivity.getPoseProvider(), getActivity());
        }
    }

    @Override
    public void onPoseProviderReady() {
        showInitialDialog();
        setupUILoop();
    }

    /**
     * Called when a waypoint has been successfully placed by user. Shows undo snackbar.
     */
    @Override
    public void onWaypointPlaced() {
        mPlaceWaypointButton.setVisibility(View.INVISIBLE);
    }
}
