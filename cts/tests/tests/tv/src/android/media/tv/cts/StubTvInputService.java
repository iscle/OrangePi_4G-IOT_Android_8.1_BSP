/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import android.content.Context;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.view.Surface;

/**
 * Stub implementation of (@link android.media.tv.TvInputService}.
 */
public class StubTvInputService extends TvInputService {
    @Override
    public Session onCreateSession(String inputId) {
        return new StubSessionImpl(this);
    }

    private static class StubSessionImpl extends Session {
        StubSessionImpl(Context context) {
            super(context);
        }

        @Override
        public void onRelease() {
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            return false;
        }

        @Override
        public void onSetStreamVolume(float volume) {
        }

        @Override
        public boolean onTune(Uri channelUri) {
            return false;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
        }
    }
}
