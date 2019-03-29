/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.PrintStreamPrinter;

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
import java.io.PrintStream;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PrintStreamPrinterTest {
    private File mFile;

    @Before
    public void setup() throws IOException {
        mFile = new File(InstrumentationRegistry.getTargetContext().getFilesDir(),
                "PrintStreamPrinter.log");
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
        new PrintStreamPrinter(new PrintStream(mFile));
    }

    @Test
    public void testPrintln() throws SecurityException, IOException {
        final String message = "testMessageOfPrintStreamPrinter";

        PrintStream ps = new PrintStream(mFile);
        PrintStreamPrinter printStreamPrinter = new PrintStreamPrinter(ps);
        printStreamPrinter.println(message);
        ps.flush();
        ps.close();
        String mLine;

        try (InputStream is = new FileInputStream(mFile)){
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            mLine = reader.readLine();
            assertEquals(message, mLine);
            reader.close();
        }
    }
}
