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
 * limitations under the License
 */

package com.android.tv.settings.name;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.settings.R;
import com.android.tv.settings.util.GuidedActionsAlignUtil;

import java.util.List;

/**
 * Fragment responsible for showing the device name summary.
 */
public class DeviceNameSummaryFragment extends GuidedStepFragment {

    public static DeviceNameSummaryFragment newInstance() {
        return new DeviceNameSummaryFragment();
    }

    @Override
    public GuidanceStylist onCreateGuidanceStylist() {
        return GuidedActionsAlignUtil.createGuidanceStylist();
    }

    @Override
    public GuidedActionsStylist onCreateActionsStylist() {
        return GuidedActionsAlignUtil.createGuidedActionsStylist();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        return GuidedActionsAlignUtil.createView(view, this);
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.device_rename_title, Build.MODEL),
                getString(R.string.device_rename_description, Build.MODEL,
                        DeviceManager.getDeviceName(getActivity())),
                null,
                null);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        final Context context = getActivity();
        actions.add(new GuidedAction.Builder(context)
                .id(GuidedAction.ACTION_ID_CONTINUE)
                .title(R.string.change_setting)
                .build());
        actions.add(new GuidedAction.Builder(context)
                .id(GuidedAction.ACTION_ID_CANCEL)
                .title(R.string.keep_settings)
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        final long actionId = action.getId();
        if (actionId == GuidedAction.ACTION_ID_CONTINUE) {
            GuidedStepFragment.add(getFragmentManager(), DeviceNameSetFragment.newInstance());
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            getActivity().finish();
        } else {
            throw new IllegalStateException("Unknown action");
        }
    }
}
