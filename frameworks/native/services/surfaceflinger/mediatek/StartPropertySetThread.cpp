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
#include <dlfcn.h>
#include <inttypes.h>

#include <cutils/properties.h>
#include <log/log.h>
#include "StartPropertySetThread.h"

namespace android {

// BOOT functions
void StartPropertySetThread::checkEnableBootAnim() {
    char propVal[PROPERTY_VALUE_MAX];
    property_get("sys.boot.reason", propVal, "");
    ALOGI("[%s] boot reason = '%s'", __func__, propVal);
    if (strcmp(propVal, "")) {
        mBootAnimationEnabled = ('1' != propVal[0]);
    } else {
        /*
         * The sys.boot.reason will be updated by boot_logo_updater.
         * However, the init can't set the system properity
         * before surface flinger is initialized.
         * Determine the boot reason by check boot path and
         * boot package information directly
         */
        char boot_reason = '0'; // 0: normal boot, 1: alarm boot
        int fd = open("/sys/class/BOOT/BOOT/boot/boot_mode", O_RDONLY);
        if (fd < 0) {
            ALOGE("fail to open: boot path %s", strerror(errno));
            boot_reason = '0';
        } else {
            char boot_mode;
            size_t s = read(fd, (void *)&boot_mode, sizeof(boot_mode));
            close(fd);

            if (s <= 0)
                ALOGE("can't read the boot_mode");
            else if (boot_mode == '7')
                boot_reason = '1';
        }

        mBootAnimationEnabled = ('1' != boot_reason);
    }
#ifdef FPGA_EARLY_PORTING
    mBootAnimationEnabled = false;
    ALOGI("[%s] BootAnimation was disabled in FPGA", __func__);
#endif

    ALOG(LOG_INFO, "boot", "mBootAnimationEnabled = %d", mBootAnimationEnabled);

    if (true == mBootAnimationEnabled) {
        property_set("service.bootanim.exit", "0");
        property_set("ctl.start", "bootanim");

        // boot time profiling
        ALOG(LOG_INFO, "boot", "BOOTPROF:BootAnimation:Start:%ld", long(ns2ms(systemTime())));
        bootProf(1);
    } else {
        ALOGI("Skip boot animation!");
    }
}

void StartPropertySetThread::bootProf(int start) const {
    int fd         = open("/proc/bootprof", O_RDWR);
    int fd_nand    = open("/proc/driver/nand", O_RDWR);

    if (fd == -1) {
        ALOGE("fail to open /proc/bootproffile : %s", strerror(errno));

        return;
    }

    const size_t BUF_SIZE = 64;
    char buf[BUF_SIZE];
    memset(buf, 0, BUF_SIZE);
    if (1 == start) {
        strncpy(buf,"BOOT_Animation:START", BUF_SIZE - 1);
        buf[BUF_SIZE - 1] = '\0';
        if (fd > 0) {
            write(fd, buf, 32);
            close(fd);
        }
        if (fd_nand > 0) {
            close(fd_nand);
        }
    } else {
        strncpy(buf, "BOOT_Animation:END", BUF_SIZE - 1);
        buf[BUF_SIZE - 1] = '\0';
        if (fd > 0) {
            write(fd, buf, 32);
            write(fd, "0", 1);    //end of bootprof
            close(fd);
        }
        if (fd_nand > 0) {
            write(fd_nand, "I1", 2);
            close(fd_nand);
        }
    }
}

} // namespace android
