/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.dialog;

import android.app.Activity;
import android.app.DialogFragment;

/**
 * Provides the safe dismiss feature regardless of the DialogFragment's life cycle.
 */
public class SafeDismissDialogFragment extends DialogFragment {
    private boolean mAttached = false;
    private boolean mDismissPending = false;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAttached = true;
        if (mDismissPending) {
            mDismissPending = false;
            dismiss();
            return;
        }
    }

    @Override
    public void onDetach() {
        mAttached = false;
        super.onDetach();
    }

    /**
     * Dismiss safely regardless of the DialogFragment's life cycle.
     */
    @Override
    public void dismiss() {
        if (!mAttached) {
            // dismiss() before getFragmentManager() is set cause NPE in dismissInternal().
            // FragmentMananager is set when a fragment is used in a trasaction,
            // so wait here until we can dismiss safely.
            mDismissPending = true;
        } else {
            super.dismiss();
        }
    }
}
