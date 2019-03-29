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

#define LOG_TAG "ITS-StatsImage-JNI"
// #define LOG_NDEBUG 0
#include <android/log.h>
#include <utils/Log.h>

#include <jni.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <inttypes.h>
#include <string.h>

jfloatArray com_android_cts_verifier_camera_its_computeStatsImage(JNIEnv* env, jobject thiz,
        // The full pixel array read off the sensor.
        jbyteArray img,
        jint width, jint height,
        // The active array crop region.
        jint aax, jint aay, jint aaw, jint aah,
        // The size of each grid cell to use when computing stats from the active array.
        jint gridWidth, jint gridHeight)
{
    int bufSize = (int)(env->GetArrayLength(img));
    unsigned char *buf = (unsigned char*)env->GetByteArrayElements(img, /*is_copy*/NULL);

    // Size of the full raw image pixel array.
    const int paw = width;
    const int pah = height;
    // Size of each grid cell.
    const int gw = gridWidth;
    const int gh = gridHeight;
    // Number of grid cells (rounding down to full cells only at right+bottom edges).
    const int ngx = aaw / gw;
    const int ngy = aah / gh;

    float *mean = new float[ngy*ngx*4];
    float *var = new float[ngy*ngx*4];
    // For each grid cell ...
    for (int gy = 0; gy < ngy; gy++) {
        for (int gx = 0; gx < ngx; gx++) {
            float sum[4] = {0};
            float sumSq[4] = {0};
            int count[4] = {0};
            // Iterate over pixels in the grid cell
            for (int y = aay+gy*gh; y < aay+(gy+1)*gh; y++) {
                int chnOffset = (y & 0x1) * 2;
                unsigned char *pbuf = buf + 2*y*paw + 2*gx*gw + 2*aax;
                for (int x = aax+gx*gw; x < aax+(gx+1)*gw; x++) {
                    // input is RAW16
                    int byte0 = *pbuf++;
                    int byte1 = *pbuf++;
                    int pixelValue = (byte1 << 8) | byte0;
                    int ch = chnOffset + (x & 1);
                    sum[ch] += pixelValue;
                    sumSq[ch] += pixelValue * pixelValue;
                    count[ch] += 1;
                }
            }
            for (int ch = 0; ch < 4; ch++) {
                float m = (float)sum[ch] / count[ch];
                float mSq = (float)sumSq[ch] / count[ch];
                mean[gy*ngx*4 + gx*4 + ch] = m;
                var[gy*ngx*4 + gx*4 + ch] = mSq - m*m;
            }
        }
    }

    jfloatArray ret = env->NewFloatArray(ngx*ngy*4*2);
    env->SetFloatArrayRegion(ret, 0, ngx*ngy*4, (float*)mean);
    env->SetFloatArrayRegion(ret, ngx*ngy*4, ngx*ngy*4, (float*)var);
    delete [] mean;
    delete [] var;
    return ret;
}

static JNINativeMethod gMethods[] = {
    {  "computeStatsImage", "([BIIIIIIII)[F",
            (void *) com_android_cts_verifier_camera_its_computeStatsImage  },
};

int register_com_android_cts_verifier_camera_its_StatsImage(JNIEnv* env)
{
    jclass clazz = env->FindClass("com/android/cts/verifier/camera/its/StatsImage");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
