/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.uirendering.cts.util;

import android.graphics.Bitmap;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.uirendering.cts.differencevisualizers.DifferenceVisualizer;
import android.util.Log;

import com.android.compatibility.common.util.BitmapUtils;

import java.io.File;

/**
 * A utility class that will allow the user to save bitmaps to the sdcard on the device.
 */
public final class BitmapDumper {
    private final static String TAG = "BitmapDumper";
    private final static String IDEAL_RENDERING_FILE_NAME = "idealCapture.png";
    private final static String TESTED_RENDERING_FILE_NAME = "testedCapture.png";
    private final static String VISUALIZER_RENDERING_FILE_NAME = "visualizer.png";
    private final static String SINGULAR_FILE_NAME = "capture.png";
    private final static String CAPTURE_SUB_DIRECTORY = Environment.getExternalStorageDirectory()
            + "/UiRenderingCaptures/";

    private BitmapDumper() {}

    /**
     * Deletes the specific files for the given test in a given class.
     */
    public static void deleteFileInClassFolder(String className, String testName) {
        File directory = new File(CAPTURE_SUB_DIRECTORY + className);

        String[] children = directory.list();
        if (children == null) {
            return;
        }
        for (String file : children) {
            if (file.startsWith(testName)) {
                new File(directory, file).delete();
            }
        }
    }

    public static void createSubDirectory(String className) {
        File saveDirectory = new File(CAPTURE_SUB_DIRECTORY + className);
        if (saveDirectory.exists()) {
            return;
        }
        // Create the directory if it isn't already created.
        saveDirectory.mkdirs();
    }

    /**
     * Saves two files, one the capture of an ideal drawing, and one the capture of the tested
     * drawing. The third file saved is a bitmap that is returned from the given visualizer's
     * method.
     * The files are saved to the sdcard directory
     */
    public static void dumpBitmaps(Bitmap idealBitmap, Bitmap testedBitmap, String testName,
            String className, DifferenceVisualizer differenceVisualizer) {
        Bitmap visualizerBitmap;

        int width = idealBitmap.getWidth();
        int height = idealBitmap.getHeight();
        int[] testedArray = new int[width * height];
        int[] idealArray = new int[width * height];
        idealBitmap.getPixels(testedArray, 0, width, 0, 0, width, height);
        testedBitmap.getPixels(idealArray, 0, width, 0, 0, width, height);
        int[] visualizerArray = differenceVisualizer.getDifferences(idealArray, testedArray);
        visualizerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        visualizerBitmap.setPixels(visualizerArray, 0, width, 0, 0, width, height);
        Bitmap croppedBitmap = Bitmap.createBitmap(testedBitmap, 0, 0, width, height);

        saveFile(className, testName, IDEAL_RENDERING_FILE_NAME, idealBitmap);
        saveFile(className, testName, TESTED_RENDERING_FILE_NAME, croppedBitmap);
        saveFile(className, testName, VISUALIZER_RENDERING_FILE_NAME, visualizerBitmap);
    }

    public static void dumpBitmap(Bitmap bitmap, String testName, String className) {
        if (bitmap == null) {
            Log.d(TAG, "File not saved, bitmap was null for test : " + testName);
            return;
        }
        saveFile(className, testName, SINGULAR_FILE_NAME, bitmap);
    }

    private static void saveFile(String className, String testName, String fileName, Bitmap bitmap) {
        BitmapUtils.saveBitmap(bitmap, CAPTURE_SUB_DIRECTORY + className,
                testName + "_" + fileName);
    }
}
