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

package com.android.tv.settings.device.apps;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v7.preference.Preference;

import com.android.settingslib.applications.ApplicationsState;

import java.util.List;

public abstract class AppActionPreference extends Preference {
    protected ApplicationsState.AppEntry mEntry;

    public AppActionPreference(Context context, ApplicationsState.AppEntry entry) {
        super(context);
        mEntry = entry;
    }

    /**
     * Set entry and refresh pref.
     * @param entry entry
     */
    public void setEntry(@NonNull ApplicationsState.AppEntry entry) {
        mEntry = entry;
        refresh();
    }

    /**
     * Refresh pref from AppEntry
     */
    public abstract void refresh();

    public static abstract class ConfirmationFragment extends GuidedStepFragment {
        private static final int ID_OK = 0;
        private static final int ID_CANCEL = 1;

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder()
                    .title(getString(android.R.string.ok))
                    .id(ID_OK)
                    .build());
            actions.add(new GuidedAction.Builder()
                    .title(getString(android.R.string.cancel))
                    .id(ID_CANCEL)
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            switch ((int) action.getId()) {
                case ID_OK:
                    onOk();
                    break;
                case ID_CANCEL:
                    break;
            }
            getFragmentManager().popBackStack();
        }

        public abstract void onOk();
    }

}
