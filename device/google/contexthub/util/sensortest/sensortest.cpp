/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <android/sensor.h>

struct SensorConfig {
    int listIndex;
    int type;
    int32_t rate;
    int64_t reportLatency;
    bool receivedEvent;
};


ASensorManager *mSensorManager;
ASensorList mSensorList;
int mNumSensors;
bool mContinuousMode;
SensorConfig mSensorConfigList[16];
int mNumSensorConfigs;

void showHelp()
{
    printf("Usage: sensortest [-h] [-l] [-e <type> <rate_usecs>] [-b <type> <rate_usecs> <batch_usecs>] [-c]\n");
}

void printSensorList()
{
    int prevMinType = -1;
    int currMinType;
    int currMinIndex = 0;

    printf("[Type] - Name\n");

    for (int i = 0; i < mNumSensors; i++) {
        currMinType = INT_MAX;

        for (int j = 0; j < mNumSensors; j++) {
            if ((ASensor_getType(mSensorList[j]) > prevMinType) &&
                (ASensor_getType(mSensorList[j]) < currMinType)) {
                currMinType = ASensor_getType(mSensorList[j]);
                currMinIndex = j;
            }
        }

        printf("[%d] = \"%s\"\n", currMinType, ASensor_getName(mSensorList[currMinIndex]));

        prevMinType = currMinType;
    }
}

int findSensorTypeInSensorList(int type)
{
    for (int i = 0; i < mNumSensors; i++) {
        if (ASensor_getType(mSensorList[i]) == type) {
            return i;
        }
    }

    return -1;
}

int findSensorTypeInConfigList(int type)
{
    for (int i = 0; i < mNumSensorConfigs; i++) {
        if (mSensorConfigList[i].type == type) {
            return i;
        }
    }

    return -1;
}

bool parseArguments(int argc, char **argv)
{
    int currArgumentIndex = 1;
    int sensorIndex;
    int existingSensorConfigIndex;

    mNumSensorConfigs = 0;

    while (currArgumentIndex < argc) {
        if (!strcmp(argv[currArgumentIndex], "-h")) {
            return false;
        } else if (!strcmp(argv[currArgumentIndex], "-l")) {
            printSensorList();
            currArgumentIndex++;
        } else if (!strcmp(argv[currArgumentIndex], "-e")) {
            if (currArgumentIndex + 2 >= argc) {
                printf ("Not enough arguments for enable option\n");
                return false;
            }

            if ((sensorIndex = findSensorTypeInSensorList(atoi(argv[currArgumentIndex+1]))) < 0) {
                printf ("No sensor found with type \"%d\"\n", atoi(argv[currArgumentIndex+1]));
                return false;
            }

            existingSensorConfigIndex = findSensorTypeInConfigList(atoi(argv[currArgumentIndex+1]));

            if (existingSensorConfigIndex >= 0) {
                printf("Replacing previous config for sensor type %d\n", atoi(argv[currArgumentIndex+1]));
                mSensorConfigList[existingSensorConfigIndex] = (SensorConfig) {
                    .listIndex = sensorIndex,
                    .type = atoi(argv[currArgumentIndex+1]),
                    .rate = atoi(argv[currArgumentIndex+2]),
                    .reportLatency = 0,
                    .receivedEvent = false
                };
            } else {
                mSensorConfigList[(mNumSensorConfigs)++] = (SensorConfig) {
                    .listIndex = sensorIndex,
                    .type = atoi(argv[currArgumentIndex+1]),
                    .rate = atoi(argv[currArgumentIndex+2]),
                    .reportLatency = 0,
                    .receivedEvent = false
                };
            }

            currArgumentIndex += 3;
        } else if (!strcmp(argv[currArgumentIndex], "-b")) {
            if (currArgumentIndex + 3 >= argc) {
                printf ("Not enough arguments for batch option\n");
                return false;
            }

            if ((sensorIndex = findSensorTypeInSensorList(atoi(argv[currArgumentIndex+1]))) < 0) {
                printf ("No sensor found with type \"%d\"\n", atoi(argv[currArgumentIndex+1]));
                return false;
            }

            existingSensorConfigIndex = findSensorTypeInConfigList(atoi(argv[currArgumentIndex+1]));

            if (existingSensorConfigIndex >= 0) {
                printf("Replacing previous config for sensor type %d\n", atoi(argv[currArgumentIndex+1]));
                mSensorConfigList[existingSensorConfigIndex] = (SensorConfig) {
                    .listIndex = sensorIndex,
                    .type = atoi(argv[currArgumentIndex+1]),
                    .rate = atoi(argv[currArgumentIndex+2]),
                    .reportLatency = atoi(argv[currArgumentIndex+3]),
                    .receivedEvent = false
                };
            } else {
                mSensorConfigList[(mNumSensorConfigs)++] = (SensorConfig) {
                    .listIndex = sensorIndex,
                    .type = atoi(argv[currArgumentIndex+1]),
                    .rate = atoi(argv[currArgumentIndex+2]),
                    .reportLatency = atoi(argv[currArgumentIndex+3]),
                    .receivedEvent = false
                };
            }

            currArgumentIndex += 4;
        } else if (!strcmp(argv[currArgumentIndex], "-c")) {
            mContinuousMode = true;
            currArgumentIndex++;
        } else {
            printf("Invalid argument \"%s\"\n", argv[currArgumentIndex]);
            return false;
        }
    }

    return true;
}

bool hasReceivedAllEvents()
{
    for (int i = 0; i < mNumSensorConfigs; i++) {
        if (!mSensorConfigList[i].receivedEvent) {
            return false;
        }
    }

    return true;
};

int main(int argc, char **argv) {
    int numSensorEvents;
    ASensorEvent sensorEvents[16];
    int configListIndex;

    mSensorManager = ASensorManager_getInstanceForPackage("");
    mNumSensors = ASensorManager_getSensorList(mSensorManager, &mSensorList);

    if ((argc == 1) || !parseArguments(argc, argv)) {
        showHelp();
        return -1;
    }

    if (mNumSensorConfigs <= 0)
        return 0;

    ALooper *mLooper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    ASensorEventQueue *sensorEventQueue = ASensorManager_createEventQueue(mSensorManager, mLooper, 0, NULL, NULL);

    for (int i = 0; i < mNumSensorConfigs; i++) {
        if (ASensorEventQueue_registerSensor(sensorEventQueue, mSensorList[mSensorConfigList[i].listIndex],
                                             mSensorConfigList[i].rate, mSensorConfigList[i].reportLatency) < 0) {
            printf("Unable to register sensor %d with rate %d and report latency %" PRId64 "\n",
                   mSensorConfigList[i].listIndex, mSensorConfigList[i].rate,
                   mSensorConfigList[i].reportLatency);
        }

    }

    while (mContinuousMode || !hasReceivedAllEvents()) {
        if ((numSensorEvents = ASensorEventQueue_getEvents(sensorEventQueue, sensorEvents, 16)) < 0) {
            printf("An error occurred while polling for events\n");
            break;
        } else if (numSensorEvents > 0) {
            for (int i = 0; i < numSensorEvents; i++) {
                if ((configListIndex = findSensorTypeInConfigList(sensorEvents[i].type)) < 0) {
                    printf("Received unexpected event for type %d\n", sensorEvents[i].type);
                    break;
                }

                if (mContinuousMode || !mSensorConfigList[configListIndex].receivedEvent) {
                    printf("[%d] = %f, %f, %f @ %" PRId64 "\n", sensorEvents[i].type,
                           sensorEvents[i].data[0], sensorEvents[i].data[1],
                           sensorEvents[i].data[2], sensorEvents[i].timestamp);

                    mSensorConfigList[configListIndex].receivedEvent = true;

                    if (!mContinuousMode) {
                        ASensorEventQueue_disableSensor(sensorEventQueue, mSensorList[mSensorConfigList[configListIndex].listIndex]);
                    }
                }
            }
        }

        fflush(stdout);
    }

    ASensorManager_destroyEventQueue(mSensorManager, sensorEventQueue);

    return 0;
}
