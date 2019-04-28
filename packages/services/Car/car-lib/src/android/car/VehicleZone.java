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
package android.car;

import android.annotation.SystemApi;
import android.car.hardware.CarPropertyValue;

/**
 * VehicleZone is an abstraction for an area in a car. Some car APIs like
 * {@link CarPropertyValue} needs to handle zone and values defined
 * here should be used.
 * @hide
 */
@SystemApi
public final class VehicleZone {
    public static final int ZONE_ROW_1_LEFT = 0x00000001;
    public static final int ZONE_ROW_1_CENTER = 0x00000002;
    public static final int ZONE_ROW_1_RIGHT = 0x00000004;
    public static final int ZONE_ROW_1_ALL = 0x00000008;
    public static final int ZONE_ROW_2_LEFT = 0x00000010;
    public static final int ZONE_ROW_2_CENTER = 0x00000020;
    public static final int ZONE_ROW_2_RIGHT = 0x00000040;
    public static final int ZONE_ROW_2_ALL = 0x00000080;
    public static final int ZONE_ROW_3_LEFT = 0x00000100;
    public static final int ZONE_ROW_3_CENTER = 0x00000200;
    public static final int ZONE_ROW_3_RIGHT = 0x00000400;
    public static final int ZONE_ROW_3_ALL = 0x00000800;
    public static final int ZONE_ROW_4_LEFT = 0x00001000;
    public static final int ZONE_ROW_4_CENTER = 0x00002000;
    public static final int ZONE_ROW_4_RIGHT = 0x00004000;
    public static final int ZONE_ROW_4_ALL = 0x00008000;
    public static final int ZONE_ALL = 0x80000000;

    private VehicleZone() {}
}
