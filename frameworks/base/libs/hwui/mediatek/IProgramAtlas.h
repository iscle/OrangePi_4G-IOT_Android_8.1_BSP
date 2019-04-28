/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 */

#ifndef ANDROID_HWUI_IPROGRAM_ATLAS_H
#define ANDROID_HWUI_IPROGRAM_ATLAS_H

#include "Program.h"

namespace android {
namespace uirenderer {

/**
 *
 */
class IProgramAtlas {
public:
    /**
     * M: ProgramEntry representing the program binary inside the atlas.
     */
    struct ProgramEntry {
        programid programKey;
        void* binary;
        GLint   binaryLength;
        GLenum  binaryFormat;

        ProgramEntry(programid key, void* b, GLint length, GLenum format):
                programKey(key), binary(b), binaryLength(length), binaryFormat(format) {
        }

        ~ProgramEntry() {
        }
    };

    IProgramAtlas() {}
    virtual ~IProgramAtlas() {/*terminate();*/}

    /**
     * M: Initialize program atlas.
     */
    virtual void init() = 0;

    /**
     * M: Get the entry of each program binary.
     */
    virtual ProgramEntry* getProgramEntry(programid key) = 0;

    /**
     *
     */
    virtual void terminate() = 0;

    /**
     * M: [ProgramBinaryAtlas] Create program and its mapping table, return the total memory size
     * for caching programs binaries, and update the correct mapLength.
     *
     * The mapping will be ProgramKey, Offset, Length, "ProgramId", ProgramKey, Offset...
     */
    virtual int createPrograms(int64_t* map, int* mapLength) = 0;

    /**
     * M: [ProgramBinaryAtlas] Load program binaries to the buffer, delete programs, and update the map
     *
     * The mapping will be ProgramKey, Offset, Length, "Format", ProgramKey, Offset...
     */
    virtual void loadProgramBinariesAndDelete(int64_t* map, int mapLength, void* buffer, int length) = 0;

    /**
     * M: check whether the program binary service is enabled
     */
    virtual bool getServiceEnabled() = 0;
};// class IProgramAtlas

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_IPROGRAM_ATLAS_H

