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
 * Tests Audio built in Microphone response for Unprocessed audio source feature.
 */
public class AudioFrequencyUnprocessedActivity extends AudioFrequencyActivity implements Runnable,
    AudioRecord.OnRecordPositionUpdateListener {
    private static final String TAG = "AudioFrequencyUnprocessedActivity";

    private static final int TEST_STARTED = 900;
    private static final int TEST_MESSAGE = 903;
    private static final int TEST_ENDED = 904;
    private static final int TEST_ENDED_ERROR = 905;
    private static final double MIN_FRACTION_POINTS_IN_BAND = 0.5;

    private static final double TONE_RMS_EXPECTED = -36.0;
    private static final double TONE_RMS_MAX_ERROR = 3.0;

    private static final double MAX_VAL = Math.pow(2, 15);
    private static final double CLIP_LEVEL = (MAX_VAL-10) / MAX_VAL;

    private static final int SOURCE_TONE = 0;
    private static final int SOURCE_NOISE = 1;

    private static final int TEST_NONE = -1;
    private static final int TEST_TONE = 0;
    private static final int TEST_NOISE = 1;
    private static final int TEST_USB_BACKGROUND = 2;
    private static final int TEST_USB_NOISE = 3;
    private static final int TEST_COUNT = 4;
    private int mCurrentTest = TEST_NONE;
    private boolean mTestsDone[] = new boolean[TEST_COUNT];

    private static final int TEST_DURATION_DEFAULT = 2000;
    private static final int TEST_DURATION_TONE = TEST_DURATION_DEFAULT;
    private static final int TEST_DURATION_NOISE = TEST_DURATION_DEFAULT;
    private static final int TEST_DURATION_USB_BACKGROUND = TEST_DURATION_DEFAULT;
    private static final int TEST_DURATION_USB_NOISE = TEST_DURATION_DEFAULT;

    final OnBtnClickListener mBtnClickListener = new OnBtnClickListener();
    Context mContext;

    LinearLayout mLayoutTestTone;
    Button mButtonTestTone;
    ProgressBar mProgressTone;
    TextView mResultTestTone;
    Button mButtonPlayTone;

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
    TextView mTextViewUnprocessedStatus;

    private boolean mIsRecording = false;
    private final Object mRecordingLock = new Object();
    private AudioRecord mRecorder;
    private int mMinRecordBufferSizeInSamples = 0;
    private short[] mAudioShortArray;
    private short[] mAudioShortArray2;

    private final int mBlockSizeSamples = 4096;
    private final int mSamplingRate = 48000;
    private final int mSelectedRecordSource = MediaRecorder.AudioSource.UNPROCESSED;
    private final int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private Thread mRecordThread;

    PipeShort mPipe = new PipeShort(65536);

    SoundPlayerObject mSPlayer;

    private boolean mSupportsUnprocessed = false;

    private DspBufferComplex mC;
    private DspBufferDouble mData;

    private DspWindow mWindow;
    private DspFftServer mFftServer;
    private VectorAverage mFreqAverageTone = new VectorAverage();
    private VectorAverage mFreqAverageNoise = new VectorAverage();
    private VectorAverage mFreqAverageUsbBackground = new VectorAverage();
    private VectorAverage mFreqAverageUsbNoise = new VectorAverage();

    //RMS for tone:
    private double mRMS;
    private double mRMSMax;

    private double mRMSTone;
    private double mRMSMaxTone;

    int mBands = 3;
    int mBandsTone = 3;
    int mBandsBack = 3;
    AudioBandSpecs[] mBandSpecsMic = new AudioBandSpecs[mBands];
    AudioBandSpecs[] mBandSpecsTone = new AudioBandSpecs[mBandsTone];
    AudioBandSpecs[] mBandSpecsBack = new AudioBandSpecs[mBandsBack];
    private Results mResultsMic;
    private Results mResultsTone;
    private Results mResultsBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_frequency_unprocessed_activity);
        mContext = this;
        mTextViewUnprocessedStatus = (TextView) findViewById(
                R.id.audio_frequency_unprocessed_defined);
        //unprocessed test
        mSupportsUnprocessed = supportsUnprocessed();
        if (mSupportsUnprocessed) {
            mTextViewUnprocessedStatus.setText(
                    getResources().getText(R.string.audio_frequency_unprocessed_defined));
        } else {
            mTextViewUnprocessedStatus.setText(
                    getResources().getText(R.string.audio_frequency_unprocessed_not_defined));
        }

        mSPlayer = new SoundPlayerObject();
        playerSetSource(SOURCE_TONE);

        // Test tone
        mLayoutTestTone = (LinearLayout) findViewById(R.id.unprocessed_layout_test_tone);
        mButtonTestTone = (Button) findViewById(R.id.unprocessed_test_tone_btn);
        mButtonTestTone.setOnClickListener(mBtnClickListener);
        mProgressTone = (ProgressBar) findViewById(R.id.unprocessed_test_tone_progress_bar);
        mResultTestTone = (TextView) findViewById(R.id.unprocessed_test_tone_result);
        mButtonPlayTone = (Button) findViewById(R.id.unprocessed_play_tone_btn);
        mButtonPlayTone.setOnClickListener(mBtnClickListener);
        showWait(mProgressTone, false);

        //Test Noise
        mLayoutTestNoise = (LinearLayout) findViewById(R.id.unprocessed_layout_test_noise);
        mButtonTestNoise = (Button) findViewById(R.id.unprocessed_test_noise_btn);
        mButtonTestNoise.setOnClickListener(mBtnClickListener);
        mProgressNoise = (ProgressBar) findViewById(R.id.unprocessed_test_noise_progress_bar);
        mResultTestNoise = (TextView) findViewById(R.id.unprocessed_test_noise_result);
        mButtonPlayNoise = (Button) findViewById(R.id.unprocessed_play_noise_btn);
        mButtonPlayNoise.setOnClickListener(mBtnClickListener);
        showWait(mProgressNoise, false);

        //USB Background
        mLayoutTestUsbBackground = (LinearLayout)
                findViewById(R.id.unprocessed_layout_test_usb_background);
        mButtonTestUsbBackground = (Button) findViewById(R.id.unprocessed_test_usb_background_btn);
        mButtonTestUsbBackground.setOnClickListener(mBtnClickListener);
        mProgressUsbBackground = (ProgressBar)
                findViewById(R.id.unprocessed_test_usb_background_progress_bar);
        mResultTestUsbBackground = (TextView)
                findViewById(R.id.unprocessed_test_usb_background_result);
        showWait(mProgressUsbBackground, false);

        mLayoutTestUsbNoise = (LinearLayout) findViewById(R.id.unprocessed_layout_test_usb_noise);
        mButtonTestUsbNoise = (Button) findViewById(R.id.unprocessed_test_usb_noise_btn);
        mButtonTestUsbNoise.setOnClickListener(mBtnClickListener);
        mProgressUsbNoise = (ProgressBar)findViewById(R.id.unprocessed_test_usb_noise_progress_bar);
        mResultTestUsbNoise = (TextView) findViewById(R.id.unprocessed_test_usb_noise_result);
        mButtonPlayUsbNoise = (Button) findViewById(R.id.unprocessed_play_usb_noise_btn);
        mButtonPlayUsbNoise.setOnClickListener(mBtnClickListener);
        showWait(mProgressUsbNoise, false);

        setButtonPlayStatus(-1);
        mGlobalResultText = (TextView) findViewById(R.id.unprocessed_test_global_result);

        //Init FFT stuff
        mAudioShortArray2 = new short[mBlockSizeSamples*2];
        mData = new DspBufferDouble(mBlockSizeSamples);
        mC = new DspBufferComplex(mBlockSizeSamples);
        mFftServer = new DspFftServer(mBlockSizeSamples);

        int overlap = mBlockSizeSamples / 2;

        mWindow = new DspWindow(DspWindow.WINDOW_HANNING, mBlockSizeSamples, overlap);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_frequency_unprocessed_test,
                R.string.audio_frequency_unprocessed_info, -1);

        //Init bands for Mic test
        mBandSpecsMic[0] = new AudioBandSpecs(
                5, 100,          /* frequency start,stop */
                20.0, -20.0,     /* start top,bottom value */
                20.0, -20.0      /* stop top,bottom value */);

        mBandSpecsMic[1] = new AudioBandSpecs(
                100, 7000,       /* frequency start,stop */
                10.0, -10.0,     /* start top,bottom value */
                10.0, -10.0      /* stop top,bottom value */);

        mBandSpecsMic[2] = new AudioBandSpecs(
                7000, 20000,     /* frequency start,stop */
                30.0, -30.0,     /* start top,bottom value */
                30.0, -30.0      /* stop top,bottom value */);

        //Init bands for Tone test
        mBandSpecsTone[0] = new AudioBandSpecs(
                5, 900,          /* frequency start,stop */
                -10.0, -100.0,     /* start top,bottom value */
                -10.0, -100.0      /* stop top,bottom value */);

        mBandSpecsTone[1] = new AudioBandSpecs(
                900, 1100,       /* frequency start,stop */
                10.0, -50.0,     /* start top,bottom value */
                10.0, -10.0      /* stop top,bottom value */);

        mBandSpecsTone[2] = new AudioBandSpecs(
                1100, 20000,     /* frequency start,stop */
                -30.0, -120.0,     /* start top,bottom value */
                -30.0, -120.0      /* stop top,bottom value */);

      //Init bands for Background test
        mBandSpecsBack[0] = new AudioBandSpecs(
                5, 100,          /* frequency start,stop */
                10.0, -120.0,     /* start top,bottom value */
                -10.0, -120.0      /* stop top,bottom value */);

        mBandSpecsBack[1] = new AudioBandSpecs(
                100, 7000,       /* frequency start,stop */
                -10.0, -120.0,     /* start top,bottom value */
                -50.0, -120.0      /* stop top,bottom value */);

        mBandSpecsBack[2] = new AudioBandSpecs(
                7000, 20000,     /* frequency start,stop */
                -50.0, -120.0,     /* start top,bottom value */
                -50.0, -120.0      /* stop top,bottom value */);

        mSupportsUnprocessed = supportsUnprocessed();
        Log.v(TAG, "Supports unprocessed: " + mSupportsUnprocessed);

        mResultsMic =  new Results("mic_response", mBands);
        mResultsTone = new Results("tone_response", mBandsTone);
        mResultsBack = new Results("background_response", mBandsBack);
    }

    private void playerToggleButton(int buttonId, int sourceId) {
        if (playerIsPlaying()) {
            playerStopAll();
        } else {
            playerSetSource(sourceId);
            playerTransport(true);
            setButtonPlayStatus(buttonId);
        }
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            switch (id) {
            case R.id.unprocessed_test_tone_btn:
                startTest(TEST_TONE);
                break;
            case R.id.unprocessed_play_tone_btn:
                playerToggleButton(id, SOURCE_TONE);
                break;
            case R.id.unprocessed_test_noise_btn:
                startTest(TEST_NOISE);
                break;
            case R.id.unprocessed_play_noise_btn:
                playerToggleButton(id, SOURCE_NOISE);
                break;
            case R.id.unprocessed_test_usb_background_btn:
                startTest(TEST_USB_BACKGROUND);
                break;
            case R.id.unprocessed_test_usb_noise_btn:
                startTest(TEST_USB_NOISE);
                break;
            case R.id.unprocessed_play_usb_noise_btn:
                playerToggleButton(id, SOURCE_NOISE);
                break;
            }
        }
    }

    private void setButtonPlayStatus(int playResId) {
        String play = getResources().getText(R.string.unprocessed_play).toString();
        String stop = getResources().getText(R.string.unprocessed_stop).toString();

        mButtonPlayTone.setText(playResId == R.id.unprocessed_play_tone_btn ? stop : play);
        mButtonPlayNoise.setText(playResId == R.id.unprocessed_play_noise_btn ? stop : play);
        mButtonPlayUsbNoise.setText(playResId ==
                R.id.unprocessed_play_usb_noise_btn ? stop : play);
    }

    private void playerSetSource(int sourceIndex) {
        switch (sourceIndex) {
            case SOURCE_TONE:
                mSPlayer.setSoundWithResId(getApplicationContext(), R.raw.onekhztone);
                break;
            default:
            case SOURCE_NOISE:
                mSPlayer.setSoundWithResId(getApplicationContext(),
                        R.raw.stereo_mono_white_noise_48);
                break;
        }
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

    private String getTestString(int testId) {
        String name = "undefined";
        switch(testId) {
            case TEST_TONE:
                name = "BuiltIn_tone";
                break;
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

    private void showWait(int testId, boolean show) {
        switch(testId) {
            case TEST_TONE:
                showWait(mProgressTone, show);
                break;
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

    private void showMessage(int testId, String msg) {
        if (msg != null && msg.length() > 0) {
            switch(testId) {
                case TEST_TONE:
                    mResultTestTone.setText(msg);
                    break;
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

    private boolean supportsUnprocessed() {
        boolean unprocessedSupport = false;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        String unprocessedSupportString = am.getProperty(
                AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        Log.v(TAG,"unprocessed support: " + unprocessedSupportString);
        if (unprocessedSupportString == null ||
                unprocessedSupportString.equalsIgnoreCase(getResources().getString(
                        R.string.audio_general_default_false_string))) {
            unprocessedSupport = false;
        } else {
            unprocessedSupport = true;
        }
        return unprocessedSupport;
    }

    private void computeAllResults() {
        StringBuilder sb = new StringBuilder();

        boolean allDone = true;

        for (int i=0; i<TEST_COUNT; i++) {
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

    private void processSpectrum(Results results, AudioBandSpecs[] bandsSpecs, int anchorBand) {
        int points = results.mValuesLog.length;
        int bandCount = bandsSpecs.length;
        int currentBand = 0;
        for (int i = 0; i < points; i++) {
            double freq = (double)mSamplingRate * i / (double)mBlockSizeSamples;
            if (freq > bandsSpecs[currentBand].mFreqStop) {
                currentBand++;
                if (currentBand >= bandCount)
                    break;
            }

            if (freq >= bandsSpecs[currentBand].mFreqStart) {
                results.mAverageEnergyPerBand[currentBand] += results.mValuesLog[i];
                results.mPointsPerBand[currentBand]++;
            }
        }

        for (int b = 0; b < bandCount; b++) {
            if (results.mPointsPerBand[b] > 0) {
                results.mAverageEnergyPerBand[b] =
                        results.mAverageEnergyPerBand[b] / results.mPointsPerBand[b];
            }
        }

        //set offset relative to band anchor band level
        for (int b = 0; b < bandCount; b++) {
            if (anchorBand > -1 && anchorBand < bandCount) {
                bandsSpecs[b].setOffset(results.mAverageEnergyPerBand[anchorBand]);
            } else {
                bandsSpecs[b].setOffset(0);
            }
        }

        //test points in band.
        currentBand = 0;
        for (int i = 0; i < points; i++) {
            double freq = (double) mSamplingRate * i / (double) mBlockSizeSamples;
            if (freq >  bandsSpecs[currentBand].mFreqStop) {
                currentBand++;
                if (currentBand >= bandCount)
                    break;
            }

            if (freq >= bandsSpecs[currentBand].mFreqStart) {
                double value = results.mValuesLog[i];
                if (bandsSpecs[currentBand].isInBounds(freq, value)) {
                    results.mInBoundPointsPerBand[currentBand]++;
                }
            }
        }
    }

    private String computeResults() {
        StringBuilder sb = new StringBuilder();

        int points = mFreqAverageNoise.getSize();
        //mFreqAverageNoise size is determined by the latest data writen to it.
        //Currently, this data is always mBlockSizeSamples/2.
        if (points < 1) {
            return "error: not enough points";
        }

        double[] tone = new double[points];
        double[] noise = new double[points];
        double[] reference = new double[points];
        double[] background = new double[points];

        mFreqAverageTone.getData(tone, false);
        mFreqAverageNoise.getData(noise, false);
        mFreqAverageUsbNoise.getData(reference, false);
        mFreqAverageUsbBackground.getData(background, false);

        //Convert to dB
        double[] toneDb = new double[points];
        double[] noiseDb = new double[points];
        double[] referenceDb = new double[points];
        double[] backgroundDb = new double[points];

        double[] compensatedNoiseDb = new double[points];

        for (int i = 0; i < points; i++) {
            toneDb[i] = 20 * Math.log10(tone[i]);
            noiseDb[i] = 20 * Math.log10(noise[i]);
            referenceDb[i] = 20 * Math.log10(reference[i]);
            backgroundDb[i] = 20 * Math.log10(background[i]);

            compensatedNoiseDb[i] = noiseDb[i] - referenceDb[i];
        }

        mResultsMic.reset();
        mResultsTone.reset();
        mResultsBack.reset();

        mResultsMic.mValuesLog = compensatedNoiseDb;
        mResultsTone.mValuesLog = toneDb;
        mResultsBack.mValuesLog = backgroundDb;

        processSpectrum(mResultsMic, mBandSpecsMic, 1);
        processSpectrum(mResultsTone, mBandSpecsTone, 1);
        processSpectrum(mResultsBack, mBandSpecsBack, -1); //no reference for offset

        //Tone test
        boolean toneTestSuccess = true;
        {
            //rms level should be -36 dbfs +/- 3 db?
            double rmsMaxDb =  20 * Math.log10(mRMSMaxTone);
            sb.append(String.format("RMS level of tone: %.2f dBFS\n", rmsMaxDb));
            sb.append(String.format("Target RMS level: %.2f dBFS +/- %.2f dB\n",
                    TONE_RMS_EXPECTED,
                    TONE_RMS_MAX_ERROR));
            if (Math.abs(rmsMaxDb - TONE_RMS_EXPECTED) > TONE_RMS_MAX_ERROR) {
                toneTestSuccess = false;
                sb.append("RMS level test FAILED\n");
            } else {
                sb.append(" RMS level test SUCCESSFUL\n");
            }
            //check the spectrum is really a tone around 1 khz
        }

        sb.append("\n");
        sb.append(mResultsTone.toString());
        if (mResultsTone.testAll()) {
            sb.append(" 1 Khz Tone Frequency Response Test SUCCESSFUL\n");
        } else {
            sb.append(" 1 Khz Tone Frequency Response Test FAILED\n");
        }
        sb.append("\n");

        sb.append("\n");
        sb.append(mResultsBack.toString());
        if (mResultsBack.testAll()) {
            sb.append(" Background environment Test SUCCESSFUL\n");
        } else {
            sb.append(" Background environment Test FAILED\n");
        }

        sb.append("\n");
        sb.append(mResultsMic.toString());
        if (mResultsMic.testAll()) {
            sb.append(" Frequency Response Test SUCCESSFUL\n");
        } else {
            sb.append(" Frequency Response Test FAILED\n");
        }
        sb.append("\n");

        recordTestResults(mResultsTone);
        recordTestResults(mResultsMic);

        boolean allTestsPassed = false;
        if (mResultsMic.testAll() && mResultsTone.testAll() && toneTestSuccess &&
                mResultsBack.testAll()) {
            allTestsPassed = true;
            String strSuccess = getResources().getString(R.string.audio_general_test_passed);
            sb.append(strSuccess);
        } else {
            String strFailed = getResources().getString(R.string.audio_general_test_failed);
            sb.append(strFailed);
        }

        sb.append("\n");
        if (mSupportsUnprocessed) { //test is mandatory
            sb.append(getResources().getText(
                    R.string.audio_frequency_unprocessed_defined).toString());
            if (allTestsPassed) {
                getPassButton().setEnabled(true);
            } else {
                getPassButton().setEnabled(false);
            }
        } else {
            //test optional
            sb.append(getResources().getText(
                    R.string.audio_frequency_unprocessed_not_defined).toString());
            getPassButton().setEnabled(true);
        }
        return sb.toString();
    }

    Thread mTestThread;
    private void startTest(int testId) {
        if (mTestThread != null && !mTestThread.isAlive()) {
            mTestThread = null; //kill it.
        }

        if (mTestThread == null) {
            mRMS = 0;
            mRMSMax = 0;
            Log.v(TAG,"Executing test Thread");
            switch(testId) {
                case TEST_TONE:
                    mTestThread = new Thread(new TestRunnable(TEST_TONE) {
                        public void run() {
                            super.run();
                            if (!mUsbMicConnected) {
                                sendMessage(mTestId, TEST_MESSAGE,
                                        "Testing Built in Microphone: Tone");
                                mRMSTone = 0;
                                mRMSMaxTone = 0;
                                mFreqAverageTone.reset();
                                mFreqAverageTone.setCaptureType(VectorAverage.CAPTURE_TYPE_MAX);
                                record(TEST_DURATION_TONE);
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
//                getPassButton().setEnabled(false);
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
        private int mBandCount;
        private String mLabel;
        public double[] mValuesLog;
        int[] mPointsPerBand; // = new int[mBands];
        double[] mAverageEnergyPerBand;// = new double[mBands];
        int[] mInBoundPointsPerBand;// = new int[mBands];
        public Results(String label, int bandCount) {
            mLabel = label;
            mBandCount = bandCount;
            mPointsPerBand = new int[mBandCount];
            mAverageEnergyPerBand = new double[mBandCount];
            mInBoundPointsPerBand = new int[mBandCount];
        }
        public void reset() {
            for (int i = 0; i < mBandCount; i++) {
                mPointsPerBand[i] = 0;
                mAverageEnergyPerBand[i] = 0;
                mInBoundPointsPerBand[i] = 0;
            }
        }

        //append results
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Channel %s\n", mLabel));
            for (int b = 0; b < mBandCount; b++) {
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

        public boolean testInBand(int b) {
            if (b >= 0 && b < mBandCount && mPointsPerBand[b] > 0) {
                if ((double) mInBoundPointsPerBand[b] / mPointsPerBand[b] >
                    MIN_FRACTION_POINTS_IN_BAND) {
                        return true;
                }
            }
            return false;
        }

        public boolean testAll() {
            for (int b = 0; b < mBandCount; b++) {
                if (!testInBand(b)) {
                    return false;
                }
            }
            return true;
        }
    }


//    /**
//     * compute test results
//     */
//    private void computeTestResults(int testId) {
//        String testName = getTestString(testId);
//        appendResultsToScreen(testId, "test finished");
//    }

    //append results
    private void appendResultsToScreen(String str, TextView text) {
        String currentText = text.getText().toString();
        text.setText(currentText + "\n" + str);
    }

    private void appendResultsToScreen(int testId, String str) {
        switch(testId) {
            case TEST_TONE:
                appendResultsToScreen(str, mResultTestTone);
                showToneRMS();
                break;
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
            mRecordThread = new Thread(AudioFrequencyUnprocessedActivity.this);
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
    private void showToneRMS() {
        String str = String.format("RMS: %.3f dBFS. Max RMS: %.3f dBFS",
                20 * Math.log10(mRMSTone),
                20 * Math.log10(mRMSMaxTone));
        showMessage(TEST_TONE, str);
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
//            double sum = 0;
            double maxabs = 0;
            int i;
            double rmsTempSum = 0;

            for (i = 0; i < samplesNeeded; i++) {
                double value = mAudioShortArray2[i] / MAX_VAL;
                double valueabs = Math.abs(value);

                if (valueabs > maxabs) {
                    maxabs = valueabs;
                }

                if (valueabs > CLIP_LEVEL) {
                    clipcount++;
                }

                rmsTempSum += value * value;
                //fft stuff
                mData.mData[i] = value;
            }
            double rms = Math.sqrt(rmsTempSum / samplesNeeded);

            double alpha = 0.9;
            double total_rms = rms * alpha + mRMS *(1-alpha);
            mRMS = total_rms;
            if (mRMS > mRMSMax) {
                mRMSMax = mRMS;
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
                case TEST_TONE: {
                    mFreqAverageTone.setData(halfMagnitude, false);
                    //Update realtime info on screen
                    mRMSTone = mRMS;
                    mRMSMaxTone = mRMSMax;
                   showToneRMS();
                }
                    break;
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
