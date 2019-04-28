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

package com.android.car.cluster.sample.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import com.android.car.cluster.sample.DebugUtil;
import com.android.car.cluster.sample.R;

/**
 * Sample card responsible for displaying weather content.
 */
public class WeatherCard extends CardView {

    private ImageView mFarCloudImage;
    private ImageView mNearCloudImage;

    public WeatherCard(Context context, PriorityChangedListener listener) {
        super(context, CardType.WEATHER, listener);
    }

    @Override
    protected void init() {
        inflate(R.layout.weather_card);

        mPriority = PRIORITY_WEATHER_CARD;
        mFarCloudImage = viewById(R.id.weather_far_cloud);
        mNearCloudImage = viewById(R.id.weather_near_cloud);

        mDetailsPanel = viewById(R.id.weather_panel);;
        Bitmap theSun = BitmapFactory.decodeResource(getResources(), R.drawable.sun_154);
        setLeftIcon(theSun);
        mLeftIconSwitcher.setVisibility(VISIBLE);
        ((ImageView)mRightIconSwitcher.getCurrentView()).setImageDrawable(
                getResources().getDrawable(R.drawable.cloud_154_shadow, null));
        mRightIconSwitcher.setVisibility(VISIBLE);
    }

    @Override
    public void onPlayRevealAnimation() {
        super.onPlayRevealAnimation();
        long duration = SHOW_ANIMATION_DURATION * DebugUtil.ANIMATION_FACTOR;

        mNearCloudImage.setTranslationX(400);
        mNearCloudImage.animate()
                .translationX(0)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator(2f));

        // Far cloud needs to travel less in screen coordinates so it will make it slower.
        mFarCloudImage.setTranslationX(100);
        mFarCloudImage.animate()
                .translationX(0)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator(2f));
    }

    @Override
    protected float getRightIconTargetX() {
        return super.getRightIconTargetX() - 22;
    }

    @Override
    protected float getDetailsPanelTargetX() {
        return super.getDetailsPanelTargetX() - 70;
    }
}
