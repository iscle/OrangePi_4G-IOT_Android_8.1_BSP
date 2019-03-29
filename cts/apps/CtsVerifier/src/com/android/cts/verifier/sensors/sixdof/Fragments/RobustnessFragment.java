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
import com.android.cts.verifier.sensors.sixdof.Activities.TestActivity;
import com.android.cts.verifier.sensors.sixdof.Dialogs.RobustnessResultDialog;
import com.android.cts.verifier.sensors.sixdof.Interfaces.RobustnessListener;
import com.android.cts.verifier.sensors.sixdof.Renderer.RobustnessRenderer;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.Colour;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.RotationData;
import com.android.cts.verifier.sensors.sixdof.Utils.ResultObjects.ResultObject;

import android.app.Activity;
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

/**
 * UI fragment for the second test.
 */
public class RobustnessFragment extends BaseUiFragment implements RobustnessListener {
    private static final String TAG = "RobustnessFragment";
    private static final Object TIMER_LOCK = new Object();

    private TextView mTvTime;
    private TextView mTvPassColour;
    private TextView mTvObjective;

    private boolean mIsPassing = false;
    private boolean mResultGiven = false;

    /**
     * Standard practice to have a static newInstance constructor. Used to pass in arguments to the
     * fragment. We don't have any at the moment, but this is good structure for the future.
     *
     * @return a new Robustness test fragment.
     */
    public static RobustnessFragment newInstance() {
        RobustnessFragment fragment = new RobustnessFragment();
        return fragment;
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
        mRenderer = new RobustnessRenderer(getActivity());
        surfaceView.setRenderer(mRenderer);
        mLLCameraLayout = (LinearLayout) getView().findViewById(R.id.llCamera);
        mLLCameraLayout.addView(surfaceView);
        Log.d(TAG, "Camera Preview add to layout");
    }

    /**
     * Initialises all of the UI elements.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_robustness, container, false);
        getActivity().setTitle(getResources().getStringArray(R.array.phase)[TestActivity.CTSTest.ROBUSTNESS.ordinal()]);

        // Set up pass/info/fail buttons
        setupButtons(view, TestActivity.CTSTest.ROBUSTNESS);

        mPlaceWaypointButton = (ImageButton) view.findViewById(R.id.fabPlaceWaypoint);
        mPlaceWaypointButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mActivity.attemptWaypointPlacement();
                } catch (WaypointDistanceException e) {
                    Toast.makeText(getActivity(),
                            getString(R.string.error_distance), Toast.LENGTH_SHORT).show();
                } catch (WaypointAreaCoveredException e) {
                    Toast.makeText(getActivity(),
                            getString(R.string.error_area), Toast.LENGTH_SHORT).show();
                } catch (WaypointStartPointException e) {
                    Toast.makeText(getActivity(),
                            getString(R.string.error_start_point), Toast.LENGTH_SHORT).show();
                } catch (WaypointRingNotEnteredException e) {
                    throw new AssertionError(
                            "WaypointRingNotEnteredException when not in 3rd test", e);
                }
            }
        });

        mTvTime = (TextView) view.findViewById(R.id.tvTimer);
        mTvPassColour = (TextView) view.findViewById(R.id.tvPassColour);
        mTvObjective = (TextView) view.findViewById(R.id.tvObjective);

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

                String stringTimeRemaining;
                String decimalTimeRemaining = (mActivity.getTimeRemaining() / 1000f) + "";
                synchronized (TIMER_LOCK) {
                    stringTimeRemaining = String.format(getString(R.string.time_remaining), decimalTimeRemaining);
                }

                synchronized (TIMER_LOCK) {
                    if (mIsPassing) {
                        mTvPassColour.setBackgroundColor(getResources().getColor(R.color.green));
                    } else {
                        mTvPassColour.setBackgroundColor(getResources().getColor(R.color.red));
                    }
                }

                int waypointCount = mActivity.getUserGeneratedWaypoints(Manager.Lap.LAP_3).size();
                mTvObjective.setText(getObjectiveText(Manager.Lap.LAP_3, waypointCount));

                if (waypointCount < Manager.MAX_MARKER_NUMBER && !mResultGiven) {
                    mPlaceWaypointButton.setVisibility(View.VISIBLE);
                    mTvTime.setText(stringTimeRemaining);
                } else {
                    mTvTime.setText("");
                }

                //Update the UI again in x milliseconds.
                if (mHandler != null) {
                    mHandler.postDelayed(this, UI_UPDATE_DELAY);
                }
            }
        };

        super.initUIHandler(runnable);
    }

    /**
     * Shows initial instruction dialog.
     */
    @Override
    protected void showInitialDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMessage(R.string.phase2_initial_message)
                .setTitle(getResources().getStringArray(R.array.phase)[TestActivity.CTSTest.ROBUSTNESS.ordinal()])
                .setPositiveButton(R.string.got_it, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onResult(final ResultObject result) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mResultGiven = true;
                RobustnessResultDialog dialog = RobustnessResultDialog.newInstance(result);
                dialog.setTargetFragment(RobustnessFragment.this, DIALOG_FRAGMENT);
                dialog.show(getActivity().getFragmentManager(), "ResultDialogFragment");
                mPlaceWaypointButton.setVisibility(View.INVISIBLE);

                if (result.hasPassed() || BuildConfig.DEBUG) {
                    mBtnPass.setEnabled(true);
                    mBtnPass.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            onReadyForPhase3();
                        }
                    });
                }
            }
        });
    }

    private void onReadyForPhase3() {
        mActivity.switchToStartFragment(TestActivity.CTSTest.COMPLEX_MOVEMENT);
    }

    @Override
    public void onNewRotationData(RotationData data) {
        synchronized (TIMER_LOCK) {
            mIsPassing = data.getRotationTestState();
        }

        if (mRenderer != null) {
            if (data.getRotationTestState()) {
                ((RobustnessRenderer) mRenderer).setLineColor(Colour.GREEN);
            } else {
                ((RobustnessRenderer) mRenderer).setLineColor(Colour.RED);
            }

            if (mActivity.getUserGeneratedWaypoints(Manager.Lap.LAP_3).size() > 0) {
                ((RobustnessRenderer) mRenderer).updateCurrentAngle(data.getCurrentAngle());
                ((RobustnessRenderer) mRenderer).updateTargetAngle(data.getTargetAngle());
            }
        }
    }
}
