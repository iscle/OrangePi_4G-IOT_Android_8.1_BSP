/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.mediastress.cts;

import android.os.Bundle;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;

import java.io.File;

import junit.framework.Assert;

public class WorkDir {

    private static final String MEDIA_PATH_INSTR_ARG_KEY = "media-path";

    static final File getTopDir() {
        Assert.assertEquals(Environment.getExternalStorageState(), Environment.MEDIA_MOUNTED);
        return Environment.getExternalStorageDirectory();
    }

    static final String getTopDirString() {
        return (getTopDir().getAbsolutePath() + File.separator);
    }

    static final String getMediaDirString() {
        Bundle bundle = InstrumentationRegistry.getArguments();
        String mediaDirString = bundle.getString(MEDIA_PATH_INSTR_ARG_KEY);
        if (mediaDirString != null) {
            // user has specified the mediaDirString via instrumentation-arg
            return mediaDirString + ((mediaDirString.endsWith("/")) ? "" : "/");
        } else {
            return (getTopDirString() + "test/");
        }
    }
}
