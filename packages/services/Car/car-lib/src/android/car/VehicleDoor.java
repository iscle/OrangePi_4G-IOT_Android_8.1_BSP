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
 * VehicleDoor is an abstraction for a door in a car. Some car APIs may provide control per
 * door and values defined here should be used to distinguish different doors.
 * @hide
 */
@SystemApi
public final class VehicleDoor {
    public static final int DOOR_ROW_1_LEFT = 0x00000001;
    public static final int DOOR_ROW_1_RIGHT = 0x00000004;
    public static final int DOOR_ROW_2_LEFT = 0x00000010;
    public static final int DOOR_ROW_2_RIGHT = 0x00000040;
    public static final int DOOR_ROW_3_LEFT = 0x00000100;
    public static final int DOOR_ROW_3_RIGHT = 0x00000400;
    public static final int DOOR_HOOD = 0x10000000;
    public static final int DOOR_REAR = 0x20000000;

    private VehicleDoor() {}
}
