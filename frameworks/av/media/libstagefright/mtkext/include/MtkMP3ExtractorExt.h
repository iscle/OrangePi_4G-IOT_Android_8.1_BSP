/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef MTK_MP3_EXTRACTOR_EXT_H_
#define MTK_MP3_EXTRACTOR_EXT_H_

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaExtractor.h>

namespace android {

class MtkMP3SourceExt{
    public:
        MtkMP3SourceExt();
        static int getMultiFrameSize(const sp<DataSource> &source, off64_t pos,
                uint32_t fixedHeader, size_t *frame_sizes, int *num_samples,
                int *sample_rate, int request_frames);

    protected:
        virtual ~MtkMP3SourceExt();

    private:
        MtkMP3SourceExt(const MtkMP3SourceExt &);
        MtkMP3SourceExt &operator=(const MtkMP3SourceExt &);
};

int isJointStereoMp3(const sp<DataSource> &source, off64_t pos,
        uint32_t fixedHeader);
}
#endif
