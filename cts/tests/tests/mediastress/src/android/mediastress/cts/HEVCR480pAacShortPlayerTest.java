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

package android.mediastress.cts;

public class HEVCR480pAacShortPlayerTest extends MediaPlayerStressTest {
    private static final String VIDEO_PATH_MIDDLE = "bbb_short/720x480/mp4_libx265_libfaac/";
    private final String[] mMedias = {
        "bbb_short.fmpeg.720x480.mp4.libx265_650kbps_24fps.libfaac_stereo_128kbps_48000hz.mp4",
        "bbb_short.fmpeg.720x480.mp4.libx265_650kbps_30fps.libfaac_stereo_128kbps_48000hz.mp4",
        "bbb_short.fmpeg.720x480.mp4.libx265_880kbps_24fps.libfaac_stereo_128kbps_48000hz.mp4",
        "bbb_short.fmpeg.720x480.mp4.libx265_880kbps_30fps.libfaac_stereo_128kbps_48000hz.mp4",
        "bbb_short.fmpeg.720x480.mp4.libx265_325kbps_24fps.libfaac_stereo_128kbps_48000hz.mp4",
        "bbb_short.fmpeg.720x480.mp4.libx265_325kbps_30fps.libfaac_stereo_128kbps_48000hz.mp4"
    };

    public void testPlay00() throws Exception {
        doTestVideoPlaybackShort(0);
    }

    public void testPlay01() throws Exception {
        doTestVideoPlaybackShort(1);
    }

    public void testPlay02() throws Exception {
        doTestVideoPlaybackShort(2);
    }

    public void testPlay03() throws Exception {
        doTestVideoPlaybackShort(3);
    }

    public void testPlay04() throws Exception {
        doTestVideoPlaybackShort(4);
    }

    public void testPlay05() throws Exception {
        doTestVideoPlaybackShort(5);
    }

    @Override
    protected String getFullVideoClipName(int mediaNumber) {
        return VIDEO_TOP_DIR + VIDEO_PATH_MIDDLE + mMedias[mediaNumber];
    }

}
