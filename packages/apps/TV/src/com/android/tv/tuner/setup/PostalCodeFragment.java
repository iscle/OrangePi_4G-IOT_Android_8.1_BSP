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

package com.android.tv.tuner.setup;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.text.InputFilter;
import android.text.InputFilter.AllCaps;
import android.view.View;
import android.widget.TextView;
import com.android.tv.R;
import com.android.tv.common.ui.setup.SetupGuidedStepFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.tuner.util.PostalCodeUtils;
import com.android.tv.util.LocationUtils;
import java.util.List;

/**
 * A fragment for initial screen.
 */
public class PostalCodeFragment extends SetupMultiPaneFragment {
    public static final String ACTION_CATEGORY =
            "com.android.tv.tuner.setup.PostalCodeFragment";
    private static final int VIEW_TYPE_EDITABLE = 1;

    @Override
    protected SetupGuidedStepFragment onCreateContentFragment() {
        ContentFragment fragment = new ContentFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(SetupGuidedStepFragment.KEY_THREE_PANE, true);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    protected String getActionCategory() {
        return ACTION_CATEGORY;
    }

    @Override
    protected boolean needsDoneButton() {
        return true;
    }

    @Override
    protected boolean needsSkipButton() {
        return true;
    }

    @Override
    protected void setOnClickAction(View view, final String category, final int actionId) {
        if (actionId == ACTION_DONE) {
            view.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            CharSequence postalCode =
                                    ((ContentFragment) getContentFragment()).mEditAction.getTitle();
                            String region = LocationUtils.getCurrentCountry(getContext());
                            if (postalCode != null && PostalCodeUtils.matches(postalCode, region)) {
                                PostalCodeUtils.setLastPostalCode(
                                        getContext(), postalCode.toString());
                                onActionClick(category, actionId);
                            } else {
                                ContentFragment contentFragment =
                                        (ContentFragment) getContentFragment();
                                contentFragment.mEditAction.setDescription(
                                        getString(R.string.postal_code_invalid_warning));
                                contentFragment.notifyActionChanged(0);
                                contentFragment.mEditedActionView.performClick();
                            }
                        }
                    });
        } else if (actionId == ACTION_SKIP) {
            super.setOnClickAction(view, category, ACTION_SKIP);
        }
    }

    public static class ContentFragment extends SetupGuidedStepFragment {
        private GuidedAction mEditAction;
        private View mEditedActionView;
        private View mDoneActionView;
        private boolean mProceed;

        @Override
        public void onGuidedActionFocused(GuidedAction action) {
            if (action.equals(mEditAction)) {
                if (mProceed) {
                    // "NEXT" in IME was just clicked, moves focus to Done button.
                    if (mDoneActionView == null) {
                        mDoneActionView = getActivity().findViewById(R.id.button_done);
                    }
                    mDoneActionView.requestFocus();
                    mProceed = false;
                } else {
                    // Directly opens IME to input postal/zip code.
                    if (mEditedActionView == null) {
                        int maxLength = PostalCodeUtils.getRegionMaxLength(getContext());
                        mEditedActionView = getView().findViewById(R.id.guidedactions_editable);
                        ((TextView) mEditedActionView.findViewById(R.id.guidedactions_item_title))
                                .setFilters(
                                        new InputFilter[] {
                                            new InputFilter.LengthFilter(maxLength), new AllCaps()
                                        });
                    }
                    mEditedActionView.performClick();
                }
            }
        }

        @Override
        public long onGuidedActionEditedAndProceed(GuidedAction action) {
            mProceed = true;
            return 0;
        }

        @NonNull
        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.postal_code_guidance_title);
            String description = getString(R.string.postal_code_guidance_description);
            String breadcrumb = getString(R.string.ut_setup_breadcrumb);
            return new Guidance(title, description, breadcrumb, null);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            String description = getString(R.string.postal_code_action_description);
            mEditAction = new GuidedAction.Builder(getActivity()).id(0).editable(true)
                    .description(description).build();
            actions.add(mEditAction);
        }

        @Override
        protected String getActionCategory() {
            return ACTION_CATEGORY;
        }

        @Override
        public GuidedActionsStylist onCreateActionsStylist() {
            return new GuidedActionsStylist() {
                @Override
                public int getItemViewType(GuidedAction action) {
                    if (action.isEditable()) {
                        return VIEW_TYPE_EDITABLE;
                    }
                    return super.getItemViewType(action);
                }

                @Override
                public int onProvideItemLayoutId(int viewType) {
                    if (viewType == VIEW_TYPE_EDITABLE) {
                        return R.layout.guided_action_editable;
                    }
                    return super.onProvideItemLayoutId(viewType);
                }
            };
        }
    }
}