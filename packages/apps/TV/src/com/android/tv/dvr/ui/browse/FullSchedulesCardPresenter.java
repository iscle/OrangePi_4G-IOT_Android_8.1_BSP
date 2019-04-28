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

package com.android.tv.dvr.ui.browse;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.util.Utils;

import java.util.Collections;
import java.util.List;

/**
 * Presents a {@link ScheduledRecording} in the {@link DvrBrowseFragment}.
 */
class FullSchedulesCardPresenter extends DvrItemPresenter<Object> {
    private final Drawable mIconDrawable;
    private final String mCardTitleText;

    FullSchedulesCardPresenter(Context context) {
        super(context);
        mIconDrawable = mContext.getDrawable(R.drawable.dvr_full_schedule);
        mCardTitleText = mContext.getString(R.string.dvr_full_schedule_card_view_title);
    }

    @Override
    public DvrItemViewHolder onCreateDvrItemViewHolder() {
        return new DvrItemViewHolder(new RecordingCardView(mContext));
    }

    @Override
    public void onBindDvrItemViewHolder(DvrItemViewHolder vh, Object o) {
        final RecordingCardView cardView = (RecordingCardView) vh.view;

        cardView.setTitle(mCardTitleText);
        cardView.setImage(mIconDrawable);
        List<ScheduledRecording> scheduledRecordings = TvApplication.getSingletons(mContext)
                .getDvrDataManager().getAvailableScheduledRecordings();
        int fullDays = 0;
        if (!scheduledRecordings.isEmpty()) {
            fullDays = Utils.computeDateDifference(System.currentTimeMillis(),
                    Collections.max(scheduledRecordings, ScheduledRecording.START_TIME_COMPARATOR)
                    .getStartTimeMs()) + 1;
        }
        cardView.setContent(mContext.getResources().getQuantityString(
                R.plurals.dvr_full_schedule_card_view_content, fullDays, fullDays), null);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder vh) {
        ((RecordingCardView) vh.view).reset();
        super.onUnbindViewHolder(vh);
    }

    @Override
    protected View.OnClickListener onCreateOnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DvrUiHelper.startSchedulesActivity(mContext, null);
            }
        };
    }
}