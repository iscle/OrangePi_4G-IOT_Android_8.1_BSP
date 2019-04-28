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
 * limitations under the License
 */

package com.android.tv.dvr.ui;

import android.content.Context;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.TvApplication;
import com.android.tv.analytics.Tracker;

/** A {@link GuidedStepFragment} with {@link Tracker} for analytics. */
public abstract class TrackedGuidedStepFragment extends GuidedStepFragment {
    private Tracker mTracker;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mTracker = TvApplication.getSingletons(context).getAnalytics().getDefaultTracker();
    }

    @Override
    public void onDetach() {
        mTracker = null;
        super.onDetach();
    }

    @Override
    public final void onGuidedActionClicked(GuidedAction action) {
        super.onGuidedActionClicked(action);
        if (mTracker != null) {
            mTracker.sendMenuClicked(
                    getTrackerPrefix() + "-action-" + getTrackerLabelForGuidedAction(action));
        }
        onTrackedGuidedActionClicked(action);
    }

    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            return "cancel";
        } else if (actionId == GuidedAction.ACTION_ID_NEXT) {
            return "next";
        } else if (actionId == GuidedAction.ACTION_ID_CURRENT) {
            return "current";
        } else if (actionId == GuidedAction.ACTION_ID_OK) {
            return "ok";
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            return "cancel";
        } else if (actionId == GuidedAction.ACTION_ID_FINISH) {
            return "finish";
        } else if (actionId == GuidedAction.ACTION_ID_CONTINUE) {
            return "continue";
        } else if (actionId == GuidedAction.ACTION_ID_YES) {
            return "yes";
        } else if (actionId == GuidedAction.ACTION_ID_NO) {
            return "no";
        } else {
            return "unknown-" + actionId;
        }
    }

    /** Delegated from {@link #onGuidedActionClicked(GuidedAction)} */
    public abstract void onTrackedGuidedActionClicked(GuidedAction action);

    /** The prefix used for analytics tracking, Usually the class name. */
    public abstract String getTrackerPrefix();
}
