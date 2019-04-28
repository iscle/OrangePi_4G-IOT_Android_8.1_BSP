/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.support.car.lenspicker;

public class LensPickerConstants {
    /**
     * List of {@link android.content.pm.ResolveInfo} of applications to be displayed
     */
    public static final String EXTRA_PACKAGE_RESOLVE_INFO = "resolve_info";

    /**
     * List of {@link String} categories that the lens picker should filter on.
     */
    public static final String EXTRA_FACET_CATEGORIES = "categories";

    /**
     * List of {@link String} package names that the lens picker should render.
     */
    public static final String EXTRA_FACET_PACKAGES = "packages";

    /**
     * {@link String} id that uniquely identifies the facet being displayed.
     */
    public static final String EXTRA_FACET_ID = "filter_id";

    /**
     * {@link boolean} to specify if the lens picker should be launched even if there is already
     * a stored "preferred" app.
     */
    public static final String EXTRA_FACET_LAUNCH_PICKER = "launch_picker";

    /**
     * An extra used to specify a system command that the LensPicker should run. The available
     * commands are the constants prefixed with "SYSTEM_COMMAND".
     */
    public static final String EXTRA_FACET_SYSTEM_COMMAND = "system_command";

    /**
     * A hard-coded string that can be specified as the value of the string extra
     * {@link #EXTRA_FACET_SYSTEM_COMMAND}. This command will cause the
     * notification shade to drop if collapsed and vice versa.
     */
    public static final String SYSTEM_COMMAND_TOGGLE_NOTIFICATIONS = "toggle_notifications";
}
