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
package com.android.compatibility.common.deviceinfo;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.os.Bundle;
import android.os.Build;

import android.media.CamcorderProfile;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Media information collector.
 */
public final class MediaDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
        store.startArray("media_codec_info");
        for (MediaCodecInfo info : allCodecs.getCodecInfos()) {

            store.startGroup();
            store.addResult("name", info.getName());
            store.addResult("encoder", info.isEncoder());

            store.startArray("supported_type");
            for (String type : info.getSupportedTypes()) {

                store.startGroup();
                store.addResult("type", type);
                CodecCapabilities codecCapabilities = info.getCapabilitiesForType(type);
                if (codecCapabilities.profileLevels.length > 0) {
                    store.startArray("codec_profile_level");
                    for (CodecProfileLevel profileLevel : codecCapabilities.profileLevels) {
                        store.startGroup();
                        store.addResult("level", profileLevel.level);
                        store.addResult("profile", profileLevel.profile);
                        store.endGroup();
                    }
                    store.endArray(); // codec_profile_level
                }
                store.addResult("supported_secure_playback", codecCapabilities.isFeatureSupported(
                        CodecCapabilities.FEATURE_SecurePlayback));
                VideoCapabilities videoCapabilities = codecCapabilities.getVideoCapabilities();
                if (videoCapabilities != null) {
                    store.startGroup("supported_resolutions");
                    store.addResult(
                            "supported_360p_30fps",
                            videoCapabilities.areSizeAndRateSupported(640, 360, 30));
                    store.addResult(
                            "supported_480p_30fps",
                            videoCapabilities.areSizeAndRateSupported(720, 480, 30));
                    store.addResult(
                            "supported_720p_30fps",
                            videoCapabilities.areSizeAndRateSupported(1280, 720, 30));
                    store.addResult(
                            "supported_1080p_30fps",
                            videoCapabilities.areSizeAndRateSupported(1920, 1080, 30));
                    // The QHD/WQHD 2560x1440 resolution is used to create YouTube and PlayMovies
                    // 2k content, so use that resolution to determine if a device supports 2k.
                    store.addResult(
                            "supported_2k_30fps",
                            videoCapabilities.areSizeAndRateSupported(2560, 1440, 30));
                    store.addResult(
                            "supported_4k_30fps",
                            videoCapabilities.areSizeAndRateSupported(3840, 2160, 30));
                    store.addResult(
                            "supported_8k_30fps",
                            videoCapabilities.areSizeAndRateSupported(7680, 4320, 30));
                    store.addResult(
                            "supported_360p_60fps",
                            videoCapabilities.areSizeAndRateSupported(640, 360, 60));
                    store.addResult(
                            "supported_480p_60fps",
                            videoCapabilities.areSizeAndRateSupported(720, 480, 60));
                    store.addResult(
                            "supported_720p_60fps",
                            videoCapabilities.areSizeAndRateSupported(1280, 720, 60));
                    store.addResult(
                            "supported_1080p_60fps",
                            videoCapabilities.areSizeAndRateSupported(1920, 1080, 60));
                    store.addResult(
                            "supported_2k_60fps",
                            videoCapabilities.areSizeAndRateSupported(2560, 1440, 60));
                    store.addResult(
                            "supported_4k_60fps",
                            videoCapabilities.areSizeAndRateSupported(3840, 2160, 60));
                    store.addResult(
                            "supported_8k_60fps",
                            videoCapabilities.areSizeAndRateSupported(7680, 4320, 60));
                    store.endGroup(); // supported_resolutions
                }
                store.endGroup();
            }
            store.endArray();
            store.endGroup();
        }

        store.endArray(); // media_codec_profile
    }
}
