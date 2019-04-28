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

package com.android.tv.dvr.ui.browse;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContentRating;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;
import android.support.v17.leanback.widget.VerticalGridView;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dialog.PinDialogFragment;
import com.android.tv.dialog.PinDialogFragment.OnPinCheckedListener;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.ToastUtils;
import com.android.tv.util.Utils;

import java.io.File;

abstract class DvrDetailsFragment extends DetailsFragment {
    private static final int LOAD_LOGO_IMAGE = 1;
    private static final int LOAD_BACKGROUND_IMAGE = 2;

    protected DetailsViewBackgroundHelper mBackgroundHelper;
    private ArrayObjectAdapter mRowsAdapter;
    private DetailsOverviewRow mDetailsOverview;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!onLoadRecordingDetails(getArguments())) {
            getActivity().finish();
            return;
        }
        mBackgroundHelper = new DetailsViewBackgroundHelper(getActivity());
        setupAdapter();
        onCreateInternal();
    }

    @Override
    public void onStart() {
        super.onStart();
        // TODO: remove the workaround of b/30401180.
        VerticalGridView container = (VerticalGridView) getActivity()
                .findViewById(R.id.container_list);
        // Need to manually modify offset. Please refer DetailsFragment.setVerticalGridViewLayout.
        container.setItemAlignmentOffset(0);
        container.setWindowAlignmentOffset(
                getResources().getDimensionPixelSize(R.dimen.lb_details_rows_align_top));
    }

    private void setupAdapter() {
        DetailsOverviewRowPresenter rowPresenter = new DetailsOverviewRowPresenter(
                new DetailsContentPresenter(getActivity()));
        rowPresenter.setBackgroundColor(getResources().getColor(R.color.common_tv_background,
                null));
        rowPresenter.setSharedElementEnterTransition(getActivity(),
                DvrDetailsActivity.SHARED_ELEMENT_NAME);
        rowPresenter.setOnActionClickedListener(onCreateOnActionClickedListener());
        mRowsAdapter = new ArrayObjectAdapter(onCreatePresenterSelector(rowPresenter));
        setAdapter(mRowsAdapter);
    }

    /**
     * Returns details views' rows adapter.
     */
    protected ArrayObjectAdapter getRowsAdapter() {
        return  mRowsAdapter;
    }

    /**
     * Sets details overview.
     */
    protected void setDetailsOverviewRow(DetailsContent detailsContent) {
        mDetailsOverview = new DetailsOverviewRow(detailsContent);
        mDetailsOverview.setActionsAdapter(onCreateActionsAdapter());
        mRowsAdapter.add(mDetailsOverview);
        onLoadLogoAndBackgroundImages(detailsContent);
    }

    /**
     * Creates and returns presenter selector will be used by rows adaptor.
     */
    protected PresenterSelector onCreatePresenterSelector(
            DetailsOverviewRowPresenter rowPresenter) {
        ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        presenterSelector.addClassPresenter(DetailsOverviewRow.class, rowPresenter);
        return presenterSelector;
    }

    /**
     * Does customized initialization of subclasses. Since {@link #onCreate(Bundle)} might finish
     * activity early when it cannot fetch valid recordings, subclasses' onCreate method should not
     * do anything after calling {@link #onCreate(Bundle)}. If there's something subclasses have to
     * do after the super class did onCreate, it should override this method and put the codes here.
     */
    protected void onCreateInternal() { }

    /**
     * Updates actions of details overview.
     */
    protected void updateActions() {
        mDetailsOverview.setActionsAdapter(onCreateActionsAdapter());
    }

    /**
     * Loads recording details according to the arguments the fragment got.
     *
     * @return false if cannot find valid recordings, else return true. If the return value
     *         is false, the detail activity and fragment will be ended.
     */
    abstract boolean onLoadRecordingDetails(Bundle args);

    /**
     * Creates actions users can interact with and their adaptor for this fragment.
     */
    abstract SparseArrayObjectAdapter onCreateActionsAdapter();

    /**
     * Creates actions listeners to implement the behavior of the fragment after users click some
     * action buttons.
     */
    abstract OnActionClickedListener onCreateOnActionClickedListener();

    /**
     * Loads logo and background images for detail fragments.
     */
    protected void onLoadLogoAndBackgroundImages(DetailsContent detailsContent) {
        Drawable logoDrawable = null;
        Drawable backgroundDrawable = null;
        if (TextUtils.isEmpty(detailsContent.getLogoImageUri())) {
            logoDrawable = getContext().getResources()
                    .getDrawable(R.drawable.dvr_default_poster, null);
            mDetailsOverview.setImageDrawable(logoDrawable);
        }
        if (TextUtils.isEmpty(detailsContent.getBackgroundImageUri())) {
            backgroundDrawable = getContext().getResources()
                    .getDrawable(R.drawable.dvr_default_poster, null);
            mBackgroundHelper.setBackground(backgroundDrawable);
        }
        if (logoDrawable != null && backgroundDrawable != null) {
            return;
        }
        if (logoDrawable == null && backgroundDrawable == null
                && detailsContent.getLogoImageUri().equals(
                detailsContent.getBackgroundImageUri())) {
            ImageLoader.loadBitmap(getContext(), detailsContent.getLogoImageUri(),
                    new MyImageLoaderCallback(this, LOAD_LOGO_IMAGE | LOAD_BACKGROUND_IMAGE,
                            getContext()));
            return;
        }
        if (logoDrawable == null) {
            int imageWidth = getResources().getDimensionPixelSize(R.dimen.dvr_details_poster_width);
            int imageHeight = getResources()
                    .getDimensionPixelSize(R.dimen.dvr_details_poster_height);
            ImageLoader.loadBitmap(getContext(), detailsContent.getLogoImageUri(),
                    imageWidth, imageHeight,
                    new MyImageLoaderCallback(this, LOAD_LOGO_IMAGE, getContext()));
        }
        if (backgroundDrawable == null) {
            ImageLoader.loadBitmap(getContext(), detailsContent.getBackgroundImageUri(),
                    new MyImageLoaderCallback(this, LOAD_BACKGROUND_IMAGE, getContext()));
        }
    }

    protected void startPlayback(RecordedProgram recordedProgram, long seekTimeMs) {
        if (Utils.isInBundledPackageSet(recordedProgram.getPackageName()) &&
                !isDataUriAccessible(recordedProgram.getDataUri())) {
            // Since cleaning RecordedProgram from forgotten storage will take some time,
            // ignore playback until cleaning is finished.
            ToastUtils.show(getContext(),
                    getContext().getResources().getString(R.string.dvr_toast_recording_deleted),
                    Toast.LENGTH_SHORT);
            return;
        }
        long programId = recordedProgram.getId();
        ParentalControlSettings parental = TvApplication.getSingletons(getActivity())
                .getTvInputManagerHelper().getParentalControlSettings();
        if (!parental.isParentalControlsEnabled()) {
            DvrUiHelper.startPlaybackActivity(getContext(), programId, seekTimeMs, false);
            return;
        }
        ChannelDataManager channelDataManager =
                TvApplication.getSingletons(getActivity()).getChannelDataManager();
        Channel channel = channelDataManager.getChannel(recordedProgram.getChannelId());
        if (channel != null && channel.isLocked()) {
            checkPinToPlay(recordedProgram, seekTimeMs);
            return;
        }
        TvContentRating[] ratings = recordedProgram.getContentRatings();
        TvContentRating blockRatings = parental.getBlockedRating(ratings);
        if (blockRatings != null) {
            checkPinToPlay(recordedProgram, seekTimeMs);
        } else {
            DvrUiHelper.startPlaybackActivity(getContext(), programId, seekTimeMs, false);
        }
    }

    private boolean isDataUriAccessible(Uri dataUri) {
        if (dataUri == null || dataUri.getPath() == null) {
            return false;
        }
        try {
            File recordedProgramPath = new File(dataUri.getPath());
            if (recordedProgramPath.exists()) {
                return true;
            }
        } catch (SecurityException e) {
        }
        return false;
    }

    private void checkPinToPlay(RecordedProgram recordedProgram, long seekTimeMs) {
        SoftPreconditions.checkState(getActivity() instanceof DvrDetailsActivity);
        if (getActivity() instanceof DvrDetailsActivity) {
            ((DvrDetailsActivity) getActivity()).setOnPinCheckListener(new OnPinCheckedListener() {
                @Override
                public void onPinChecked(boolean checked, int type, String rating) {
                    ((DvrDetailsActivity) getActivity()).setOnPinCheckListener(null);
                    if (checked && type == PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_PROGRAM) {
                        DvrUiHelper.startPlaybackActivity(getContext(), recordedProgram.getId(),
                                seekTimeMs, true);
                    }
                }
            });
            PinDialogFragment.create(PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_PROGRAM)
                    .show(getActivity().getFragmentManager(), PinDialogFragment.DIALOG_TAG);
        }
    }

    private static class MyImageLoaderCallback extends
            ImageLoader.ImageLoaderCallback<DvrDetailsFragment> {
        private final Context mContext;
        private final int mLoadType;

        public MyImageLoaderCallback(DvrDetailsFragment fragment,
                int loadType, Context context) {
            super(fragment);
            mLoadType = loadType;
            mContext = context;
        }

        @Override
        public void onBitmapLoaded(DvrDetailsFragment fragment,
                @Nullable Bitmap bitmap) {
            Drawable drawable;
            int loadType = mLoadType;
            if (bitmap == null) {
                Resources res = mContext.getResources();
                drawable = res.getDrawable(R.drawable.dvr_default_poster, null);
                if ((loadType & LOAD_BACKGROUND_IMAGE) != 0 && !fragment.isDetached()) {
                    loadType &= ~LOAD_BACKGROUND_IMAGE;
                    fragment.mBackgroundHelper.setBackgroundColor(
                            res.getColor(R.color.dvr_detail_default_background));
                    fragment.mBackgroundHelper.setScrim(
                            res.getColor(R.color.dvr_detail_default_background_scrim));
                }
            } else {
                drawable = new BitmapDrawable(mContext.getResources(), bitmap);
            }
            if (!fragment.isDetached()) {
                if ((loadType & LOAD_LOGO_IMAGE) != 0) {
                    fragment.mDetailsOverview.setImageDrawable(drawable);
                }
                if ((loadType & LOAD_BACKGROUND_IMAGE) != 0) {
                    fragment.mBackgroundHelper.setBackground(drawable);
                }
            }
        }
    }
}
