/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef UTILITY_AAUDIO_UTILITIES_H
#define UTILITY_AAUDIO_UTILITIES_H

#include <algorithm>
#include <functional>
#include <stdint.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <hardware/audio.h>

#include "aaudio/AAudio.h"

/**
 * Convert an AAudio result into the closest matching Android status.
 */
android::status_t AAudioConvert_aaudioToAndroidStatus(aaudio_result_t result);

/**
 * Convert an Android status into the closest matching AAudio result.
 */
aaudio_result_t AAudioConvert_androidToAAudioResult(android::status_t status);

/**
 * Convert an array of floats to an array of int16_t.
 *
 * @param source
 * @param destination
 * @param numSamples number of values in the array
 * @param amplitude level between 0.0 and 1.0
 */
void AAudioConvert_floatToPcm16(const float *source,
                                int16_t *destination,
                                int32_t numSamples,
                                float amplitude);

/**
 * Convert floats to int16_t and scale by a linear ramp.
 *
 * The ramp stops just short of reaching amplitude2 so that the next
 * ramp can start at amplitude2 without causing a discontinuity.
 *
 * @param source
 * @param destination
 * @param numFrames
 * @param samplesPerFrame AKA number of channels
 * @param amplitude1 level at start of ramp, between 0.0 and 1.0
 * @param amplitude2 level past end of ramp, between 0.0 and 1.0
 */
void AAudioConvert_floatToPcm16(const float *source,
                                int16_t *destination,
                                int32_t numFrames,
                                int32_t samplesPerFrame,
                                float amplitude1,
                                float amplitude2);

/**
 * Convert int16_t array to float array ranging from -1.0 to +1.0.
 * @param source
 * @param destination
 * @param numSamples
 */
//void AAudioConvert_pcm16ToFloat(const int16_t *source, int32_t numSamples,
//                                float *destination);

/**
 *
 * Convert int16_t array to float array ranging from +/- amplitude.
 * @param source
 * @param destination
 * @param numSamples
 * @param amplitude
 */
void AAudioConvert_pcm16ToFloat(const int16_t *source,
                                float *destination,
                                int32_t numSamples,
                                float amplitude);

/**
 * Convert floats to int16_t and scale by a linear ramp.
 *
 * The ramp stops just short of reaching amplitude2 so that the next
 * ramp can start at amplitude2 without causing a discontinuity.
 *
 * @param source
 * @param destination
 * @param numFrames
 * @param samplesPerFrame AKA number of channels
 * @param amplitude1 level at start of ramp, between 0.0 and 1.0
 * @param amplitude2 level at end of ramp, between 0.0 and 1.0
 */
void AAudioConvert_pcm16ToFloat(const int16_t *source,
                                float *destination,
                                int32_t numFrames,
                                int32_t samplesPerFrame,
                                float amplitude1,
                                float amplitude2);

/**
 * Scale floats by a linear ramp.
 *
 * The ramp stops just short of reaching amplitude2 so that the next
 * ramp can start at amplitude2 without causing a discontinuity.
 *
 * @param source
 * @param destination
 * @param numFrames
 * @param samplesPerFrame
 * @param amplitude1
 * @param amplitude2
 */
void AAudio_linearRamp(const float *source,
                       float *destination,
                       int32_t numFrames,
                       int32_t samplesPerFrame,
                       float amplitude1,
                       float amplitude2);

/**
 * Scale int16_t's by a linear ramp.
 *
 * The ramp stops just short of reaching amplitude2 so that the next
 * ramp can start at amplitude2 without causing a discontinuity.
 *
 * @param source
 * @param destination
 * @param numFrames
 * @param samplesPerFrame
 * @param amplitude1
 * @param amplitude2
 */
void AAudio_linearRamp(const int16_t *source,
                       int16_t *destination,
                       int32_t numFrames,
                       int32_t samplesPerFrame,
                       float amplitude1,
                       float amplitude2);

/**
 * Calculate the number of bytes and prevent numeric overflow.
 * @param numFrames frame count
 * @param bytesPerFrame size of a frame in bytes
 * @param sizeInBytes total size in bytes
 * @return AAUDIO_OK or negative error, eg. AAUDIO_ERROR_OUT_OF_RANGE
 */
int32_t AAudioConvert_framesToBytes(int32_t numFrames,
                                            int32_t bytesPerFrame,
                                            int32_t *sizeInBytes);

audio_format_t AAudioConvert_aaudioToAndroidDataFormat(aaudio_format_t aaudio_format);

aaudio_format_t AAudioConvert_androidToAAudioDataFormat(audio_format_t format);

/**
 * @return the size of a sample of the given format in bytes or AAUDIO_ERROR_ILLEGAL_ARGUMENT
 */
int32_t AAudioConvert_formatToSizeInBytes(aaudio_format_t format);


// Note that this code may be replaced by Settings or by some other system configuration tool.

#define AAUDIO_PROP_MMAP_POLICY           "aaudio.mmap_policy"

/**
 * Read system property.
 * @return AAUDIO_UNSPECIFIED, AAUDIO_POLICY_NEVER or AAUDIO_POLICY_AUTO or AAUDIO_POLICY_ALWAYS
 */
int32_t AAudioProperty_getMMapPolicy();

#define AAUDIO_PROP_MMAP_EXCLUSIVE_POLICY "aaudio.mmap_exclusive_policy"

/**
 * Read system property.
 * @return AAUDIO_UNSPECIFIED, AAUDIO_POLICY_NEVER or AAUDIO_POLICY_AUTO or AAUDIO_POLICY_ALWAYS
 */
int32_t AAudioProperty_getMMapExclusivePolicy();

#define AAUDIO_PROP_MIXER_BURSTS           "aaudio.mixer_bursts"

/**
 * Read system property.
 * @return number of bursts per AAudio service mixer cycle
 */
int32_t AAudioProperty_getMixerBursts();

#define AAUDIO_PROP_HW_BURST_MIN_USEC      "aaudio.hw_burst_min_usec"

/**
 * Read a system property that specifies the number of extra microseconds that a thread
 * should sleep when waiting for another thread to service a FIFO. This is used
 * to avoid the waking thread from being overly optimistic about the other threads
 * wakeup timing. This value should be set high enough to cover typical scheduling jitter
 * for a real-time thread.
 *
 * @return number of microseconds to delay the wakeup.
 */
int32_t AAudioProperty_getWakeupDelayMicros();

#define AAUDIO_PROP_WAKEUP_DELAY_USEC      "aaudio.wakeup_delay_usec"

/**
 * Read a system property that specifies the minimum sleep time when polling the FIFO.
 *
 * @return minimum number of microseconds to sleep.
 */
int32_t AAudioProperty_getMinimumSleepMicros();

#define AAUDIO_PROP_MINIMUM_SLEEP_USEC      "aaudio.minimum_sleep_usec"

/**
 * Read system property.
 * This is handy in case the DMA is bursting too quickly for the CPU to keep up.
 * For example, there may be a DMA burst every 100 usec but you only
 * want to feed the MMAP buffer every 2000 usec.
 *
 * This will affect the framesPerBurst for an MMAP stream.
 *
 * @return minimum number of microseconds for a MMAP HW burst
 */
int32_t AAudioProperty_getHardwareBurstMinMicros();

/**
 * Try a function f until it returns true.
 *
 * The function is always called at least once.
 *
 * @param f the function to evaluate, which returns a bool.
 * @param times the number of times to evaluate f.
 * @param sleepMs the sleep time per check of f, if greater than 0.
 * @return true if f() eventually returns true.
 */
static inline bool AAudio_tryUntilTrue(
        std::function<bool()> f, int times, int sleepMs) {
    static const useconds_t US_PER_MS = 1000;

    sleepMs = std::max(sleepMs, 0);
    for (;;) {
        if (f()) return true;
        if (times <= 1) return false;
        --times;
        usleep(sleepMs * US_PER_MS);
    }
}


/**
 * Simple double buffer for a structure that can be written occasionally and read occasionally.
 * This allows a SINGLE writer with multiple readers.
 *
 * It is OK if the FIFO overflows and we lose old values.
 * It is also OK if we read an old value.
 * Thread may return a non-atomic result if the other thread is rapidly writing
 * new values on another core.
 */
template <class T>
class SimpleDoubleBuffer {
public:
    SimpleDoubleBuffer()
            : mValues() {}

    __attribute__((no_sanitize("integer")))
    void write(T value) {
        int index = mCounter.load() & 1;
        mValues[index] = value;
        mCounter++; // Increment AFTER updating storage, OK if it wraps.
    }

    /**
     * This should only be called by the same thread that calls write() or when
     * no other thread is calling write.
     */
    void clear() {
        mCounter.store(0);
    }

    T read() const {
        T result;
        int before;
        int after;
        int timeout = 3;
        do {
            // Check to see if a write occurred while were reading.
            before = mCounter.load();
            int index = (before & 1) ^ 1;
            result = mValues[index];
            after = mCounter.load();
        } while ((after != before) && (after > 0) && (--timeout > 0));
        return result;
    }

    /**
     * @return true if at least one value has been written
     */
    bool isValid() const {
        return mCounter.load() > 0;
    }

private:
    T                    mValues[2];
    std::atomic<int>     mCounter{0};
};

class Timestamp {
public:
    Timestamp()
            : mPosition(0)
            , mNanoseconds(0) {}
    Timestamp(int64_t position, int64_t nanoseconds)
            : mPosition(position)
            , mNanoseconds(nanoseconds) {}

    int64_t getPosition() const { return mPosition; }

    int64_t getNanoseconds() const { return mNanoseconds; }

private:
    // These cannot be const because we need to implement the copy assignment operator.
    int64_t mPosition;
    int64_t mNanoseconds;
};


/**
 * Pass a request to another thread.
 * This is used when one thread, A, wants another thread, B, to do something.
 * A naive approach would be for A to set a flag and for B to clear it when done.
 * But that creates a race condition. This technique avoids the race condition.
 *
 * Assumes only one requester and one acknowledger.
 */
class AtomicRequestor {
public:

    __attribute__((no_sanitize("integer")))
    void request() {
        mRequested++;
    }

    __attribute__((no_sanitize("integer")))
    bool isRequested() {
        return (mRequested.load() - mAcknowledged.load()) > 0;
    }

    __attribute__((no_sanitize("integer")))
    void acknowledge() {
        mAcknowledged++;
    }

private:
    std::atomic<int> mRequested{0};
    std::atomic<int> mAcknowledged{0};
};
#endif //UTILITY_AAUDIO_UTILITIES_H
