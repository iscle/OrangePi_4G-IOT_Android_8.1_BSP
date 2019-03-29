/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.os.cts;

import android.system.OsConstants;
import android.system.ErrnoException;
import android.util.ArraySet;

import com.android.compatibility.common.util.ReadElf;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class AbiTest extends TestCase {
    public void testNo64() throws Exception {
        ArraySet<String> abiDirs = new ArraySet(Arrays.asList(
            "/sbin",
            "/system",
            "/vendor"));
        String pathVar = System.getenv("PATH");
        if (pathVar != null) {
            abiDirs.addAll(Arrays.asList(pathVar.split(":")));
        }
        for (String dir : abiDirs) {
            boolean skip_dir = false;
            for (String dirOther : abiDirs) {
                if (dir.equals(dirOther)) {
                    continue;
                } else if (dir.startsWith(dirOther + "/")) {
                    skip_dir = true;
                    break;
                }
            }
            if (!skip_dir) {
                checkElfFilesInDirectory(new File(dir));
            }
        }
    }

    private void checkElfFilesInDirectory(File dir) throws Exception {
        if (!dir.isDirectory()) {
            return;
        }

        if (isSymbolicLink(dir)) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            if (f.isDirectory()) {
                checkElfFilesInDirectory(f);
            } else if (f.getName().endsWith(".so") || f.canExecute()) {
                ReadElf elf = null;
                try { // TODO: switch to try-with-resources.
                    elf = ReadElf.read(f);
                } catch (IllegalArgumentException ignored) {
                    // If it's not actually an ELF file, we don't care.
                } catch (IOException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof ErrnoException) {
                        // if we are denied access to the file, ignore.
                        if (((ErrnoException) cause).errno != OsConstants.EACCES) {
                            throw ex;
                        }
                    }
                } finally {
                    if (elf != null) {
                        elf.close();
                    }
                }
            }
        }
    }

    private static boolean isSymbolicLink(File f) throws Exception {
        return !f.getAbsolutePath().equals(f.getCanonicalPath());
    }
}
