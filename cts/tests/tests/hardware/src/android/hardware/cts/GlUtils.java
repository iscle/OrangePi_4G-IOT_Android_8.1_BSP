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

package android.hardware.cts;

import android.opengl.GLES20;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GlUtils {
    private GlUtils() {
    }

    static int getMajorVersion() {
        // Section 6.1.5 of the OpenGL ES specification indicates the GL version
        // string strictly follows this format:
        //
        // OpenGL<space>ES<space><version number><space><vendor-specific information>
        //
        // In addition section 6.1.5 describes the version number in the following manner:
        //
        // "The version number is either of the form major number.minor number or
        // major number.minor number.release number, where the numbers all have one
        // or more digits. The release number and vendor specific information are
        // optional."
        String version = GLES20.glGetString(GLES20.GL_VERSION);
        Pattern pattern = Pattern.compile("OpenGL ES ([0-9]+)\\.([0-9]+)");
        Matcher matcher = pattern.matcher(version);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 2;
    }

    static String[] getExtensions() {
        return GLES20.glGetString(GLES20.GL_EXTENSIONS).split(" ");
    }

    static boolean hasExtensions(String... extensions) {
        String[] available = getExtensions();
        Arrays.sort(available);
        for (String extension : extensions) {
            if (Arrays.binarySearch(available, extension) < 0) return false;
        }
        return true;
    }
}
