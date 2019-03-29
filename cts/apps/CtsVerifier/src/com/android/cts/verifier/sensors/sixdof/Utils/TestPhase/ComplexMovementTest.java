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

package com.android.cts.verifier.sensors.sixdof.Utils.TestPhase;

import com.android.cts.verifier.sensors.sixdof.Dialogs.BaseResultsDialog;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager;
import com.android.cts.verifier.sensors.sixdof.Utils.TestReport;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.ComplexMovementPath;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.ReferencePath;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Ring;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Handles all the ComplexMovement test related features.
 */
public class ComplexMovementTest extends Test {
    private boolean mResultsGiven = false;

    /**
     * Created a new ComplexMovement path which is to be used in this test.
     *
     * @param referencePath Reference the the reference path.
     * @param testReport    The test report object to record the tests.
     * @param manager       The manager to call when the test is done.
     */
    public ComplexMovementTest(ReferencePath referencePath, TestReport testReport, Manager manager) {
        super(referencePath, testReport, manager, "Complex Movement Test");
        mTestPath = new ComplexMovementPath(mReferencePathDistances, mReferencePath.getCurrentPath());
    }

    /**
     * Implementation of the abstract method which check whether the test is complete.
     */
    @Override
    protected void runAdditionalMethods() {
        if (mTestPath.getPathMarkersSize() == MAX_MARKER_NUMBER && !mResultsGiven) {
            mResultsGiven = true;
            executeComplexMovementTests();
        }
    }

    /**
     * Starts the ComplexMovement tests.
     */
    private void executeComplexMovementTests() {
        HashMap<BaseResultsDialog.ResultType, Boolean> complexMovementTestResults;
        complexMovementTestResults = executeTests(true, false);
        complexMovementTestResults.put(BaseResultsDialog.ResultType.RINGS, testRings());
        mManager.onComplexMovementTestCompleted(complexMovementTestResults);
    }

    /**
     * Tests whether the current location enters a ring.
     *
     * @param location the current location of the user
     */
    public void checkIfARingHasBeenPassed(float[] location) {
        Ring ring = ((ComplexMovementPath) mTestPath).hasRingBeenEntered(location);
        if (ring != null && !ring.isEntered()) {
            // If ring has not already been entered.
            mManager.ringEntered(ring);
            ring.setEntered(true);
        }
    }

    /**
     * Finds the rings that have not been entered.
     *
     * @return true if all rings are entered and false if there is at least one ring not entered
     */
    public boolean testRings() {
        ArrayList<Ring> testArray = ((ComplexMovementPath) mTestPath).getRings();
        boolean state = true;
        for (int i = 0; i < testArray.size(); i++) {
            if (!testArray.get(i).isEntered()) {
                recordRingTestResults(i);
                state = false;
            }
        }
        return state;
    }

    /**
     * Forms a string for the failed ring and updates the test report with the string.
     *
     * @param ringIndex the index of the array the ring is in
     */
    private void recordRingTestResults(int ringIndex) {
        Ring ring = ((ComplexMovementPath) mTestPath).getRings().get(ringIndex);
        String testDetails =
                "Ring Test: Ring was not entered. Path number: " + ring.getPathNumber() +
                        "Ring number:" + ((ringIndex % ComplexMovementPath.RINGS_PER_PATH) + 1) + "\n";
        Log.e("Ring Result", testDetails);
        mTestReport.setFailDetails(testDetails);

    }

    /**
     * Returns the rings in the path.
     */
    public ArrayList<Ring> getRings() {
        return ((ComplexMovementPath) mTestPath).getRings();
    }
}
