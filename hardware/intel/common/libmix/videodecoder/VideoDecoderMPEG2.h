/*
* Copyright (c) 2009-2011 Intel Corporation.  All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#ifndef VIDEO_DECODER_MPEG2_H_
#define VIDEO_DECODER_MPEG2_H_

#include "VideoDecoderBase.h"

#ifndef TIANMI_DEBUG
#define TIANMI_DEBUG
#endif


class VideoDecoderMPEG2 : public VideoDecoderBase {
public:
    VideoDecoderMPEG2(const char *mimeType);
    virtual ~VideoDecoderMPEG2();

    virtual Decode_Status start(VideoConfigBuffer *buffer);
    virtual void stop(void);
    virtual void flush(void);
    virtual Decode_Status decode(VideoDecodeBuffer *buffer);

protected:
    virtual Decode_Status checkHardwareCapability();

private:
    Decode_Status decodeFrame(VideoDecodeBuffer *buffer, vbp_data_mpeg2 *data);
    //Decode_Status beginDecodingFrame(vbp_data_mp42 *data);
    //Decode_Status continueDecodingFrame(vbp_data_mp42 *data);
    Decode_Status decodePicture(vbp_data_mpeg2 *data, int picIndex);
    Decode_Status decodeSlice(vbp_data_mpeg2 *data, int picIndex, int slcIndex);
    Decode_Status setReference(VAPictureParameterBufferMPEG2 *picParam);
    Decode_Status startVA(vbp_data_mpeg2 *data);
    void updateFormatInfo(vbp_data_mpeg2 *data);
    inline Decode_Status allocateVABufferIDs(int32_t number);

private:
    enum {
        MPEG2_PICTURE_TYPE_I = 1,
        MPEG2_PICTURE_TYPE_P = 2,
        MPEG2_PICTURE_TYPE_B = 3
    };

    enum {
        MPEG2_PIC_STRUCT_TOP       = 1,
        MPEG2_PIC_STRUCT_BOTTOM    = 2,
        MPEG2_PIC_STRUCT_FRAME     = 3
    };

    enum {
        MPEG2_SURFACE_NUMBER = 10,
    };

    VABufferID *mBufferIDs;
    int32_t mNumBufferIDs;
};

#endif /* VIDEO_DECODER_MPEG2_H_ */
