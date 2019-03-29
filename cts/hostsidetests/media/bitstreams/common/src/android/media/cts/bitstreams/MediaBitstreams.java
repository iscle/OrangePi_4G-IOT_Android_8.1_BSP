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

package android.media.cts.bitstreams;

/**
 * This class provides constants and utilities shared between the host-side and device-side test
 * components.
 */
public class MediaBitstreams {

    /* options */
    public static final String OPT_HOST_BITSTREAMS_PATH = "host-bitstreams-path";
    public static final String OPT_DEVICE_BITSTREAMS_PATH = "device-bitstreams-path";
    public static final String OPT_DOWNLOAD_BITSTREAMS = "download-bitstreams";
    public static final String OPT_DEBUG_TARGET_DEVICE = "debug-target-device";
    public static final String OPT_BITSTREAMS_TO_TEST_TXT = "bitstreams-to-test-txt";
    public static final String OPT_UTILIZATION_RATE = "utilization-rate";
    public static final String OPT_NUM_BATCHES = "num-batches";
    public static final String OPT_LAST_CRASH = "last-crash";
    public static final String OPT_BITSTREAMS_PREFIX = "prefix";

    /* defaults */
    public static final String DEFAULT_HOST_BITSTREAMS_PATH = "TestVectorsIttiam";
    public static final String DEFAULT_DEVICE_BITSTEAMS_PATH = "/data/local/tmp/TestVectorsIttiam";

    /* metric keys */
    public static final String KEY_BITSTREAMS_FORMATS_XML = "bitstreams_formats_xml";
    public static final String KEY_SUPPORTED_BITSTREAMS_TXT = "supported_bitstreams_txt";
    public static final String KEY_BITSTREAMS_VALIDATION_TXT = "bitstreams_validation_txt";
    public static final String KEY_APP_CACHE_DIR = "app_cache_dir";
    public static final String KEY_ERR_MSG = "err_msg";
    public static final String KEY_PATH = "path";
    public static final String KEY_CODEC_NAME = "codec_name";
    public static final String KEY_STATUS = "status";

    /* constants */
    public static final String K_MODULE = "CtsMediaBitstreamsTestCases";
    public static final String K_BITSTREAMS_LIST_TXT = "bitstreamsFile.txt";
    public static final String K_TEST_GET_SUPPORTED_BITSTREAMS = "testGetSupportedBitstreams";
    public static final String K_NATIVE_CRASH = "native crash";
    public static final String K_UNSUPPORTED = "unsupported";
    public static final String K_UNAVAILABLE = "unavailable";

    public static final String DYNAMIC_CONFIG_XML = "DynamicConfig.xml";
    public static final String DYNAMIC_CONFIG = "dynamicConfig";
    public static final String DYNAMIC_CONFIG_ENTRY = "entry";
    public static final String DYNAMIC_CONFIG_KEY = "key";
    public static final String DYNAMIC_CONFIG_VALUE = "value";
    public static final String DYNAMIC_CONFIG_PACKAGE = "package";

    /* utilities */
    /**
     * @param bitstreamPath path of individual bitstream relative to bitstreams root,
     * e.g. {@code h264/../../../../*.mp4}
     * @return checksum file path for {@code bitstreamPath}, e.g. {@code h264/../../../../*_md5}.
     */
    public static String getMd5Path(String bitstreamPath) {
        String base = bitstreamPath.replaceAll(".mp4$|.webm$", "");
        String codec = bitstreamPath.split("/", 2)[0];
        String md5Path = String.format("%s_%s_md5", base, codec);
        return md5Path;
    }

    /**
     * @param path relative bitstream path, e.g. {@code h264/../../../../*.mp4}
     * @param name codec name, e.g. {@code OMX.google.h264.decoder}
     * @return crash signature for a crashed device decoding session,
     * in the form of {@code <bitstream path>:<codec name>}
     */
    public static String generateCrashSignature(String path, String name) {
        return String.format("%s:%s", path, name);
    }

}
