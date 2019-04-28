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

import android.app.Activity;
import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.v17.leanback.widget.Presenter;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.dvr.ui.DvrUiHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * An abstract class to present DVR items in {@link RecordingCardView}, which is mainly used in
 * {@link DvrBrowseFragment}. DVR items might include:
 * {@link com.android.tv.dvr.data.ScheduledRecording},
 * {@link com.android.tv.dvr.data.RecordedProgram}, and
 * {@link com.android.tv.dvr.data.SeriesRecording}.
 */
public abstract class DvrItemPresenter<T> extends Presenter {
    protected final Context mContext;
    private final Set<DvrItemViewHolder> mBoundViewHolders = new HashSet<>();
    private final OnClickListener mOnClickListener = onCreateOnClickListener();

    protected class DvrItemViewHolder extends ViewHolder {
        DvrItemViewHolder(RecordingCardView view) {
            super(view);
        }

        protected RecordingCardView getView() {
            return (RecordingCardView) view;
        }

        protected void onBound(T item) { }

        protected void onUnbound() { }
    }

    DvrItemPresenter(Context context) {
        mContext = context;
    }

    @Override
    public final ViewHolder onCreateViewHolder(ViewGroup parent) {
        return onCreateDvrItemViewHolder();
    }

    @Override
    public final void onBindViewHolder(ViewHolder baseHolder, Object item) {
        DvrItemViewHolder viewHolder;
        T dvrItem;
        try {
            viewHolder = (DvrItemViewHolder) baseHolder;
            Class<T> itemType = (Class<T>) item.getClass();
            dvrItem = itemType.cast(item);
        } catch (ClassCastException e) {
            SoftPreconditions.checkState(false);
            return;
        }
        viewHolder.view.setTag(item);
        viewHolder.view.setOnClickListener(mOnClickListener);
        onBindDvrItemViewHolder(viewHolder, dvrItem);
        viewHolder.onBound(dvrItem);
        mBoundViewHolders.add(viewHolder);
    }

    @Override
    @CallSuper
    public void onUnbindViewHolder(ViewHolder baseHolder) {
        DvrItemViewHolder viewHolder = (DvrItemViewHolder) baseHolder;
        mBoundViewHolders.remove(viewHolder);
        viewHolder.onUnbound();
        viewHolder.view.setTag(null);
        viewHolder.view.setOnClickListener(null);
    }

    /**
     * Unbinds all bound view holders.
     */
    public void unbindAllViewHolders() {
        // When browse fragments are destroyed, RecyclerView would not call presenters'
        // onUnbindViewHolder(). We should handle it by ourselves to prevent resources leaks.
        for (ViewHolder viewHolder : new HashSet<>(mBoundViewHolders)) {
            onUnbindViewHolder(viewHolder);
        }
    }

    /**
     * This method will be called when a {@link DvrItemViewHolder} is needed to be created.
     */
    abstract protected DvrItemViewHolder onCreateDvrItemViewHolder();

    /**
     * This method will be called when a {@link DvrItemViewHolder} is bound to a DVR item.
     */
    abstract protected void onBindDvrItemViewHolder(DvrItemViewHolder viewHolder, T item);

    /**
     * Returns context.
     */
    protected Context getContext() {
        return mContext;
    }

    /**
     * Creates {@link OnClickListener} for DVR library's card views.
     */
    protected OnClickListener onCreateOnClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view instanceof RecordingCardView) {
                    RecordingCardView v = (RecordingCardView) view;
                    DvrUiHelper.startDetailsActivity((Activity) v.getContext(),
                            v.getTag(), v.getImageView(), false);
                }
            }
        };
    }
}