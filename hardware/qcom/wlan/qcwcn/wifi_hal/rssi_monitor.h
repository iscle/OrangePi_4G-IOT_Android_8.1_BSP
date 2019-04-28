/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
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

#ifndef __WIFI_HAL_RSSI_MONITOR_COMMAND_H__
#define __WIFI_HAL_RSSI_MONITOR_COMMAND_H__

#include "cpp_bindings.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

class RSSIMonitorCommand: public WifiVendorCommand
{
private:
    static RSSIMonitorCommand *mRSSIMonitorCommandInstance;
    wifi_rssi_event_handler mHandler;
    RSSIMonitorCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);
    bool mEventHandlingEnabled;
    /* mutex for the mEventHandlingEnabled access*/
    pthread_mutex_t rm_lock;

public:
    virtual ~RSSIMonitorCommand();
    static RSSIMonitorCommand* instance(wifi_handle handle, wifi_request_id id);
    virtual int handleEvent(WifiEvent &event);
    virtual void setReqId(wifi_request_id reqid);
    virtual void setCallbackHandler(wifi_rssi_event_handler nHandler);
    void enableEventHandling();
    void disableEventHandling();
    bool isEventHandlingEnabled();
};

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
