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
package com.android.cts.verifier.camera.intents;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.cts.verifier.camera.intents.CameraContentJobService;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

import java.util.TreeSet;

/**
 * Tests for manual verification of uri trigger being fired.
 *
 * MediaStore.Images.Media.EXTERNAL_CONTENT_URI - this should fire
 *  when a new picture was captured by the camera app, and it has been
 *  added to the media store.
 * MediaStore.Video.Media.EXTERNAL_CONTENT_URI - this should fire when a new
 *  video has been captured by the camera app, and it has been added
 *  to the media store.
 *
 * The tests verify this both by asking the user to manually launch
 *  the camera activity, as well as by programatically launching the camera
 *  activity via MediaStore intents.
 *
 * Please ensure when replacing the default camera app on a device,
 *  that these intents are still firing as a lot of 3rd party applications
 *  (e.g. social network apps that upload a photo after you take a picture)
 *  rely on this functionality present and correctly working.
 */
public class CameraIntentsActivity extends PassFailButtons.Activity
implements OnClickListener, SurfaceHolder.Callback {

    private static final String TAG = "CameraIntents";
    private static final int STATE_OFF = 0;
    private static final int STATE_STARTED = 1;
    private static final int STATE_SUCCESSFUL = 2;
    private static final int STATE_FAILED = 3;
    private static final int NUM_STAGES = 4;
    private static final String STAGE_INDEX_EXTRA = "stageIndex";

    private static final int STAGE_APP_PICTURE = 0;
    private static final int STAGE_APP_VIDEO = 1;
    private static final int STAGE_INTENT_PICTURE = 2;
    private static final int STAGE_INTENT_VIDEO = 3;

    private ImageButton mPassButton;
    private ImageButton mFailButton;
    private Button mStartTestButton;

    private int mState = STATE_OFF;

    private boolean mActivityResult = false;
    private boolean mDetectCheating = false;

    private StringBuilder mReportBuilder = new StringBuilder();
    private final TreeSet<String> mTestedCombinations = new TreeSet<String>();
    private final TreeSet<String> mUntestedCombinations = new TreeSet<String>();

    private CameraContentJobService.TestEnvironment mTestEnv;
    private static final int CAMERA_JOB_ID = CameraIntentsActivity.class.hashCode();
    private static final int JOB_TYPE_IMAGE = 0;
    private static final int JOB_TYPE_VIDEO = 1;

    private static int[] TEST_JOB_TYPES = new int[] {
        JOB_TYPE_IMAGE,
        JOB_TYPE_VIDEO,
        JOB_TYPE_IMAGE,
        JOB_TYPE_VIDEO
    };

    private JobInfo makeJobInfo(int jobType) {
        JobInfo.Builder builder = new JobInfo.Builder(CAMERA_JOB_ID,
                new ComponentName(this, CameraContentJobService.class));
        // Look for specific changes to images in the provider.
        Uri uriToTrigger = null;
        switch (jobType) {
            case JOB_TYPE_IMAGE:
                uriToTrigger = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                break;
            case JOB_TYPE_VIDEO:
                uriToTrigger = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                break;
            default:
                Log.e(TAG, "Unknown jobType" + jobType);
                return null;
        }
        builder.addTriggerContentUri(new JobInfo.TriggerContentUri(
                uriToTrigger,
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
        // For testing purposes, react quickly.
        builder.setTriggerContentUpdateDelay(100);
        builder.setTriggerContentMaxDelay(100);
        return builder.build();
    }

    private int getStageIndex()
    {
        final int stageIndex = getIntent().getIntExtra(STAGE_INDEX_EXTRA, 0);
        return stageIndex;
    }

    private String getStageString(int stageIndex)
    {
        if (stageIndex == STAGE_APP_PICTURE) {
            return "Application Picture";
        }
        if (stageIndex == STAGE_APP_VIDEO) {
            return "Application Video";
        }
        if (stageIndex == STAGE_INTENT_PICTURE) {
            return "Intent Picture";
        }
        if (stageIndex == STAGE_INTENT_VIDEO) {
            return "Intent Video";
        }

        return "Unknown!!!";
    }

    private String getStageIntentString(int stageIndex)
    {
        if (stageIndex == STAGE_APP_PICTURE) {
            return android.hardware.Camera.ACTION_NEW_PICTURE;
        }
        if (stageIndex == STAGE_APP_VIDEO) {
            return android.hardware.Camera.ACTION_NEW_VIDEO;
        }
        if (stageIndex == STAGE_INTENT_PICTURE) {
            return android.hardware.Camera.ACTION_NEW_PICTURE;
        }
        if (stageIndex == STAGE_INTENT_VIDEO) {
            return android.hardware.Camera.ACTION_NEW_VIDEO;
        }

        return "Unknown Intent!!!";
    }

    private String getStageInstructionLabel(int stageIndex)
    {
        if (stageIndex == STAGE_APP_PICTURE) {
            return getString(R.string.ci_instruction_text_app_picture_label);
        }
        if (stageIndex == STAGE_APP_VIDEO) {
            return getString(R.string.ci_instruction_text_app_video_label);
        }
        if (stageIndex == STAGE_INTENT_PICTURE) {
            return getString(R.string.ci_instruction_text_intent_picture_label);
        }
        if (stageIndex == STAGE_INTENT_VIDEO) {
            return getString(R.string.ci_instruction_text_intent_video_label);
        }

        return "Unknown Instruction Label!!!";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ci_main);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.camera_intents, R.string.ci_info, -1);

        mPassButton         = (ImageButton) findViewById(R.id.pass_button);
        mFailButton         = (ImageButton) findViewById(R.id.fail_button);
        mStartTestButton  = (Button) findViewById(R.id.start_test_button);
        mStartTestButton.setOnClickListener(this);

        // This activity is reused multiple times
        // to test each camera/intents combination
        final int stageIndex = getIntent().getIntExtra(STAGE_INDEX_EXTRA, 0);

        // Hitting the pass button goes to the next test activity.
        // Only the last one uses the PassFailButtons click callback function,
        // which gracefully terminates the activity.
        if (stageIndex + 1 < NUM_STAGES) {
            setPassButtonGoesToNextStage(stageIndex);
        }
        resetButtons();

        // Set initial values

        TextView intentsLabel =
                (TextView) findViewById(R.id.intents_text);
        intentsLabel.setText(
                getString(R.string.ci_intents_label)
                + " "
                + Integer.toString(getStageIndex()+1)
                + " of "
                + Integer.toString(NUM_STAGES)
                + ": "
                + getStageIntentString(getStageIndex())
                );

        TextView instructionLabel =
                (TextView) findViewById(R.id.instruction_text);
        instructionLabel.setText(R.string.ci_instruction_text_photo_label);

        /* Display the instructions to launch camera app and take a photo */
        TextView cameraExtraLabel =
                (TextView) findViewById(R.id.instruction_extra_text);
        cameraExtraLabel.setText(getStageInstructionLabel(getStageIndex()));

        mStartTestButton.setEnabled(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        /*
        When testing INTENT_PICTURE, INTENT_VIDEO,
        do not allow user to cheat by going to camera app and re-firing
        the intents by taking a photo/video
        */
        if (getStageIndex() == STAGE_INTENT_PICTURE ||
            getStageIndex() == STAGE_INTENT_VIDEO) {

            if (mActivityResult && mState == STATE_STARTED) {
                mDetectCheating = true;
                Log.w(TAG, "Potential cheating detected");
            }
        }

    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data) {
        if (requestCode == 1337 + getStageIndex()) {
            Log.v(TAG, "Activity we launched was finished");
            mActivityResult = true;

            if (mState != STATE_FAILED
                && getStageIndex() == STAGE_INTENT_PICTURE) {
                mPassButton.setEnabled(true);
                mFailButton.setEnabled(false);

                mState = STATE_SUCCESSFUL;
                /* successful, unless we get the URI trigger back
                 at some point later on */
            }
        }
    }

    @Override
    public String getTestDetails() {
        return mReportBuilder.toString();
    }

    private class WaitForTriggerTask extends AsyncTask<Void, Void, Boolean> {
        protected Boolean doInBackground(Void... param) {
            try {
                boolean executed = mTestEnv.awaitExecution();
                // Check latest test param
                if (executed && mState == STATE_STARTED) {

                    // this can happen if..
                    //  the camera apps intent finishes,
                    //  user returns to cts verifier,
                    //  user leaves cts verifier and tries to fake receiver intents
                    if (mDetectCheating) {
                        Log.w(TAG, "Cheating attempt suppressed");
                        mState = STATE_FAILED;
                    }

                    // For STAGE_INTENT_PICTURE test, if EXTRA_OUTPUT is not assigned in intent,
                    // file should NOT be saved so triggering this is a test failure.
                    if (getStageIndex() == STAGE_INTENT_PICTURE) {
                        Log.e(TAG, "FAIL: STAGE_INTENT_PICTURE test should not create file");
                        mState = STATE_FAILED;
                    }

                    if (mState != STATE_FAILED) {
                        mState = STATE_SUCCESSFUL;
                        return true;
                    } else {
                        return false;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (getStageIndex() == STAGE_INTENT_PICTURE) {
                // STAGE_INTENT_PICTURE should timeout
                return true;
            } else {
                Log.e(TAG, "FAIL: timeout waiting for URI trigger");
                return false;
            }
        }

        protected void onPostExecute(Boolean pass) {
            if (pass) {
                mPassButton.setEnabled(true);
                mFailButton.setEnabled(false);
            } else {
                mPassButton.setEnabled(false);
                mFailButton.setEnabled(true);
            }
        }
    }

    @Override
    public void onClick(View view) {
        Log.v(TAG, "Click detected");

        final int stageIndex = getStageIndex();

        if (view == mStartTestButton) {
            Log.v(TAG, "Starting testing... ");


            mState = STATE_STARTED;

            JobScheduler jobScheduler = (JobScheduler) getSystemService(
                    Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.cancelAll();

            mTestEnv = CameraContentJobService.TestEnvironment.getTestEnvironment();

            mTestEnv.setUp();

            JobInfo job = makeJobInfo(TEST_JOB_TYPES[stageIndex]);
            jobScheduler.schedule(job);

            new WaitForTriggerTask().execute();

            /* we can allow user to fail immediately */
            mFailButton.setEnabled(true);

            /* trigger an ACTION_IMAGE_CAPTURE intent
                which will run the camera app itself */
            String intentStr = null;
            Intent cameraIntent = null;
            if (stageIndex == STAGE_INTENT_PICTURE) {
                intentStr = android.provider.MediaStore.ACTION_IMAGE_CAPTURE;
            }
            else if (stageIndex == STAGE_INTENT_VIDEO) {
                intentStr = android.provider.MediaStore.ACTION_VIDEO_CAPTURE;
            }

            if (intentStr != null) {
                cameraIntent = new Intent(intentStr);
                startActivityForResult(cameraIntent, 1337 + getStageIndex());
            }

            mStartTestButton.setEnabled(false);
        }

        if(view == mPassButton || view == mFailButton) {
            // Stop any running wait
            mTestEnv.cancelWait();

            for (int counter = 0; counter < NUM_STAGES; counter++) {
                String combination = getStageString(counter) + "\n";

                if(counter < stageIndex) {
                    // test already passed, or else wouldn't have made
                    // it to current stageIndex
                    mTestedCombinations.add(combination);
                }

                if(counter == stageIndex) {
                    // current test configuration
                    if(view == mPassButton) {
                        mTestedCombinations.add(combination);
                    }
                    else if(view == mFailButton) {
                        mUntestedCombinations.add(combination);
                    }
                }

                if(counter > stageIndex) {
                    // test not passed yet, since haven't made it to
                    // stageIndex
                    mUntestedCombinations.add(combination);
                }

                counter++;
            }

            mReportBuilder = new StringBuilder();
            mReportBuilder.append("Passed combinations:\n");
            for (String combination : mTestedCombinations) {
                mReportBuilder.append(combination);
            }
            mReportBuilder.append("Failed/untested combinations:\n");
            for (String combination : mUntestedCombinations) {
                mReportBuilder.append(combination);
            }

            if(view == mPassButton) {
                TestResult.setPassedResult(this, "CameraIntentsActivity",
                        getTestDetails());
            }
            if(view == mFailButton) {
                TestResult.setFailedResult(this, "CameraIntentsActivity",
                        getTestDetails());
            }

            // restart activity to test next intents
            Intent intent = new Intent(CameraIntentsActivity.this,
                    CameraIntentsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            intent.putExtra(STAGE_INDEX_EXTRA, stageIndex + 1);
            startActivity(intent);
        }
    }

    private void resetButtons() {
        enablePassFailButtons(false);
    }

    private void enablePassFailButtons(boolean enable) {
        mPassButton.setEnabled(enable);
        mFailButton.setEnabled(enable);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Auto-generated method stub
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Auto-generated method stub
    }

    private void setPassButtonGoesToNextStage(final int stageIndex) {
        findViewById(R.id.pass_button).setOnClickListener(this);
    }

}
