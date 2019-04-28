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
import android.util.AttributeSet;
import android.view.View;
import android.widget.ViewSwitcher;
import android.widget.ViewSwitcher.ViewFactory;

import com.android.car.cluster.sample.ManeuverPanel;
import com.android.car.cluster.sample.R;

/**
 * Card responsible for showing navigation.
 */
public class NavCard extends CardView {

    private ViewSwitcher mDirectionsSwitcher;

    public NavCard(Context context, PriorityChangedListener listener) {
        this(context, null, CardType.NAV, listener);
    }

    public NavCard(Context context, AttributeSet attrs, @CardType int cardType,
            PriorityChangedListener listener) {
        super(context, attrs, cardType, listener);

    }

    @Override
    protected void init() {
        inflate(R.layout.nav_card);

        mPriority = PRIORITY_NAVIGATION_ACTIVE;

        mDirectionsSwitcher = viewById(R.id.nav_directions_switcher);
        mDetailsPanel = mDirectionsSwitcher;

        mDirectionsSwitcher.setFactory(new ViewFactory() {
            @Override
            public View makeView() {
                return new ManeuverPanel(getContext());
            }
        });

        setRightIcon(null);  // To hide it.
    }

    public void setDistanceToNextManeuver(String distance, String units) {
        ManeuverPanel maneuver = (ManeuverPanel) mDirectionsSwitcher.getCurrentView();
        maneuver.setDistanceToNextManeuver(distance, units);
    }

    public void setStreet(CharSequence street) {
        ManeuverPanel maneuver = (ManeuverPanel) mDirectionsSwitcher.getNextView();
        maneuver.setStreet(street);
        mDirectionsSwitcher.showNext();
    }

    public void setManeuverImage(Bitmap maneuverImage) {
        setLeftIcon(maneuverImage, true /* animated */);
    }

    @Override
    public void onPlayRevealAnimation() {
        super.onPlayRevealAnimation();
    }
}
