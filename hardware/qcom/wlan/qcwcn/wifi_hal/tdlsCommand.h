/* Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

#ifndef __WIFI_HAL_LLSTATSCOMMAND_H__
#define __WIFI_HAL_LLSTATSCOMMAND_H__

#include <stdint.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <linux/rtnetlink.h>
#include <netpacket/packet.h>
#include <linux/filter.h>
#include <linux/errqueue.h>

#include <linux/pkt_sched.h>
#include <netlink/object-api.h>
#include <netlink/netlink.h>
#include <netlink/socket.h>
#include <net/if.h>

#include "nl80211_copy.h"
#include "common.h"
#include "cpp_bindings.h"
#include "tdls.h"

#ifdef __GNUC__
#define PRINTF_FORMAT(a,b) __attribute__ ((format (printf, (a), (b))))
#define STRUCT_PACKED __attribute__ ((packed))
#else
#define PRINTF_FORMAT(a,b)
#define STRUCT_PACKED
#endif

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

#define IS_GLOBAL_TDLS_SUPPORTED        BIT(0)
#define IS_PER_MAC_TDLS_SUPPORTED       BIT(1)
#define IS_OFF_CHANNEL_TDLS_SUPPORTED   BIT(2)

typedef struct {
    int maxConcurrentTdlsSessionNum;
    u32 tdlsSupportedFeatures;
} wifiTdlsCapabilities;

class TdlsCommand: public WifiVendorCommand
{
private:
    static TdlsCommand *mTdlsCommandInstance;
    wifi_tdls_status mTDLSgetStatusRspParams;
    wifi_request_id mRequestId;
    wifi_tdls_handler mHandler;
    wifiTdlsCapabilities mTDLSgetCaps;

    TdlsCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);

public:
    static TdlsCommand* instance(wifi_handle handle);
    virtual ~TdlsCommand();

    virtual void setSubCmd(u32 subcmd);

    virtual int requestResponse();

    virtual int handleEvent(WifiEvent &event);

    virtual int handleResponse(WifiEvent &reply);

    virtual int setCallbackHandler(wifi_tdls_handler nHandler, u32 event);

    virtual void unregisterHandler(u32 subCmd);

    virtual void getStatusRspParams(wifi_tdls_status *status);

    virtual void getCapsRspParams(wifi_tdls_capabilities *caps);
};

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
