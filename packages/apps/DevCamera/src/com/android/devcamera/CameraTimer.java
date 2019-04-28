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
package com.android.devcamera;

/**
 * A global spot to store some times.
 */
public class CameraTimer {
    // Got control in onCreate()
    public static long t0;
    // Sent open() to camera.
    public static long t_open_start;
    // Open from camera done.
    public static long t_open_end;
    // Told camera to configure capture session.
    public static long t_session_go;
    // Told session to do repeating request.
    public static long t_burst;

}
