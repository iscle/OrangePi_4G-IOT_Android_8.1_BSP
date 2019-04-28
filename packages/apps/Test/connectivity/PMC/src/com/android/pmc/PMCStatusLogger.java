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

package com.android.pmc;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


/**
 * Logging class to log status so PMC can communicate the status back to client
 */
public class PMCStatusLogger {
    private File mFile;
    public static String TAG;
    public static String LOG_DIR = "/mnt/sdcard/Download";
    public static JSONObject mJObject;
    public static JSONArray mJArray;

    /**
     * Construtor - check if the file exist. If it is delete and create a new.
     *
     * @param message - message to be logged
     */
    public PMCStatusLogger(String fileName, String tag) {
        TAG = tag;

        try {
            mFile = new File(LOG_DIR + "/" + fileName);
            if (mFile.exists()) mFile.delete();
            mFile.createNewFile();
        } catch (IOException e) {
            Log.e(TAG, "Exception creating log file: " + fileName + " " + e);
        }
        mJObject = new JSONObject();
        mJArray = new JSONArray();
    }

    /**
     * Function to log status message into log file
     *
     * @param message - message to be logged
     */
    public void logStatus(String message) {
        try {
            FileWriter fos = new FileWriter(mFile);
            BufferedWriter bw = new BufferedWriter(fos);
            bw.write(message);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception writing log: " + message + " " + e);
        }
    }

    /**
     * Function to add alarm times into JSONArray object
     *
     * @param startTime - Start time for the cycle
     * @param endTime - End time for the cycle
     */
    public void logAlarmTimes(double startTime, double endTime) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("StartTime", startTime);
            obj.put("EndTime", endTime);
            mJArray.put(obj);
        } catch (JSONException e) {
            Log.e(TAG, "Exception to put Alarm Times into JSONArray: " + e);
        }
    }

    /**
     * Function to save Json object into log file
     *
     */
    public void flash() {
        try {
            mJObject.put("AlarmTimes", mJArray);

            FileWriter fos = new FileWriter(mFile);
            BufferedWriter bw = new BufferedWriter(fos);
            Log.v(TAG, "JSON: " + mJObject.toString());
            bw.write(mJObject.toString());
            bw.newLine();
            bw.close();
        } catch (JSONException e) {
            Log.e(TAG, "Exception to put JSONArray into main JSON object: " + e);
        } catch (IOException e) {
            Log.e(TAG, "Exception writing JSON to log file: " + e);
        }
    }

}

