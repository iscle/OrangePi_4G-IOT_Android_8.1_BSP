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
package com.google.android.exoplayer2.ext.ffmpeg;

import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * This class is based on com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary from ExoPlayer2
 * in order to support mp2 decoder.
 * Configures and queries the underlying native library.
 */
public final class FfmpegLibrary {

    private static final LibraryLoader LOADER =
            new LibraryLoader("avutil", "avresample", "avcodec", "ffmpeg");

    private FfmpegLibrary() {}

    /**
     * Overrides the names of the FFmpeg native libraries. If an application wishes to call this
     * method, it must do so before calling any other method defined by this class, and before
     * instantiating a {@link FfmpegAudioRenderer} instance.
     */
    public static void setLibraries(String... libraries) {
        LOADER.setLibraries(libraries);
    }

    /**
     * Returns whether the underlying library is available, loading it if necessary.
     */
    public static boolean isAvailable() {
        return LOADER.isAvailable();
    }

    /**
     * Returns the version of the underlying library if available, or null otherwise.
     */
    public static String getVersion() {
        return isAvailable() ? ffmpegGetVersion() : null;
    }

    /**
     * Returns whether the underlying library supports the specified MIME type.
     */
    public static boolean supportsFormat(String mimeType) {
        if (!isAvailable()) {
            return false;
        }
        String codecName = getCodecName(mimeType);
        return codecName != null && ffmpegHasDecoder(codecName);
    }

    /**
     * Returns the name of the FFmpeg decoder that could be used to decode {@code mimeType}.
     */
    /* package */ static String getCodecName(String mimeType) {
        switch (mimeType) {
            case MimeTypes.AUDIO_MPEG_L2:
                return "mp2";
            case MimeTypes.AUDIO_AC3:
                return "ac3";
            default:
                return null;
        }
    }

    private static native String ffmpegGetVersion();
    private static native boolean ffmpegHasDecoder(String codecName);

}
