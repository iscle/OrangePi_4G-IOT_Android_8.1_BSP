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

import android.app.Activity;
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
import com.android.tv.settings.name.setup.DeviceNameFlowStartActivity;
import com.android.tv.settings.util.GuidedActionsAlignUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fragment responsible for showing the device names list.
 */
public class DeviceNameSetFragment extends GuidedStepFragment {
    private ArrayList<String> mDeviceNames = new ArrayList<>();

    public static DeviceNameSetFragment newInstance() {
        return new DeviceNameSetFragment();
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
                getString(R.string.select_device_name_title, Build.MODEL),
                getString(R.string.select_device_name_description, Build.MODEL),
                null,
                null);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        mDeviceNames.add(Build.MODEL);
        mDeviceNames.addAll(Arrays.asList(getResources().getStringArray(R.array.rooms)));
        // The strings added above are static names that should always be shown.
        String currentDeviceName = DeviceManager.getDeviceName(getActivity());
        if (currentDeviceName == null) {
            currentDeviceName = Build.MODEL;
        }
        // Ideally we don't want to have identical entries. (e.g., if a device was named
        // "Android TV", then "Android TV" (from static names) will be pre-selected/highlighted
        // instead of being added to top of the list.
        // However, since "Enter Custom Name..." is not considered as an static name, if someone
        // name his/her device to be "Enter Custom Name..." (same to the title of action to
        // customize device name), this name will still show at top of list just like any other
        // "normal" names, co-existing with the action button at bottom.
        if (mDeviceNames.indexOf(currentDeviceName) == -1) {
            mDeviceNames.add(0, currentDeviceName);
        }

        final int length = mDeviceNames.size();
        for (int i = 0; i < length; i++) {
            actions.add(new GuidedAction.Builder()
                    .title(mDeviceNames.get(i))
                    .id(i)
                    .build());
        }
        actions.add(new GuidedAction.Builder()
                .title(getString(R.string.custom_room))
                .id(mDeviceNames.size())
                .build());
        super.onCreateActions(actions, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        int currentNamePosition = mDeviceNames.indexOf(DeviceManager.getDeviceName(getActivity()));
        if (currentNamePosition != -1) {
            setSelectedActionPosition(currentNamePosition);
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        final long id = action.getId();
        if (id < 0 || id > mDeviceNames.size()) {
            throw new IllegalStateException("Unknown action ID");
        } else if (id < mDeviceNames.size()) {
            DeviceManager.setDeviceName(getActivity(), mDeviceNames.get((int) id));

            // Set the flag for the appropriate exit animation for setup.
            if (getActivity() instanceof DeviceNameFlowStartActivity) {
                ((DeviceNameFlowStartActivity) getActivity()).setResultOk(true);
            }

            getActivity().setResult(Activity.RESULT_OK);
            getActivity().finish();
        } else if (id == mDeviceNames.size()) {
            GuidedStepFragment.add(getFragmentManager(), DeviceNameSetCustomFragment.newInstance());
        }
    }

    // Overriding this method removes the unpreferable exit transition animation of this fragment,
    // which is currently only applied before showing DeviceNameSetCustomFragment.
    // Be sure not to remove this method or leave its body empty as it is also used on Settings
    // (not during Setup) and we need its default enter transition animation in that case.
    @Override
    protected void onProvideFragmentTransitions() {
        super.onProvideFragmentTransitions();
        setExitTransition(null);
    }
}
