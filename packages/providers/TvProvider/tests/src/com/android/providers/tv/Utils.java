/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.providers.tv;

import android.content.ContentResolver;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.RecordedPrograms;

public class Utils {

    public static void clearTvProvider(ContentResolver resolver) {
        resolver.delete(Channels.CONTENT_URI, null, null);
        // Programs and WatchedPrograms table will be automatically cleared when the Channels
        // table is cleared.
        resolver.delete(RecordedPrograms.CONTENT_URI, null, null);
    }

    private Utils() { }
}
