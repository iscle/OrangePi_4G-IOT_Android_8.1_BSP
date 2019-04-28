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
import android.util.Log;

import java.io.File;
import java.io.IOException;

enum LogGenerator {
    BUGREPORT(new BugreportGenerator()),
    BUGREPORTZ(new BugreportzGenerator()),
    GRAPHICS_STATS(new GraphicsGenerator()),
    MEM_INFO(new CompactMemInfoGenerator()),
    CPU_INFO(new CpuInfoGenerator()),
    FRAGMENTATION(new FragmentationGenerator()),
    ION_HEAP(new IonHeapGenerator()),
    PAGETYPE_INFO(new PageTypeInfoGenerator()),
    TRACE(new TraceGenerator());

    private static final String TAG = "AuptDataCollector";

    /** Save the output of a process to a log file with the given name template. */
    private static void saveLog(
            Instrumentation instr,
            String command,
            String template) throws IOException {
        FilesystemUtil.saveProcessOutput(
            instr,
            command,
            new File(FilesystemUtil.templateToFilename(template)));
    }

    /* Generator Types */

    protected interface Generator {
        void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException;
    }

    private static class CompactMemInfoGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                saveLog(instr, "dumpsys meminfo -c -S", logDir + "/compact-meminfo-%s.txt");
            } catch (IOException ioe) {
                Log.w(TAG, "Error while saving dumpsys meminfo -c: " + ioe.getMessage());
            }
        }
    }

    private static class CpuInfoGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                saveLog(instr, "dumpsys cpuinfo", logDir + "/cpuinfo-%s.txt");
            } catch (IOException ioe) {
                Log.w(TAG, "Error while saving dumpsys cpuinfo : " + ioe.getMessage());
            }
        }
    }

    private static class BugreportGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                FilesystemUtil.saveBugreport(instr, logDir + "/bugreport-%s.txt");
            } catch (IOException e) {
                Log.w(TAG, String.format("Failed to take bugreport: %s", e.getMessage()));
            }
        }
    }

    private static class BugreportzGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                FilesystemUtil.saveBugreportz(instr);
            } catch (IOException e) {
                Log.w(TAG, String.format("Failed to take bugreport: %s", e.getMessage()));
            }
        }
    }

    private static class FragmentationGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                saveLog(instr, "cat /d/extfrag/unusable_index", logDir + "/unusable-index-%s.txt");
            } catch (IOException e) {
                Log.w(TAG, String.format("Failed to save frangmentation: %s", e.getMessage()));
            }
        }
    }

    private static class GraphicsGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                saveLog(instr, "dumpsys graphicsstats", logDir + "/graphics-%s.txt");
            } catch (IOException e) {
                Log.w(TAG, String.format("Failed to save graphicsstats: %s", e.getMessage()));
            }
        }
    }

    private static class IonHeapGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                saveLog(instr, "cat /d/ion/heaps/audio", logDir + "/ion-audio-%s.txt");
                saveLog(instr, "cat /d/ion/heaps/system", logDir + "/ion-system-%s.txt");
            } catch (IOException e) {
                Log.w(TAG, String.format("Failed to save ION heap: %s", e.getMessage()));
            }
        }
    }

    private static class PageTypeInfoGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                saveLog(instr, "cat /proc/pagetypeinfo", logDir + "/pagetypeinfo-%s.txt");
            } catch (IOException e) {
                Log.w(TAG, String.format("Failed to save pagetypeinfo: %s", e.getMessage()));
            }
        }
    }

    private static class TraceGenerator implements Generator {
        @Override
        public void save(Instrumentation instr, String logDir)
                throws IOException, InterruptedException {
            try {
                saveLog(instr, "cat /sys/kernel/debug/tracing/trace", logDir + "/trace-%s.txt");
            } catch (IOException e) {
                Log.w(TAG, String.format("Failed to save trace: %s", e.getMessage()));
            }
        }
    }

    // Individual LogGenerator instance methods
    private final Generator mGenerator;

    LogGenerator (Generator generator) {
        mGenerator = generator;
    }

    public void save(Instrumentation instr, String logDir)
            throws IOException, InterruptedException {
        mGenerator.save(instr, logDir);
    }
}
