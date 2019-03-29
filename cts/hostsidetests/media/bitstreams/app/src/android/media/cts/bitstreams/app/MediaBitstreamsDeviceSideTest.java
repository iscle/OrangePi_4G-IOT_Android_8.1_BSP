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

package android.media.cts.bitstreams.app;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.bitstreams.MediaBitstreams;
import android.os.Bundle;
import android.os.Debug;
import android.support.test.InstrumentationRegistry;
import android.util.Xml;
import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.MediaUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xmlpull.v1.XmlSerializer;

/**
 * Test class that uses device-side media APIs to determine up to which resolution MediaPreparer
 * should copy media files for CtsMediaStressTestCases.
 */
@RunWith(JUnit4.class)
public class MediaBitstreamsDeviceSideTest {

    private static final String KEY_SIZE = "size";
    private static final String UTF_8 = "utf-8";
    /** Instrumentation status code used to write resolution to metrics */
    private static final int INST_STATUS_IN_PROGRESS = 2;

    private static File mAppCache = InstrumentationRegistry.getContext().getExternalCacheDir();
    private static String mDeviceBitstreamsPath = InstrumentationRegistry.getArguments().getString(
            MediaBitstreams.OPT_DEVICE_BITSTREAMS_PATH,
            MediaBitstreams.DEFAULT_DEVICE_BITSTEAMS_PATH);

    @BeforeClass
    public static void setUp() {
        Bundle args = InstrumentationRegistry.getArguments();
        String debugStr = args.getString(MediaBitstreams.OPT_DEBUG_TARGET_DEVICE, "false");
        boolean debug = Boolean.parseBoolean(debugStr);
        if (debug && !Debug.isDebuggerConnected()) {
            Debug.waitForDebugger();
        }
    }

    static interface ReportCallback {
        void run(OutputStream out) throws Exception;
    }

    static class GenerateBitstreamsFormatsXml implements ReportCallback {
        @Override
        public void run(OutputStream out) throws Exception {

            String[] keys = new String[] {
                    MediaFormat.KEY_WIDTH,
                    MediaFormat.KEY_HEIGHT,
                    MediaFormat.KEY_FRAME_RATE,
                    MediaFormat.KEY_PROFILE,
                    MediaFormat.KEY_LEVEL,
                    MediaFormat.KEY_BIT_RATE};

            XmlSerializer formats = Xml.newSerializer();
            formats.setOutput(out, UTF_8);
            formats.startDocument(UTF_8, true);
            formats.startTag(null, MediaBitstreams.DYNAMIC_CONFIG);

            DynamicConfigDeviceSide config = new DynamicConfigDeviceSide(MediaBitstreams.K_MODULE);
            for (String path : config.keySet()) {

                formats.startTag(null, MediaBitstreams.DYNAMIC_CONFIG_ENTRY);
                formats.attribute(null, MediaBitstreams.DYNAMIC_CONFIG_KEY, path);
                formats.startTag(null, MediaBitstreams.DYNAMIC_CONFIG_VALUE);

                String formatStr = config.getValue(path);
                if (formatStr != null && !formatStr.isEmpty()) {
                    formats.text(formatStr);
                } else {
                    File media = new File(mDeviceBitstreamsPath, path);
                    String fullPath = media.getPath();
                    MediaFormat format = MediaUtils.getTrackFormatForPath(null, fullPath, "video");
                    StringBuilder formatStringBuilder = new StringBuilder(MediaFormat.KEY_MIME);
                    formatStringBuilder.append('=').append(format.getString(MediaFormat.KEY_MIME));
                    formatStringBuilder.append(',').append(KEY_SIZE)
                            .append('=').append(media.length());
                    for (String key : keys) {
                        formatStringBuilder.append(',').append(key).append('=');
                        if (format.containsKey(key)) {
                            formatStringBuilder.append(format.getInteger(key));
                        }
                    }
                    formats.text(formatStringBuilder.toString());
                }

                formats.endTag(null, MediaBitstreams.DYNAMIC_CONFIG_VALUE);
                formats.endTag(null, MediaBitstreams.DYNAMIC_CONFIG_ENTRY);

            }

            formats.endTag(null, MediaBitstreams.DYNAMIC_CONFIG);
            formats.endDocument();

        }
    }

    static class GenerateSupportedBitstreamsFormatsTxt implements ReportCallback {

        @Override
        public void run(OutputStream out) throws Exception {

            PrintStream ps = new PrintStream(out);
            Bundle args = InstrumentationRegistry.getArguments();
            String prefix = args.getString(MediaBitstreams.OPT_BITSTREAMS_PREFIX, "");
            DynamicConfigDeviceSide config = new DynamicConfigDeviceSide(MediaBitstreams.K_MODULE);

            for (String path : config.keySet()) {

                if (!path.startsWith(prefix)) {
                    continue;
                }

                String formatStr = config.getValue(path);
                if (formatStr == null || formatStr.isEmpty()) {
                    continue;
                }

                MediaFormat format = parseTrackFormat(formatStr);
                String mime = format.getString(MediaFormat.KEY_MIME);
                String[] decoders = MediaUtils.getDecoderNamesForMime(mime);

                ps.println(path);
                ps.println(decoders.length);
                for (String name : decoders) {
                    ps.println(name);
                    ps.println(MediaUtils.supports(name, format));
                }

            }

            ps.flush();
        }
    }

    static class TestBitstreamsConformance implements ReportCallback {

        ExecutorService mExecutorService;

        private SharedPreferences getSettings() {
            Context ctx = InstrumentationRegistry.getContext();
            SharedPreferences settings = ctx.getSharedPreferences(MediaBitstreams.K_MODULE, 0);
            return settings;
        }

        private void setup() {
            Bundle args = InstrumentationRegistry.getArguments();
            String lastCrash = args.getString(MediaBitstreams.OPT_LAST_CRASH);
            if (lastCrash != null) {
                SharedPreferences settings = getSettings();
                int n = settings.getInt(lastCrash, 0);
                Editor editor = settings.edit();
                editor.putInt(lastCrash, n + 1);
                editor.commit();
            }
        }

        @Override
        public void run(OutputStream out) throws Exception {
            setup();
            mExecutorService = Executors.newFixedThreadPool(3);
            try (
                Scanner sc = new Scanner(
                        new File(mDeviceBitstreamsPath, MediaBitstreams.K_BITSTREAMS_LIST_TXT));
                PrintStream ps = new PrintStream(out, true)
            ) {
                while (sc.hasNextLine()) {
                    verifyBitstream(ps, sc.nextLine());
                }
            } finally {
                mExecutorService.shutdown();
            }
        }

        private List<String> getDecodersForPath(String path) throws IOException {
            List<String> decoders = new ArrayList<>();
            MediaExtractor ex = new MediaExtractor();
            try {
                ex.setDataSource(path);
                MediaFormat format = ex.getTrackFormat(0);
                boolean[] vendors = new boolean[] {false, true};
                for (boolean v : vendors) {
                    for (String name : MediaUtils.getDecoderNames(v, format)) {
                        decoders.add(name);
                    }
                }
            } finally {
                ex.release();
            }
            return decoders;
        }

        private List<String> getFrameChecksumsForPath(String path) throws IOException {
            String md5Path = MediaBitstreams.getMd5Path(path);
            List<String> frameMD5Sums = Files.readAllLines(
                    new File(mDeviceBitstreamsPath, md5Path).toPath());
            for (int i = 0; i < frameMD5Sums.size(); i++) {
                String line = frameMD5Sums.get(i);
                frameMD5Sums.set(i, line.split(" ")[0]);
            }
            return frameMD5Sums;
        }

        private void verifyBitstream(PrintStream ps, String relativePath) {
            ps.println(relativePath);

            List<String> decoders = new ArrayList<>();
            List<String> frameChecksums = new ArrayList<>();
            SharedPreferences settings = getSettings();
            String fullPath = new File(mDeviceBitstreamsPath, relativePath).toString();
            try {
                String lastCrash = MediaBitstreams.generateCrashSignature(relativePath, "");
                if (settings.getInt(lastCrash, 0) >= 3) {
                    ps.println(MediaBitstreams.K_NATIVE_CRASH);
                    return;
                }
                decoders = getDecodersForPath(fullPath);
                frameChecksums = getFrameChecksumsForPath(relativePath);
                ps.println(false);
            } catch (Exception e) {
                ps.println(true);
                ps.println(e.toString());
                return;
            }

            ps.println(decoders.size());
            for (String name : decoders) {
                ps.println(name);
                String lastCrash = MediaBitstreams.generateCrashSignature(relativePath, name);
                if (settings.getInt(lastCrash, 0) >= 3) {
                    ps.println(MediaBitstreams.K_NATIVE_CRASH);
                } else {
                    ps.println(verifyBitstream(fullPath, name, frameChecksums));
                }
            }

        }

        private String verifyBitstream(String path, String name, List<String> frameChecksums)  {
            MediaExtractor ex = new MediaExtractor();
            MediaCodec d = null;
            try {
                Future<MediaCodec> dec = mExecutorService.submit(new Callable<MediaCodec>() {
                    @Override
                    public MediaCodec call() throws Exception {
                        return MediaCodec.createByCodecName(name);
                    }
                });
                MediaCodec decoder = d = dec.get(1, TimeUnit.SECONDS);
                Future<Boolean> conform = mExecutorService.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        ex.setDataSource(path);
                        ex.selectTrack(0);
                        ex.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                        return MediaUtils.verifyDecoder(decoder, ex, frameChecksums);
                    }
                });
                return conform.get(15, TimeUnit.SECONDS).toString();
            } catch (Exception e) {
                return e.toString();
            } finally {
                ex.release();
                if (d != null) {
                    d.release();
                }
            }
        }

    }

    private void generateReportFile(String suffix, String reportKey, ReportCallback callback)
            throws IOException, FileNotFoundException, Exception {

        OutputStream out = new ByteArrayOutputStream(0);

        try {

            File tmpf = File.createTempFile(getClass().getSimpleName(), suffix, mAppCache);
            Instrumentation inst = InstrumentationRegistry.getInstrumentation();
            Bundle bundle = new Bundle();
            bundle.putString(MediaBitstreams.KEY_APP_CACHE_DIR, mAppCache.getCanonicalPath());
            bundle.putString(reportKey, tmpf.getCanonicalPath());
            inst.sendStatus(INST_STATUS_IN_PROGRESS, bundle);

            out = new FileOutputStream(tmpf);
            callback.run(out);
            out.flush();

        } finally {

            out.close();

        }
    }

    @Test
    public void testGetBitstreamsFormats() throws Exception {
        generateReportFile(".xml",
                MediaBitstreams.KEY_BITSTREAMS_FORMATS_XML,
                new GenerateBitstreamsFormatsXml());
    }

    @Test
    public void testGetSupportedBitstreams() throws Exception {
        generateReportFile(".txt",
                MediaBitstreams.KEY_SUPPORTED_BITSTREAMS_TXT,
                new GenerateSupportedBitstreamsFormatsTxt());
    }

    @Test
    public void testBitstreamsConformance() throws Exception {
        generateReportFile(".txt",
                MediaBitstreams.KEY_BITSTREAMS_VALIDATION_TXT,
                new TestBitstreamsConformance());
    }

    /**
     * Converts a single media track format string into a MediaFormat object
     *
     * @param trackFormatString a string representation of the format of one media track
     * @return a MediaFormat
     */
    private static MediaFormat parseTrackFormat(String trackFormatString) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "");
        for (String entry : trackFormatString.split(",")) {
            String[] kv = entry.split("=");
            if (kv.length < 2 || kv[1].isEmpty()) {
                continue;
            }
            String k = kv[0];
            String v = kv[1];
            try {
                format.setInteger(k, Integer.parseInt(v));
            } catch (NumberFormatException e) {
                format.setString(k, v);
            }
        }
        return format;
    }
}
