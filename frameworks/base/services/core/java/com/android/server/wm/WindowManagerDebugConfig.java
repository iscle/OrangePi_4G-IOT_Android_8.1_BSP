/*
 * Copyright (C) 2007 The Android Open Source Project
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


package com.android.server.wm;

/// M: WindowManager debug Mechanism
import com.mediatek.server.wm.WindowManagerDebugger;
import com.mediatek.server.MtkSystemServiceFactory;

/**
 * Common class for the various debug {@link android.util.Log} output configuration in the window
 * manager package.
 */
public class WindowManagerDebugConfig {
    // All output logs in the window manager package use the {@link #TAG_WM} string for tagging
    // their log output. This makes it easy to identify the origin of the log message when sifting
    // through a large amount of log output from multiple sources. However, it also makes trying
    // to figure-out the origin of a log message while debugging the window manager a little
    // painful. By setting this constant to true, log messages from the window manager package
    // will be tagged with their class names instead fot the generic tag.
    static final boolean TAG_WITH_CLASS_NAME = false;

    // Default log tag for the window manager package.
    static final String TAG_WM = "WindowManager";
    /// M: Add WindowManager debug Mechanism
    private static WindowManagerDebugger mWindowManagerDebugger =
                    MtkSystemServiceFactory.getInstance().makeWindowManagerDebugger();

    static boolean DEBUG_RESIZE = false;
    static boolean DEBUG = false;
    static boolean DEBUG_ADD_REMOVE = false;
    static boolean DEBUG_FOCUS = false;
    static boolean DEBUG_FOCUS_LIGHT = DEBUG_FOCUS || mWindowManagerDebugger.WMS_DEBUG_USER;
    static boolean DEBUG_ANIM = false;
    static boolean DEBUG_KEYGUARD = false;
    static boolean DEBUG_LAYOUT = false;
    static boolean DEBUG_LAYERS = false;
    static boolean DEBUG_INPUT = false;
    static boolean DEBUG_INPUT_METHOD = false;
    static boolean DEBUG_VISIBILITY = false;
    static boolean DEBUG_WINDOW_MOVEMENT = false;
    static boolean DEBUG_TOKEN_MOVEMENT = false;
    static boolean DEBUG_ORIENTATION = false;
    static boolean DEBUG_APP_ORIENTATION = false;
    static boolean DEBUG_CONFIGURATION = false;
    static boolean DEBUG_APP_TRANSITIONS = false;
    static boolean DEBUG_STARTING_WINDOW_VERBOSE = false;
    static boolean DEBUG_STARTING_WINDOW = DEBUG_STARTING_WINDOW_VERBOSE || false;
    static boolean DEBUG_WALLPAPER = false;
    static boolean DEBUG_WALLPAPER_LIGHT = false || DEBUG_WALLPAPER;
    static boolean DEBUG_DRAG = false;
    static boolean DEBUG_SCREEN_ON = false || mWindowManagerDebugger.WMS_DEBUG_USER;
    static boolean DEBUG_SCREENSHOT = false;
    static boolean DEBUG_BOOT = false || mWindowManagerDebugger.WMS_DEBUG_USER;
    static boolean DEBUG_LAYOUT_REPEATS = false;
    static boolean DEBUG_SURFACE_TRACE = false;
    static boolean DEBUG_WINDOW_TRACE = false;
    static boolean DEBUG_TASK_MOVEMENT = false;
    static boolean DEBUG_TASK_POSITIONING = false;
    static boolean DEBUG_STACK = false;
    static boolean DEBUG_DISPLAY = false || mWindowManagerDebugger.WMS_DEBUG_USER;
    static boolean DEBUG_POWER = false;
    static boolean DEBUG_DIM_LAYER = false;
    static boolean SHOW_SURFACE_ALLOC = false;
    static boolean SHOW_TRANSACTIONS = false;
    static boolean SHOW_VERBOSE_TRANSACTIONS = false && SHOW_TRANSACTIONS;
    static boolean SHOW_LIGHT_TRANSACTIONS = false || SHOW_TRANSACTIONS;
    static boolean SHOW_STACK_CRAWLS = false;
    static boolean DEBUG_WINDOW_CROP = false;
    static boolean DEBUG_UNKNOWN_APP_VISIBILITY = false;

    static final String TAG_KEEP_SCREEN_ON = "DebugKeepScreenOn";
    static boolean DEBUG_KEEP_SCREEN_ON = false;
}
