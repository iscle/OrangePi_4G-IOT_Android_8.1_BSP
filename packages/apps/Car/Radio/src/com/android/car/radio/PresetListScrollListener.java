/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.radio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import com.android.car.view.PagedListView;

/**
 * Listener on the preset list that will add elevation on the container holding the current
 * playing radio station. This elevation will give the illusion of the preset list scrolling
 * under that container.
 */
public class PresetListScrollListener extends RecyclerView.OnScrollListener {
    private static final int ANIMATION_DURATION_MS = 100;

    private final float mContainerElevation;
    private final View mCurrentRadioCardContainer;
    private final View mCurrentRadioCard;
    private final PagedListView mPresetList;
    private final ValueAnimator mRemoveElevationAnimator;

    public PresetListScrollListener(Context context, View container, View currentRadioCard,
            PagedListView presetList) {
        mPresetList = presetList;
        mCurrentRadioCardContainer = container.findViewById(R.id.preset_current_card_container);
        mCurrentRadioCard = currentRadioCard;
        mContainerElevation = context.getResources()
                .getDimension(R.dimen.car_preset_container_elevation);

        mRemoveElevationAnimator = ValueAnimator.ofFloat(mContainerElevation, 0.f);
        mRemoveElevationAnimator
                .setDuration(ANIMATION_DURATION_MS)
                .addUpdateListener(animation -> mCurrentRadioCardContainer.setElevation(
                        (float) animation.getAnimatedValue()));
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        if (mPresetList.getTranslationY() != 0.f) {
            return;
        }

        if (mPresetList.getLayoutManager().isAtTop()) {
            // Animate the removal of the elevation so that it's not jarring.
            mRemoveElevationAnimator.start();
        } else {
            // No animation needed when adding the elevation because the scroll masks the adding
            // of the elevation.
            mCurrentRadioCardContainer.setElevation(mContainerElevation);
            mCurrentRadioCard.setTranslationZ(mContainerElevation);
        }
    }
}
