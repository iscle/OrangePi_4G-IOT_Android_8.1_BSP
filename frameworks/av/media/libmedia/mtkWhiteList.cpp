/*
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "WhiteList"
#include <utils/Log.h>

#include <fcntl.h>
#include <inttypes.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <media/mediaplayer.h>
#include <utils/String8.h>

#include "mtkWhiteList.h"

namespace android {

int addWriteList(const sp<IMediaPlayer>& player)
{
    sp<IMediaPlayer> p;
    char process_name[40] = {0};
    char filename[25] = {0};
    FILE *f = NULL;
    sprintf(filename, "/proc/%d/cmdline", getpid());

    p = player;
    f = fopen(filename, "r");

    if (NULL == f){
        *process_name = '\0';
        //ALOGV("Fail to open file %s", filename);
        return 0;
    }
    if (!fgets(process_name, sizeof(process_name), f)){
        *process_name = '\0';
        fclose(f);
        f = NULL;
        //ALOGV("Fail to fgets file %s", filename);
        return 0;
    }
    if(NULL != f) {
        fclose(f);
    }

    //ALOGV("process_name %s", process_name);
    if((!strncasecmp(process_name, "com.android.music", 17)) ||
        (!strncasecmp(process_name, "com.google.android.music", 24))) {

        Parcel requestPlayback;
        requestPlayback.writeInt32(1);
        if (p != NULL) {
            status_t err = p->setParameter(KEY_PARAMETER_PLAYBACK_WHITELIST, requestPlayback);
            if (err == OK) {
                //ALOGV("add %s to white list", process_name);
                return 1;
            }
        }
    }

    return 0;
}

}
