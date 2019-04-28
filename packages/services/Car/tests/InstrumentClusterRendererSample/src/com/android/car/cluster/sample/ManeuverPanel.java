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

package com.android.car.cluster.sample;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.car.cluster.sample.cards.NavCard;

/**
 * Contains text information about next maneuver. This panel is used in {@link NavCard}.
 */
public class ManeuverPanel extends FrameLayout {

    private TextView mDistance;
    private TextView mDistanceUnits;
    private TextView mStreet;

    public ManeuverPanel(Context context) {
        this(context, null);
    }

    public ManeuverPanel(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflate(context, R.layout.nav_card_maneuver_description, this);

        mDistance = (TextView) findViewById(R.id.nav_distance);
        mDistanceUnits = (TextView) findViewById(R.id.nav_distance_units);
        mStreet = (TextView) findViewById(R.id.nav_street);
    }

    public void setDistanceToNextManeuver(String distance, String units) {
        mDistance.setText(distance);
        mDistanceUnits.setText(units);
    }

    public void setStreet(CharSequence street) {
        mStreet.setText(street);
    }
}
