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
package com.android.cts.verifier.sensors.sixdof.Activities;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.sixdof.Activities.StartActivity.ResultCode;
import com.android.cts.verifier.sensors.sixdof.Fragments.AccuracyFragment;
import com.android.cts.verifier.sensors.sixdof.Fragments.ComplexMovementFragment;
import com.android.cts.verifier.sensors.sixdof.Fragments.DataFragment;
import com.android.cts.verifier.sensors.sixdof.Fragments.PhaseStartFragment;
import com.android.cts.verifier.sensors.sixdof.Fragments.RobustnessFragment;
import com.android.cts.verifier.sensors.sixdof.Interfaces.AccuracyListener;
import com.android.cts.verifier.sensors.sixdof.Interfaces.BaseUiListener;
import com.android.cts.verifier.sensors.sixdof.Interfaces.ComplexMovementListener;
import com.android.cts.verifier.sensors.sixdof.Interfaces.RobustnessListener;
import com.android.cts.verifier.sensors.sixdof.Utils.ReportExporter;
import com.android.cts.verifier.sensors.sixdof.Utils.TestReport;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager.Lap;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Ring;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.RotationData;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;
import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.PoseProvider;
import com.android.cts.verifier.sensors.sixdof.Utils.ResultObjects.ResultObject;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.AlertDialog;
import android.app.Activity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Main Activity for 6DOF tests Handles calls between UI fragments and the Data fragment. The
 * controller in the MVC structure.
 */
public class TestActivity extends Activity implements BaseUiListener, AccuracyListener,
        RobustnessListener, ComplexMovementListener {

    private static final String TAG = "TestActivity";
    private static final String TAG_DATA_FRAGMENT = "data_fragment";
    public static final String EXTRA_RESULT_ID = "extraResult";
    public static final String EXTRA_REPORT = "extraReport";
    public static final String EXTRA_ON_RESTART = "6dof_verifier_restart";
    public static final Object POSE_LOCK = new Object();

    private DataFragment mDataFragment;

    private BaseUiListener mUiListener;
    private AccuracyListener mAccuracyListener;
    private RobustnessListener mRobustnessListener;
    private ComplexMovementListener mComplexMovementListener;

    private CTSTest mCurrentTest = CTSTest.ACCURACY;

    private boolean mHasBeenPaused = false;

    public enum CTSTest {
        ACCURACY,
        ROBUSTNESS,
        COMPLEX_MOVEMENT
    }

    /**
     * Initialises camera preview, looks for a retained data fragment if we have one and adds UI
     * fragment.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If we are restarting, kill the test as data is invalid.
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(EXTRA_ON_RESTART)) {
                Intent intent = this.getIntent();
                intent.putExtra(EXTRA_RESULT_ID, ResultCode.FAILED_PAUSE_AND_RESUME);
                this.setResult(RESULT_OK, intent);
                finish();
            }
        }

        setContentView(R.layout.activity_cts);

        // Add the first instructions fragment.
        Fragment fragment = PhaseStartFragment.newInstance(CTSTest.ACCURACY);
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.contentFragment, fragment);
        transaction.commit();

        mDataFragment = new DataFragment();
        fragmentManager.beginTransaction().add(mDataFragment, TAG_DATA_FRAGMENT).commit();

        // Lock the screen to its current rotation
        lockRotation();
    }

    /**
     * Lock the orientation of the device in its current state.
     */
    private void lockRotation() {
        final Display display = getWindowManager().getDefaultDisplay();
        int naturalOrientation = Configuration.ORIENTATION_LANDSCAPE;
        int configOrientation = getResources().getConfiguration().orientation;
        switch (display.getRotation()) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                // We are currently in the same basic orientation as the natural orientation
                naturalOrientation = configOrientation;
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                // We are currently in the other basic orientation to the natural orientation
                naturalOrientation = (configOrientation == Configuration.ORIENTATION_LANDSCAPE) ?
                        Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
                break;
        }

        int[] orientationMap = {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        };
        // Since the map starts at portrait, we need to offset if this device's natural orientation
        // is landscape.
        int indexOffset = 0;
        if (naturalOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            indexOffset = 1;
        }

        // The map assumes default rotation. Check for reverse rotation and correct map if required
        try {
            if (getResources().getBoolean(getResources().getSystem().getIdentifier(
                    "config_reverseDefaultRotation", "bool", "android"))) {
                orientationMap[0] = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                orientationMap[2] = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            }
        } catch (Resources.NotFoundException e) {
            // If resource is not found, assume default rotation and continue
            Log.d(TAG, "Cannot determine device rotation direction, assuming default");
        }

        setRequestedOrientation(orientationMap[(display.getRotation() + indexOffset) % 4]);
    }

    @Override
    public void onResume() {
        super.onResume();

        // 6DoF is reset after a recreation of activity, which invalidates the tests.
        if (mHasBeenPaused) {
            Intent intent = this.getIntent();
            intent.putExtra(EXTRA_RESULT_ID, ResultCode.FAILED_PAUSE_AND_RESUME);
            this.setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mHasBeenPaused = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_cts, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_save_results:
                saveResults();
                return true;
            case R.id.action_xml:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                try {
                    builder.setMessage(mDataFragment.getTestReport().getContents())
                            .setTitle(R.string.results)
                            .setPositiveButton(R.string.got_it, null);
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }

                AlertDialog dialog = builder.create();
                dialog.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void saveResults() {
        try {
            new ReportExporter(this, getTestReport().getContents()).execute();
        } catch (IOException e) {
            Log.e(TAG, "Couldn't create test report.");
        }
    }

    public TestReport getTestReport() {
        return mDataFragment.getTestReport();
    }

    public void listenFor6DofData(Fragment listener) {
        mUiListener = (BaseUiListener) listener;
        switch (mCurrentTest) {
            case ACCURACY:
                mAccuracyListener = (AccuracyListener) listener;
                mRobustnessListener = null;
                mComplexMovementListener = null;
                break;
            case ROBUSTNESS:
                mAccuracyListener = null;
                mRobustnessListener = (RobustnessListener) listener;
                mComplexMovementListener = null;
                break;
            case COMPLEX_MOVEMENT:
                mAccuracyListener = null;
                mRobustnessListener = null;
                mComplexMovementListener = (ComplexMovementListener) listener;
                break;
            default:
                throw new AssertionError("mCurrentTest is a test that doesn't exist!");
        }
    }

    public boolean isPoseProviderReady() {
        if (mDataFragment != null) {
            return mDataFragment.isPoseProviderReady();
        } else {
            return false;
        }

    }

    public ArrayList<Waypoint> getUserGeneratedWaypoints(Lap lap) {
        return mDataFragment.getUserGeneratedWaypoints(lap);
    }

    public Lap getLap() {
        return mDataFragment.getLap();
    }

    public ArrayList<Ring> getRings() {
        return mDataFragment.getRings();
    }

    @Override
    public void onPoseProviderReady() {
        if (mUiListener != null) {
            mUiListener.onPoseProviderReady();
        } else {
            Log.e(TAG, getString(R.string.error_null_fragment));
        }

        // Possible for this to be called while switching UI fragments, so mUiListener is null
        // but we want to start the test anyway.
        mDataFragment.testStarted();
    }

    @Override
    public void onWaypointPlaced() {
        if (mUiListener != null) {
            mUiListener.onWaypointPlaced();
        } else {
            Log.e(TAG, getString(R.string.error_null_fragment));
        }
    }

    @Override
    public void onResult(ResultObject result) {
        if (mUiListener != null) {
            mUiListener.onResult(result);
        } else {
            Log.e(TAG, getString(R.string.error_null_fragment));
        }
    }

    @Override
    public void onReset() {
        if (mAccuracyListener != null) {
            if (mCurrentTest == CTSTest.ACCURACY) {
                mAccuracyListener.onReset();
            } else {
                throw new RuntimeException("We are in the wrong test for this listener to be called.");
            }
        } else {
            Log.e(TAG, getString(R.string.error_null_fragment));
        }
    }

    @Override
    public void lap1Complete() {
        if (mAccuracyListener != null) {
            mAccuracyListener.lap1Complete();
        } else {
            Log.e(TAG, getString(R.string.error_null_fragment));
        }
    }

    public void attemptWaypointPlacement() throws WaypointAreaCoveredException, WaypointDistanceException, WaypointStartPointException, WaypointRingNotEnteredException {
        mDataFragment.onWaypointPlacementAttempt();
    }

    public void undoWaypointPlacement() {
        if (mDataFragment != null) {
            mDataFragment.undoWaypointPlacement();
        } else {
            Log.e(TAG, getString(R.string.error_retained_fragment_null));
        }
    }

    public void readyForLap2() {
        mDataFragment.startTest(CTSTest.ACCURACY);
    }

    public float getLatestDistanceData() {
        return mDataFragment.getLatestDistanceData();
    }

    public float getTimeRemaining() {
        return mDataFragment.getTimeRemaining();
    }

    public PoseProvider getPoseProvider() {
        return mDataFragment.getPoseProvider();
    }

    @Override
    public void onNewRotationData(RotationData data) {
        if (mRobustnessListener != null) {
            mRobustnessListener.onNewRotationData(data);
        } else {
            Log.e(TAG, getString(R.string.error_null_fragment));
        }
    }

    @Override
    public void onRingEntered(Ring ring) {
        if (mComplexMovementListener != null) {
            mComplexMovementListener.onRingEntered(ring);
        } else {
            Log.e(TAG, getString(R.string.error_null_fragment));
        }
    }

    /**
     * Loads test fragment for a particular phase.
     *
     * @param phase test to be started.
     */
    public void switchToTestFragment(CTSTest phase) {
        Log.d(TAG, "switchToTestFragment");
        Fragment fragment;

        switch (phase) {
            case ACCURACY:
                fragment = AccuracyFragment.newInstance();
                break;
            case ROBUSTNESS:
                fragment = RobustnessFragment.newInstance();
                break;
            case COMPLEX_MOVEMENT:
                fragment = ComplexMovementFragment.newInstance(); //Complex Motion
                break;
            default:
                throw new AssertionError("Trying to start a test that doesn't exist!");
        }
        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.contentFragment, fragment);
        transaction.commit();
    }

    /**
     * Loads start instruction fragment for a particular test.
     *
     * @param phase test to show instruction screen for.
     */
    public void switchToStartFragment(CTSTest phase) {
        Log.e(TAG, "switchToStartFragment");
        mUiListener = null;
        mAccuracyListener = null;
        mRobustnessListener = null;
        mComplexMovementListener = null;

        mCurrentTest = phase;
        mDataFragment.startTest(mCurrentTest);
        Fragment fragment = PhaseStartFragment.newInstance(phase);
        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.contentFragment, fragment);
        transaction.commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // We are always going to be restarting if this is called.
        outState.putBoolean(EXTRA_ON_RESTART, true);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onDestroyUi();
        mUiListener = null;
        mAccuracyListener = null;
        mRobustnessListener = null;
        mComplexMovementListener = null;
        mDataFragment = null;
    }

    @Override
    public void onDestroyUi() {
        if (mUiListener != null) {
            mUiListener.onDestroyUi();
        }
    }
}
