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
import android.support.annotation.Nullable;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import com.android.tv.common.ui.setup.SetupGuidedStepFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.tuner.R;
import com.android.tv.tuner.TunerHal;
import com.android.tv.tuner.TunerPreferences;
import java.util.List;

/**
 * A fragment for initial screen.
 */
public class WelcomeFragment extends SetupMultiPaneFragment {
    public static final String ACTION_CATEGORY =
            "com.android.tv.tuner.setup.WelcomeFragment";

    @Override
    protected SetupGuidedStepFragment onCreateContentFragment() {
        ContentFragment fragment = new ContentFragment();
        fragment.setArguments(getArguments());
        return fragment;
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
        private int mChannelCountOnPreference;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            mChannelCountOnPreference =
                    TunerPreferences.getScannedChannelCount(getActivity().getApplicationContext());
            super.onCreate(savedInstanceState);
        }

        @NonNull
        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title;
            String description;
            int tunerType = getArguments().getInt(TunerSetupActivity.KEY_TUNER_TYPE,
                    TunerHal.TUNER_TYPE_BUILT_IN);
            if (mChannelCountOnPreference == 0) {
                switch (tunerType) {
                    case TunerHal.TUNER_TYPE_USB:
                        title = getString(R.string.ut_setup_new_title);
                        description = getString(R.string.ut_setup_new_description);
                        break;
                    case TunerHal.TUNER_TYPE_NETWORK:
                        title = getString(R.string.nt_setup_new_title);
                        description = getString(R.string.nt_setup_new_description);
                        break;
                    default:
                        title = getString(R.string.bt_setup_new_title);
                        description = getString(R.string.bt_setup_new_description);
                }
            } else {
                title = getString(R.string.bt_setup_again_title);
                switch (tunerType) {
                    case TunerHal.TUNER_TYPE_USB:
                        description = getString(R.string.ut_setup_again_description);
                        break;
                    case TunerHal.TUNER_TYPE_NETWORK:
                        description = getString(R.string.nt_setup_again_description);
                        break;
                    default:
                        description = getString(R.string.bt_setup_again_description);
                }
            }
            return new Guidance(title, description, null, null);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            String[] choices = getResources().getStringArray(mChannelCountOnPreference == 0
                    ? R.array.ut_setup_new_choices : R.array.ut_setup_again_choices);
            for (int i = 0; i < choices.length - 1; ++i) {
                actions.add(new GuidedAction.Builder(getActivity()).id(i).title(choices[i])
                        .build());
            }
            actions.add(new GuidedAction.Builder(getActivity()).id(ACTION_DONE)
                    .title(choices[choices.length - 1]).build());
        }

        @Override
        protected String getActionCategory() {
            return ACTION_CATEGORY;
        }
    }
}
