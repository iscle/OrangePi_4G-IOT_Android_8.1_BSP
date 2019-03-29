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

package com.android.cts.verifier.camera.its;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Test for Camera features that require that the camera be aimed at a specific test scene.
 * This test activity requires a USB connection to a computer, and a corresponding host-side run of
 * the python scripts found in the CameraITS directory.
 */
public class ItsTestActivity extends DialogTestListActivity {
    private static final String TAG = "ItsTestActivity";
    private static final String EXTRA_CAMERA_ID = "camera.its.extra.CAMERA_ID";
    private static final String EXTRA_RESULTS = "camera.its.extra.RESULTS";
    private static final String EXTRA_VERSION = "camera.its.extra.VERSION";
    private static final String CURRENT_VERSION = "1.0";
    private static final String ACTION_ITS_RESULT =
            "com.android.cts.verifier.camera.its.ACTION_ITS_RESULT";

    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String RESULT_NOT_EXECUTED = "NOT_EXECUTED";
    private static final Set<String> RESULT_VALUES = new HashSet<String>(
            Arrays.asList(new String[] {RESULT_PASS, RESULT_FAIL, RESULT_NOT_EXECUTED}));
    private static final int MAX_SUMMARY_LEN = 200;

    private final ResultReceiver mResultsReceiver = new ResultReceiver();

    // Initialized in onCreate
    ArrayList<String> mNonLegacyCameraIds = null;

    // Scenes
    private static final ArrayList<String> mSceneIds = new ArrayList<String> () { {
            add("scene0");
            add("scene1");
            add("scene2");
            add("scene3");
            add("scene4");
            add("scene5");
            add("sensor_fusion");
        } };

    // TODO: cache the following in saved bundle
    private Set<ResultKey> mAllScenes = null;
    // (camera, scene) -> (pass, fail)
    private final HashMap<ResultKey, Boolean> mExecutedScenes = new HashMap<>();
    // map camera id to ITS summary report path
    private final HashMap<ResultKey, String> mSummaryMap = new HashMap<>();

    final class ResultKey {
        public final String cameraId;
        public final String sceneId;

        public ResultKey(String cameraId, String sceneId) {
            this.cameraId = cameraId;
            this.sceneId = sceneId;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) return false;
            if (this == o) return true;
            if (o instanceof ResultKey) {
                final ResultKey other = (ResultKey) o;
                return cameraId.equals(other.cameraId) && sceneId.equals(other.sceneId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h = cameraId.hashCode();
            h = ((h << 5) - h) ^ sceneId.hashCode();
            return h;
        }
    }

    public ItsTestActivity() {
        super(R.layout.its_main,
                R.string.camera_its_test,
                R.string.camera_its_test_info,
                R.string.camera_its_test);
    }

    private final Comparator<ResultKey> mComparator = new Comparator<ResultKey>() {
        @Override
        public int compare(ResultKey k1, ResultKey k2) {
            if (k1.cameraId.equals(k2.cameraId))
                return k1.sceneId.compareTo(k2.sceneId);
            return k1.cameraId.compareTo(k2.cameraId);
        }
    };

    class ResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received result for Camera ITS tests");
            if (ACTION_ITS_RESULT.equals(intent.getAction())) {
                String version = intent.getStringExtra(EXTRA_VERSION);
                if (version == null || !version.equals(CURRENT_VERSION)) {
                    Log.e(TAG, "Its result version mismatch: expect " + CURRENT_VERSION +
                            ", got " + ((version == null) ? "null" : version));
                    ItsTestActivity.this.showToast(R.string.its_version_mismatch);
                    return;
                }

                String cameraId = intent.getStringExtra(EXTRA_CAMERA_ID);
                String results = intent.getStringExtra(EXTRA_RESULTS);
                if (cameraId == null || results == null) {
                    Log.e(TAG, "cameraId = " + ((cameraId == null) ? "null" : cameraId) +
                            ", results = " + ((results == null) ? "null" : results));
                    return;
                }

                if (!mNonLegacyCameraIds.contains(cameraId)) {
                    Log.e(TAG, "Unknown camera id " + cameraId + " reported to ITS");
                    return;
                }

                try {
                    /* Sample JSON results string
                    {
                       "scene0":{
                          "result":"PASS",
                          "summary":"/sdcard/cam0_scene0.txt"
                       },
                       "scene1":{
                          "result":"NOT_EXECUTED"
                       },
                       "scene2":{
                          "result":"FAIL",
                          "summary":"/sdcard/cam0_scene2.txt"
                       }
                    }
                    */
                    JSONObject jsonResults = new JSONObject(results);
                    Set<String> scenes = new HashSet<>();
                    Iterator<String> keys = jsonResults.keys();
                    while (keys.hasNext()) {
                        scenes.add(keys.next());
                    }
                    boolean newScenes = false;
                    if (mAllScenes == null) {
                        mAllScenes = new TreeSet<>(mComparator);
                        newScenes = true;
                    } else { // See if scene lists changed
                        for (String scene : scenes) {
                            if (!mAllScenes.contains(new ResultKey(cameraId, scene))) {
                                // Scene list changed. Cleanup previous test results
                                newScenes = true;
                                break;
                            }
                        }
                        for (ResultKey k : mAllScenes) {
                            if (!scenes.contains(k.sceneId)) {
                                newScenes = true;
                                break;
                            }
                        }
                    }
                    if (newScenes) {
                        mExecutedScenes.clear();
                        mAllScenes.clear();
                        for (String scene : scenes) {
                            for (String c : mNonLegacyCameraIds) {
                                mAllScenes.add(new ResultKey(c, scene));
                            }
                        }
                    }

                    // Update test execution results
                    for (String scene : scenes) {
                        JSONObject sceneResult = jsonResults.getJSONObject(scene);
                        String result = sceneResult.getString("result");
                        if (result == null) {
                            Log.e(TAG, "Result for " + scene + " is null");
                            return;
                        }
                        Log.i(TAG, "ITS camera" + cameraId + " " + scene + ": result:" + result);
                        if (!RESULT_VALUES.contains(result)) {
                            Log.e(TAG, "Unknown result for " + scene + ": " + result);
                            return;
                        }
                        ResultKey key = new ResultKey(cameraId, scene);
                        if (result.equals(RESULT_PASS) || result.equals(RESULT_FAIL)) {
                            boolean pass = result.equals(RESULT_PASS);
                            mExecutedScenes.put(key, pass);
                            setTestResult(testId(cameraId, scene), pass ?
                                    TestResult.TEST_RESULT_PASSED : TestResult.TEST_RESULT_FAILED);
                            Log.e(TAG, "setTestResult for " + testId(cameraId, scene) + ": " + result);
                            String summary = sceneResult.optString("summary");
                            if (!summary.equals("")) {
                                mSummaryMap.put(key, summary);
                            }
                        } // do nothing for NOT_EXECUTED scenes
                    }
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "Error reading json result string:" + results , e);
                    return;
                }

                // Set summary if all scenes reported
                if (mSummaryMap.keySet().containsAll(mAllScenes)) {
                    StringBuilder summary = new StringBuilder();
                    for (String path : mSummaryMap.values()) {
                        appendFileContentToSummary(summary, path);
                    }
                    if (summary.length() > MAX_SUMMARY_LEN) {
                        Log.w(TAG, "ITS summary report too long: len: " + summary.length());
                    }
                    ItsTestActivity.this.getReportLog().setSummary(
                            summary.toString(), 1.0, ResultType.NEUTRAL, ResultUnit.NONE);
                }

                // Display current progress
                StringBuilder progress = new StringBuilder();
                for (ResultKey k : mAllScenes) {
                    String status = RESULT_NOT_EXECUTED;
                    if (mExecutedScenes.containsKey(k)) {
                        status = mExecutedScenes.get(k) ? RESULT_PASS : RESULT_FAIL;
                    }
                    progress.append(String.format("Cam %s, %s: %s\n",
                            k.cameraId, k.sceneId, status));
                }
                TextView progressView = (TextView) findViewById(R.id.its_progress);
                progressView.setMovementMethod(new ScrollingMovementMethod());
                progressView.setText(progress.toString());


                // Enable pass button if all scenes pass
                boolean allScenesPassed = true;
                for (ResultKey k : mAllScenes) {
                    Boolean pass = mExecutedScenes.get(k);
                    if (pass == null || pass == false) {
                        allScenesPassed = false;
                        break;
                    }
                }
                if (allScenesPassed) {
                    // Enable pass button
                    ItsTestActivity.this.showToast(R.string.its_test_passed);
                    ItsTestActivity.this.getPassButton().setEnabled(true);
                    ItsTestActivity.this.setTestResultAndFinish(true);
                } else {
                    ItsTestActivity.this.getPassButton().setEnabled(false);
                }
            }
        }

        private void appendFileContentToSummary(StringBuilder summary, String path) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(path));
                String line = null;
                do {
                    line = reader.readLine();
                    if (line != null) {
                        summary.append(line);
                    }
                } while (line != null);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Cannot find ITS summary file at " + path);
                summary.append("Cannot find ITS summary file at " + path);
            } catch (IOException e) {
                Log.e(TAG, "IO exception when trying to read " + path);
                summary.append("IO exception when trying to read " + path);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Hide the test if all camera devices are legacy
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            mNonLegacyCameraIds = new ArrayList<String>();
            boolean allCamerasAreLegacy = true;
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                if (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                        != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    mNonLegacyCameraIds.add(id);
                    allCamerasAreLegacy = false;
                }
            }
            if (allCamerasAreLegacy) {
                showToast(R.string.all_legacy_devices);
                ItsTestActivity.this.getReportLog().setSummary(
                        "PASS: all cameras on this device are LEGACY"
                        , 1.0, ResultType.NEUTRAL, ResultUnit.NONE);
                setTestResultAndFinish(true);
            }
        } catch (CameraAccessException e) {
            Toast.makeText(ItsTestActivity.this,
                    "Received error from camera service while checking device capabilities: "
                            + e, Toast.LENGTH_SHORT).show();
        }

        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void showManualTestDialog(final DialogTestListItem test,
            final DialogTestListItem.TestCallback callback) {
        //Nothing todo for ITS
    }

    protected String testTitle(String cam, String scene) {
        return "Camera: " + cam + ", " + scene;
    }

    protected String testId(String cam, String scene) {
        return "Camera_ITS_" + cam + "_" + scene;
    }

    protected void setupItsTests(ArrayTestListAdapter adapter) {
        for (String cam : mNonLegacyCameraIds) {
            for (String scene : mSceneIds) {
                adapter.add(new DialogTestListItem(this,
                testTitle(cam, scene),
                testId(cam, scene)));
            }
        }
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        setupItsTests(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            showToast(R.string.no_camera_manager);
        } else {
            Log.d(TAG, "register ITS result receiver");
            IntentFilter filter = new IntentFilter(ACTION_ITS_RESULT);
            registerReceiver(mResultsReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "unregister ITS result receiver");
        unregisterReceiver(mResultsReceiver);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.its_main);
        setInfoResources(R.string.camera_its_test, R.string.camera_its_test_info, -1);
        setPassFailButtonClickListeners();
    }
}
