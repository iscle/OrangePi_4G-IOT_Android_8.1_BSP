/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.testinput;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.util.Log;

import com.android.tv.testing.ChannelInfo;
import com.android.tv.testing.ChannelUtils;
import com.android.tv.testing.Constants;
import com.android.tv.testing.ProgramInfo;
import com.android.tv.testing.ProgramUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The setup activity for {@link TestTvInputService}.
 */
public class TestTvInputSetupActivity extends Activity {
    private static final String TAG = "TestTvInputSetup";
    private String mInputId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInputId = getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);

        DialogFragment newFragment = new MyAlertDialogFragment();
        newFragment.show(getFragmentManager(), "dialog");
    }

    private void registerChannels(int channelCount) {
        TestTvInputSetupActivity context = this;
        registerChannels(context, mInputId, channelCount);
    }

    public static void registerChannels(Context context, String inputId, int channelCount) {
        Log.i(TAG, "Registering " + channelCount + " channels");
        List<ChannelInfo> channels = new ArrayList<>();
        for (int i = 1; i <= channelCount; i++) {
            channels.add(ChannelInfo.create(context, i));
        }
        ChannelUtils.updateChannels(context, inputId, channels);

        // Reload channels so we have the ids.
        Map<Long, ChannelInfo> channelIdToInfoMap =
                ChannelUtils.queryChannelInfoMapForTvInput(context, inputId);
        for (Long channelId : channelIdToInfoMap.keySet()) {
            ProgramInfo programInfo = ProgramInfo.create();
            ProgramUtils.populatePrograms(context, TvContract.buildChannelUri(channelId),
                    programInfo);
        }
    }

    public static class MyAlertDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity()).setTitle(R.string.simple_setup_title)
                    .setMessage(R.string.simple_setup_message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // TODO: add UI to ask how many channels
                            ((TestTvInputSetupActivity) getActivity())
                                    .registerChannels(Constants.UNIT_TEST_CHANNEL_COUNT);
                            // Sets the results so that the application can process the
                            // registered channels properly.
                            getActivity().setResult(Activity.RESULT_OK);
                            getActivity().finish();
                        }
                    }).setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    getActivity().finish();
                                }
                            }).create();
        }
    }
}
