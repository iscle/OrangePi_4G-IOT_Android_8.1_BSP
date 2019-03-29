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

package com.android.server.cts.storaged;

import android.app.Activity;
import android.os.Bundle;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SimpleIOActivity extends Activity {
    @Override
    public void onStart() {
        super.onStart();

        File testFile = new File(getFilesDir(), "StoragedTest_Temp_FG");
        try {
            char data[] = new char[4096];
            FileWriter w = new FileWriter(testFile);
            w.write(data);
            w.flush();
            w.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            finish();
        }
    }
}
