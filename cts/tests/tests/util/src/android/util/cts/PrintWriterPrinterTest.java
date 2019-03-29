/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.util.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.PrintWriterPrinter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PrintWriterPrinterTest {
    private File mFile;

    @Before
    public void setup() throws IOException {
        File dbDir = InstrumentationRegistry.getTargetContext().getDir("tests",
                Context.MODE_PRIVATE);
        mFile = new File(dbDir,"print.log");
        if (!mFile.exists()) {
            mFile.createNewFile();
        }
    }

    @After
    public void teardown() throws Exception {
        if (mFile.exists()) {
            mFile.delete();
        }
    }

    @Test
    public void testConstructor() throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(mFile);
        new PrintWriterPrinter(pw);
    }

    @Test
    public void testPrintln() throws FileNotFoundException {
        String mMessage = "testMessage";
        PrintWriter pw = new PrintWriter(mFile);
        PrintWriterPrinter printWriterPrinter = new PrintWriterPrinter(pw);
        printWriterPrinter.println(mMessage);
        pw.flush();
        pw.close();
        String mLine = "";
        try {
            InputStream is = new FileInputStream(mFile);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is));
            mLine = reader.readLine();
        } catch (Exception e) {
        }
        assertEquals(mMessage, mLine);
    }
}

