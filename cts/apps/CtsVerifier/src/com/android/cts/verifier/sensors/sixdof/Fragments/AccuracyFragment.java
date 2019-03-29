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
import com.android.cts.verifier.sensors.sixdof.Dialogs.AccuracyResultDialog;
import com.android.cts.verifier.sensors.sixdof.Dialogs.Lap2Dialog;
import com.android.cts.verifier.sensors.sixdof.Interfaces.AccuracyListener;
import com.android.cts.verifier.sensors.sixdof.Renderer.AccuracyRenderer;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;
import com.android.cts.verifier.sensors.sixdof.Utils.ResultObjects.ResultObject;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.app.DialogFragment;
import android.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * UI fragment for the first test.
 */
public class AccuracyFragment extends BaseUiFragment implements AccuracyListener,
        Lap2Dialog.Lap2DialogListener {
    private static final String TAG = "AccuracyFragment";

    private String mCurrentObjective = "";

    private TextView mTvDistanceRemaining;
    private TextView mTvMarkers;
    private TextView mTvObjective;

    /**
     * Necessary empty constructor.
     */
    public AccuracyFragment() {
    }

    /**
     * Standard practice to have a static newInstance constructor. Used to pass in arguments to the
     * fragment. We don't have any at the moment, but this is good structure for the future.
     *
     * @return a new Accuracy test fragment.
     */
    public static AccuracyFragment newInstance() {
        AccuracyFragment fragment = new AccuracyFragment();
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
        mRenderer = new AccuracyRenderer(getActivity());
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
        View view = inflater.inflate(R.layout.fragment_accuracy, container, false);
        getActivity().setTitle(getResources().getStringArray(R.array.phase)[TestActivity.CTSTest.ACCURACY.ordinal()]);
        mTvDistanceRemaining = (TextView) view.findViewById(R.id.tvTranslations);
        mTvMarkers = (TextView) view.findViewById(R.id.tvMarkers);
        mTvObjective = (TextView) view.findViewById(R.id.tvObjective);
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

        // Setup buttons for pass/info/fail
        setupButtons(view, TestActivity.CTSTest.ACCURACY);

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
                DecimalFormat oneDecimalFormat = new DecimalFormat("0.0");

                if (mActivity == null || getActivity() == null) {
                    return;
                }

                String distanceString = "";
                String markerString = "";
                ArrayList<Waypoint> referenceWaypoints;

                switch (mActivity.getLap()) {
                    case LAP_1:
                        referenceWaypoints = mActivity.getUserGeneratedWaypoints(Manager.Lap.LAP_1);

                        float distanceRemaining = 0f;

                        if (referenceWaypoints.size() > 0) {
                            distanceRemaining = mActivity.getLatestDistanceData();
                            float adjustedDistanceRemaining = Math.max(distanceRemaining, 0);
                            distanceString = getResources().getString(R.string.distance_remaining) +
                                    oneDecimalFormat.format(adjustedDistanceRemaining);

                            markerString = getResources().getString(R.string.markers);
                            for (Waypoint waypoint : referenceWaypoints) {
                                markerString +=
                                        MathsUtils.coordinatesToString(waypoint.getCoordinates()) + "\n";
                            }
                        }

                        if (distanceRemaining <= 0 || referenceWaypoints.size() == 0) {
                            mPlaceWaypointButton.setVisibility(View.VISIBLE);
                        } else {
                            mPlaceWaypointButton.setVisibility(View.INVISIBLE);
                        }
                        break;
                    case LAP_2:
                        referenceWaypoints = mActivity.getUserGeneratedWaypoints(Manager.Lap.LAP_2);

                        if (referenceWaypoints.size() == Manager.MAX_MARKER_NUMBER) {
                            mPlaceWaypointButton.setVisibility(View.INVISIBLE);
                        } else {
                            mPlaceWaypointButton.setVisibility(View.VISIBLE);
                        }
                        break;
                    default:
                        //Possible for this state to be entered when switching fragments
                        Log.e(TAG, "Trying to run UI on Accuracy Test on a lap greater than 2");

                        //Use an empty list as not interested in this state
                        referenceWaypoints = new ArrayList<Waypoint>();
                }

                mCurrentObjective = getObjectiveText(mActivity.getLap(), referenceWaypoints.size());

                mTvDistanceRemaining.setText(distanceString);
                mTvMarkers.setText(markerString);
                mTvObjective.setText(mCurrentObjective);

                //Update the UI again in x milliseconds.
                if (mHandler != null) {
                    mHandler.postDelayed(this, UI_UPDATE_DELAY);
                }
            }
        };

        super.initUIHandler(runnable);
    }

    /**
     * Called when this phase is done and a result is ready. Shows the results dialog and enables
     * pass button if test has been passed.
     */
    @Override
    public void onResult(ResultObject result) {
        AccuracyResultDialog dialog = AccuracyResultDialog.newInstance(result);
        dialog.setTargetFragment(AccuracyFragment.this, DIALOG_FRAGMENT);
        dialog.show(getActivity().getFragmentManager(), "ResultDialogFragment");
        mPlaceWaypointButton.setVisibility(View.INVISIBLE);

        if (result.hasPassed() || BuildConfig.DEBUG) {
            mBtnPass.setEnabled(true);
            mBtnPass.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onReadyForPhase2();
                }
            });
        }
    }

    /**
     * Resets UI to how it is at the start of test. Currently called when first waypoint is undone.
     */
    @Override
    public void onReset() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPlaceWaypointButton.setVisibility(View.VISIBLE);
                mTvDistanceRemaining.setText("");
                mTvMarkers.setText("");
                mCurrentObjective = getResources().getStringArray(R.array.initial_waypoint)[0];
                mTvObjective.setText(mCurrentObjective);
            }
        });
    }

    @Override
    public void lap1Complete() {
        onBackToFirstWaypoint();
        mActivity.readyForLap2();
    }

    /**
     * Shows initial instruction dialog
     */
    @Override
    protected void showInitialDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMessage(R.string.phase1_initial_message)
                .setTitle(R.string.initial)
                .setPositiveButton(R.string.got_it, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Called when user finishes the first lap
     */
    public void onBackToFirstWaypoint() {
        DialogFragment dialog = Lap2Dialog.newInstance();
        dialog.setTargetFragment(AccuracyFragment.this, DIALOG_FRAGMENT);
        dialog.show(getActivity().getFragmentManager(), "Lap2DialogFragment");
    }

    /**
     * Move to next test
     */
    public void onReadyForPhase2() {
        mActivity.switchToStartFragment(TestActivity.CTSTest.ROBUSTNESS);
    }

    /**
     * Called when lap 2 starts.
     */
    @Override
    public void onLap2Start() {
        mPlaceWaypointButton.setVisibility(View.VISIBLE);
    }
}
