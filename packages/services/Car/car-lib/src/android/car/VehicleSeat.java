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
 * VehicleSeat is an abstraction for a seat in a car. Some car APIs like
 * {@link CarPropertyValue} may provide control per seat and
 * values defined here should be used to distinguish different seats.
 * @hide
 */
@SystemApi
public final class VehicleSeat {
    public static final int SEAT_ROW_1_LEFT = 0x0001;
    public static final int SEAT_ROW_1_CENTER = 0x0002;
    public static final int SEAT_ROW_1_RIGHT = 0x0004;
    public static final int SEAT_ROW_2_LEFT = 0x0010;
    public static final int SEAT_ROW_2_CENTER = 0x0020;
    public static final int SEAT_ROW_2_RIGHT = 0x0040;
    public static final int SEAT_ROW_3_LEFT = 0x0100;
    public static final int SEAT_ROW_3_CENTER = 0x0200;
    public static final int SEAT_ROW_3_RIGHT = 0x0400;

    private VehicleSeat() {}
}
