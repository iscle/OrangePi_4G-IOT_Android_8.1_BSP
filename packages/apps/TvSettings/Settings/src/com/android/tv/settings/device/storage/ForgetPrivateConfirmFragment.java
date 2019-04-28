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

package com.android.tv.settings.device.storage;

import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeRecord;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.settings.R;

import java.util.List;

public class ForgetPrivateConfirmFragment extends GuidedStepFragment {

    private static final int ACTION_ID_FORGET = 1;

    public static void prepareArgs(Bundle b, String fsUuid) {
        b.putString(VolumeRecord.EXTRA_FS_UUID, fsUuid);
    }

    @Override
    public @NonNull GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.storage_wizard_forget_confirm_title),
                getString(R.string.storage_wizard_forget_confirm_description), "",
                getActivity().getDrawable(R.drawable.ic_warning_132dp));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getContext())
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
        actions.add(new GuidedAction.Builder(getContext())
                .id(ACTION_ID_FORGET)
                .title(getString(R.string.storage_wizard_forget_action))
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        final long id = action.getId();

        if (id == GuidedAction.ACTION_ID_CANCEL) {
            getFragmentManager().popBackStack();
        } else if (id == ACTION_ID_FORGET) {
            getContext().getSystemService(StorageManager.class)
                    .forgetVolume(getArguments().getString(VolumeRecord.EXTRA_FS_UUID));
            getFragmentManager().popBackStack();
        }
    }
}
