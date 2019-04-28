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

package com.android.tv.experiments;

import static com.android.tv.experiments.ExperimentFlag.createFlag;

import com.android.tv.common.BuildConfig;

/**
 * Set of experiments visible in AOSP.
 *
 * <p>This file is maintained by hand.
 */
public final class Experiments {
    public static final ExperimentFlag<Boolean> CLOUD_EPG = createFlag(
            true);

    public static final ExperimentFlag<Boolean> ENABLE_UNRATED_CONTENT_SETTINGS =
            createFlag(
                    false);

    /**
     * Allow developer features such as the dev menu and other aids.
     *
     * <p>These features are available to select users(aka fishfooders) on production builds.
     */
    public static final ExperimentFlag<Boolean> ENABLE_DEVELOPER_FEATURES = createFlag(
            BuildConfig.ENG);

    private Experiments() {}
}
