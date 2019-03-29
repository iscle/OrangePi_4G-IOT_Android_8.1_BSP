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


import com.android.cts.verifier.sensors.sixdof.Activities.TestActivity;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager;
import com.android.cts.verifier.sensors.sixdof.Utils.TestReport;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Ring;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;
import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.AndroidPoseProvider;
import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.PoseData;
import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.PoseProvider;

import android.app.Activity;
import android.content.Context;
import android.app.Fragment;

import java.util.ArrayList;

/**
 * This currently deals with the pose data and what to do with it.
 */
public class DataFragment extends Fragment implements PoseProvider.PoseProviderListener {
    private final static String TAG = "DataFragment";

    private TestReport mTestReport;
    private Manager mManager;

    private PoseProvider mPoseProvider;
    protected boolean mIsPoseProviderReady = false;

    @Override
    public void onStart() {
        super.onStart();
        mPoseProvider = new AndroidPoseProvider(getActivity(), this);
        mPoseProvider.setup();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPoseProvider = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        mPoseProvider.onStopPoseProviding();
        mIsPoseProviderReady = false;
    }

    /**
     * Start PoseProvider.
     */
    @Override
    public void onSetupComplete() {
        mPoseProvider.onStartPoseProviding();
    }

    @Override
    public void onNewPoseData(PoseData newPoseData) {
        if (!mIsPoseProviderReady) {
            mIsPoseProviderReady = true;
            mManager.onPoseProviderReady();
        }

        mManager.onNewPoseData(newPoseData);
    }

    /**
     * Assign the listener when this fragment is attached to an activity.
     *
     * @param activity the activity that this fragment is attached to.
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        initManager(activity);
    }

    private void initManager(Context context) {
        mTestReport = new TestReport(getActivity());
        mManager = new Manager(mTestReport);
        mManager.setupListeners(context);
    }

    /**
     * Nullify the listener to avoid leaking the activity.
     */
    @Override
    public void onDetach() {
        super.onDetach();
        mManager.stopListening();
    }

    /**
     * @return PoseProvider object associated with these tests.
     */
    public PoseProvider getPoseProvider() {
        return mPoseProvider;
    }

    /**
     * @return true if we are connected to the pose provider.
     */
    public boolean isPoseProviderReady() {
        return mIsPoseProviderReady;
    }

    /**
     * Gets all the markers (user generated waypoints) for the specified phase.
     *
     * @param lap the lap of the test to get the markers from
     * @return a list of the markers
     */
    public ArrayList<Waypoint> getUserGeneratedWaypoints(Manager.Lap lap) {
        switch (lap) {
            case LAP_1:
                return mManager.getReferencePathMarkers();
            case LAP_2:
                return mManager.getTestPathMarkers();
            case LAP_3:
                return mManager.getRobustnessMarker();
            case LAP_4:
                return mManager.getComplexMovementTestMarkers();
            default:
                throw new AssertionError("Unrecognised Lap!", null);
        }
    }

    /**
     * Returns a reference to the mTestReport object.
     */
    public TestReport getTestReport() {
        return mTestReport;
    }

    /**
     * Initiates the adding of a waypoint and checks if the state of the current test need to be
     * changed.
     *
     * @throws WaypointDistanceException    if the location is too close to another
     * @throws WaypointAreaCoveredException if the area covered by the user is too little
     * @throws WaypointStartPointException  if the location is not close enough to the start
     */
    public void onWaypointPlacementAttempt()
            throws WaypointStartPointException, WaypointDistanceException,
            WaypointAreaCoveredException, WaypointRingNotEnteredException {
        synchronized (TestActivity.POSE_LOCK) {
            mManager.addPoseDataToPath(
                    mPoseProvider.getLatestPoseData().getTranslationAsFloats(), true);
        }
    }

    /**
     * Removes the last marker added in the current test phase.
     */
    public void undoWaypointPlacement() {
        mManager.removeLastAddedMarker();
    }

    /**
     * Returns the current phase of the test.
     */
    public Manager.Lap getLap() {
        return mManager.getLap();
    }

    /**
     * Sets the test status to executed.
     */
    public void testStarted() {
        mTestReport.setTestState(TestReport.TestStatus.EXECUTED);
    }

    public void startTest(TestActivity.CTSTest newTest) {
        switch (newTest) {
            case ACCURACY:
                mManager.startAccuracyTest();
                break;
            case ROBUSTNESS:
                mManager.startRobustnessTest();
                break;
            case COMPLEX_MOVEMENT:
                mManager.startComplexMovementTest();
                break;
            default:
                throw new AssertionError("Test not recognised!");
        }
    }

    public float getLatestDistanceData() {
        return mManager.getRemainingPath();
    }

    public float getTimeRemaining() {
        return mManager.getTimeRemaining();
    }

    public ArrayList<Ring> getRings() {
        return mManager.getRings();
    }
}
