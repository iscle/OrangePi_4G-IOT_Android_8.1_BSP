/*
 * Copyright 2015 The Android Open Source Project
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

package com.android.cts.verifier.camera.flashlight;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.HashSet;
import java.util.HashMap;

/**
 * This test checks the flashlight functionality by turning on and off the flashlight. After it
 * turns on or off the flashlight, it asks for user input to verify the flashlight status. The
 * test will pass when the user input is correct for all camera devices with a flash unit.
 */
public class CameraFlashlightActivity extends PassFailButtons.Activity {

    private static final String TAG = "CameraFlashlight";

    private CameraManager mCameraManager;
    private TestState mTestState;
    private final HashSet<String> mPendingCameraIds = new HashSet<>();
    private String mCurrentCameraId;

    private Button mInstructionButton;
    private Button mOnButton;
    private Button mOffButton;
    private TextView mInstructionTextView;
    private final HashSet<View> mAllButtons = new HashSet<>();
    // TestState -> enabled buttons
    private final HashMap<TestState, HashSet<View>> mStateButtonsMap = new HashMap<>();

    private enum TestState {
        NOT_STARTED,
        TESTING_ON,
        WAITING_ON_CALLBACK_ON,
        RESPONDED_ON_CORRECTLY,
        WAITING_ON_CALLBACK_OFF,
        TESTING_OFF,
        RESPONDED_OFF_CORRECTLY,
        ALL_PASSED,
        FAILED
    }

    private final View.OnClickListener mInstructionButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (mTestState) {
                case NOT_STARTED:
                    // Start testing turning on the first camera's flashlight.
                    // Fall through.
                case RESPONDED_OFF_CORRECTLY:
                    // Current camera passed. Start testing turning on next camera's flashlight.
                    if (mPendingCameraIds.size() == 0) {
                        // Passed
                        mTestState = TestState.ALL_PASSED;
                        updateButtonsAndInstructionLocked();
                        return;
                    }

                    mCurrentCameraId = (String)mPendingCameraIds.toArray()[0];
                    mPendingCameraIds.remove(mCurrentCameraId);

                    try {
                        mCameraManager.setTorchMode(mCurrentCameraId, true);
                        mTestState = TestState.WAITING_ON_CALLBACK_ON;
                    } catch (Exception e) {
                        e.printStackTrace();
                        mTestState = TestState.FAILED;
                    }
                    break;

                case RESPONDED_ON_CORRECTLY:
                    // Flashlight is on and user responded correctly.
                    // Turning off the flashlight.
                    try {
                        mCameraManager.setTorchMode(mCurrentCameraId, false);
                        mTestState = TestState.WAITING_ON_CALLBACK_OFF;
                    } catch (Exception e) {
                        e.printStackTrace();
                        mTestState = TestState.FAILED;
                    }
                    break;

                case FAILED:
                    // The test failed, report failure.
                    if (mCurrentCameraId != null) {
                        try {
                            mCameraManager.setTorchMode(mCurrentCameraId, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e(TAG, "Test failed but cannot turn off the torch");
                        }
                    }
                    setTestResultAndFinish(false);
                    break;

                case ALL_PASSED:
                    // The test passed, report pass.
                    setTestResultAndFinish(true);
                    break;
            }

            updateButtonsAndInstructionLocked();
        }
    };

    private final View.OnClickListener mOnButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Check if user responded correctly.
            if (mTestState == TestState.TESTING_ON) {
                mTestState = TestState.RESPONDED_ON_CORRECTLY;
            } else {
                mTestState = TestState.FAILED;
            }
            updateButtonsAndInstructionLocked();
        }
    };

    private final View.OnClickListener mOffButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Check if user responded correctly.
            if (mTestState == TestState.TESTING_OFF) {
                mTestState = TestState.RESPONDED_OFF_CORRECTLY;
            } else {
                mTestState = TestState.FAILED;
            }
            updateButtonsAndInstructionLocked();
        }
    };

    private final CameraManager.TorchCallback mTorchCallback = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (!cameraId.equals(mCurrentCameraId)) {
                return;
            }

            // Move to next state after receiving the expected callback.
            if (mTestState == TestState.WAITING_ON_CALLBACK_ON && enabled) {
                mTestState = TestState.TESTING_ON;
            } else if (mTestState == TestState.WAITING_ON_CALLBACK_OFF && !enabled) {
                mTestState = TestState.TESTING_OFF;
            }
            updateButtonsAndInstructionLocked();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize state -> buttons map
        for (TestState state : TestState.values()) {
            mStateButtonsMap.put(state, new HashSet<View>());
        }

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            String[] cameraIds = mCameraManager.getCameraIdList();
            for (String id : cameraIds) {
                CameraCharacteristics info = mCameraManager.getCameraCharacteristics(id);
                if (info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE).booleanValue() ==
                        true) {
                    mPendingCameraIds.add(id);
                }
            }
            mCameraManager.registerTorchCallback(mTorchCallback, new Handler());
        } catch (Exception e) {
            e.printStackTrace();
            mTestState = TestState.FAILED;
            updateButtonsAndInstructionLocked();
            return;
        }

        // Setup the UI.
        setContentView(R.layout.camera_flashlight);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.camera_flashlight_test, R.string.camera_flashlight_info, -1);

        mInstructionTextView = (TextView) findViewById(R.id.flash_instruction_text);

        // Get the buttons and attach the listener.
        mInstructionButton = (Button) findViewById(R.id.flash_instruction_button);
        mInstructionButton.setOnClickListener(mInstructionButtonListener);
        mStateButtonsMap.get(TestState.NOT_STARTED).add(mInstructionButton);
        mStateButtonsMap.get(TestState.RESPONDED_ON_CORRECTLY).add(mInstructionButton);
        mStateButtonsMap.get(TestState.RESPONDED_OFF_CORRECTLY).add(mInstructionButton);
        mStateButtonsMap.get(TestState.ALL_PASSED).add(mInstructionButton);
        mStateButtonsMap.get(TestState.FAILED).add(mInstructionButton);
        mAllButtons.add(mInstructionButton);

        mOnButton = (Button) findViewById(R.id.flash_on_button);
        mOnButton.setOnClickListener(mOnButtonListener);
        mStateButtonsMap.get(TestState.TESTING_ON).add(mOnButton);
        mStateButtonsMap.get(TestState.TESTING_OFF).add(mOnButton);
        mAllButtons.add(mOnButton);

        mOffButton = (Button) findViewById(R.id.flash_off_button);
        mOffButton.setOnClickListener(mOffButtonListener);
        mStateButtonsMap.get(TestState.TESTING_ON).add(mOffButton);
        mStateButtonsMap.get(TestState.TESTING_OFF).add(mOffButton);
        mAllButtons.add(mOffButton);

        View passButton = getPassButton();
        mStateButtonsMap.get(TestState.ALL_PASSED).add(passButton);
        mAllButtons.add(passButton);

        mTestState = TestState.NOT_STARTED;
        updateButtonsAndInstructionLocked();
    }


    private void updateButtonsAndInstructionLocked() {
        for (View v : mAllButtons) {
            v.setEnabled(false);
        }

        // Only enable the buttons for this state.
        HashSet<View> views = mStateButtonsMap.get(mTestState);
        for (View v : views) {
            v.setEnabled(true);
        }

        switch (mTestState) {
            case TESTING_ON:
            case TESTING_OFF:
                mInstructionTextView.setText(String.format(
                        getString(R.string.camera_flashlight_question_text), mCurrentCameraId));
                break;
            case RESPONDED_ON_CORRECTLY:
            case RESPONDED_OFF_CORRECTLY:
                mInstructionTextView.setText(R.string.camera_flashlight_next_text);
                mInstructionButton.setText(R.string.camera_flashlight_next_button);
                break;
            case FAILED:
                mInstructionTextView.setText(R.string.camera_flashlight_failed_text);
                mInstructionButton.setText(R.string.camera_flashlight_done_button);
                break;
            case ALL_PASSED:
                mInstructionTextView.setText(R.string.camera_flashlight_passed_text);
                mInstructionButton.setText(R.string.camera_flashlight_done_button);
                break;
            default:
                break;
        }
    }
}
