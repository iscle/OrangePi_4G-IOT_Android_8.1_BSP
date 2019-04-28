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

package com.android.tv.data;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A class that represents a lineup.
 */
public class Lineup {
    /**
     * The ID of this lineup.
     */
    public final String id;

    /**
     * The type associated with this lineup.
     */
    public final int type;

    /**
     * The human readable name associated with this lineup.
     */
    public final String name;

    /**
     * Location this lineup can be found.
     * This is a human readable description of a geographic location.
     */
    public final String location;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({LINEUP_CABLE, LINEUP_SATELLITE, LINEUP_BROADCAST_DIGITAL, LINEUP_BROADCAST_ANALOG,
            LINEUP_IPTV, LINEUP_MVPD})
    public @interface LineupType {}

    /**
     * Lineup type for cable.
     */
    public static final int LINEUP_CABLE = 0;

    /**
     * Lineup type for satelite.
     */
    public static final int LINEUP_SATELLITE = 1;

    /**
     * Lineup type for broadcast digital.
     */
    public static final int LINEUP_BROADCAST_DIGITAL = 2;

    /**
     * Lineup type for broadcast analog.
     */
    public static final int LINEUP_BROADCAST_ANALOG = 3;

    /**
     * Lineup type for IPTV.
     */
    public static final int LINEUP_IPTV = 4;

    /**
     * Indicates the lineup is either satelite, cable or IPTV but we are not sure which specific
     * type.
      */
    public static final int LINEUP_MVPD = 5;

    /**
     * Creates a lineup.
     */
    public Lineup(String id, int type, String name, String location) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.location = location;
    }
}
