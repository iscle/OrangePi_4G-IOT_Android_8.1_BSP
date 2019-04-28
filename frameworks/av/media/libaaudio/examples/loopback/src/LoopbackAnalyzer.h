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

/**
 * Tools for measuring latency and for detecting glitches.
 * These classes are pure math and can be used with any audio system.
 */

#ifndef AAUDIO_EXAMPLES_LOOPBACK_ANALYSER_H
#define AAUDIO_EXAMPLES_LOOPBACK_ANALYSER_H

#include <algorithm>
#include <assert.h>
#include <cctype>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

// Tag for machine readable results as property = value pairs
#define LOOPBACK_RESULT_TAG      "RESULT: "
#define LOOPBACK_SAMPLE_RATE     48000

#define MILLIS_PER_SECOND        1000

#define MAX_ZEROTH_PARTIAL_BINS  40

static const float s_Impulse[] = {
        0.0f, 0.0f, 0.0f, 0.0f, 0.2f, // silence on each side of the impulse
        0.5f, 0.9999f, 0.0f, -0.9999, -0.5f, // bipolar
        -0.2f, 0.0f, 0.0f, 0.0f, 0.0f
};

class PseudoRandom {
public:
    PseudoRandom() {}
    PseudoRandom(int64_t seed)
            :    mSeed(seed)
    {}

    /**
     * Returns the next random double from -1.0 to 1.0
     *
     * @return value from -1.0 to 1.0
     */
     double nextRandomDouble() {
        return nextRandomInteger() * (0.5 / (((int32_t)1) << 30));
    }

    /** Calculate random 32 bit number using linear-congruential method. */
    int32_t nextRandomInteger() {
        // Use values for 64-bit sequence from MMIX by Donald Knuth.
        mSeed = (mSeed * (int64_t)6364136223846793005) + (int64_t)1442695040888963407;
        return (int32_t) (mSeed >> 32); // The higher bits have a longer sequence.
    }

private:
    int64_t mSeed = 99887766;
};

static double calculateCorrelation(const float *a,
                                   const float *b,
                                   int windowSize)
{
    double correlation = 0.0;
    double sumProducts = 0.0;
    double sumSquares = 0.0;

    // Correlate a against b.
    for (int i = 0; i < windowSize; i++) {
        float s1 = a[i];
        float s2 = b[i];
        // Use a normalized cross-correlation.
        sumProducts += s1 * s2;
        sumSquares += ((s1 * s1) + (s2 * s2));
    }

    if (sumSquares >= 0.00000001) {
        correlation = (float) (2.0 * sumProducts / sumSquares);
    }
    return correlation;
}

static int calculateCorrelations(const float *haystack, int haystackSize,
                                 const float *needle, int needleSize,
                                 float *results, int resultSize)
{
    int maxCorrelations = haystackSize - needleSize;
    int numCorrelations = std::min(maxCorrelations, resultSize);

    for (int ic = 0; ic < numCorrelations; ic++) {
        double correlation = calculateCorrelation(&haystack[ic], needle, needleSize);
        results[ic] = correlation;
    }

    return numCorrelations;
}

/*==========================================================================================*/
/**
 * Scan until we get a correlation of a single scan that goes over the tolerance level,
 * peaks then drops back down.
 */
static double findFirstMatch(const float *haystack, int haystackSize,
                             const float *needle, int needleSize, double threshold  )
{
    int ic;
    // How many correlations can we calculate?
    int numCorrelations = haystackSize - needleSize;
    double maxCorrelation = 0.0;
    int peakIndex = -1;
    double location = -1.0;
    const double backThresholdScaler = 0.5;

    for (ic = 0; ic < numCorrelations; ic++) {
        double correlation = calculateCorrelation(&haystack[ic], needle, needleSize);

        if( (correlation > maxCorrelation) ) {
            maxCorrelation = correlation;
            peakIndex = ic;
        }

        //printf("PaQa_FindFirstMatch: ic = %4d, correlation = %8f, maxSum = %8f\n",
        //    ic, correlation, maxSum );
        // Are we past what we were looking for?
        if((maxCorrelation > threshold) && (correlation < backThresholdScaler * maxCorrelation)) {
            location = peakIndex;
            break;
        }
    }

    return location;
}

typedef struct LatencyReport_s {
    double latencyInFrames;
    double confidence;
} LatencyReport;

// Apply a technique similar to Harmonic Product Spectrum Analysis to find echo fundamental.
// Using first echo instead of the original impulse for a better match.
static int measureLatencyFromEchos(const float *haystack, int haystackSize,
                            const float *needle, int needleSize,
                            LatencyReport *report) {
    const double threshold = 0.1;

    // Find first peak
    int first = (int) (findFirstMatch(haystack,
                                      haystackSize,
                                      needle,
                                      needleSize,
                                      threshold) + 0.5);

    // Use first echo as the needle for the other echos because
    // it will be more similar.
    needle = &haystack[first];
    int again = (int) (findFirstMatch(haystack,
                                      haystackSize,
                                      needle,
                                      needleSize,
                                      threshold) + 0.5);

    printf("first = %d, again at %d\n", first, again);
    first = again;

    // Allocate results array
    int remaining = haystackSize - first;
    const int maxReasonableLatencyFrames = 48000 * 2; // arbitrary but generous value
    int numCorrelations = std::min(remaining, maxReasonableLatencyFrames);
    float *correlations = new float[numCorrelations];
    float *harmonicSums = new float[numCorrelations](); // set to zero

    // Generate correlation for every position.
    numCorrelations = calculateCorrelations(&haystack[first], remaining,
                                            needle, needleSize,
                                            correlations, numCorrelations);

    // Add higher harmonics mapped onto lower harmonics.
    // This reinforces the "fundamental" echo.
    const int numEchoes = 10;
    for (int partial = 1; partial < numEchoes; partial++) {
        for (int i = 0; i < numCorrelations; i++) {
            harmonicSums[i / partial] += correlations[i] / partial;
        }
    }

    // Find highest peak in correlation array.
    float maxCorrelation = 0.0;
    float sumOfPeaks = 0.0;
    int peakIndex = 0;
    const int skip = MAX_ZEROTH_PARTIAL_BINS; // skip low bins
    for (int i = skip; i < numCorrelations; i++) {
        if (harmonicSums[i] > maxCorrelation) {
            maxCorrelation = harmonicSums[i];
            sumOfPeaks += maxCorrelation;
            peakIndex = i;
            printf("maxCorrelation = %f at %d\n", maxCorrelation, peakIndex);
        }
    }

    report->latencyInFrames = peakIndex;
    if (sumOfPeaks < 0.0001) {
        report->confidence = 0.0;
    } else {
        report->confidence = maxCorrelation / sumOfPeaks;
    }

    delete[] correlations;
    delete[] harmonicSums;
    return 0;
}

class AudioRecording
{
public:
    AudioRecording() {
    }
    ~AudioRecording() {
        delete[] mData;
    }

    void allocate(int maxFrames) {
        delete[] mData;
        mData = new float[maxFrames];
        mMaxFrames = maxFrames;
    }

    // Write SHORT data from the first channel.
    int write(int16_t *inputData, int inputChannelCount, int numFrames) {
        // stop at end of buffer
        if ((mFrameCounter + numFrames) > mMaxFrames) {
            numFrames = mMaxFrames - mFrameCounter;
        }
        for (int i = 0; i < numFrames; i++) {
            mData[mFrameCounter++] = inputData[i * inputChannelCount] * (1.0f / 32768);
        }
        return numFrames;
    }

    // Write FLOAT data from the first channel.
    int write(float *inputData, int inputChannelCount, int numFrames) {
        // stop at end of buffer
        if ((mFrameCounter + numFrames) > mMaxFrames) {
            numFrames = mMaxFrames - mFrameCounter;
        }
        for (int i = 0; i < numFrames; i++) {
            mData[mFrameCounter++] = inputData[i * inputChannelCount];
        }
        return numFrames;
    }

    int size() {
        return mFrameCounter;
    }

    float *getData() {
        return mData;
    }

    int save(const char *fileName, bool writeShorts = true) {
        int written = 0;
        const int chunkSize = 64;
        FILE *fid = fopen(fileName, "wb");
        if (fid == NULL) {
            return -errno;
        }

        if (writeShorts) {
            int16_t buffer[chunkSize];
            int32_t framesLeft = mFrameCounter;
            int32_t cursor = 0;
            while (framesLeft) {
                int32_t framesToWrite = framesLeft < chunkSize ? framesLeft : chunkSize;
                for (int i = 0; i < framesToWrite; i++) {
                    buffer[i] = (int16_t) (mData[cursor++] * 32767);
                }
                written += fwrite(buffer, sizeof(int16_t), framesToWrite, fid);
                framesLeft -= framesToWrite;
            }
        } else {
            written = (int) fwrite(mData, sizeof(float), mFrameCounter, fid);
        }
        fclose(fid);
        return written;
    }

private:
    float  *mData = nullptr;
    int32_t mFrameCounter = 0;
    int32_t mMaxFrames = 0;
};

// ====================================================================================
class LoopbackProcessor {
public:
    virtual ~LoopbackProcessor() = default;


    virtual void reset() {}

    virtual void process(float *inputData, int inputChannelCount,
                 float *outputData, int outputChannelCount,
                 int numFrames) = 0;


    virtual void report() = 0;

    virtual void printStatus() {};

    virtual bool isDone() {
        return false;
    }

    void setSampleRate(int32_t sampleRate) {
        mSampleRate = sampleRate;
    }

    int32_t getSampleRate() {
        return mSampleRate;
    }

    // Measure peak amplitude of buffer.
    static float measurePeakAmplitude(float *inputData, int inputChannelCount, int numFrames) {
        float peak = 0.0f;
        for (int i = 0; i < numFrames; i++) {
            float pos = fabs(*inputData);
            if (pos > peak) {
                peak = pos;
            }
            inputData += inputChannelCount;
        }
        return peak;
    }


private:
    int32_t mSampleRate = LOOPBACK_SAMPLE_RATE;
};

class PeakDetector {
public:
    float process(float input) {
        float output = mPrevious * mDecay;
        if (input > output) {
            output = input;
        }
        mPrevious = output;
        return output;
    }

private:
    float  mDecay = 0.99f;
    float  mPrevious = 0.0f;
};


static void printAudioScope(float sample) {
    const int maxStars = 80
    ; // arbitrary, fits on one line
    char c = '*';
    if (sample < -1.0) {
        sample = -1.0;
        c = '$';
    } else if (sample > 1.0) {
        sample = 1.0;
        c = '$';
    }
    int numSpaces = (int) (((sample + 1.0) * 0.5) * maxStars);
    for (int i = 0; i < numSpaces; i++) {
        putchar(' ');
    }
    printf("%c\n", c);
}

// ====================================================================================
/**
 * Measure latency given a loopback stream data.
 * Uses a state machine to cycle through various stages including:
 *
 */
class EchoAnalyzer : public LoopbackProcessor {
public:

    EchoAnalyzer() : LoopbackProcessor() {
        audioRecorder.allocate(2 * LOOPBACK_SAMPLE_RATE);
    }

    void reset() override {
        mDownCounter = 200;
        mLoopCounter = 0;
        mMeasuredLoopGain = 0.0f;
        mEchoGain = 1.0f;
        mState = STATE_INITIAL_SILENCE;
    }

    virtual bool isDone() {
        return mState == STATE_DONE;
    }

    void setGain(float gain) {
        mEchoGain = gain;
    }

    float getGain() {
        return mEchoGain;
    }

    void report() override {

        printf("EchoAnalyzer ---------------\n");
        printf(LOOPBACK_RESULT_TAG "measured.gain          = %f\n", mMeasuredLoopGain);
        printf(LOOPBACK_RESULT_TAG "echo.gain              = %f\n", mEchoGain);
        printf(LOOPBACK_RESULT_TAG "frame.count            = %d\n", mFrameCounter);
        printf(LOOPBACK_RESULT_TAG "test.state             = %d\n", mState);
        if (mMeasuredLoopGain >= 0.9999) {
            printf("   ERROR - clipping, turn down volume slightly\n");
        } else {
            const float *needle = s_Impulse;
            int needleSize = (int) (sizeof(s_Impulse) / sizeof(float));
            float *haystack = audioRecorder.getData();
            int haystackSize = audioRecorder.size();
            int result = measureLatencyFromEchos(haystack, haystackSize,
                                                 needle, needleSize,
                                                 &latencyReport);
            if (latencyReport.confidence < 0.01) {
                printf("   ERROR - confidence too low = %f\n", latencyReport.confidence);
            } else {
                double latencyMillis = 1000.0 * latencyReport.latencyInFrames / getSampleRate();
                printf(LOOPBACK_RESULT_TAG "latency.frames        = %8.2f\n", latencyReport.latencyInFrames);
                printf(LOOPBACK_RESULT_TAG "latency.msec          = %8.2f\n", latencyMillis);
                printf(LOOPBACK_RESULT_TAG "latency.confidence    = %8.6f\n", latencyReport.confidence);
            }
        }

        {
#define ECHO_FILENAME "/data/oboe_echo.raw"
            int written = audioRecorder.save(ECHO_FILENAME);
            printf("Echo wrote %d mono samples to %s on Android device\n", written, ECHO_FILENAME);
        }
    }

    void printStatus() override {
        printf("state = %d, echo gain = %f ", mState, mEchoGain);
    }

    static void sendImpulse(float *outputData, int outputChannelCount) {
        for (float sample : s_Impulse) {
            *outputData = sample;
            outputData += outputChannelCount;
        }
    }

    void process(float *inputData, int inputChannelCount,
                 float *outputData, int outputChannelCount,
                 int numFrames) override {
        int channelsValid = std::min(inputChannelCount, outputChannelCount);
        float peak = 0.0f;
        int numWritten;
        int numSamples;

        echo_state_t nextState = mState;

        switch (mState) {
            case STATE_INITIAL_SILENCE:
                // Output silence at the beginning.
                numSamples = numFrames * outputChannelCount;
                for (int i = 0; i < numSamples; i++) {
                    outputData[i] = 0;
                }
                if (mDownCounter-- <= 0) {
                    nextState = STATE_MEASURING_GAIN;
                    //printf("%5d: switch to STATE_MEASURING_GAIN\n", mLoopCounter);
                    mDownCounter = 8;
                }
                break;

            case STATE_MEASURING_GAIN:
                sendImpulse(outputData, outputChannelCount);
                peak = measurePeakAmplitude(inputData, inputChannelCount, numFrames);
                // If we get several in a row then go to next state.
                if (peak > mPulseThreshold) {
                    if (mDownCounter-- <= 0) {
                        nextState = STATE_WAITING_FOR_SILENCE;
                        //printf("%5d: switch to STATE_WAITING_FOR_SILENCE, measured peak = %f\n",
                        //       mLoopCounter, peak);
                        mDownCounter = 8;
                        mMeasuredLoopGain = peak;  // assumes original pulse amplitude is one
                        // Calculate gain that will give us a nice decaying echo.
                        mEchoGain = mDesiredEchoGain / mMeasuredLoopGain;
                    }
                } else {
                    mDownCounter = 8;
                }
                break;

            case STATE_WAITING_FOR_SILENCE:
                // Output silence.
                numSamples = numFrames * outputChannelCount;
                for (int i = 0; i < numSamples; i++) {
                    outputData[i] = 0;
                }
                peak = measurePeakAmplitude(inputData, inputChannelCount, numFrames);
                // If we get several in a row then go to next state.
                if (peak < mSilenceThreshold) {
                    if (mDownCounter-- <= 0) {
                        nextState = STATE_SENDING_PULSE;
                        //printf("%5d: switch to STATE_SENDING_PULSE\n", mLoopCounter);
                        mDownCounter = 8;
                    }
                } else {
                    mDownCounter = 8;
                }
                break;

            case STATE_SENDING_PULSE:
                audioRecorder.write(inputData, inputChannelCount, numFrames);
                sendImpulse(outputData, outputChannelCount);
                nextState = STATE_GATHERING_ECHOS;
                //printf("%5d: switch to STATE_GATHERING_ECHOS\n", mLoopCounter);
                break;

            case STATE_GATHERING_ECHOS:
                numWritten = audioRecorder.write(inputData, inputChannelCount, numFrames);
                peak = measurePeakAmplitude(inputData, inputChannelCount, numFrames);
                if (peak > mMeasuredLoopGain) {
                    mMeasuredLoopGain = peak;  // AGC might be raising gain so adjust it on the fly.
                    // Recalculate gain that will give us a nice decaying echo.
                    mEchoGain = mDesiredEchoGain / mMeasuredLoopGain;
                }
                // Echo input to output.
                for (int i = 0; i < numFrames; i++) {
                    int ic;
                    for (ic = 0; ic < channelsValid; ic++) {
                        outputData[ic] = inputData[ic] * mEchoGain;
                    }
                    for (; ic < outputChannelCount; ic++) {
                        outputData[ic] = 0;
                    }
                    inputData += inputChannelCount;
                    outputData += outputChannelCount;
                }
                if (numWritten  < numFrames) {
                    nextState = STATE_DONE;
                    //printf("%5d: switch to STATE_DONE\n", mLoopCounter);
                }
                break;

            case STATE_DONE:
            default:
                break;
        }

        mState = nextState;
        mLoopCounter++;
    }

private:

    enum echo_state_t {
        STATE_INITIAL_SILENCE,
        STATE_MEASURING_GAIN,
        STATE_WAITING_FOR_SILENCE,
        STATE_SENDING_PULSE,
        STATE_GATHERING_ECHOS,
        STATE_DONE
    };

    int           mDownCounter = 500;
    int           mLoopCounter = 0;
    int           mLoopStart = 1000;
    float         mPulseThreshold = 0.02f;
    float         mSilenceThreshold = 0.002f;
    float         mMeasuredLoopGain = 0.0f;
    float         mDesiredEchoGain = 0.95f;
    float         mEchoGain = 1.0f;
    echo_state_t  mState = STATE_INITIAL_SILENCE;
    int32_t       mFrameCounter = 0;

    AudioRecording     audioRecorder;
    LatencyReport      latencyReport;
    PeakDetector       mPeakDetector;
};


// ====================================================================================
/**
 * Output a steady sinewave and analyze the return signal.
 *
 * Use a cosine transform to measure the predicted magnitude and relative phase of the
 * looped back sine wave. Then generate a predicted signal and compare with the actual signal.
 */
class SineAnalyzer : public LoopbackProcessor {
public:

    void report() override {
        printf("SineAnalyzer ------------------\n");
        printf(LOOPBACK_RESULT_TAG "peak.amplitude     = %7.5f\n", mPeakAmplitude);
        printf(LOOPBACK_RESULT_TAG "sine.magnitude     = %7.5f\n", mMagnitude);
        printf(LOOPBACK_RESULT_TAG "phase.offset       = %7.5f\n", mPhaseOffset);
        printf(LOOPBACK_RESULT_TAG "ref.phase          = %7.5f\n", mPhase);
        printf(LOOPBACK_RESULT_TAG "frames.accumulated = %6d\n", mFramesAccumulated);
        printf(LOOPBACK_RESULT_TAG "sine.period        = %6d\n", mPeriod);
        printf(LOOPBACK_RESULT_TAG "test.state         = %6d\n", mState);
        printf(LOOPBACK_RESULT_TAG "frame.count        = %6d\n", mFrameCounter);
        // Did we ever get a lock?
        bool gotLock = (mState == STATE_LOCKED) || (mGlitchCount > 0);
        if (!gotLock) {
            printf("ERROR - failed to lock on reference sine tone\n");
        } else {
            // Only print if meaningful.
            printf(LOOPBACK_RESULT_TAG "glitch.count       = %6d\n", mGlitchCount);
        }
    }

    void printStatus() override {
        printf("  state = %d, glitches = %d,", mState, mGlitchCount);
    }

    double calculateMagnitude(double *phasePtr = NULL) {
        if (mFramesAccumulated == 0) {
            return 0.0;
        }
        double sinMean = mSinAccumulator / mFramesAccumulated;
        double cosMean = mCosAccumulator / mFramesAccumulated;
        double magnitude = 2.0 * sqrt( (sinMean * sinMean) + (cosMean * cosMean ));
        if( phasePtr != NULL )
        {
            double phase = M_PI_2 - atan2( sinMean, cosMean );
            *phasePtr = phase;
        }
        return magnitude;
    }

    /**
     * @param inputData contains microphone data with sine signal feedback
     * @param outputData contains the reference sine wave
     */
    void process(float *inputData, int inputChannelCount,
                 float *outputData, int outputChannelCount,
                 int numFrames) override {
        float sample;
        float peak = measurePeakAmplitude(inputData, inputChannelCount, numFrames);
        if (peak > mPeakAmplitude) {
            mPeakAmplitude = peak;
        }

        for (int i = 0; i < numFrames; i++) {
            float sample = inputData[i * inputChannelCount];

            float sinOut = sinf(mPhase);

            switch (mState) {
                case STATE_IMMUNE:
                case STATE_WAITING_FOR_SIGNAL:
                    break;
                case STATE_WAITING_FOR_LOCK:
                    mSinAccumulator += sample * sinOut;
                    mCosAccumulator += sample * cosf(mPhase);
                    mFramesAccumulated++;
                    // Must be a multiple of the period or the calculation will not be accurate.
                    if (mFramesAccumulated == mPeriod * 4) {
                        mPhaseOffset = 0.0;
                        mMagnitude = calculateMagnitude(&mPhaseOffset);
                        if (mMagnitude > mThreshold) {
                            if (fabs(mPreviousPhaseOffset - mPhaseOffset) < 0.001) {
                                mState = STATE_LOCKED;
                                //printf("%5d: switch to STATE_LOCKED\n", mFrameCounter);
                            }
                            mPreviousPhaseOffset = mPhaseOffset;
                        }
                        resetAccumulator();
                    }
                    break;

                case STATE_LOCKED: {
                    // Predict next sine value
                    float predicted = sinf(mPhase + mPhaseOffset) * mMagnitude;
                    // printf("    predicted = %f, actual = %f\n", predicted, sample);

                    float diff = predicted - sample;
                    if (fabs(diff) > mTolerance) {
                        mGlitchCount++;
                        //printf("%5d: Got a glitch # %d, predicted = %f, actual = %f\n",
                        //       mFrameCounter, mGlitchCount, predicted, sample);
                        mState = STATE_IMMUNE;
                        //printf("%5d: switch to STATE_IMMUNE\n", mFrameCounter);
                        mDownCounter = mPeriod;  // Set duration of IMMUNE state.
                    }
                } break;
            }

            // Output sine wave so we can measure it.
            outputData[i * outputChannelCount] = (sinOut * mOutputAmplitude)
                    + (mWhiteNoise.nextRandomDouble() * mNoiseAmplitude);
            // printf("%5d: sin(%f) = %f, %f\n", i, mPhase, sinOut,  mPhaseIncrement);

            // advance and wrap phase
            mPhase += mPhaseIncrement;
            if (mPhase > M_PI) {
                mPhase -= (2.0 * M_PI);
            }

            mFrameCounter++;
        }

        // Do these once per buffer.
        switch (mState) {
            case STATE_IMMUNE:
                mDownCounter -= numFrames;
                if (mDownCounter <= 0) {
                    mState = STATE_WAITING_FOR_SIGNAL;
                    //printf("%5d: switch to STATE_WAITING_FOR_SIGNAL\n", mFrameCounter);
                }
                break;
            case STATE_WAITING_FOR_SIGNAL:
                if (peak > mThreshold) {
                    mState = STATE_WAITING_FOR_LOCK;
                    //printf("%5d: switch to STATE_WAITING_FOR_LOCK\n", mFrameCounter);
                    resetAccumulator();
                }
                break;
            case STATE_WAITING_FOR_LOCK:
            case STATE_LOCKED:
                break;
        }

    }

    void resetAccumulator() {
        mFramesAccumulated = 0;
        mSinAccumulator = 0.0;
        mCosAccumulator = 0.0;
    }

    void reset() override {
        mGlitchCount = 0;
        mState = STATE_IMMUNE;
        mPhaseIncrement = 2.0 * M_PI / mPeriod;
        printf("phaseInc = %f for period %d\n", mPhaseIncrement, mPeriod);
        resetAccumulator();
    }

private:

    enum sine_state_t {
        STATE_IMMUNE,
        STATE_WAITING_FOR_SIGNAL,
        STATE_WAITING_FOR_LOCK,
        STATE_LOCKED
    };

    int     mPeriod = 79;
    double  mPhaseIncrement = 0.0;
    double  mPhase = 0.0;
    double  mPhaseOffset = 0.0;
    double  mPreviousPhaseOffset = 0.0;
    double  mMagnitude = 0.0;
    double  mThreshold = 0.005;
    double  mTolerance = 0.01;
    int32_t mFramesAccumulated = 0;
    double  mSinAccumulator = 0.0;
    double  mCosAccumulator = 0.0;
    int32_t mGlitchCount = 0;
    double  mPeakAmplitude = 0.0;
    int     mDownCounter = 4000;
    int32_t mFrameCounter = 0;
    float   mOutputAmplitude = 0.75;

    int32_t mZeroCrossings = 0;

    PseudoRandom  mWhiteNoise;
    float   mNoiseAmplitude = 0.00; // Used to experiment with warbling caused by DRC.

    sine_state_t  mState = STATE_IMMUNE;
};


#undef LOOPBACK_SAMPLE_RATE
#undef LOOPBACK_RESULT_TAG

#endif /* AAUDIO_EXAMPLES_LOOPBACK_ANALYSER_H */
