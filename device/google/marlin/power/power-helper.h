/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * *    * Redistributions of source code must retain the above copyright
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
 */
#ifndef __POWER_HELPER_H__
#define __POWER_HELPER_H__

#ifdef __cplusplus
extern "C" {
#endif

#include <hardware/power.h>

enum platform_param_id {
    VLOW_COUNT = 0,
    ACCUMULATED_VLOW_TIME,
    VMIN_COUNT,
    ACCUMULATED_VMIN_TIME,
    RPM_PARAM_COUNT,

    XO_ACCUMULATED_DURATION_APSS = RPM_PARAM_COUNT,
    XO_COUNT_APSS,
    XO_ACCUMULATED_DURATION_MPSS,
    XO_COUNT_MPSS,
    XO_ACCUMULATED_DURATION_ADSP,
    XO_COUNT_ADSP,
    XO_ACCUMULATED_DURATION_SLPI,
    XO_COUNT_SLPI,

    //Don't add any lines after that line
    PLATFORM_PARAM_COUNT
};

enum platform_mode_id {
    RPM_MODE_XO = 0,
    RPM_MODE_VMIN,

    //Don't add any lines after that line
    RPM_MODE_COUNT
};

#define XO_VOTERS 4
#define VMIN_VOTERS 0

enum voter_id {
    APSS,
    MPSS,
    ADSP,
    SLPI,

    //Don't add any lines after that line
    VOTER_COUNT
};

enum subsystem_type {
    SUBSYSTEM_WLAN,

    //Don't add any lines after that line
    SUBSYSTEM_COUNT
};

enum wlan_param_id {
    CUMULATIVE_SLEEP_TIME_MS,
    CUMULATIVE_TOTAL_ON_TIME_MS,
    DEEP_SLEEP_ENTER_COUNTER,
    LAST_DEEP_SLEEP_ENTER_TSTAMP_MS,

    //Don't add any lines after that line
    WLAN_PARAM_COUNT
};

enum wlan_state_id {
    WLAN_STATE_ACTIVE = 0,
    WLAN_STATE_DEEP_SLEEP,

    //Don't add any lines after that line
    WLAN_STATE_COUNT
};


void power_init(void);
void power_hint(power_hint_t hint, void *data);
void power_set_interactive(int on);
int extract_platform_stats(uint64_t *list);
int extract_wlan_stats(uint64_t *list);

#ifdef __cplusplus
}
#endif

#endif //__POWER_HELPER_H__
