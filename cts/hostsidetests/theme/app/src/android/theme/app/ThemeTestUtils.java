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

package android.theme.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ThemeTestUtils {

    /**
     * Compresses the contents of a directory to a ZIP file.
     *
     * @param dir the directory to compress
     * @return {@code true} on success, {@code false} on failure
     */
    public static boolean compressDirectory(File dir, File file) throws IOException {
        if (dir == null || !dir.exists() || file == null || file.exists()) {
            return false;
        }

        final File[] srcFiles = dir.listFiles();
        if (srcFiles.length == 0) {
            return false;
        }

        final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(file));
        final byte[] data = new byte[8192];
        for (int i = 0; i < srcFiles.length; i++) {
            final FileInputStream fileIn = new FileInputStream(srcFiles[i]);
            final ZipEntry entry = new ZipEntry(srcFiles[i].getName());
            zipOut.putNextEntry(entry);

            int count;
            while ((count = fileIn.read(data, 0, data.length)) != -1) {
                zipOut.write(data, 0, count);
                zipOut.flush();
            }

            zipOut.closeEntry();
            fileIn.close();
        }

        zipOut.close();
        return true;
    }

    /**
     * Recursively deletes a directory and its contents.
     *
     * @param dir the directory to delete
     * @return {@code true} on success, {@code false} on failure
     */
    public static boolean deleteDirectory(File dir) {
        final File files[] = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteDirectory(file);
            }
        }
        return dir.delete();
    }
}
