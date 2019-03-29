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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.targetprep.MediaPreparer;
import com.android.compatibility.common.util.MetricsReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.util.FileUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.Ignore;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Test that verifies video bitstreams decode pixel perfectly
 */
@OptionClass(alias="media-bitstreams-test")
public abstract class MediaBitstreamsTest implements IDeviceTest, IBuildReceiver, IAbiReceiver {

    @Option(name = MediaBitstreams.OPT_HOST_BITSTREAMS_PATH,
            description = "Absolute path of Ittiam bitstreams (host)",
            mandatory = true)
    private File mHostBitstreamsPath = getDefaultBitstreamsDir();

    @Option(name = MediaBitstreams.OPT_DEVICE_BITSTREAMS_PATH,
            description = "Absolute path of Ittiam bitstreams (device)")
    private String mDeviceBitstreamsPath = MediaBitstreams.DEFAULT_DEVICE_BITSTEAMS_PATH;

    @Option(name = MediaBitstreams.OPT_DOWNLOAD_BITSTREAMS,
            description = "Whether to download the bitstreams files")
    private boolean mDownloadBitstreams = false;

    @Option(name = MediaBitstreams.OPT_UTILIZATION_RATE,
            description = "Percentage of external storage space used for test")
    private int mUtilizationRate = 80;

    @Option(name = MediaBitstreams.OPT_NUM_BATCHES,
            description = "Number of batches to test;"
                    + " each batch uses external storage up to utilization rate")
    private int mNumBatches = Integer.MAX_VALUE;

    @Option(name = MediaBitstreams.OPT_DEBUG_TARGET_DEVICE,
            description = "Whether to debug target device under test")
    private boolean mDebugTargetDevice = false;

    @Option(name = MediaBitstreams.OPT_BITSTREAMS_PREFIX,
            description = "Only test bitstreams in this sub-directory")
    private String mPrefix = "";

    private String mPath = "";

    private static ConcurrentMap<String, List<ConformanceEntry>> mResults = new ConcurrentHashMap<>();

    /**
     * Which subset of bitstreams to test
     */
    enum BitstreamPackage {
        STANDARD,
        FULL,
    }

    private BitstreamPackage mPackage = BitstreamPackage.FULL;
    private BitstreamPackage mPackageToRun = BitstreamPackage.STANDARD;

    static class ConformanceEntry {
        final String mPath, mCodecName, mStatus;
        ConformanceEntry(String path, String codecName, String status) {
            mPath = path;
            mCodecName = codecName;
            mStatus = status;
        }
        @Override
        public String toString() {
            return String.format("%s,%s,%s", mPath, mCodecName, mStatus);
        }
    }

    /**
     * A helper to access resources in the build.
     */
    private CompatibilityBuildHelper mBuildHelper;

    private IAbi mAbi;
    private ITestDevice mDevice;

    static File getDefaultBitstreamsDir() {
        File mediaDir = MediaPreparer.getDefaultMediaDir();
        File[] subDirs = mediaDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File child) {
                return child.isDirectory();
            }
        });
        if (subDirs != null && subDirs.length == 1) {
            File parent = new File(mediaDir, subDirs[0].getName());
            return new File(parent, MediaBitstreams.DEFAULT_HOST_BITSTREAMS_PATH);
        } else {
            return new File(MediaBitstreams.DEFAULT_HOST_BITSTREAMS_PATH);
        }
    }

    static Collection<Object[]> bitstreams(String prefix, BitstreamPackage packageToRun) {
        final String dynConfXml = new File("/", MediaBitstreams.DYNAMIC_CONFIG_XML).toString();
        try (InputStream is = MediaBitstreamsTest.class.getResourceAsStream(dynConfXml)) {
            List<Object[]> entries = new ArrayList<>();
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(is, null);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, MediaBitstreams.DYNAMIC_CONFIG);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG
                        || !MediaBitstreams.DYNAMIC_CONFIG_ENTRY.equals(parser.getName())) {
                    continue;
                }
                final String key = MediaBitstreams.DYNAMIC_CONFIG_KEY;
                String bitstream = parser.getAttributeValue(null, key);
                if (!bitstream.startsWith(prefix)) {
                    continue;
                }
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    if (MediaBitstreams.DYNAMIC_CONFIG_VALUE.equals(parser.getName())) {
                        parser.next();
                        break;
                    }
                }
                String format = parser.getText();
                String[] kvPairs = format.split(",");
                BitstreamPackage curPackage = BitstreamPackage.FULL;
                for (String kvPair : kvPairs) {
                    String[] kv = kvPair.split("=");
                    if (MediaBitstreams.DYNAMIC_CONFIG_PACKAGE.equals(kv[0])) {
                        String packageName = kv[1];
                        try {
                            curPackage = BitstreamPackage.valueOf(packageName.toUpperCase());
                        } catch (Exception e) {
                            CLog.w(e);
                        }
                    }
                }
                if (curPackage.compareTo(packageToRun) <= 0) {
                    entries.add(new Object[] {prefix, bitstream, curPackage, packageToRun});
                }
            }
            return entries;
        } catch (XmlPullParserException | IOException e) {
            CLog.e(e);
            return Collections.emptyList();
        }
    }

    public MediaBitstreamsTest(String prefix, String path, BitstreamPackage pkg, BitstreamPackage packageToRun
            ) {
        mPrefix = prefix;
        mPath = path;
        mPackage = pkg;
        mPackageToRun = packageToRun;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        // Get the build, this is used to access the APK.
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /*
     * Returns true if all necessary media files exist on the device, and false otherwise.
     *
     * This method is exposed for unit testing.
     */
    private boolean bitstreamsExistOnDevice(ITestDevice device)
            throws DeviceNotAvailableException {
        return device.doesFileExist(mDeviceBitstreamsPath)
                && device.isDirectory(mDeviceBitstreamsPath);
    }

    private String getCurrentMethod() {
        return Thread.currentThread().getStackTrace()[2].getMethodName();
    }

    private MetricsReportLog createReport(String methodName) {
        String className = MediaBitstreamsTest.class.getCanonicalName();
        MetricsReportLog report = new MetricsReportLog(
                mBuildHelper.getBuildInfo(), mAbi.getName(),
                String.format("%s#%s", className, methodName),
                MediaBitstreams.K_MODULE + "." + this.getClass().getSimpleName(),
                "media_bitstreams_conformance", true);
        return report;
    }

    /**
     * @param method test method name in the form class#method
     * @param p path to bitstream
     * @param d decoder name
     * @param s test status: unsupported, true, false, crash, or timeout.
     */
    private void addConformanceEntry(String method, String p, String d, String s) {
        MetricsReportLog report = createReport(method);
        report.addValue(MediaBitstreams.KEY_PATH, p, ResultType.NEUTRAL, ResultUnit.NONE);
        report.addValue(MediaBitstreams.KEY_CODEC_NAME, d, ResultType.NEUTRAL, ResultUnit.NONE);
        report.addValue(MediaBitstreams.KEY_STATUS, s, ResultType.NEUTRAL, ResultUnit.NONE);
        report.submit();

        ConformanceEntry ce = new ConformanceEntry(p, d, s);
        mResults.putIfAbsent(p, new ArrayList<>());
        mResults.get(p).add(ce);
    }

    Map<String, String> getArgs() {
        Map<String, String> args = new HashMap<>();
        args.put(MediaBitstreams.OPT_DEBUG_TARGET_DEVICE, Boolean.toString(mDebugTargetDevice));
        args.put(MediaBitstreams.OPT_DEVICE_BITSTREAMS_PATH, mDeviceBitstreamsPath);
        return args;
    }

    private class ProcessBitstreamsFormats extends ReportProcessor {

        @Override
        void setUp(ITestDevice device) throws DeviceNotAvailableException {
            if (mDownloadBitstreams || !bitstreamsExistOnDevice(device)) {
                device.pushDir(mHostBitstreamsPath, mDeviceBitstreamsPath);
            }
        }

        @Override
        Map<String, String> getArgs() {
            return MediaBitstreamsTest.this.getArgs();
        }

        @Override
        void process(ITestDevice device, String reportPath)
                throws DeviceNotAvailableException, IOException {
            File dynamicConfigFile = mBuildHelper.getTestFile(MediaBitstreams.K_MODULE + ".dynamic");
            device.pullFile(reportPath, dynamicConfigFile);
            CLog.i("Pulled bitstreams formats to %s", dynamicConfigFile.getPath());
        }

    }

    private class ProcessBitstreamsValidation extends ReportProcessor {

        Set<String> mBitstreams;
        Deque<String> mProcessedBitstreams = new ArrayDeque<>();
        private final String mMethodName;
        private final String mBitstreamsListTxt = new File(
                mDeviceBitstreamsPath,
                MediaBitstreams.K_BITSTREAMS_LIST_TXT).toString();
        private String mLastCrash;

        ProcessBitstreamsValidation(Set<String> bitstreams, String methodName) {
            mBitstreams = bitstreams;
            mMethodName = methodName;
        }

        private String getBitstreamsListString() {
            OutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos, true);
            try {
                for (String b : mBitstreams) {
                    ps.println(b);
                }
                return baos.toString();
            } finally {
                ps.close();
            }
        }

        private void pushBitstreams(ITestDevice device)
                throws IOException, DeviceNotAvailableException {
            File tmp = null;
            try {
                CLog.i("Pushing %d bitstream(s) from %s to %s",
                        mBitstreams.size(),
                        mHostBitstreamsPath,
                        mDeviceBitstreamsPath);
                tmp = Files.createTempDirectory(null).toFile();
                for (String b : mBitstreams) {
                    String m = MediaBitstreams.getMd5Path(b);
                    for (String f : new String[] {m, b}) {
                        File tmpf = new File(tmp, f);
                        new File(tmpf.getParent()).mkdirs();
                        FileUtil.copyFile(new File(mHostBitstreamsPath, f), tmpf);
                    }
                }
                device.executeShellCommand(String.format("rm -rf %s", mDeviceBitstreamsPath));
                device.pushDir(tmp, mDeviceBitstreamsPath);
                device.pushString(getBitstreamsListString(), mBitstreamsListTxt);
            } finally {
                FileUtil.recursiveDelete(tmp);
            }
        }

        @Override
        void setUp(ITestDevice device) throws DeviceNotAvailableException, IOException {
            pushBitstreams(device);
        }

        @Override
        Map<String, String> getArgs() {
            Map<String, String> args = MediaBitstreamsTest.this.getArgs();
            if (mLastCrash != null) {
                args.put(MediaBitstreams.OPT_LAST_CRASH, mLastCrash);
            }
            return args;
        }

        private void parse(ITestDevice device, String reportPath)
                throws DeviceNotAvailableException {
            String[] lines = getReportLines(device, reportPath);
            mProcessedBitstreams.clear();
            for (int i = 0; i < lines.length;) {

                String path = lines[i++];
                mProcessedBitstreams.add(path);
                String errMsg;

                boolean failedEarly;
                if (i < lines.length) {
                    failedEarly = Boolean.parseBoolean(lines[i++]);
                    errMsg = failedEarly ? lines[i++] : "";
                } else {
                    failedEarly = true;
                    errMsg = MediaBitstreams.K_NATIVE_CRASH;
                    mLastCrash = MediaBitstreams.generateCrashSignature(path, "");
                    mProcessedBitstreams.removeLast();
                }

                if (failedEarly) {
                    addConformanceEntry(mMethodName, path, null, errMsg);
                    continue;
                }

                int n = Integer.parseInt(lines[i++]);
                for (int j = 0; j < n && i < lines.length; j++) {
                    String decoderName = lines[i++];
                    String result;
                    if (i < lines.length) {
                        result = lines[i++];
                    } else {
                        result = MediaBitstreams.K_NATIVE_CRASH;
                        mLastCrash = MediaBitstreams.generateCrashSignature(path, decoderName);
                        mProcessedBitstreams.removeLast();
                    }
                    addConformanceEntry(mMethodName, path, decoderName, result);
                }


            }
        }

        @Override
        void process(ITestDevice device, String reportPath)
                throws DeviceNotAvailableException, IOException {
            parse(device, reportPath);
        }

        @Override
        boolean recover(ITestDevice device, String reportPath)
                throws DeviceNotAvailableException, IOException {
            try {
                parse(device, reportPath);
                mBitstreams.removeAll(mProcessedBitstreams);
                device.pushString(getBitstreamsListString(), mBitstreamsListTxt);
                return true;
            } catch (RuntimeException e) {
                File hostFile = reportPath == null ? null : device.pullFile(reportPath);
                CLog.e("Error parsing report; saving report to %s", hostFile);
                CLog.e(e);
                return false;
            }
        }

    }

    @Ignore
    @Test
    public void testGetBitstreamsFormats() throws DeviceNotAvailableException, IOException {
        ReportProcessor processor = new ProcessBitstreamsFormats();
        processor.processDeviceReport(
                getDevice(),
                getCurrentMethod(),
                MediaBitstreams.KEY_BITSTREAMS_FORMATS_XML);
    }

    @Test
    public void testBitstreamsConformance() {
        File bitstreamFile = new File(mHostBitstreamsPath, mPath);
        if (!bitstreamFile.exists()) {
            // todo(b/65165250): throw Exception once MediaPreparer can auto-download
            CLog.w(bitstreamFile + " not found; skipping");
            return;
        }

        if (!mResults.containsKey(mPath)) {
            try {
                testBitstreamsConformance(mPrefix);
            } catch (DeviceNotAvailableException | IOException e) {
                String curMethod = getCurrentMethod();
                addConformanceEntry(curMethod, mPath, MediaBitstreams.K_UNAVAILABLE, e.toString());
            }
        }
        // todo(robertshih): lookup conformance entry; pass/fail based on lookup result
    }

    private void testBitstreamsConformance(String prefix)
            throws DeviceNotAvailableException, IOException {

        ITestDevice device = getDevice();
        SupportedBitstreamsProcessor preparer;
        preparer = new SupportedBitstreamsProcessor(prefix, mDebugTargetDevice);
        preparer.processDeviceReport(
                device,
                MediaBitstreams.K_TEST_GET_SUPPORTED_BITSTREAMS,
                MediaBitstreams.KEY_SUPPORTED_BITSTREAMS_TXT);
        Collection<Object[]> bitstreams = bitstreams(mPrefix, mPackageToRun);
        Set<String> supportedBitstreams = preparer.getSupportedBitstreams();
        CLog.i("%d supported bitstreams under %s", supportedBitstreams.size(), prefix);

        int n = 0;
        long size = 0;
        long limit = device.getExternalStoreFreeSpace() * mUtilizationRate * 1024 / 100;

        String curMethod = getCurrentMethod();
        Set<String> toPush = new LinkedHashSet<>();
        Iterator<Object[]> iter = bitstreams.iterator();

        for (int i = 0; i < bitstreams.size(); i++) {

            if (n >= mNumBatches) {
                break;
            }

            String p = (String) iter.next()[1];
            Map<String, Boolean> decoderCapabilities;
            decoderCapabilities = preparer.getDecoderCapabilitiesForPath(p);
            if (decoderCapabilities.isEmpty()) {
                addConformanceEntry(
                        curMethod, p,
                        MediaBitstreams.K_UNAVAILABLE,
                        MediaBitstreams.K_UNSUPPORTED);
            }
            for (Entry<String, Boolean> entry : decoderCapabilities.entrySet()) {
                Boolean supported = entry.getValue();
                if (supported) {
                    File bitstreamFile = new File(mHostBitstreamsPath, p);
                    String md5Path = MediaBitstreams.getMd5Path(p);
                    File md5File = new File(mHostBitstreamsPath, md5Path);
                    if (md5File.exists() && bitstreamFile.exists() && toPush.add(p)) {
                        size += md5File.length();
                        size += bitstreamFile.length();
                    }
                } else {
                    String d = entry.getKey();
                    addConformanceEntry(curMethod, p, d, MediaBitstreams.K_UNSUPPORTED);
                }
            }

            if (size > limit || i + 1 == bitstreams.size()) {
                ReportProcessor processor;
                processor = new ProcessBitstreamsValidation(toPush, curMethod);
                processor.processDeviceReport(
                        device,
                        curMethod,
                        MediaBitstreams.KEY_BITSTREAMS_VALIDATION_TXT);
                toPush.clear();
                size = 0;
                n++;
            }

        }

    }


}
