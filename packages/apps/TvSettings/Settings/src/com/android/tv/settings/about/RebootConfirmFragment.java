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

package com.android.tv.settings.about;

import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.view.View;

import com.android.tv.settings.R;

import java.util.List;

@Keep
public class RebootConfirmFragment extends GuidedStepFragment {

    private static final String ARG_SAFE_MODE = "RebootConfirmFragment.safe_mode";

    public static RebootConfirmFragment newInstance(boolean safeMode) {

        Bundle args = new Bundle(1);
        args.putBoolean(ARG_SAFE_MODE, safeMode);

        RebootConfirmFragment fragment = new RebootConfirmFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setSelectedActionPosition(1);
    }

    @Override
    public @NonNull
    GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        if (getArguments().getBoolean(ARG_SAFE_MODE, false)) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.reboot_safemode_confirm),
                    getString(R.string.reboot_safemode_desc),
                    getString(R.string.about_preference),
                    getActivity().getDrawable(R.drawable.ic_warning_132dp)
            );
        } else {
            return new GuidanceStylist.Guidance(
                    getString(R.string.system_reboot_confirm),
                    null,
                    getString(R.string.about_preference),
                    getActivity().getDrawable(R.drawable.ic_warning_132dp)
            );
        }
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions,
            Bundle savedInstanceState) {
        final Context context = getActivity();
        if (getArguments().getBoolean(ARG_SAFE_MODE, false)) {
            actions.add(new GuidedAction.Builder(context)
                    .id(GuidedAction.ACTION_ID_OK)
                    .title(R.string.reboot_safemode_action)
                    .build());
        } else {
            actions.add(new GuidedAction.Builder(context)
                    .id(GuidedAction.ACTION_ID_OK)
                    .title(R.string.restart_button_label)
                    .build());
        }
        actions.add(new GuidedAction.Builder(context)
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == GuidedAction.ACTION_ID_OK) {
            PowerManager pm =
                    (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
            if (getArguments().getBoolean(ARG_SAFE_MODE, false)) {
                pm.rebootSafeMode();
            } else {
                pm.reboot(null);
            }
        } else {
            getFragmentManager().popBackStack();
        }
    }
}
