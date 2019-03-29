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

package android.media.tv.cts;

import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.view.Surface;

/**
 * A TvInputService for testing crash on TV input application.
 */
public class FaultyTvInputService extends StubTvInputService {
    @Override
    public Session onCreateSession(String inputId) {
        return new FaultySession(this);
    }

    @Override
    public RecordingSession onCreateRecordingSession(String inputId) {
        return new FaultyRecordingSession(this);
    }

    public static class FaultySession extends Session {
        FaultySession(Context context) {
            super(context);
        }

        @Override
        public void onRelease() { }

        @Override
        public boolean onTune(Uri ChannelUri) {
            Process.killProcess(Process.myPid());
            return false;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            return false;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) { }

        @Override
        public void onSetStreamVolume(float volume) { }
    }

    public static class FaultyRecordingSession extends RecordingSession {
        FaultyRecordingSession(Context context) {
            super(context);
        }

        @Override
        public void onTune(Uri channelUri) {
            Process.killProcess(Process.myPid());
        }

        @Override
        public void onStartRecording(Uri programHint) { }

        @Override
        public void onStopRecording() { }

        @Override
        public void onRelease() { }
    }
}
