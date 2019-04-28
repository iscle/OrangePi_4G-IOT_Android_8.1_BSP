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

package com.android.tv.common.ui.setup;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

/**
 * Helper class for the execution in the fragment.
 */
public class SetupActionHelper {
    private static final String TAG = "SetupActionHelper";

    /**
     * Executes the action.
     */
    public static boolean onActionClick(Fragment fragment, String category, int actionId) {
        return onActionClick(fragment, category, actionId, null);
    }

    /**
     * Executes the action.
     */
    public static boolean onActionClick(Fragment fragment, String category, int actionId,
            Bundle params) {
        if (fragment.getActivity() instanceof OnActionClickListener) {
            return ((OnActionClickListener) fragment.getActivity()).onActionClick(category,
                    actionId, params);
        }
        Log.e(TAG, "Activity can't handle the action: {category=" + category + ", actionId="
                + actionId + ", params=" + params + "}");
        return false;
    }

    /**
     * Creates an {@link OnClickListener} to handle the action.
     */
    public static OnClickListener createOnClickListenerForAction(Fragment fragment, String category,
            int actionId, Bundle params) {
        return new OnActionClickListenerForAction(fragment, category, actionId, params);
    }

    /**
     * The {@link OnClickListener} for the view.
     * <p>
     * Note that this class should be used only for the views in the {@code mFragment} to avoid the
     * leak of mFragment.
     */
    private static class OnActionClickListenerForAction implements OnClickListener {
        private final Fragment mFragment;
        private final String mCategory;
        private final int mActionId;
        private final Bundle mParams;

        OnActionClickListenerForAction(Fragment fragment, String category, int actionId,
                Bundle params) {
            mFragment = fragment;
            mCategory = category;
            mActionId = actionId;
            mParams = params;
        }

        @Override
        public void onClick(View v) {
            SetupActionHelper.onActionClick(mFragment, mCategory, mActionId, mParams);
        }
    }

    private SetupActionHelper() { }
}
