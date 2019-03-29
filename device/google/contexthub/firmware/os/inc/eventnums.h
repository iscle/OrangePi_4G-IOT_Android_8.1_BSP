/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef EVENTNUMS_H
#define EVENTNUMS_H

#include <stdint.h>
#include "toolchain.h"

/* These define ranges of reserved events */
// local events are 16-bit always
#define EVT_NO_FIRST_USER_EVENT          0x00000100    //all events lower than this are reserved for the OS. all of them are nondiscardable necessarily!
#define EVT_NO_FIRST_SENSOR_EVENT        0x00000200    //sensor type SENSOR_TYPE_x produces events of type EVT_NO_FIRST_SENSOR_EVENT + SENSOR_TYPE_x for all Google-defined sensors
#define EVT_NO_SENSOR_CONFIG_EVENT       0x00000300    //event to configure sensors
#define EVT_APP_START                    0x00000400    //sent when an app can actually start
#define EVT_APP_TO_HOST                  0x00000401    //app data to host. Type is struct HostHubRawPacket
#define EVT_MARSHALLED_SENSOR_DATA       0x00000402    //marshalled event data. Type is MarshalledUserEventData
#define EVT_RESET_REASON                 0x00000403    //reset reason to host.
#define EVT_APP_TO_SENSOR_HAL_DATA       0x00000404    // sensor driver out of band data update to sensor hal
#define EVT_DEBUG_LOG                    0x00007F01    // send message payload to Linux kernel log
#define EVT_MASK                         0x0000FFFF

// host-side events are 32-bit

// DEBUG_LOG_EVT is normally undefined, or defined with a special value, recognized by nanohub driver: 0x3B474F4C
// if defined with this value, the log message payload will appear in Linux kernel message log.
// If defined with other value, it will still be sent to nanohub driver, and then forwarded to userland
// verbatim, where it could be logged by nanohub HAL (by turning on it's logging via 'setprop persist.nanohub.debug 1'
#ifdef DEBUG_LOG_EVT
#define HOST_EVT_DEBUG_LOG               DEBUG_LOG_EVT
#endif

#define HOST_HUB_RAW_PACKET_MAX_LEN      128

SET_PACKED_STRUCT_MODE_ON
struct HostHubRawPacket {
    uint64_t appId;
    uint8_t dataLen; //not incl this header, 128 bytes max
    //raw data in unspecified format here
}ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubMsgChreHdr {
    uint8_t size;
    uint32_t appEvent;
}ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct MarshalledUserEventData {
    //for matching
    uint32_t origEvtType;

    int32_t dataLen;  //use negative here to indicate marshalling error.
    //raw data in unspecified format here

}ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF


/*
 * When sensor drivers use EVT_APP_TO_HOST, e.g. for reporting calibration data,
 * the data segment of struct HostHubRawPacket is strongly recommended to begin
 * with this header to allow for common parsing. But this is not a requirement,
 * as these messages are inherently application-specific.
 */
SET_PACKED_STRUCT_MODE_ON
struct SensorAppEventHeader {
    uint8_t msgId;
    uint8_t sensorType;
    uint8_t status; // 0 for success, else application-specific error code
}ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define SENSOR_APP_EVT_STATUS_SUCCESS    0x00
#define SENSOR_APP_EVT_STATUS_ERROR      0x01 // General failure
#define SENSOR_APP_EVT_STATUS_BUSY       0x02

#define SENSOR_APP_MSG_ID_CAL_RESULT     0x00 // Status of calibration, with resulting biases
#define SENSOR_APP_MSG_ID_TEST_RESULT    0x01 // Status of self-test

/*
 * These events are in private OS-reserved range, and are sent targettedly
 * to one app. This is OK since real OS-reserved internal events will never
 * go to apps, as that region is reserved for them. We thus achieve succesful
 * overloading of the range.
 */

//for all apps
#define EVT_APP_FREE_EVT_DATA            0x000000FF    //sent to an external app when its event has been marked for freeing. Data: struct AppEventFreeData
// this event is never enqueued; it goes directly to the app.
// It notifies app that hav outstanding IO, that is is about to end;
// Expected app behavior is to not send any more events to system;
// any events sent after this point will be silently ignored by the system;
// any outstading events will be allowed to proceed to completion. (this is SIG_STOP)
#define EVT_APP_STOP                     0x000000FE
// Internal event, with task pointer as event data;
// system ends the task unconditionally; no further checks performed (this is SIG_KILL)
#define EVT_APP_END                      0x000000FD
//for host comms
#define EVT_APP_FROM_HOST                0x000000F8    //host data to an app. Type is struct HostMsgHdr
#define EVT_APP_FROM_HOST_CHRE           0x000000F9    //host data to an app. Type is struct HostMsgHdrChre

//for apps that use I2C
#define EVT_APP_I2C_CBK                  0x000000F0    //data pointer points to struct I2cEventData

//for apps that claim to be a sensor
#define EVT_APP_SENSOR_POWER             0x000000EF    //data pointer is not a pointer, it is a bool encoded as (void*)
#define EVT_APP_SENSOR_FW_UPLD           0x000000EE
#define EVT_APP_SENSOR_SET_RATE          0x000000ED    //data pointer points to a "const struct SensorSetRateEvent"
#define EVT_APP_SENSOR_FLUSH             0x000000EC
#define EVT_APP_SENSOR_TRIGGER           0x000000EB
#define EVT_APP_SENSOR_CALIBRATE         0x000000EA
#define EVT_APP_SENSOR_CFG_DATA          0x000000E9
#define EVT_APP_SENSOR_SEND_ONE_DIR_EVT  0x000000E8
#define EVT_APP_SENSOR_MARSHALL          0x000000E7    // for external sensors that send events of "user type"
#define EVT_APP_SENSOR_SELF_TEST         0x000000E6

//for timers
#define EVT_APP_TIMER                    0x000000DF

#endif /* EVENTNUMS_H */
