/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef __WIFI_HAL_WIFILOGGER_COMMAND_H__
#define __WIFI_HAL_WIFILOGGER_COMMAND_H__

#include "common.h"
#include "cpp_bindings.h"
#include "wifi_logger.h"
#include "wifilogger_diag.h"
#include "vendor_definitions.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

#define POWER_EVENTS_RB_BUF_SIZE 2048
#define POWER_EVENTS_NUM_BUFS    4

#define CONNECTIVITY_EVENTS_RB_BUF_SIZE 4096
#define CONNECTIVITY_EVENTS_NUM_BUFS    4

#define PKT_STATS_RB_BUF_SIZE 4096
#define PKT_STATS_NUM_BUFS    32

#define DRIVER_PRINTS_RB_BUF_SIZE 4096
#define DRIVER_PRINTS_NUM_BUFS    128

#define FIRMWARE_PRINTS_RB_BUF_SIZE 32768
#define FIRMWARE_PRINTS_NUM_BUFS    16

enum rb_info_indices {
    POWER_EVENTS_RB_ID = 0,
    CONNECTIVITY_EVENTS_RB_ID = 1,
    PKT_STATS_RB_ID = 2,
    DRIVER_PRINTS_RB_ID = 3,
    FIRMWARE_PRINTS_RB_ID = 4,
};

typedef struct {
  void (*on_firmware_memory_dump) (char *buffer,
                                   int buffer_size);

} WifiLoggerCallbackHandler;


class WifiLoggerCommand : public WifiVendorCommand
{
private:
    WifiLoggerCallbackHandler mHandler;
    char                      *mVersion;
    int                       mVersionLen;
    u32                       *mSupportedSet;
    int                       mRequestId;
    bool                      mWaitforRsp;
    bool                      mMoreData;
    WLAN_DRIVER_WAKE_REASON_CNT *mGetWakeStats;
public:

    WifiLoggerCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);

    static WifiLoggerCommand* instance(wifi_handle handle);
    virtual ~WifiLoggerCommand();

    // This function implements creation of WifiLogger specific Request
    // based on  the request type
    virtual int create();
    virtual int requestEvent();
    virtual int requestResponse();
    virtual int handleResponse(WifiEvent &reply);
    virtual int handleEvent(WifiEvent &event);
    int setCallbackHandler(WifiLoggerCallbackHandler nHandler);
    virtual void unregisterHandler(u32 subCmd);

    /* Takes wait time in seconds. */
    virtual int timed_wait(u16 wait_time);
    virtual void waitForRsp(bool wait);
    virtual void setVersionInfo(char *buffer, int buffer_size);
    virtual void setFeatureSet(u32 *support);
    virtual void getWakeStatsRspParams(
                    WLAN_DRIVER_WAKE_REASON_CNT *wifi_wake_reason_cnt);
};
void rb_timerhandler(hal_info *info);
wifi_error wifi_logger_ring_buffers_init(hal_info *info);
void wifi_logger_ring_buffers_deinit(hal_info *info);
void push_out_all_ring_buffers(hal_info *info);
void send_alert(hal_info *info, int reason_code);
#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __WIFI_HAL_WIFILOGGER_COMMAND_H__ */
