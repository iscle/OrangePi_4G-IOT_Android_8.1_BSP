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

// Play sine waves using an AAudio callback.
// If a disconnection occurs then reopen the stream on the new device.

#include <assert.h>
#include <unistd.h>
#include <stdlib.h>
#include <sched.h>
#include <stdio.h>
#include <math.h>
#include <string.h>
#include <time.h>
#include <aaudio/AAudio.h>
#include "AAudioExampleUtils.h"
#include "AAudioSimplePlayer.h"
#include "../../utils/AAudioSimplePlayer.h"

/**
 * Open stream, play some sine waves, then close the stream.
 *
 * @param argParser
 * @return AAUDIO_OK or negative error code
 */
static aaudio_result_t testOpenPlayClose(AAudioArgsParser &argParser)
{
    SineThreadedData_t myData;
    AAudioSimplePlayer &player = myData.simplePlayer;
    aaudio_result_t    result = AAUDIO_OK;
    bool               disconnected = false;
    int64_t            startedAtNanos;

    printf("----------------------- run complete test --------------------------\n");
    myData.schedulerChecked = false;
    myData.callbackCount = 0;
    myData.forceUnderruns = false; // set true to test AAudioStream_getXRunCount()

    result = player.open(argParser,
                         SimplePlayerDataCallbackProc, SimplePlayerErrorCallbackProc, &myData);
    if (result != AAUDIO_OK) {
        fprintf(stderr, "ERROR -  player.open() returned %d\n", result);
        goto error;
    }

    argParser.compareWithStream(player.getStream());

    // Setup sine wave generators.
    {
        int32_t actualSampleRate = player.getSampleRate();
        myData.sineOsc1.setup(440.0, actualSampleRate);
        myData.sineOsc1.setSweep(300.0, 600.0, 5.0);
        myData.sineOsc1.setAmplitude(0.2);
        myData.sineOsc2.setup(660.0, actualSampleRate);
        myData.sineOsc2.setSweep(350.0, 900.0, 7.0);
        myData.sineOsc2.setAmplitude(0.2);
    }

#if 0
    //  writes not allowed for callback streams
    result = player.prime(); // FIXME crashes AudioTrack.cpp
    if (result != AAUDIO_OK) {
        fprintf(stderr, "ERROR - player.prime() returned %d\n", result);
        goto error;
    }
#endif

    result = player.start();
    if (result != AAUDIO_OK) {
        fprintf(stderr, "ERROR - player.start() returned %d\n", result);
        goto error;
    }

    // Play a sine wave in the background.
    printf("Sleep for %d seconds while audio plays in a callback thread.\n",
           argParser.getDurationSeconds());
    startedAtNanos = getNanoseconds(CLOCK_MONOTONIC);
    for (int second = 0; second < argParser.getDurationSeconds(); second++)
    {
        // Sleep a while. Wake up early if there is an error, for example a DISCONNECT.
        long ret = myData.waker.wait(AAUDIO_OK, NANOS_PER_SECOND);
        int64_t millis = (getNanoseconds(CLOCK_MONOTONIC) - startedAtNanos) / NANOS_PER_MILLISECOND;
        result = myData.waker.get();
        printf("wait() returns %ld, aaudio_result = %d, at %6d millis"
               ", second = %d, framesWritten = %8d, underruns = %d\n",
               ret, result, (int) millis,
               second,
               (int) AAudioStream_getFramesWritten(player.getStream()),
               (int) AAudioStream_getXRunCount(player.getStream()));
        if (result != AAUDIO_OK) {
            if (result == AAUDIO_ERROR_DISCONNECTED) {
                disconnected = true;
            }
            break;
        }
    }
    printf("AAudio result = %d = %s\n", result, AAudio_convertResultToText(result));

    printf("call stop() callback # = %d\n", myData.callbackCount);
    result = player.stop();
    if (result != AAUDIO_OK) {
        goto error;
    }
    printf("call close()\n");
    result = player.close();
    if (result != AAUDIO_OK) {
        goto error;
    }

    for (int i = 0; i < myData.timestampCount; i++) {
        Timestamp *timestamp = &myData.timestamps[i];
        bool retro = (i > 0 &&
                      ((timestamp->position < (timestamp - 1)->position)
                       || ((timestamp->nanoseconds < (timestamp - 1)->nanoseconds))));
        const char *message = retro ? "  <= RETROGRADE!" : "";
        printf("Timestamp %3d : %8lld, %8lld %s\n", i,
               (long long) timestamp->position,
               (long long) timestamp->nanoseconds,
               message);
    }

    if (myData.schedulerChecked) {
        printf("scheduler = 0x%08x, SCHED_FIFO = 0x%08X\n",
               myData.scheduler,
               SCHED_FIFO);
    }

    printf("min numFrames = %8d\n", (int) myData.minNumFrames);
    printf("max numFrames = %8d\n", (int) myData.maxNumFrames);

    printf("SUCCESS\n");
error:
    player.close();
    return disconnected ? AAUDIO_ERROR_DISCONNECTED : result;
}

int main(int argc, const char **argv)
{
    AAudioArgsParser   argParser;
    aaudio_result_t    result;

    // Make printf print immediately so that debug info is not stuck
    // in a buffer if we hang or crash.
    setvbuf(stdout, nullptr, _IONBF, (size_t) 0);

    printf("%s - Play a sine sweep using an AAudio callback V0.1.2\n", argv[0]);

    if (argParser.parseArgs(argc, argv)) {
        return EXIT_FAILURE;
    }

    // Keep looping until we can complete the test without disconnecting.
    while((result = testOpenPlayClose(argParser)) == AAUDIO_ERROR_DISCONNECTED);

    return (result) ? EXIT_FAILURE : EXIT_SUCCESS;
}
