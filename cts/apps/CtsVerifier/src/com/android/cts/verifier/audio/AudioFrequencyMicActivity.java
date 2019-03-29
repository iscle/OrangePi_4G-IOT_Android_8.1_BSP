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

package com.android.cts.verifier.audio;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import com.android.cts.verifier.audio.wavelib.*;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import android.content.Context;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import android.util.Log;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * Tests Audio built in Microphone response using external speakers and USB reference microphone.
 */
public class AudioFrequencyMicActivity extends AudioFrequencyActivity implements Runnable,
    AudioRecord.OnRecordPositionUpdateListener {
    private static final String TAG = "AudioFrequencyMicActivity";

    private static final int TEST_STARTED = 900;
    private static final int TEST_MESSAGE = 903;
    private static final int TEST_ENDED = 904;
    private static final int TEST_ENDED_ERROR = 905;

    private static final double MIN_ENERGY_BAND_1 = -50.0;          //dB Full Scale
    private static final double MAX_ENERGY_BAND_1_BASE = -60.0;     //dB Full Scale
    private static final double MIN_FRACTION_POINTS_IN_BAND = 0.3;
    private static final double MAX_VAL = Math.pow(2, 15);
    private static final double CLIP_LEVEL = (MAX_VAL-10) / MAX_VAL;

    private static final int TEST_NONE = -1;
    private static final int TEST_NOISE = 0;
    private static final int TEST_USB_BACKGROUND = 1;
    private static final int TEST_USB_NOISE = 2;
    private static final int TEST_COUNT = 3;
    private int mCurrentTest = TEST_NONE;
    private boolean mTestsDone[] = new boolean[TEST_COUNT];

    private static final int TEST_DURATION_DEFAULT = 2000;
    private static final int TEST_DURATION_NOISE = TEST_DURATION_DEFAULT;
    private static final int TEST_DURATION_USB_BACKGROUND = TEST_DURATION_DEFAULT;
    private static final int TEST_DURATION_USB_NOISE = TEST_DURATION_DEFAULT;

    final OnBtnClickListener mBtnClickListener = new OnBtnClickListener();
    Context mContext;

    LinearLayout mLayoutTestNoise;
    Button mButtonTestNoise;
    ProgressBar mProgressNoise;
    TextView mResultTestNoise;
    Button mButtonPlayNoise;

    LinearLayout mLayoutTestUsbBackground;
    Button mButtonTestUsbBackground;
    ProgressBar mProgressUsbBackground;
    TextView mResultTestUsbBackground;

    LinearLayout mLayoutTestUsbNoise;
    Button mButtonTestUsbNoise;
    ProgressBar mProgressUsbNoise;
    TextView mResultTestUsbNoise;
    Button mButtonPlayUsbNoise;

    TextView mGlobalResultText;

    private boolean mIsRecording = false;
    private final Object mRecordingLock = new Object();
    private AudioRecord mRecorder;
    private int mMinRecordBufferSizeInSamples = 0;
    private short[] mAudioShortArray;
    private short[] mAudioShortArray2;

    private final int mBlockSizeSamples = 1024;
    private final int mSamplingRate = 48000;
    private final int mSelectedRecordSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    private final int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private Thread mRecordThread;

    PipeShort mPipe = new PipeShort(65536);
    SoundPlayerObject mSPlayer;

    private DspBufferComplex mC;
    private DspBufferDouble mData;

    private DspWindow mWindow;
    private DspFftServer mFftServer;
    private VectorAverage mFreqAverageUsbBackground = new VectorAverage();
    private VectorAverage mFreqAverageNoise = new VectorAverage();
    private VectorAverage mFreqAverageUsbNoise = new VectorAverage();


    int mBands = 4;
    AudioBandSpecs[] bandSpecsArray = new AudioBandSpecs[mBands];
    AudioBandSpecs[] baseBandSpecsArray = new AudioBandSpecs[mBands];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_frequency_mic_activity);
        mContext = this;

        //Test Noise
        mLayoutTestNoise = (LinearLayout) findViewById(R.id.frequency_mic_layout_test_noise);
        mButtonTestNoise = (Button) findViewById(R.id.frequency_mic_test_noise_btn);
        mButtonTestNoise.setOnClickListener(mBtnClickListener);
        mProgressNoise = (ProgressBar) findViewById(R.id.frequency_mic_test_noise_progress_bar);
        mResultTestNoise = (TextView) findViewById(R.id.frequency_mic_test_noise_result);
        mButtonPlayNoise = (Button) findViewById(R.id.frequency_mic_play_noise_btn);
        mButtonPlayNoise.setOnClickListener(mBtnClickListener);
        showWait(mProgressNoise, false);

      //USB Background
        mLayoutTestUsbBackground = (LinearLayout)
                findViewById(R.id.frequency_mic_layout_test_usb_background);
        mButtonTestUsbBackground = (Button)
                findViewById(R.id.frequency_mic_test_usb_background_btn);
        mButtonTestUsbBackground.setOnClickListener(mBtnClickListener);
        mProgressUsbBackground = (ProgressBar)
                findViewById(R.id.frequency_mic_test_usb_background_progress_bar);
        mResultTestUsbBackground = (TextView)
                findViewById(R.id.frequency_mic_test_usb_background_result);
        showWait(mProgressUsbBackground, false);

        mLayoutTestUsbNoise = (LinearLayout) findViewById(R.id.frequency_mic_layout_test_usb_noise);
        mButtonTestUsbNoise = (Button) findViewById(R.id.frequency_mic_test_usb_noise_btn);
        mButtonTestUsbNoise.setOnClickListener(mBtnClickListener);
        mProgressUsbNoise = (ProgressBar)
                findViewById(R.id.frequency_mic_test_usb_noise_progress_bar);
        mResultTestUsbNoise = (TextView) findViewById(R.id.frequency_mic_test_usb_noise_result);
        mButtonPlayUsbNoise = (Button) findViewById(R.id.frequency_mic_play_usb_noise_btn);
        mButtonPlayUsbNoise.setOnClickListener(mBtnClickListener);
        showWait(mProgressUsbNoise, false);

        mGlobalResultText = (TextView) findViewById(R.id.frequency_mic_test_global_result);

        mSPlayer = new SoundPlayerObject();
        mSPlayer.setSoundWithResId(getApplicationContext(), R.raw.stereo_mono_white_noise_48);
        mSPlayer.setBalance(0.5f);

        //Init FFT stuff
        mAudioShortArray2 = new short[mBlockSizeSamples*2];
        mData = new DspBufferDouble(mBlockSizeSamples);
        mC = new DspBufferComplex(mBlockSizeSamples);
        mFftServer = new DspFftServer(mBlockSizeSamples);

        int overlap = mBlockSizeSamples / 2;

        mWindow = new DspWindow(DspWindow.WINDOW_HANNING, mBlockSizeSamples, overlap);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_frequency_mic_test,
                R.string.frequency_mic_info, -1);

        //Init bands for BuiltIn/Reference test
        bandSpecsArray[0] = new AudioBandSpecs(
                50, 500,        /* frequency start,stop */
                4.0, -50,     /* start top,bottom value */
                4.0, -4.0       /* stop top,bottom value */);

        bandSpecsArray[1] = new AudioBandSpecs(
                500,4000,       /* frequency start,stop */
                4.0, -4.0,      /* start top,bottom value */
                4.0, -4.0        /* stop top,bottom value */);

        bandSpecsArray[2] = new AudioBandSpecs(
                4000, 12000,    /* frequency start,stop */
                4.0, -4.0,      /* start top,bottom value */
                5.0, -5.0       /* stop top,bottom value */);

        bandSpecsArray[3] = new AudioBandSpecs(
                12000, 20000,   /* frequency start,stop */
                5.0, -5.0,      /* start top,bottom value */
                5.0, -30.0      /* stop top,bottom value */);

        //Init base bands for silence
        baseBandSpecsArray[0] = new AudioBandSpecs(
                50, 500,        /* frequency start,stop */
                40.0, -50.0,     /* start top,bottom value */
                5.0, -50.0       /* stop top,bottom value */);

        baseBandSpecsArray[1] = new AudioBandSpecs(
                500,4000,       /* frequency start,stop */
                5.0, -50.0,      /* start top,bottom value */
                5.0, -50.0        /* stop top,bottom value */);

        baseBandSpecsArray[2] = new AudioBandSpecs(
                4000, 12000,    /* frequency start,stop */
                5.0, -50.0,      /* start top,bottom value */
                5.0, -50.0       /* stop top,bottom value */);

        baseBandSpecsArray[3] = new AudioBandSpecs(
                12000, 20000,   /* frequency start,stop */
                5.0, -50.0,      /* start top,bottom value */
                5.0, -50.0      /* stop top,bottom value */);

    }
    private void playerToggleButton(int buttonId) {
        if (playerIsPlaying()) {
            playerStopAll();
        } else {
            playerTransport(true);
            setButtonPlayStatus(buttonId);
        }
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            switch (id) {
            case R.id.frequency_mic_test_noise_btn:
                startTest(TEST_NOISE);
                break;
            case R.id.frequency_mic_play_noise_btn:
                playerToggleButton(id);
                break;
            case R.id.frequency_mic_test_usb_background_btn:
                startTest(TEST_USB_BACKGROUND);
                break;
            case R.id.frequency_mic_test_usb_noise_btn:
                startTest(TEST_USB_NOISE);
                break;
            case R.id.frequency_mic_play_usb_noise_btn:
                playerToggleButton(id);
                break;
            }
        }
    }

    private void setButtonPlayStatus(int playResId) {
        String play = getResources().getText(R.string.frequency_mic_play).toString();
        String stop = getResources().getText(R.string.frequency_mic_stop).toString();

        mButtonPlayNoise.setText(playResId == R.id.frequency_mic_play_noise_btn ? stop : play);
        mButtonPlayUsbNoise.setText(playResId ==
                R.id.frequency_mic_play_usb_noise_btn ? stop : play);
    }

    private void playerTransport(boolean play) {
        if (!mSPlayer.isAlive()) {
            mSPlayer.start();
        }
        mSPlayer.play(play);
    }

    private boolean playerIsPlaying() {
       return mSPlayer.isPlaying();
    }

    private void playerStopAll() {
        if (mSPlayer.isAlive() && mSPlayer.isPlaying()) {
            mSPlayer.play(false);
            setButtonPlayStatus(-1);
        }
    }

    /**
     * enable test ui elements
     */
    private void enableLayout(LinearLayout layout, boolean enable) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View view = layout.getChildAt(i);
            view.setEnabled(enable);
        }
    }

    private void showWait(ProgressBar pb, boolean show) {
        if (show) {
            pb.setVisibility(View.VISIBLE);
        } else {
            pb.setVisibility(View.INVISIBLE);
        }
    }

    private void showWait(int testId, boolean show) {
        switch(testId) {
            case TEST_NOISE:
                showWait(mProgressNoise, show);
                break;
            case TEST_USB_BACKGROUND:
                showWait(mProgressUsbBackground, show);
                break;
            case TEST_USB_NOISE:
                showWait(mProgressUsbNoise, show);
                break;
        }
    }

    private String getTestString(int testId) {
        String name = "undefined";
        switch(testId) {
            case TEST_NOISE:
                name = "BuiltIn_noise";
                break;
            case TEST_USB_BACKGROUND:
                name = "USB_background";
                break;
            case TEST_USB_NOISE:
                name = "USB_noise";
                break;
        }
        return name;
    }

    private void showMessage(int testId, String msg) {
        if (msg != null && msg.length() > 0) {
            switch(testId) {
                case TEST_NOISE:
                    mResultTestNoise.setText(msg);
                    break;
                case TEST_USB_BACKGROUND:
                    mResultTestUsbBackground.setText(msg);
                    break;
                case TEST_USB_NOISE:
                    mResultTestUsbNoise.setText(msg);
                    break;
            }
        }
    }

    Thread mTestThread;
    private void startTest(int testId) {
        if (mTestThread != null && !mTestThread.isAlive()) {
            mTestThread = null; //kill it.
        }

        if (mTestThread == null) {
            Log.v(TAG,"Executing test Thread");
            switch(testId) {
                case TEST_NOISE:
                    mTestThread = new Thread(new TestRunnable(TEST_NOISE) {
                        public void run() {
                            super.run();
                            if (!mUsbMicConnected) {
                                sendMessage(mTestId, TEST_MESSAGE,
                                        "Testing Built in Microphone: Noise");
                                mFreqAverageNoise.reset();
                                mFreqAverageNoise.setCaptureType(VectorAverage.CAPTURE_TYPE_MAX);
                                record(TEST_DURATION_NOISE);
                                sendMessage(mTestId, TEST_ENDED, "Testing Completed");
                                mTestsDone[mTestId] = true;
                            } else {
                                sendMessage(mTestId, TEST_ENDED_ERROR,
                                        "Please Unplug USB Microphone");
                                mTestsDone[mTestId] = false;
                            }
                        }
                    });
                break;
                case TEST_USB_BACKGROUND:
                    playerStopAll();
                    mTestThread = new Thread(new TestRunnable(TEST_USB_BACKGROUND) {
                        public void run() {
                            super.run();
                            if (mUsbMicConnected) {
                                sendMessage(mTestId, TEST_MESSAGE,
                                        "Testing USB Microphone: background");
                                mFreqAverageUsbBackground.reset();
                                mFreqAverageUsbBackground.setCaptureType(
                                        VectorAverage.CAPTURE_TYPE_AVERAGE);
                                record(TEST_DURATION_USB_BACKGROUND);
                                sendMessage(mTestId, TEST_ENDED, "Testing Completed");
                                mTestsDone[mTestId] = true;
                            } else {
                                sendMessage(mTestId, TEST_ENDED_ERROR,
                                        "USB Microphone not detected.");
                                mTestsDone[mTestId] = false;
                            }
                        }
                    });
                break;
                case TEST_USB_NOISE:
                    mTestThread = new Thread(new TestRunnable(TEST_USB_NOISE) {
                        public void run() {
                            super.run();
                            if (mUsbMicConnected) {
                                sendMessage(mTestId, TEST_MESSAGE, "Testing USB Microphone: Noise");
                                mFreqAverageUsbNoise.reset();
                                mFreqAverageUsbNoise.setCaptureType(VectorAverage.CAPTURE_TYPE_MAX);
                                record(TEST_DURATION_USB_NOISE);
                                sendMessage(mTestId, TEST_ENDED, "Testing Completed");
                                mTestsDone[mTestId] = true;
                            } else {
                                sendMessage(mTestId, TEST_ENDED_ERROR,
                                        "USB Microphone not detected.");
                                mTestsDone[mTestId] = false;
                            }
                        }
                    });
                break;
            }
            mTestThread.start();
        } else {
            Log.v(TAG,"test Thread already running.");
        }
    }
    public class TestRunnable implements Runnable {
        public int mTestId;
        public boolean mUsbMicConnected;
        TestRunnable(int testId) {
            Log.v(TAG,"New TestRunnable");
            mTestId = testId;
        }
        public void run() {
            mCurrentTest = mTestId;
            sendMessage(mTestId, TEST_STARTED,"");
            mUsbMicConnected =
                    UsbMicrophoneTester.getIsMicrophoneConnected(getApplicationContext());
        };
        public void record(int durationMs) {
            startRecording();
            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
                //restore interrupted status
                Thread.currentThread().interrupt();
            }
            stopRecording();
        }
        public void sendMessage(int testId, int msgType, String str) {
            Message msg = Message.obtain();
            msg.what = msgType;
            msg.obj = str;
            msg.arg1 = testId;
            mMessageHandler.sendMessage(msg);
        }
    }

    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int testId = msg.arg1; //testId
            String str = (String) msg.obj;
            switch (msg.what) {
            case TEST_STARTED:
                showWait(testId, true);
                break;
            case TEST_MESSAGE:
                    showMessage(testId, str);
                break;
            case TEST_ENDED:
                showWait(testId, false);
                playerStopAll();
                showMessage(testId, str);
                appendResultsToScreen(testId, "test finished");
                computeAllResults();
                break;
            case TEST_ENDED_ERROR:
                showWait(testId, false);
                playerStopAll();
                showMessage(testId, str);
                computeAllResults();
            default:
                Log.e(TAG, String.format("Unknown message: %d", msg.what));
            }
        }
    };

    private class Results {
        private String mLabel;
        public double[] mValuesLog;
        int[] mPointsPerBand = new int[mBands];
        double[] mAverageEnergyPerBand = new double[mBands];
        int[] mInBoundPointsPerBand = new int[mBands];
        public boolean mIsBaseMeasurement = false;
        public Results(String label) {
            mLabel = label;
        }

        //append results
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Channel %s\n", mLabel));
            sb.append("Level in Band 1 : " + (testLevel() ? "OK" :"Not Optimal") +
                    (mIsBaseMeasurement ? " (Base Meas.)" : "") + "\n");
            for (int b = 0; b < mBands; b++) {
                double percent = 0;
                if (mPointsPerBand[b] > 0) {
                    percent = 100.0 * (double) mInBoundPointsPerBand[b] / mPointsPerBand[b];
                }
                sb.append(String.format(
                        " Band %d: Av. Level: %.1f dB InBand: %d/%d (%.1f%%) %s\n",
                        b, mAverageEnergyPerBand[b],
                        mInBoundPointsPerBand[b],
                        mPointsPerBand[b],
                        percent,
                        (testInBand(b) ? "OK" : "Not Optimal")));
            }
            return sb.toString();
        }

        public boolean testLevel() {
            if (mIsBaseMeasurement && mAverageEnergyPerBand[1] <= MAX_ENERGY_BAND_1_BASE) {
                return true;
            } else if (mAverageEnergyPerBand[1] >= MIN_ENERGY_BAND_1) {
                return true;
            }
            return false;
        }

        public boolean testInBand(int b) {
            if (b >= 0 && b < mBands && mPointsPerBand[b] > 0) {
                if ((double) mInBoundPointsPerBand[b] / mPointsPerBand[b] >
                    MIN_FRACTION_POINTS_IN_BAND) {
                        return true;
                }
            }
            return false;
        }

        public boolean testAll() {
            if (!testLevel()) {
                return false;
            }
            for (int b = 0; b < mBands; b++) {
                if (!testInBand(b)) {
                    return false;
                }
            }
            return true;
        }
    }

    private void computeAllResults() {
        StringBuilder sb = new StringBuilder();

        boolean allDone = true;

        for (int i = 0; i < TEST_COUNT; i++) {
            allDone = allDone & mTestsDone[i];
            sb.append(String.format("%s : %s\n", getTestString(i),
                    mTestsDone[i] ? "DONE" :" NOT DONE"));
        }

        if (allDone) {
            sb.append(computeResults());
        } else {
            sb.append("Please execute all tests for results\n");
        }
        mGlobalResultText.setText(sb.toString());
    }

    private String computeResults() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");

        Results resultsBuiltIn = new Results(getTestString(TEST_NOISE));
        if (computeResultsForVector(mFreqAverageNoise, resultsBuiltIn, false, bandSpecsArray)) {
            sb.append(resultsBuiltIn.toString());
            sb.append("\n");
            recordTestResults(resultsBuiltIn);
        }

        Results resultsBase = new Results(getTestString(TEST_USB_BACKGROUND));
        if (computeResultsForVector(mFreqAverageUsbBackground, resultsBase, true,
                baseBandSpecsArray)) {
            sb.append(resultsBase.toString());
            sb.append("\n");
            recordTestResults(resultsBase);
        }

        Results resultsUsbNoise = new Results(getTestString(TEST_USB_NOISE));
        if (computeResultsForVector(mFreqAverageUsbNoise, resultsUsbNoise, false,
                bandSpecsArray)) {
            sb.append(resultsUsbNoise.toString());
            sb.append("\n");
            recordTestResults(resultsUsbNoise);
            getPassButton().setEnabled(true);
        }
        return sb.toString();
    }

    private boolean computeResultsForVector(VectorAverage freqAverage, Results results,
            boolean isBase, AudioBandSpecs[] bandSpecs) {

        results.mIsBaseMeasurement = isBase;
        int points = freqAverage.getSize();
        if (points > 0) {
            //compute vector in db
            double[] values = new double[points];
            freqAverage.getData(values, false);
            results.mValuesLog = new double[points];
            for (int i = 0; i < points; i++) {
                results.mValuesLog[i] = 20 * Math.log10(values[i]);
            }

            int currentBand = 0;
            for (int i = 0; i < points; i++) {
                double freq = (double)mSamplingRate * i / (double)mBlockSizeSamples;
                if (freq > bandSpecs[currentBand].mFreqStop) {
                    currentBand++;
                    if (currentBand >= mBands)
                        break;
                }

                if (freq >= bandSpecs[currentBand].mFreqStart) {
                    results.mAverageEnergyPerBand[currentBand] += results.mValuesLog[i];
                    results.mPointsPerBand[currentBand]++;
                }
            }

            for (int b = 0; b < mBands; b++) {
                if (results.mPointsPerBand[b] > 0) {
                    results.mAverageEnergyPerBand[b] =
                            results.mAverageEnergyPerBand[b] / results.mPointsPerBand[b];
                }
            }

            //set offset relative to band 1 level
            for (int b = 0; b < mBands; b++) {
                bandSpecs[b].setOffset(results.mAverageEnergyPerBand[1]);
            }

            //test points in band.
            currentBand = 0;
            for (int i = 0; i < points; i++) {
                double freq = (double)mSamplingRate * i / (double)mBlockSizeSamples;
                if (freq > bandSpecs[currentBand].mFreqStop) {
                    currentBand++;
                    if (currentBand >= mBands)
                        break;
                }

                if (freq >= bandSpecs[currentBand].mFreqStart) {
                    double value = results.mValuesLog[i];
                    if (bandSpecs[currentBand].isInBounds(freq, value)) {
                        results.mInBoundPointsPerBand[currentBand]++;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    //append results
    private void appendResultsToScreen(String str, TextView text) {
        String currentText = text.getText().toString();
        text.setText(currentText + "\n" + str);
    }

    private void appendResultsToScreen(int testId, String str) {
        switch(testId) {
            case TEST_NOISE:
                appendResultsToScreen(str, mResultTestNoise);
                break;
            case TEST_USB_BACKGROUND:
                appendResultsToScreen(str, mResultTestUsbBackground);
                break;
            case TEST_USB_NOISE:
                appendResultsToScreen(str, mResultTestUsbNoise);
                break;
        }
    }

    /**
     * Store test results in log
     */
    private void recordTestResults(Results results) {
        String channelLabel = "channel_" + results.mLabel;

        for (int b = 0; b < mBands; b++) {
            String bandLabel = String.format(channelLabel + "_%d", b);
            getReportLog().addValue(
                    bandLabel + "_Level",
                    results.mAverageEnergyPerBand[b],
                    ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);

            getReportLog().addValue(
                    bandLabel + "_pointsinbound",
                    results.mInBoundPointsPerBand[b],
                    ResultType.HIGHER_BETTER,
                    ResultUnit.COUNT);

            getReportLog().addValue(
                    bandLabel + "_pointstotal",
                    results.mPointsPerBand[b],
                    ResultType.NEUTRAL,
                    ResultUnit.COUNT);
        }

        getReportLog().addValues(channelLabel + "_magnitudeSpectrumLog",
                results.mValuesLog,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        Log.v(TAG, "Results Recorded");
    }

    private void recordHeasetPortFound(boolean found) {
        getReportLog().addValue(
                "User Reported Headset Port",
                found ? 1.0 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    private void startRecording() {
        synchronized (mRecordingLock) {
            mIsRecording = true;
        }

        boolean successful = initRecord();
        if (successful) {
            startRecordingForReal();
        } else {
            Log.v(TAG, "Recorder initialization error.");
            synchronized (mRecordingLock) {
                mIsRecording = false;
            }
        }
    }

    private void startRecordingForReal() {
        // start streaming
        if (mRecordThread == null) {
            mRecordThread = new Thread(AudioFrequencyMicActivity.this);
            mRecordThread.setName("FrequencyAnalyzerThread");
        }
        if (!mRecordThread.isAlive()) {
            mRecordThread.start();
        }

        mPipe.flush();

        long startTime = SystemClock.uptimeMillis();
        mRecorder.startRecording();
        if (mRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            stopRecording();
            return;
        }
        Log.v(TAG, "Start time: " + (long) (SystemClock.uptimeMillis() - startTime) + " ms");
    }

    private void stopRecording() {
        synchronized (mRecordingLock) {
            stopRecordingForReal();
            mIsRecording = false;
        }
    }

    private void stopRecordingForReal() {

        // stop streaming
        Thread zeThread = mRecordThread;
        mRecordThread = null;
        if (zeThread != null) {
            zeThread.interrupt();
            try {
                zeThread.join();
            } catch(InterruptedException e) {
                //restore interrupted status of recording thread
                zeThread.interrupt();
            }
        }
         // release recording resources
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    private boolean initRecord() {
        int minRecordBuffSizeInBytes = AudioRecord.getMinBufferSize(mSamplingRate,
                mChannelConfig, mAudioFormat);
        Log.v(TAG,"FrequencyAnalyzer: min buff size = " + minRecordBuffSizeInBytes + " bytes");
        if (minRecordBuffSizeInBytes <= 0) {
            return false;
        }

        mMinRecordBufferSizeInSamples = minRecordBuffSizeInBytes / 2;
        // allocate the byte array to read the audio data

        mAudioShortArray = new short[mMinRecordBufferSizeInSamples];

        Log.v(TAG, "Initiating record:");
        Log.v(TAG, "      using source " + mSelectedRecordSource);
        Log.v(TAG, "      at " + mSamplingRate + "Hz");

        try {
            mRecorder = new AudioRecord(mSelectedRecordSource, mSamplingRate,
                    mChannelConfig, mAudioFormat, 2 * minRecordBuffSizeInBytes);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            mRecorder.release();
            mRecorder = null;
            return false;
        }
        mRecorder.setRecordPositionUpdateListener(this);
        mRecorder.setPositionNotificationPeriod(mBlockSizeSamples / 2);
        return true;
    }

    // ---------------------------------------------------------
    // Implementation of AudioRecord.OnPeriodicNotificationListener
    // --------------------
    public void onPeriodicNotification(AudioRecord recorder) {
        int samplesAvailable = mPipe.availableToRead();
        int samplesNeeded = mBlockSizeSamples;
        if (samplesAvailable >= samplesNeeded) {
            mPipe.read(mAudioShortArray2, 0, samplesNeeded);

            //compute stuff.
            int clipcount = 0;
            double sum = 0;
            double maxabs = 0;
            int i;

            for (i = 0; i < samplesNeeded; i++) {
                double value = mAudioShortArray2[i] / MAX_VAL;
                double valueabs = Math.abs(value);

                if (valueabs > maxabs) {
                    maxabs = valueabs;
                }

                if (valueabs > CLIP_LEVEL) {
                    clipcount++;
                }

                sum += value * value;
                //fft stuff
                mData.mData[i] = value;
            }

            //for the current frame, compute FFT and send to the viewer.

            //apply window and pack as complex for now.
            DspBufferMath.mult(mData, mData, mWindow.mBuffer);
            DspBufferMath.set(mC, mData);
            mFftServer.fft(mC, 1);

            double[] halfMagnitude = new double[mBlockSizeSamples / 2];
            for (i = 0; i < mBlockSizeSamples / 2; i++) {
                halfMagnitude[i] = Math.sqrt(mC.mReal[i] * mC.mReal[i] + mC.mImag[i] * mC.mImag[i]);
            }

            switch(mCurrentTest) {
                case TEST_NOISE:
                    mFreqAverageNoise.setData(halfMagnitude, false);
                    break;
                case TEST_USB_BACKGROUND:
                    mFreqAverageUsbBackground.setData(halfMagnitude, false);
                    break;
                case TEST_USB_NOISE:
                    mFreqAverageUsbNoise.setData(halfMagnitude, false);
                    break;
            }
        }
    }

    public void onMarkerReached(AudioRecord track) {
    }

    // ---------------------------------------------------------
    // Implementation of Runnable for the audio recording + playback
    // --------------------
    public void run() {
        Thread thisThread = Thread.currentThread();
        while (!thisThread.isInterrupted()) {
            // read from native recorder
            int nSamplesRead = mRecorder.read(mAudioShortArray, 0, mMinRecordBufferSizeInSamples);
            if (nSamplesRead > 0) {
                mPipe.write(mAudioShortArray, 0, nSamplesRead);
            }
        }
    }

}
