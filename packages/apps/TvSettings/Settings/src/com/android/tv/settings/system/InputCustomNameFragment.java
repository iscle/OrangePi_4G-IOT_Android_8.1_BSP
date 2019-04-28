/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.settings.system;

import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.settings.R;

import java.util.List;

@Keep
public class InputCustomNameFragment extends GuidedStepFragment {

    private static final String ARG_CURRENT_NAME = "current_name";
    private static final String ARG_DEFAULT_NAME = "default_name";

    private CharSequence mName;

    public static void prepareArgs(@NonNull Bundle args, CharSequence defaultName,
            CharSequence currentName) {
        args.putCharSequence(ARG_DEFAULT_NAME, defaultName);
        args.putCharSequence(ARG_CURRENT_NAME, currentName);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mName = getArguments().getCharSequence(ARG_CURRENT_NAME);

        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.inputs_custom_name),
                getString(R.string.inputs_custom_name_description_fmt,
                        getArguments().getCharSequence(ARG_DEFAULT_NAME)),
                null,
                getContext().getDrawable(R.drawable.ic_input_132dp)
        );
    }

    @Override
    public void onCreateButtonActions(@NonNull List<GuidedAction> actions,
            Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getContext())
                .clickAction(GuidedAction.ACTION_ID_OK)
                .build());
        actions.add(new GuidedAction.Builder(getContext())
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions,
            Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getContext())
                .title(mName)
                .editable(true)
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == GuidedAction.ACTION_ID_OK) {
            ((Callback) getTargetFragment()).onSetCustomName(mName);
            getFragmentManager().popBackStack();
        } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
            getFragmentManager().popBackStack();
        }
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        mName = action.getTitle();
        return GuidedAction.ACTION_ID_OK;
    }

    public interface Callback {
        void onSetCustomName(CharSequence name);
    }
}
