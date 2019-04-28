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

package com.android.tv.settings.accessibility;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.settings.R;

import java.util.List;

/**
 * Fragment for confirming [de]activation of accessibility service
 */
public class AccessibilityServiceConfirmationFragment extends GuidedStepFragment {
    private static final String ARG_LABEL = "label";
    private static final String ARG_COMPONENT = "component";
    private static final String ARG_ENABLING = "enabling";

    /**
     * Callback for dialog completion
     */
    public interface OnAccessibilityServiceConfirmedListener {
        /**
         * Called when enabling/disabling was confirmed by the user, not called otherwise.
         * @param componentName Service in question
         * @param enabling True for enabling
         */
        void onAccessibilityServiceConfirmed(ComponentName componentName, boolean enabling);
    }

    /**
     * Create a new instance of the fragment
     * @param cn Component of service
     * @param label Human readable label
     * @param enabling True for enabling
     * @return new fragment instance
     */
    public static AccessibilityServiceConfirmationFragment newInstance(ComponentName cn,
            CharSequence label, boolean enabling) {
        Bundle args = new Bundle(3);
        prepareArgs(args, cn, label, enabling);
        AccessibilityServiceConfirmationFragment fragment =
                new AccessibilityServiceConfirmationFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Put args in bundle
     * @param args Bundle to prepare
     * @param cn Component of service
     * @param label Human readable label
     * @param enabling True for enabling
     */
    public static void prepareArgs(@NonNull Bundle args, ComponentName cn, CharSequence label,
            boolean enabling) {
        args.putParcelable(ARG_COMPONENT, cn);
        args.putCharSequence(ARG_LABEL, label);
        args.putBoolean(ARG_ENABLING, enabling);
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        final CharSequence label = getArguments().getCharSequence(ARG_LABEL);
        if (getArguments().getBoolean(ARG_ENABLING)) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.system_accessibility_service_on_confirm_title,
                            label),
                    getString(R.string.system_accessibility_service_on_confirm_desc,
                            label),
                    null,
                    getActivity().getDrawable(R.drawable.ic_accessibility_new_132dp)
            );
        } else {
            return new GuidanceStylist.Guidance(
                    getString(R.string.system_accessibility_service_off_confirm_title,
                            label),
                    getString(R.string.system_accessibility_service_off_confirm_desc,
                            label),
                    null,
                    getActivity().getDrawable(R.drawable.ic_accessibility_new_132dp)
            );
        }
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions,
            Bundle savedInstanceState) {
        final Context context = getActivity();
        actions.add(new GuidedAction.Builder(context)
                .clickAction(GuidedAction.ACTION_ID_OK).build());
        actions.add(new GuidedAction.Builder(context)
                .clickAction(GuidedAction.ACTION_ID_CANCEL).build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == GuidedAction.ACTION_ID_OK) {
            final ComponentName component = getArguments().getParcelable(ARG_COMPONENT);
            final Fragment fragment = getTargetFragment();
            final boolean enabling = getArguments().getBoolean(ARG_ENABLING);
            if (fragment instanceof OnAccessibilityServiceConfirmedListener) {
                ((OnAccessibilityServiceConfirmedListener) fragment)
                        .onAccessibilityServiceConfirmed(component, enabling);
            } else {
                throw new IllegalStateException("Target fragment is not an "
                        + "OnAccessibilityServiceConfirmedListener");
            }
            getFragmentManager().popBackStack();
        } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
            getFragmentManager().popBackStack();
        } else {
            super.onGuidedActionClicked(action);
        }
    }
}
