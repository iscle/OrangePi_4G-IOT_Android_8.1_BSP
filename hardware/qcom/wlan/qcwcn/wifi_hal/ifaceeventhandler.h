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

#ifndef __WIFI_HAL_IFACEEVENTHANDLER_COMMAND_H__
#define __WIFI_HAL_IFACEEVENTHANDLER_COMMAND_H__

#include "common.h"
#include "cpp_bindings.h"
#ifdef __GNUC__
#define PRINTF_FORMAT(a,b) __attribute__ ((format (printf, (a), (b))))
#define STRUCT_PACKED __attribute__ ((packed))
#else
#define PRINTF_FORMAT(a,b)
#define STRUCT_PACKED
#endif
#include "vendor_definitions.h"
#include "wifi_hal.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */


class wifiEventHandler: public WifiCommand
{
private:
    int mRequestId;

protected:
    struct nlattr *tb[NL80211_ATTR_MAX + 1];
    u32 mSubcmd;

public:
    wifiEventHandler(wifi_handle handle, int id, u32 subcmd);
    virtual ~wifiEventHandler();
    virtual int get_request_id();
    virtual int handleEvent(WifiEvent &event);
};

class IfaceEventHandlerCommand: public wifiEventHandler
{
private:
    char *mEventData;
    u32 mDataLen;
    wifi_event_handler mHandler;

public:
    IfaceEventHandlerCommand(wifi_handle handle, int id, u32 subcmd);
    virtual ~IfaceEventHandlerCommand();

    virtual int handleEvent(WifiEvent &event);
    virtual void setCallbackHandler(wifi_event_handler nHandler);
    virtual int get_request_id();
};

class WifihalGeneric: public WifiVendorCommand
{
private:
    feature_set mSet;
    int mSetSizeMax;
    int *mSetSizePtr;
    feature_set *mConcurrencySet;
    int filterVersion;
    int filterLength;
    int firmware_bus_max_size;
    wifi_capa *mCapa;

    virtual wifi_error wifiParseCapabilities(struct nlattr **tbVendor);

public:
    WifihalGeneric(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);
    virtual ~WifihalGeneric();
    virtual int requestResponse();
    virtual int handleResponse(WifiEvent &reply);
    virtual void getResponseparams(feature_set *pset);
    virtual void setMaxSetSize(int set_size_max);
    virtual void setSizePtr(int *set_size);
    virtual void setConcurrencySet(feature_set set[]);
    virtual int getFilterVersion();
    virtual int getFilterLength();
    virtual int getBusSize();
    virtual wifi_error wifiGetCapabilities(wifi_interface_handle handle);
};
#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
