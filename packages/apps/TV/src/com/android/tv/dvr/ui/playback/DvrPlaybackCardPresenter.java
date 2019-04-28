/*
 * Copyright (c) 2016 The Android Open Source Project
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

package com.android.tv.dvr.ui.playback;

import android.content.Context;

import com.android.tv.R;
import com.android.tv.dvr.ui.browse.RecordedProgramPresenter;
import com.android.tv.dvr.ui.browse.RecordingCardView;

/**
 * This class is used to generate Views and bind Objects for related recordings in DVR playback.
 */
class DvrPlaybackCardPresenter extends RecordedProgramPresenter {
    private final int mRelatedRecordingCardWidth;
    private final int mRelatedRecordingCardHeight;

    DvrPlaybackCardPresenter(Context context) {
        super(context);
        mRelatedRecordingCardWidth =
                context.getResources().getDimensionPixelSize(R.dimen.dvr_related_recordings_width);
        mRelatedRecordingCardHeight =
                context.getResources().getDimensionPixelSize(R.dimen.dvr_related_recordings_height);
    }

    @Override
    public DvrItemViewHolder onCreateDvrItemViewHolder() {
        return new RecordedProgramViewHolder(new RecordingCardView(
                getContext(), mRelatedRecordingCardWidth, mRelatedRecordingCardHeight, true), null);
    }
}
