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
 * limitations under the License.
 */

package com.android.tv.dvr.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.dvr.DvrStorageStatusManager;
import com.android.tv.dialog.HalfSizedDialogFragment;
import com.android.tv.dvr.ui.DvrConflictFragment.DvrChannelWatchConflictFragment;
import com.android.tv.dvr.ui.DvrConflictFragment.DvrProgramConflictFragment;
import com.android.tv.guide.ProgramGuide;

import java.util.List;

public class DvrHalfSizedDialogFragment extends HalfSizedDialogFragment {
    /**
     * Key for input ID.
     * Type: String.
     */
    public static final String KEY_INPUT_ID = "DvrHalfSizedDialogFragment.input_id";
    /**
     * Key for the program.
     * Type: {@link com.android.tv.data.Program}.
     */
    public static final String KEY_PROGRAM = "DvrHalfSizedDialogFragment.program";
    /**
     * Key for the channel ID.
     * Type: long.
     */
    public static final String KEY_CHANNEL_ID = "DvrHalfSizedDialogFragment.channel_id";
    /**
     * Key for the recording start time in millisecond.
     * Type: long.
     */
    public static final String KEY_START_TIME_MS = "DvrHalfSizedDialogFragment.start_time_ms";
    /**
     * Key for the recording end time in millisecond.
     * Type: long.
     */
    public static final String KEY_END_TIME_MS = "DvrHalfSizedDialogFragment.end_time_ms";

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ProgramGuide programGuide =
                    ((MainActivity) activity).getOverlayManager().getProgramGuide();
            if (programGuide != null && programGuide.isActive()) {
                programGuide.cancelHide();
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ProgramGuide programGuide =
                    ((MainActivity) activity).getOverlayManager().getProgramGuide();
            if (programGuide != null && programGuide.isActive()) {
                programGuide.scheduleHide();
            }
        }
    }

    public abstract static class DvrGuidedStepDialogFragment extends DvrHalfSizedDialogFragment {
        private DvrGuidedStepFragment mFragment;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            mFragment = onCreateGuidedStepFragment();
            mFragment.setArguments(getArguments());
            mFragment.setOnActionClickListener(getOnActionClickListener());
            GuidedStepFragment.add(getChildFragmentManager(),
                    mFragment, R.id.halfsized_dialog_host);
            return view;
        }

        @Override
        public void setOnActionClickListener(OnActionClickListener listener) {
            super.setOnActionClickListener(listener);
            if (mFragment != null) {
                mFragment.setOnActionClickListener(listener);
            }
        }

        protected abstract DvrGuidedStepFragment onCreateGuidedStepFragment();
    }

    /** A dialog fragment for {@link DvrScheduleFragment}. */
    public static class DvrScheduleDialogFragment extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrScheduleFragment();
        }
    }

    /** A dialog fragment for {@link DvrProgramConflictFragment}. */
    public static class DvrProgramConflictDialogFragment extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrProgramConflictFragment();
        }
    }

    /** A dialog fragment for {@link DvrChannelWatchConflictFragment}. */
    public static class DvrChannelWatchConflictDialogFragment extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrChannelWatchConflictFragment();
        }
    }

    /** A dialog fragment for {@link DvrChannelRecordDurationOptionFragment}. */
    public static class DvrChannelRecordDurationOptionDialogFragment
            extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrChannelRecordDurationOptionFragment();
        }
    }

    /** A dialog fragment for {@link DvrInsufficientSpaceErrorFragment}. */
    public static class DvrInsufficientSpaceErrorDialogFragment
            extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrInsufficientSpaceErrorFragment();
        }
    }

    /** A dialog fragment for {@link DvrMissingStorageErrorFragment}. */
    public static class DvrMissingStorageErrorDialogFragment
            extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrMissingStorageErrorFragment();
        }
    }

    /**
     * A dialog fragment to show error message when there is no enough free space to record.
     */
    public static class DvrNoFreeSpaceErrorDialogFragment
            extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrGuidedStepFragment.DvrNoFreeSpaceErrorFragment();
        }
    }

    /**
     * A dialog fragment to show error message when the current storage is too small to
     * support DVR
     */
    public static class DvrSmallSizedStorageErrorDialogFragment
            extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrGuidedStepFragment.DvrSmallSizedStorageErrorFragment();
        }
    }

    /** A dialog fragment for {@link DvrStopRecordingFragment}. */
    public static class DvrStopRecordingDialogFragment extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrStopRecordingFragment();
        }
    }

    /** A dialog fragment for {@link DvrAlreadyScheduledFragment}. */
    public static class DvrAlreadyScheduledDialogFragment extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrAlreadyScheduledFragment();
        }
    }

    /** A dialog fragment for {@link DvrAlreadyRecordedFragment}. */
    public static class DvrAlreadyRecordedDialogFragment extends DvrGuidedStepDialogFragment {
        @Override
        protected DvrGuidedStepFragment onCreateGuidedStepFragment() {
            return new DvrAlreadyRecordedFragment();
        }
    }
}