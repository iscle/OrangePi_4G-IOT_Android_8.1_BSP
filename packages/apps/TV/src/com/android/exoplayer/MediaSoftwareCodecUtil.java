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
package com.google.android.exoplayer;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer.util.MimeTypes;

import java.util.HashMap;

/**
 * Mostly copied from {@link com.google.android.exoplayer.MediaCodecUtil} in order to choose
 * software codec over hardware codec.
 */
public class MediaSoftwareCodecUtil {
    private static final String TAG = "MediaSoftwareCodecUtil";

    /**
     * Thrown when an error occurs querying the device for its underlying media capabilities.
     * <p>
     * Such failures are not expected in normal operation and are normally temporary (e.g. if the
     * mediaserver process has crashed and is yet to restart).
     */
    public static class DecoderQueryException extends Exception {

        private DecoderQueryException(Throwable cause) {
            super("Failed to query underlying media codecs", cause);
        }

    }

    private static final HashMap<CodecKey, Pair<String, MediaCodecInfo.CodecCapabilities>>
            sSwCodecs = new HashMap<>();

    /**
     * Gets information about the software decoder that will be used for a given mime type.
     */
    public static DecoderInfo getSoftwareDecoderInfo(String mimeType, boolean secure)
            throws DecoderQueryException {
        // TODO: Add a test for this method.
        Pair<String, MediaCodecInfo.CodecCapabilities> info =
                getMediaSoftwareCodecInfo(mimeType, secure);
        if (info == null) {
            return null;
        }
        return new DecoderInfo(info.first, info.second);
    }

    /**
     * Returns the name of the software decoder and its capabilities for the given mimeType.
     */
    private static synchronized Pair<String, MediaCodecInfo.CodecCapabilities>
    getMediaSoftwareCodecInfo(String mimeType, boolean secure) throws DecoderQueryException {
        CodecKey key = new CodecKey(mimeType, secure);
        if (sSwCodecs.containsKey(key)) {
            return sSwCodecs.get(key);
        }
        MediaCodecListCompat mediaCodecList = new MediaCodecListCompatV21(secure);
        Pair<String, MediaCodecInfo.CodecCapabilities> codecInfo =
                getMediaSoftwareCodecInfo(key, mediaCodecList);
        if (secure && codecInfo == null) {
            // Some devices don't list secure decoders on API level 21. Try the legacy path.
            mediaCodecList = new MediaCodecListCompatV16();
            codecInfo = getMediaSoftwareCodecInfo(key, mediaCodecList);
            if (codecInfo != null) {
                Log.w(TAG, "MediaCodecList API didn't list secure decoder for: " + mimeType
                        + ". Assuming: " + codecInfo.first);
            }
        }
        return codecInfo;
    }

    private static Pair<String, MediaCodecInfo.CodecCapabilities> getMediaSoftwareCodecInfo(
            CodecKey key, MediaCodecListCompat mediaCodecList) throws DecoderQueryException {
        try {
            return getMediaSoftwareCodecInfoInternal(key, mediaCodecList);
        } catch (Exception e) {
            // If the underlying mediaserver is in a bad state, we may catch an
            // IllegalStateException or an IllegalArgumentException here.
            throw new DecoderQueryException(e);
        }
    }

    private static Pair<String, MediaCodecInfo.CodecCapabilities> getMediaSoftwareCodecInfoInternal(
            CodecKey key, MediaCodecListCompat mediaCodecList) {
        String mimeType = key.mimeType;
        int numberOfCodecs = mediaCodecList.getCodecCount();
        boolean secureDecodersExplicit = mediaCodecList.secureDecodersExplicit();
        // Note: MediaCodecList is sorted by the framework such that the best decoders come first.
        for (int i = 0; i < numberOfCodecs; i++) {
            MediaCodecInfo info = mediaCodecList.getCodecInfoAt(i);
            String codecName = info.getName();
            if (!info.isEncoder() && codecName.startsWith("OMX.google.")
                    && (secureDecodersExplicit || !codecName.endsWith(".secure"))) {
                String[] supportedTypes = info.getSupportedTypes();
                for (String supportedType : supportedTypes) {
                    if (supportedType.equalsIgnoreCase(mimeType)) {
                        MediaCodecInfo.CodecCapabilities capabilities =
                                info.getCapabilitiesForType(supportedType);
                        boolean secure = mediaCodecList.isSecurePlaybackSupported(
                                key.mimeType, capabilities);
                        if (!secureDecodersExplicit) {
                            // Cache variants for both insecure and (if we think it's supported)
                            // secure playback.
                            sSwCodecs.put(key.secure ? new CodecKey(mimeType, false) : key,
                                    Pair.create(codecName, capabilities));
                            if (secure) {
                                sSwCodecs.put(key.secure ? key : new CodecKey(mimeType, true),
                                        Pair.create(codecName + ".secure", capabilities));
                            }
                        } else {
                            // Only cache this variant. If both insecure and secure decoders are
                            // available, they should both be listed separately.
                            sSwCodecs.put(
                                    key.secure == secure ? key: new CodecKey(mimeType, secure),
                                    Pair.create(codecName, capabilities));
                        }
                        if (sSwCodecs.containsKey(key)) {
                            return sSwCodecs.get(key);
                        }
                    }
                }
            }
        }
        sSwCodecs.put(key, null);
        return null;
    }

    private interface MediaCodecListCompat {

        /**
         * Returns the number of codecs in the list.
         */
        int getCodecCount();

        /**
         * Returns the info at the specified index in the list.
         *
         * @param index The index.
         */
        MediaCodecInfo getCodecInfoAt(int index);

        /**
         * Returns whether secure decoders are explicitly listed, if present.
         */
        boolean secureDecodersExplicit();

        /**
         * Returns true if secure playback is supported for the given
         * {@link android.media.MediaCodecInfo.CodecCapabilities}, which should
         * have been obtained from a {@link MediaCodecInfo} obtained from this list.
         */
        boolean isSecurePlaybackSupported(String mimeType,
                MediaCodecInfo.CodecCapabilities capabilities);

    }

    @TargetApi(21)
    private static final class MediaCodecListCompatV21 implements MediaCodecListCompat {

        private final int codecKind;

        private MediaCodecInfo[] mediaCodecInfos;

        public MediaCodecListCompatV21(boolean includeSecure) {
            codecKind = includeSecure ? MediaCodecList.ALL_CODECS : MediaCodecList.REGULAR_CODECS;
        }

        @Override
        public int getCodecCount() {
            ensureMediaCodecInfosInitialized();
            return mediaCodecInfos.length;
        }

        @Override
        public MediaCodecInfo getCodecInfoAt(int index) {
            ensureMediaCodecInfosInitialized();
            return mediaCodecInfos[index];
        }

        @Override
        public boolean secureDecodersExplicit() {
            return true;
        }

        @Override
        public boolean isSecurePlaybackSupported(String mimeType,
                MediaCodecInfo.CodecCapabilities capabilities) {
            return capabilities.isFeatureSupported(
                    MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback);
        }

        private void ensureMediaCodecInfosInitialized() {
            if (mediaCodecInfos == null) {
                mediaCodecInfos = new MediaCodecList(codecKind).getCodecInfos();
            }
        }

    }

    @SuppressWarnings("deprecation")
    private static final class MediaCodecListCompatV16 implements MediaCodecListCompat {

        @Override
        public int getCodecCount() {
            return MediaCodecList.getCodecCount();
        }

        @Override
        public MediaCodecInfo getCodecInfoAt(int index) {
            return MediaCodecList.getCodecInfoAt(index);
        }

        @Override
        public boolean secureDecodersExplicit() {
            return false;
        }

        @Override
        public boolean isSecurePlaybackSupported(String mimeType,
                MediaCodecInfo.CodecCapabilities capabilities) {
            // Secure decoders weren't explicitly listed prior to API level 21. We assume that
            // a secure H264 decoder exists.
            return MimeTypes.VIDEO_H264.equals(mimeType);
        }

    }

    private static final class CodecKey {

        public final String mimeType;
        public final boolean secure;

        public CodecKey(String mimeType, boolean secure) {
            this.mimeType = mimeType;
            this.secure = secure;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mimeType == null) ? 0 : mimeType.hashCode());
            result = 2 * result + (secure ? 0 : 1);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CodecKey)) {
                return false;
            }
            CodecKey other = (CodecKey) obj;
            return TextUtils.equals(mimeType, other.mimeType) && secure == other.secure;
        }

    }

}
