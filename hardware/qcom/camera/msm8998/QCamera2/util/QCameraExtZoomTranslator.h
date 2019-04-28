/* Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#ifndef __QCAMERAEXTZOOMTRANSLATOR_H__
#define __QCAMERAEXTZOOMTRANSLATOR_H__

#include "cam_intf.h"

using namespace android;

namespace qcamera {

typedef enum {
    MODE_CAMERA,
    MODE_CAMCORDER,
    MODE_RTB
} dual_cam_mode;

typedef struct {
    uint32_t width;
    uint32_t height;
} dimension_t;

typedef struct {
    dual_cam_mode mode;
    void*         calibData;
    uint32_t      calibDataSize;
    dimension_t   previewDimension;
    dimension_t   ispOutDimension;
    dimension_t   sensorOutDimensionMain;
    dimension_t   sensorOutDimensionAux;
    uint32_t     *zoomRatioTable;
    uint32_t      zoomRatioTableCount;
} zoom_trans_init_data;

class QCameraExtZoomTranslator {
public:
    ~QCameraExtZoomTranslator();
    static QCameraExtZoomTranslator* create();
    int32_t init(zoom_trans_init_data initData);
    int32_t deInit();
    int32_t getZoomValues(uint32_t userZoom, uint32_t *wideZoom, uint32_t *teleZoom);
    bool isInitialized();
private:
    QCameraExtZoomTranslator();

    void                   *mLibHandle;
    bool                    mInitSuccess;
    zoom_trans_init_data    mInitData;
};

}; // namespace qcamera

#endif /* __QCAMERAEXTZOOMTRANSLATOR_H__ */
