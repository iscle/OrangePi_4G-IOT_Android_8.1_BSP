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
 * limitations under the License
 */

package com.android.tv.common.feature;

import static com.android.tv.common.feature.FeatureUtils.AND;
import static com.android.tv.common.feature.TestableFeature.createTestableFeature;

/**
 * List of {@link Feature} that affect more than just the Live TV app.
 *
 * <p>Remove the {@code Feature} once it is launched.
 */
public class CommonFeatures {
    /**
     * DVR
     *
     * <p>See <a href="https://goto.google.com/atv-dvr-onepager">go/atv-dvr-onepager</a>
     *
     * DVR API is introduced in N, it only works when app runs as a system app.
     */
    public static final TestableFeature DVR = createTestableFeature(
            AND(Sdk.AT_LEAST_N, SystemAppFeature.SYSTEM_APP_FEATURE));

    /**
     * ENABLE_RECORDING_REGARDLESS_OF_STORAGE_STATUS
     *
     * Enables dvr recording regardless of storage status.
     */
    public static final Feature FORCE_RECORDING_UNTIL_NO_SPACE =
            new PropertyFeature("force_recording_until_no_space", false);

    /**
     * USE_SW_CODEC_FOR_SD
     *
     * Prefer software based codec for SD channels.
     */
    public static final Feature USE_SW_CODEC_FOR_SD =
            new PropertyFeature("use_sw_codec_for_sd", false
            );
}
