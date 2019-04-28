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

/**
 * VehicleWindow is an abstraction for a window in a car. Some car APIs may provide control per
 * window and values defined here should be used to distinguish different windows.
 * @hide
 */
@SystemApi
public final class VehicleWindow {
    public static final int WINDOW_FRONT_WINDSHIELD = 0x0001;
    public static final int WINDOW_REAR_WINDSHIELD = 0x0002;
    public static final int WINDOW_ROOF_TOP = 0x0004;
    public static final int WINDOW_ROW_1_LEFT = 0x0010;
    public static final int WINDOW_ROW_1_RIGHT = 0x0020;
    public static final int WINDOW_ROW_2_LEFT = 0x0100;
    public static final int WINDOW_ROW_2_RIGHT = 0x0200;
    public static final int WINDOW_ROW_3_LEFT = 0x1000;
    public static final int WINDOW_ROW_3_RIGHT = 0x2000;

    private VehicleWindow() {}
}

