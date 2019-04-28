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

package android.support.test.aupt;

import android.app.Instrumentation;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MemHealthRecord {
    // Process State
    private final String mProcName;
    private final boolean mInForeground;

    // Memory health state
    private final long mTimeMs;
    private final long mDalvikHeap;
    private final long mNativeHeap;
    private final long mPss;

    // App summary metrics
    private final long mAsJavaHeap;
    private final long mAsNativeHeap;
    private final long mAsCode;
    private final long mAsStack;
    private final long mAsGraphics;
    private final long mAsOther;
    private final long mAsSystem;
    private final long mAsOverallPss;

    public MemHealthRecord(String procName, long timeMs, long dalvikHeap, long nativeHeap, long pss,
            long asJavaHeap, long asNativeHeap, long asCode, long asStack,
            long asGraphics, long asOther, long asSystem, long asOverallPss,
            boolean inForeground) {
        mProcName = procName;
        mTimeMs = timeMs;
        mDalvikHeap = dalvikHeap;
        mNativeHeap = nativeHeap;
        mPss = pss;
        mAsJavaHeap = asJavaHeap;
        mAsNativeHeap = asNativeHeap;
        mAsCode = asCode;
        mAsStack = asStack;
        mAsGraphics = asGraphics;
        mAsOther = asOther;
        mAsSystem = asSystem;
        mAsOverallPss = asOverallPss;
        mInForeground = inForeground;
    }

    public MemHealthRecord(
            String procName, long timeMs, long dalvikHeap,
            long nativeHeap, long pss, boolean inForeground) {
        this(procName, timeMs, dalvikHeap, nativeHeap, pss, 0, 0, 0, 0, 0, 0, 0, 0, inForeground);
    }

    /* Static methods */

    static List<MemHealthRecord> get(
            Instrumentation instr,
            List<String> procNames,
            long timeMs,
            List<String> foregroundProcs) throws IOException {

        List<MemHealthRecord> records = new ArrayList<>();

        for (String procName : procNames) {
            String meminfo = getMeminfoOutput(instr, procName);
            int nativeHeap = parseMeminfoLine(meminfo, "Native Heap\\s+\\d+\\s+(\\d+)");
            int dalvikHeap = parseMeminfoLine(meminfo, "Dalvik Heap\\s+\\d+\\s+(\\d+)");
            int pss = parseMeminfoLine(meminfo, "TOTAL\\s+(\\d+)");

            int asJavaHeap = parseMeminfoLine(meminfo, "Java Heap:\\s+(\\d+)");
            int asNativeHeap = parseMeminfoLine(meminfo, "Native Heap:\\s+(\\d+)");
            int asCode = parseMeminfoLine(meminfo, "Code:\\s+(\\d+)");
            int asStack = parseMeminfoLine(meminfo, "Stack:\\s+(\\d+)");
            int asGraphics = parseMeminfoLine(meminfo, "Graphics:\\s+(\\d+)");
            int asOther = parseMeminfoLine(meminfo, "Private Other:\\s+(\\d+)");
            int asSystem = parseMeminfoLine(meminfo, "System:\\s+(\\d+)");
            int asOverallPss = parseMeminfoLine(meminfo, "TOTAL:\\s+(\\d+)");

            if (nativeHeap < 0 || dalvikHeap < 0 || pss < 0) {
                continue;
            }

            records.add(new MemHealthRecord(
                    procName, timeMs, dalvikHeap, nativeHeap, pss, asJavaHeap,
                    asNativeHeap, asCode, asStack, asGraphics, asOther, asSystem,
                    asOverallPss, foregroundProcs.contains(procName)));
        }

        return records;
    }

    static void saveVerbose(Collection<MemHealthRecord> allRecords, String fileName)
            throws IOException {
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));

        Map<String, List<MemHealthRecord>> fgRecords = getRecordMap(allRecords, true);
        Map<String, List<MemHealthRecord>> bgRecords = getRecordMap(allRecords, false);

        out.println("Foreground");

        for (Map.Entry<String, List<MemHealthRecord>> entry : fgRecords.entrySet()) {
            String procName = entry.getKey();
            List<MemHealthRecord> records = entry.getValue();

            List<Long> nativeHeap = getForegroundNativeHeap(records);
            List<Long> dalvikHeap = getForegroundDalvikHeap(records);
            List<Long> pss = getForegroundPss(records);
            List<Long> asJavaHeap = getForegroundSummaryJavaHeap(records);
            List<Long> asNativeHeap = getForegroundSummaryNativeHeap(records);
            List<Long> asCode = getForegroundSummaryCode(records);
            List<Long> asStack = getForegroundSummaryStack(records);
            List<Long> asGraphics = getForegroundSummaryGraphics(records);
            List<Long> asOther = getForegroundSummaryOther(records);
            List<Long> asSystem = getForegroundSummarySystem(records);
            List<Long> asOverallPss = getForegroundSummaryOverallPss(records);

            // nativeHeap, dalvikHeap, and pss all have the same size, just use one
            if (nativeHeap.isEmpty()) {
                continue;
            }

            out.println(procName);
            out.printf("Average Native Heap: %d\n", getAverage(nativeHeap));
            out.printf("Average Dalvik Heap: %d\n", getAverage(dalvikHeap));
            out.printf("Average PSS: %d\n", getAverage(pss));
            out.printf("Peak Native Heap: %d\n", getMax(nativeHeap));
            out.printf("Peak Dalvik Heap: %d\n", getMax(dalvikHeap));
            out.printf("Peak PSS: %d\n", getMax(pss));
            out.printf("Count %d\n", nativeHeap.size());

            out.printf("Average Summary Java Heap: %d\n", getAverage(asJavaHeap));
            out.printf("Average Summary Native Heap: %d\n", getAverage(asNativeHeap));
            out.printf("Average Summary Code: %d\n", getAverage(asCode));
            out.printf("Average Summary Stack: %d\n", getAverage(asStack));
            out.printf("Average Summary Graphics: %d\n", getAverage(asGraphics));
            out.printf("Average Summary Other: %d\n", getAverage(asOther));
            out.printf("Average Summary System: %d\n", getAverage(asSystem));
            out.printf("Average Summary Overall Pss: %d\n", getAverage(asOverallPss));
        }

        out.println("Background");
        for (Map.Entry<String, List<MemHealthRecord>> entry : fgRecords.entrySet()) {
            String procName = entry.getKey();
            List<MemHealthRecord> records = entry.getValue();

            List<Long> nativeHeap = getBackgroundNativeHeap(records);
            List<Long> dalvikHeap = getBackgroundDalvikHeap(records);
            List<Long> pss = getBackgroundPss(records);
            List<Long> asJavaHeap = getBackgroundSummaryJavaHeap(records);
            List<Long> asNativeHeap = getBackgroundSummaryNativeHeap(records);
            List<Long> asCode = getBackgroundSummaryCode(records);
            List<Long> asStack = getBackgroundSummaryStack(records);
            List<Long> asGraphics = getBackgroundSummaryGraphics(records);
            List<Long> asOther = getBackgroundSummaryOther(records);
            List<Long> asSystem = getBackgroundSummarySystem(records);
            List<Long> asOverallPss = getBackgroundSummaryOverallPss(records);

            // nativeHeap, dalvikHeap, and pss all have the same size, just use one
            if (nativeHeap.isEmpty()) {
                continue;
            }

            out.println(procName);
            out.printf("Average Native Heap: %d\n", getAverage(nativeHeap));
            out.printf("Average Dalvik Heap: %d\n", getAverage(dalvikHeap));
            out.printf("Average PSS: %d\n", getAverage(pss));
            out.printf("Peak Native Heap: %d\n", getMax(nativeHeap));
            out.printf("Peak Dalvik Heap: %d\n", getMax(dalvikHeap));
            out.printf("Peak PSS: %d\n", getMax(pss));
            out.printf("Count %d\n", nativeHeap.size());

            out.printf("Average Summary Java Heap: %d\n", getAverage(asJavaHeap));
            out.printf("Average Summary Native Heap: %d\n", getAverage(asNativeHeap));
            out.printf("Average Summary Code: %d\n", getAverage(asCode));
            out.printf("Average Summary Stack: %d\n", getAverage(asStack));
            out.printf("Average Summary Graphics: %d\n", getAverage(asGraphics));
            out.printf("Average Summary Other: %d\n", getAverage(asOther));
            out.printf("Average Summary System: %d\n", getAverage(asSystem));
            out.printf("Average Summary Overall Pss: %d\n", getAverage(asOverallPss));
        }

        out.close();
    }

    /**
     * NOTE (rsloan): I've meaningfully changed this format because the previous iteration was a
     *                horrific mix of CSV and not-CSV
     */
    static void saveCsv(Collection<MemHealthRecord> allRecords, String fileName) throws IOException{
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));

        out.printf("name,time,native_heap,dalvik_heap,pss,context\n");
        for (MemHealthRecord record : allRecords) {
            out.printf("%s,%d,%d,%d,%s\n",
                    record.mProcName, record.mTimeMs, record.mNativeHeap,
                    record.mDalvikHeap, record.mInForeground ? "foreground" : "background");
        }

        out.close();
    }

    /* Getters defined on a Collection<MemHealthRecord> */

    public static List<Long> getForegroundDalvikHeap(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mDalvikHeap);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundDalvikHeap(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mDalvikHeap);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundNativeHeap(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mNativeHeap);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundNativeHeap(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mNativeHeap);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundPss(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mPss);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundPss(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mPss);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundSummaryJavaHeap(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mAsJavaHeap);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundSummaryJavaHeap(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mAsJavaHeap);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundSummaryNativeHeap(
            Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mAsNativeHeap);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundSummaryNativeHeap(
            Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mAsNativeHeap);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundSummaryCode(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mAsCode);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundSummaryCode(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mAsCode);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundSummaryStack(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mAsStack);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundSummaryStack(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mAsStack);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundSummaryGraphics(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mAsGraphics);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundSummaryGraphics(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mAsGraphics);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundSummaryOther(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mAsOther);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundSummaryOther(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mAsOther);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundSummarySystem(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mAsSystem);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundSummarySystem(Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mAsSystem);
            }
        }
        return ret;
    }

    public static List<Long> getForegroundSummaryOverallPss(
            Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (sample.mInForeground) {
                ret.add(sample.mAsOverallPss);
            }
        }
        return ret;
    }

    public static List<Long> getBackgroundSummaryOverallPss(
            Collection<MemHealthRecord> samples) {
        List<Long> ret = new ArrayList<>(samples.size());
        for (MemHealthRecord sample : samples) {
            if (!sample.mInForeground) {
                ret.add(sample.mAsOverallPss);
            }
        }
        return ret;
    }

    /* Utility Methods */

    private static Long getMax(Collection<Long> samples) {
        Long max = null;
        for (Long sample : samples) {
            if (max == null || sample > max) {
                max = sample;
            }
        }
        return max;
    }

    private static Long getAverage(Collection<Long> samples) {
        if (samples.size() == 0) {
            return null;
        }

        double sum = 0;
        for (Long sample : samples) {
            sum += sample;
        }
        return (long) (sum / samples.size());
    }

    private static int parseMeminfoLine(String meminfo, String pattern)
    {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(meminfo);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        } else {
            return -1;
        }
    }

    public static String getMeminfoOutput(Instrumentation instr, String processName)
            throws IOException {
        return getProcessOutput(instr, "dumpsys meminfo " + processName);
    }

    public static String getProcessOutput(Instrumentation instr, String command)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FilesystemUtil.saveProcessOutput(instr, command, baos);
        baos.close();
        return baos.toString();
    }

    private static Map<String, List<MemHealthRecord>> getRecordMap(
            Collection<MemHealthRecord> allRecords,
            boolean inForeground) {

        Map<String, List<MemHealthRecord>> records = new HashMap<>();

        for (MemHealthRecord record : allRecords) {
            if (record.mInForeground == inForeground) {
                if (!records.containsKey(record.mProcName)) {
                    records.put(record.mProcName, new ArrayList<MemHealthRecord>());
                }

                records.get(record.mProcName).add(record);
            }
        }

        return records;
    }

}
