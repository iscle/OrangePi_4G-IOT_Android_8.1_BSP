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

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FilesystemUtil {
    private static final String TAG = FilesystemUtil.class.getSimpleName();

    /** Save the output of a process to a file */
    public static void saveProcessOutput(Instrumentation instr, String command, File file)
            throws IOException {
        Log.d(TAG, String.format("Saving command \"%s\" output into file %s",
                command, file.getAbsolutePath()));

        OutputStream out = new FileOutputStream(file);
        saveProcessOutput(instr, command, out);
        out.close();
    }

    /** Send the output of a process to an OutputStream. */
    public static void saveProcessOutput(Instrumentation instr, String command, OutputStream out)
            throws IOException {
        try {
            // First, try to execute via our UiAutomation
            ParcelFileDescriptor pfd = instr.getUiAutomation().executeShellCommand(command);
            pipe(new ParcelFileDescriptor.AutoCloseInputStream(pfd), out);
        } catch (IllegalStateException ise) {
            // If we don't have a UiAutomation, we'll get an IllegalStatException;
            // so try to do it via an exec()
            Process process = Runtime.getRuntime().exec(command);
            pipe(process.getInputStream(), out);

            // Wait for our process to finish
            try {
                process.waitFor();
            } catch (InterruptedException ie) {
                throw new IOException("Thread interrupted waiting for command: " + command);
            }

            // Make sure it succeeded.
            if (process.exitValue() != 0) {
                throw new IOException("Failed to save output of command: " + command);
            }
        }
    }

    /** Save a bugreport to the given file */
    public static void saveBugreport(Instrumentation instr, String filename)
            throws IOException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String cmdline = String.format("/system/bin/sh -c /system/bin/bugreport>%s",
                templateToFilename(filename));
        saveProcessOutput(instr, cmdline, baos);
        baos.close();
    }

    /** Save a bugreport to the given file */
    public static void saveBugreportz(Instrumentation instr) throws IOException {
        try {
            ActivityManager.getService().requestBugReport(ActivityManager.BUGREPORT_OPTION_FULL);
        } catch (RemoteException e) {
            throw new IOException("Could not capture bugreportz", e);
        }
    }

    /** Save annotated Meminfo to our default logging directory */
    public static void dumpMeminfo(Instrumentation instr, String notes) {
        long epochSeconds = System.currentTimeMillis() / 1000;
        File outputDir = new File(Environment.getExternalStorageDirectory(), "meminfo");
        Log.i(TAG, outputDir.toString());
        if (!outputDir.exists()) {
            boolean yes  = outputDir.mkdirs();
            Log.i(TAG, yes ? "created" : "not created");
        }
        File outputFile = new File(outputDir, String.format("%d.txt", epochSeconds));
        Log.i(TAG, outputFile.toString());
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(outputFile);
            fos.write(String.format("notes: %s\n\n", notes).getBytes());

            saveProcessOutput(instr, "dumpsys meminfo -c", fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "exception while dumping meminfo", e);
        } catch (IOException e) {
            Log.e(TAG, "exception while dumping meminfo", e);
        }
    }

    /** Splice the date into the "%s" in a file name */
    public static String templateToFilename(String filenameTemplate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        return String.format(filenameTemplate, sdf.format(new Date()));
    }

    /** Pipe an inputstream to an outputstream. This matches Apache's IOUtils::copy */
    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead = 0;

        try {
            while (bytesRead >= 0) {
                out.write(buffer, 0, bytesRead);
                bytesRead = in.read(buffer);
            }
        } finally {
            in.close();
            out.flush();
        }
    }
}
