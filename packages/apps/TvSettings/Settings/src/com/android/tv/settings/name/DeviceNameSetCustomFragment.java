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
import android.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.settings.R;
import com.android.tv.settings.name.setup.DeviceNameFlowStartActivity;
import com.android.tv.settings.util.GuidedActionsAlignUtil;

import java.util.List;

/**
 * Fragment responsible for adding new device name.
 */
public class DeviceNameSetCustomFragment extends GuidedStepFragment {

    private GuidedAction mEditAction;

    public static DeviceNameSetCustomFragment newInstance() {
        return new DeviceNameSetCustomFragment();
    }

    @Override
    public GuidanceStylist onCreateGuidanceStylist() {
        return GuidedActionsAlignUtil.createGuidanceStylist();
    }

    @Override
    public GuidedActionsStylist onCreateActionsStylist() {
        return GuidedActionsAlignUtil.createNoBackgroundGuidedActionsStylist();
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
        mEditAction = new GuidedAction.Builder()
                .editable(true)
                .editTitle("")
                .build();
        actions.add(mEditAction);
    }

    @Override
    public void onResume() {
        super.onResume();
        openInEditMode(mEditAction);
    }

    // Overriding this method removes the unpreferable enter transition animation of this fragment.
    @Override
    protected void onProvideFragmentTransitions() {
        setEnterTransition(null);
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        final CharSequence name = action.getEditTitle();
        if (TextUtils.isGraphic(name)) {
            DeviceManager.setDeviceName(getActivity(), name.toString());
            getActivity().setResult(Activity.RESULT_OK);

            // Set the flag for the appropriate exit animation for setup.
            if (getActivity() instanceof DeviceNameFlowStartActivity) {
                ((DeviceNameFlowStartActivity) getActivity()).setResultOk(true);
            }

            getActivity().finish();
            return super.onGuidedActionEditedAndProceed(action);
        } else {
            popBackStackToGuidedStepFragment(
                    DeviceNameSetCustomFragment.class, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return GuidedAction.ACTION_ID_CANCEL;
        }
    }

    @Override
    public void onGuidedActionEditCanceled(GuidedAction action) {
        // We need to "pop to" current fragment with INCLUSIVE flag instead of popping to previous
        // fragment because DeviceNameSetFragment was set to be root and not added on backstack.
        popBackStackToGuidedStepFragment(
                DeviceNameSetCustomFragment.class, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }
}
