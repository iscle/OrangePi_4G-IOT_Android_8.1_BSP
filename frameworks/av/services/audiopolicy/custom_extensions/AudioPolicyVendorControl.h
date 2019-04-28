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

#pragma once


#include <utils/threads.h>

namespace android {
/* Vendor provide to add new feature in audio policy
*/
    class AudioPolicyVendorControl
    {
    public:
        AudioPolicyVendorControl():mVoiceCurveReplaceDTMFCurve(false),mUseCustomVolume(false),mFMTxEnable(false),
                                    mNumHSPole(4), mHDMI_ChannelCount(8), mUsbSuspended(false), mPrevMode(AUDIO_MODE_NORMAL) {};

        bool getFMTxStatus() const {return mFMTxEnable;}
        void setFMTxStatus(bool enable) {mFMTxEnable = enable;}

        bool getVoiceReplaceDTMFStatus() const {return mVoiceCurveReplaceDTMFCurve;}
        void setVoiceReplaceDTMFStatus(bool enable) {mVoiceCurveReplaceDTMFCurve = enable;}

        bool getCustomVolumeStatus() const {return mUseCustomVolume;}
        void setCustomVolumeStatus(bool enable) {mUseCustomVolume = enable;}

        bool isStateInCallOnly(int state)
        {
            return state == AUDIO_MODE_IN_CALL;
        }

        void     setNumOfHeadsetPole(int pole) { mNumHSPole = pole; }
        int      getNumOfHeadsetPole(void) { return mNumHSPole; }

        int getHDMI_ChannelCount() const {return mHDMI_ChannelCount;}
        void setHDMI_ChannelCount(int channels) {mHDMI_ChannelCount = channels;}

        bool getUsbSuspended() const {return mUsbSuspended;}
        void setUsbSuspended(bool enable) {mUsbSuspended = enable;}
        audio_mode_t getPrevMode() const {return mPrevMode;}
        void setPrevMode(audio_mode_t mode) {mPrevMode = mode;}
    private:
        bool mVoiceCurveReplaceDTMFCurve;
        bool mUseCustomVolume;
        bool mFMTxEnable;
        int mNumHSPole;
        int mHDMI_ChannelCount;
        bool mUsbSuspended;
        audio_mode_t mPrevMode;
    };
}
