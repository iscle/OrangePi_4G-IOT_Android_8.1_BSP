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
package com.android.tv.testing.testinput;

import com.android.tv.testing.ChannelInfo;

/**
 * Constants for interacting with TvTestInput.
 */
public final class TvTestInputConstants {

    /**
     * Channel 1.
     *
     * <p> By convention Channel 1 should not be changed.  Test often start by tuning to this
     * channel.
     */
    public static final ChannelInfo CH_1_DEFAULT_DONT_MODIFY = ChannelInfo.create(null, 1);
    /**
     * Channel 2.
     *
     * <p> By convention the state of Channel 2 is changed by tests. Testcases should explicitly
     * set the state of this channel before using it in tests.
     */
    public static final ChannelInfo CH_2 = ChannelInfo.create(null, 2);
}
