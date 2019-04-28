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
package com.mediatek.server.wm;

import android.view.WindowManagerPolicy.WindowState;
import android.os.Build;
import android.view.WindowManager;

import android.graphics.Rect;
import java.io.PrintWriter;

public class WindowManagerDebugger {
    public static final String TAG = "WindowManagerDebugger";

    /// M: for build type check
    public static boolean WMS_DEBUG_ENG = false;
    public static boolean WMS_DEBUG_USER = false;
    public static boolean WMS_DEBUG_LOG_OFF = false;

    public void runDebug(PrintWriter pw, String[] args, int opti) {}

    public void debugInterceptKeyBeforeQueueing(String tag, int keycode, boolean interactive,
                boolean keyguardActive, int policyFlags, boolean down, boolean canceled,
                boolean isWakeKey, boolean screenshotChordVolumeDownKeyTriggered, int result,
                boolean useHapticFeedback, boolean isInjected) {}

    public void debugApplyPostLayoutPolicyLw(String tag, WindowState win,
                WindowManager.LayoutParams attrs,
                WindowState mTopFullscreenOpaqueWindowState, WindowState attached,
                WindowState imeTarget, boolean dreamingLockscreen, boolean showingDream){}

    public void debugLayoutWindowLw(String tag, int adjust, int type, int fl,
                boolean canHideNavigationBar, int sysUiFl) {}

    public void debugGetOrientation(String tag, boolean displayFrozen,
                int lastWindowForcedOrientation, int lastKeyguardForcedOrientation) {}

    public void debugGetOrientingWindow(String tag, WindowState w,
            WindowManager.LayoutParams attrs, boolean isVisible,
            boolean policyVisibilityAfterAnim, boolean policyVisibility, boolean destroying){}

    public void debugPrepareSurfaceLocked(String tag, boolean isWallpaper, WindowState win,
            boolean wallpaperVisible, boolean isOnScreen, boolean policyVisibility,
            boolean hasSurface, boolean destroying, boolean lastHidden){}

    public void debugRelayoutWindow(String tag, WindowState win,
            int originType, int changeType) {}

    public void debugInputAttr(String tag, WindowManager.LayoutParams attrs) {}

    public void debugViewVisibility(String tag, WindowState win, int viewVisibility,
                int oldVisibility, boolean focusMayChange) {}
}
