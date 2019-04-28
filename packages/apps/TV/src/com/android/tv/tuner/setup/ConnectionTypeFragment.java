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

package com.android.tv.tuner.setup;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.common.BuildConfig;
import com.android.tv.common.ui.setup.SetupGuidedStepFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.tuner.R;

import java.util.List;
import java.util.TimeZone;

/**
 * A fragment for connection type selection.
 */
public class ConnectionTypeFragment extends SetupMultiPaneFragment {
    public static final String ACTION_CATEGORY =
            "com.android.tv.tuner.setup.ConnectionTypeFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ((TunerSetupActivity) getActivity()).generateTunerHal();
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        ((TunerSetupActivity) getActivity()).generateTunerHal();
        super.onResume();
    }

    @Override
    public void onDestroy() {
        ((TunerSetupActivity) getActivity()).clearTunerHal();
        super.onDestroy();
    }

    @Override
    protected SetupGuidedStepFragment onCreateContentFragment() {
        return new ContentFragment();
    }

    @Override
    protected String getActionCategory() {
        return ACTION_CATEGORY;
    }

    @Override
    protected boolean needsDoneButton() {
        return false;
    }

    public static class ContentFragment extends SetupGuidedStepFragment {

        @NonNull
        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new Guidance(getString(R.string.ut_connection_title),
                    getString(R.string.ut_connection_description),
                    getString(R.string.ut_setup_breadcrumb), null);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            String[] choices = getResources().getStringArray(R.array.ut_connection_choices);
            int length = choices.length - 1;
            int startOffset = 0;
            for (int i = 0; i < length; ++i) {
                actions.add(new GuidedAction.Builder(getActivity())
                        .id(startOffset + i)
                        .title(choices[i])
                        .build());
            }
        }

        @Override
        protected String getActionCategory() {
            return ACTION_CATEGORY;
        }
    }
}
