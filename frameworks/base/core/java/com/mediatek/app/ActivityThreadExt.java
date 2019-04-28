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
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.app;

import android.app.ActivityThread;
import android.os.SystemProperties;

public class ActivityThreadExt {

    //Dynamically enable ActivityThread logs
    public static void enableActivityThreadLog(ActivityThread activityThread) {
        String activitylog = SystemProperties.get("persist.sys.activitylog", null);
        if (activitylog != null && !activitylog.equals("")) {
            if (activitylog.indexOf(" ") != -1
                    && activitylog.indexOf(" ") + 1 <= activitylog.length()) {
                String option = activitylog.substring(0, activitylog.indexOf(" "));
                String enable = activitylog.substring(activitylog.indexOf(" ") + 1,
                        activitylog.length());
                boolean isEnable = "on".equals(enable) ? true : false;
                if (option.equals("x")) {
                    enableActivityThreadLog(isEnable, activityThread);
                }
            } else {
                SystemProperties.set("persist.sys.activitylog", "");
            }
        }
    }

    public static void enableActivityThreadLog(boolean isEnable, ActivityThread activityThread) {
        activityThread.localLOGV = isEnable;
        activityThread.DEBUG_MESSAGES = isEnable;
        activityThread.DEBUG_BROADCAST = isEnable;
        activityThread.DEBUG_RESULTS = isEnable;
        activityThread.DEBUG_BACKUP = isEnable;
        activityThread.DEBUG_CONFIGURATION = isEnable;
        activityThread.DEBUG_SERVICE = isEnable;
        activityThread.DEBUG_MEMORY_TRIM = isEnable;
        activityThread.DEBUG_PROVIDER = isEnable;
        activityThread.DEBUG_ORDER = isEnable;
    }
}
