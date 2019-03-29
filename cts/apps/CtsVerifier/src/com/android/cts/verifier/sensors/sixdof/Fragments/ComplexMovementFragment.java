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
import com.android.cts.verifier.sensors.sixdof.BuildConfig;
import com.android.cts.verifier.sensors.sixdof.Activities.StartActivity;
import com.android.cts.verifier.sensors.sixdof.Activities.TestActivity;
import com.android.cts.verifier.sensors.sixdof.Dialogs.ComplexMovementResultDialog;
import com.android.cts.verifier.sensors.sixdof.Interfaces.ComplexMovementListener;
import com.android.cts.verifier.sensors.sixdof.Renderer.ComplexMovementRenderer;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.ComplexMovementPath;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Ring;
import com.android.cts.verifier.sensors.sixdof.Utils.ResultObjects.ResultObject;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * UI fragment for the third test.
 */
public class ComplexMovementFragment extends BaseUiFragment implements ComplexMovementListener {
    private static final String TAG = "ComplexMovementFragment";

    private TextView mTvObjective;
    private TextView mTvRings;

    /**
     * Standard practice to have a static newInstance constructor. Used to pass in arguments to the
     * fragment. We don't have any at the moment, but this is good structure for the future.
     *
     * @return a new Robustness test fragment.
     */
    public static ComplexMovementFragment newInstance() {
        return new ComplexMovementFragment();
    }

    /**
     * Called when the parent activity has been created. Adds the GLSurfaceView to the fragment
     * layout.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        GLSurfaceView surfaceView = new GLSurfaceView(getActivity());
        surfaceView.setEGLContextClientVersion(2);
        mRenderer = new ComplexMovementRenderer(getActivity(), mActivity.getRings());
        surfaceView.setRenderer(mRenderer);
        mLLCameraLayout = (LinearLayout) getView().findViewById(R.id.llCamera);
        mLLCameraLayout.addView(surfaceView);
        Log.d(TAG, "Camera Preview add to layout");
    }

    /**
     * Initialises all of the UI elements
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_complex_movement, container, false);
        getActivity().setTitle(getResources().getStringArray(R.array.phase)[TestActivity.CTSTest.COMPLEX_MOVEMENT.ordinal()]);

        // Set up pass/info/fail buttons.
        setupButtons(view, TestActivity.CTSTest.COMPLEX_MOVEMENT);

        mPlaceWaypointButton = (ImageButton) view.findViewById(R.id.fabPlaceWaypoint);
        mPlaceWaypointButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mActivity.attemptWaypointPlacement();
                } catch (WaypointAreaCoveredException e) {
                    Toast.makeText(getActivity(),
                            getString(R.string.error_area), Toast.LENGTH_SHORT).show();
                } catch (WaypointDistanceException e) {
                    Toast.makeText(getActivity(),
                            getString(R.string.error_distance), Toast.LENGTH_SHORT).show();
                } catch (WaypointStartPointException e) {
                    Toast.makeText(getActivity(),
                            getString(R.string.error_start_point), Toast.LENGTH_SHORT).show();
                } catch (WaypointRingNotEnteredException e) {
                    Toast.makeText(getActivity(),
                            getString(R.string.error_rings_not_entered), Toast.LENGTH_SHORT).show();
                }
            }
        });

        mTvObjective = (TextView) view.findViewById(R.id.tvObjective);
        mTvRings = (TextView) view.findViewById(R.id.tvRings);

        return view;
    }

    /**
     * Called after onCreateView. Starts listening for 6DoF events.
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mActivity.listenFor6DofData(this);
    }

    @Override
    protected void setupUILoop() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (mActivity == null || getActivity() == null) {
                    return;
                }

                int waypointCount = mActivity.getUserGeneratedWaypoints(Manager.Lap.LAP_4).size();
                mTvObjective.setText(getObjectiveText(Manager.Lap.LAP_4, waypointCount));

                int ringCount = 0;
                for (Ring ring : mActivity.getRings()) {
                    if (ring.getPathNumber() == waypointCount && ring.isEntered()) {
                        ringCount++;
                    }
                }

                mTvRings.setText(String.format(getString(R.string.rings_entered),
                        ringCount, ComplexMovementPath.RINGS_PER_PATH));

                if (waypointCount < Manager.MAX_MARKER_NUMBER) {
                    mPlaceWaypointButton.setVisibility(View.VISIBLE);
                }

                // Update the UI again in x milliseconds.
                if (mHandler != null) {
                    mHandler.postDelayed(this, UI_UPDATE_DELAY);
                }
            }
        };

        super.initUIHandler(runnable);
    }

    /**
     * Shows initial instruction dialog
     */
    @Override
    protected void showInitialDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMessage(R.string.phase3_initial_message)
                .setTitle(getResources().getStringArray(R.array.phase)[TestActivity.CTSTest.COMPLEX_MOVEMENT.ordinal()])
                .setPositiveButton(R.string.got_it, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onWaypointPlaced() {
        super.onWaypointPlaced();
        ((ComplexMovementRenderer) mRenderer).onWaypointPlaced(mActivity.getUserGeneratedWaypoints(Manager.Lap.LAP_4).size());
    }

    @Override
    public void onRingEntered(Ring ring) {
        ((ComplexMovementRenderer) mRenderer).onRingEntered(ring);
    }

    @Override
    public void onResult(ResultObject result) {
        ComplexMovementResultDialog dialog = ComplexMovementResultDialog.newInstance(result);
        dialog.setTargetFragment(ComplexMovementFragment.this, DIALOG_FRAGMENT);
        dialog.show(getActivity().getFragmentManager(), "ResultDialogFragment");
        mPlaceWaypointButton.setVisibility(View.INVISIBLE);

        if (result.hasPassed() || BuildConfig.DEBUG) {
            mBtnPass.setEnabled(true);
            mBtnPass.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finishTest();
                }
            });
        }
    }

    private void finishTest() {
        Intent resultIntent = getActivity().getIntent();
        String report = "Couldn't create test report.";
        try {
            report = mActivity.getTestReport().getContents();
        } catch (IOException e) {
            Log.e(TAG, report);
        }
        resultIntent.putExtra(TestActivity.EXTRA_REPORT, report);
        resultIntent.putExtra(TestActivity.EXTRA_RESULT_ID, StartActivity.ResultCode.PASSED);
        getActivity().setResult(Activity.RESULT_OK, resultIntent);
        getActivity().finish();
    }
}
