/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#ifndef MTK_HWUI_DUMPER_H
#define MTK_HWUI_DUMPER_H

#include <utils/RefBase.h>
#include <utils/Singleton.h>
#include <utils/Vector.h>
#include <SkBitmap.h>

namespace android {
namespace uirenderer {

///////////////////////////////////////////////////////////////////////////////
// Dumper
///////////////////////////////////////////////////////////////////////////////
class DumpBarrier;
class DumpTask;
class DumperThread;

class Dumper: public Singleton<Dumper> {
    friend class Singleton<Dumper>;
public:
    Dumper();
    ~Dumper();

    int mPid;
    char* mProcessName;

public:
    bool addTask(DumpTask* task);
    bool dumpDisplayList(int width, int height, const char* name, int frameCount, void* renderer);
    bool dumpDraw(int width, int height, int frameCount,
        int index, void* renderer, void* drawOp, int sub = 0);
    bool dumpLayer(int width, int height, int fbo,
        int frameCount, void* renderer, void* layer);
    bool dumpTexture(int texture, int width, int height, const SkBitmap* bitmap, bool isLayer);
    bool dumpAlphaTexture(int width, int height, uint8_t* data, const char* prefix, bool isA8 = true);

private:
    bool dumpImage(int width, int height, const char *filename);
    int mThreadCount;
    Vector<sp<DumperThread> > mThreads;
};

}; // namespace uirenderer
}; // namespace android

#endif /* MTK_HWUI_DUMPER_H */
