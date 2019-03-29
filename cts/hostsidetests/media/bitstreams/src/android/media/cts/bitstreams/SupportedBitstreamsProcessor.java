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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runs a test on target device to query support for bitstreams listed in device side
 * dynamic configuration file.
 */
public class SupportedBitstreamsProcessor extends ReportProcessor {

    private final String mPrefix;
    private final boolean mDebugTargetDevice;
    private final Set<String> mSupportedBitstreams = new LinkedHashSet<>();
    private final Map<String, Map<String, Boolean>> mDecodersForPath = new HashMap<>();

    public SupportedBitstreamsProcessor() {
        this("",false);
    }

    /**
     * @param prefix only bitstreams whose relative paths start with {@code prefix}
     * would be processed
     * @param debugTargetDevice whether to pause {@code device} for debugging
     */
    public SupportedBitstreamsProcessor(String prefix, boolean debugTargetDevice) {
        mPrefix = prefix;
        mDebugTargetDevice = debugTargetDevice;
    }

    /**
     * @return paths to bitstreams that are supported on device
     */
    public Set<String> getSupportedBitstreams() {
        return mSupportedBitstreams;
    }

    /**
     * @return paths to all bitstreams whose relative paths start with <code>prefix</code>
     */
    public Set<String> getBitstreams() {
        return mDecodersForPath.keySet();
    }

    public Map<String, Boolean> getDecoderCapabilitiesForPath(String path) {
        if (mDecodersForPath.containsKey(path)) {
            return mDecodersForPath.get(path);
        }
        return Collections.emptyMap();
    }

    @Override
    Map<String, String> getArgs() {
        Map<String, String> args = new HashMap<>();
        args.put(MediaBitstreams.OPT_BITSTREAMS_PREFIX, mPrefix);
        args.put(MediaBitstreams.OPT_DEBUG_TARGET_DEVICE, Boolean.toString(mDebugTargetDevice));
        return args;
    }

    @Override
    void process(ITestDevice device, String reportPath) throws DeviceNotAvailableException {
        mSupportedBitstreams.clear();
        String[] lines = getReportLines(device, reportPath);
        try {
            for (int i = 0; i < lines.length;) {
                String path = lines[i++];
                int n = Integer.parseInt(lines[i++]);
                for (int j = 0; j < n; j++) {
                    String name = lines[i++];
                    String status = lines[i++];
                    boolean supported = status.equals("true");
                    if (supported) {
                        mSupportedBitstreams.add(path);
                    }
                    Map<String, Boolean> decoderCapabilities;
                    if (mDecodersForPath.containsKey(path)) {
                        decoderCapabilities = mDecodersForPath.get(path);
                    } else {
                        mDecodersForPath.put(path, decoderCapabilities = new HashMap<>());
                    }
                    decoderCapabilities.put(name, supported);
                }
            }
        } catch (Exception e) {
            CLog.w(e);
        }
    }

}
