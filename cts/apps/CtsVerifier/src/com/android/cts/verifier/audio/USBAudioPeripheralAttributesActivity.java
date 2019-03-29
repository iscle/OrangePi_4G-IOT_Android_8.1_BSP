/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.android.cts.verifier.audio.peripheralprofile.ListsHelper;
import com.android.cts.verifier.audio.peripheralprofile.PeripheralProfile;

import com.android.cts.verifier.R;  // needed to access resource in CTSVerifier project namespace.

public class USBAudioPeripheralAttributesActivity extends USBAudioPeripheralActivity {
    private static final String TAG = "USBAudioPeripheralAttributesActivity";

    private TextView mTestStatusTx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uap_attribs_panel);

        connectPeripheralStatusWidgets();

        mTestStatusTx = (TextView)findViewById(R.id.uap_attribsStatusTx);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.usbaudio_attribs_test, R.string.usbaudio_attribs_info, -1);
    }

    //
    // USBAudioPeripheralActivity
    //
    public void updateConnectStatus() {
        boolean outPass = false;
        boolean inPass = false;
        if (mIsPeripheralAttached && mSelectedProfile != null) {
            boolean match = true;
            StringBuilder metaSb = new StringBuilder();

            // Outputs
            if (mOutputDevInfo != null) {
                AudioDeviceInfo deviceInfo = mOutputDevInfo;
                PeripheralProfile.ProfileAttributes attribs =
                    mSelectedProfile.getOutputAttributes();
                StringBuilder sb = new StringBuilder();

                // Channel Counts
                if (deviceInfo.getChannelCounts().length == 0) {
                    sb.append("Output - No Peripheral Channel Counts\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getChannelCounts(), attribs.mChannelCounts)) {
                    sb.append("Output - Channel Counts Mismatch\n");
                }

                // Encodings
                if (deviceInfo.getEncodings().length == 0) {
                    sb.append("Output - No Peripheral Encodings\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getEncodings(), attribs.mEncodings)) {
                    sb.append("Output - Encodings Mismatch\n");
                }

                // Sample Rates
                if (deviceInfo.getSampleRates().length == 0) {
                    sb.append("Output - No Peripheral Sample Rates\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getSampleRates(), attribs.mSampleRates)) {
                    sb.append("Output - Sample Rates Mismatch\n");
                }

                // Channel Masks
                if (deviceInfo.getChannelIndexMasks().length == 0 &&
                    deviceInfo.getChannelMasks().length == 0) {
                    sb.append("Output - No Peripheral Channel Masks\n");
                } else {
                    // Channel Index Masks
                    if (!ListsHelper.isSubset(deviceInfo.getChannelIndexMasks(),
                            attribs.mChannelIndexMasks)) {
                        sb.append("Output - Channel Index Masks Mismatch\n");
                    }

                    // Channel Position Masks
                    if (!ListsHelper.isSubset(deviceInfo.getChannelMasks(),
                            attribs.mChannelPositionMasks)) {
                        sb.append("Output - Channel Position Masks Mismatch\n");
                    }
                }

                // Report
                if (sb.toString().length() == 0){
                    metaSb.append("Output - Match\n");
                    outPass = true;
                } else {
                    metaSb.append(sb.toString());
                }
            } else {
                // No output device to test, so pass it.
                outPass = true;
            }

            // Inputs
            if (mInputDevInfo != null) {
                AudioDeviceInfo deviceInfo = mInputDevInfo;
                PeripheralProfile.ProfileAttributes attribs =
                    mSelectedProfile.getInputAttributes();
                StringBuilder sb = new StringBuilder();

                // Channel Counts
                if (deviceInfo.getChannelCounts().length == 0) {
                    sb.append("Input - No Peripheral Channel Counts\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getChannelCounts(), attribs.mChannelCounts)) {
                    sb.append("Input - Channel Counts Mismatch\n");
                }

                // Encodings
                if (deviceInfo.getEncodings().length == 0) {
                    sb.append("Input - No Peripheral Encodings\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getEncodings(), attribs.mEncodings)) {
                    sb.append("Input - Encodings Mismatch\n");
                }

                // Sample Rates
                if (deviceInfo.getSampleRates().length == 0) {
                    sb.append("Input - No Peripheral Sample Rates\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getSampleRates(), attribs.mSampleRates)) {
                    sb.append("Input - Sample Rates Mismatch\n");
                }

                // Channel Masks
                if (deviceInfo.getChannelIndexMasks().length == 0 &&
                        deviceInfo.getChannelMasks().length == 0) {
                    sb.append("Input - No Peripheral Channel Masks\n");
                } else {
                    if (!ListsHelper.isSubset(deviceInfo.getChannelIndexMasks(),
                            attribs.mChannelIndexMasks)) {
                        sb.append("Input - Channel Index Masks Mismatch\n");
                    }
                    if (!ListsHelper.isSubset(deviceInfo.getChannelMasks(),
                            attribs.mChannelPositionMasks)) {
                        sb.append("Input - Channel Position Masks Mismatch\n");
                    }
                }
                if (sb.toString().length() == 0){
                    metaSb.append("Input - Match\n");
                    inPass = true;
                } else {
                    metaSb.append(sb.toString());
                }
            } else {
                // No input device, so pass it.
                inPass = true;
            }

            mTestStatusTx.setText(metaSb.toString());
        } else {
            mTestStatusTx.setText("No Peripheral or No Matching Profile.");
        }

        getPassButton().setEnabled(outPass && inPass);
    }
}
