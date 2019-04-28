

#define LOG_TAG "UibcServerHandler"
#include <utils/Log.h>

#include "UibcMessage.h"
#include "UibcServerHandler.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/foundation/hexdump.h>


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <netinet/in.h>
#include <unistd.h>
#include <linux/fb.h>
#include <sys/mman.h>
#include <linux/input.h>
#include <linux/uinput.h>

#include <binder/IServiceManager.h>
#include <gui/SurfaceComposerClient.h>
#include <ui/DisplayInfo.h>
#include <gui/ISurfaceComposer.h>
#include <cutils/properties.h>

#include <ctype.h>
#include <sys/poll.h>
#include <pthread.h>
#include <linux/uhid.h>
#include <linux/input.h>

namespace android {

static unsigned short uibc_keycode[256] = {
    KEY_RESERVED,
    BTN_LEFT,
    BTN_TOUCH,
    KEY_BACKSPACE,
    KEY_TAB,
    KEY_LINEFEED,
    KEY_PAGEDOWN,
    KEY_ENTER,
    KEY_LEFTSHIFT,
    KEY_LEFTCTRL,
    KEY_CANCEL,
    KEY_BACK,
    KEY_DELETE,
    KEY_HOME,
    KEY_VOLUMEDOWN,
    KEY_VOLUMEUP,
};

static unsigned short uibc_keycode_chars[256] = {
    KEY_A,
    KEY_B,
    KEY_C,
    KEY_D,
    KEY_E,
    KEY_F,
    KEY_G,
    KEY_H,
    KEY_I,
    KEY_J,
    KEY_K,
    KEY_L,
    KEY_M,
    KEY_N,
    KEY_O,
    KEY_P,
    KEY_Q,
    KEY_R,
    KEY_S,
    KEY_T,
    KEY_U,
    KEY_V,
    KEY_W,
    KEY_X,
    KEY_Y,
    KEY_Z,
    KEY_SPACE,
    KEY_RESERVED,
    BTN_LEFT,
    BTN_TOUCH,
    KEY_BACKSPACE,
    KEY_TAB,
    KEY_LINEFEED,
    KEY_PAGEDOWN,
    KEY_ENTER,
    KEY_LEFTSHIFT,
    KEY_LEFTCTRL,
    KEY_CANCEL,
    KEY_BACK,
    KEY_DELETE,
    KEY_HOME
};

uint8_t default_KB_Desc[] = {0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x05, 0x07,
                             0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
                             0x75, 0x01, 0x95, 0x08, 0x81, 0x02, 0x95, 0x01,
                             0x75, 0x08, 0x81, 0x01, 0x95, 0x05, 0x75, 0x01,
                             0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
                             0x95, 0x01, 0x75, 0x03, 0x91, 0x01, 0x95, 0x06,
                             0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07,
                             0x19, 0x00, 0x29, 0x65, 0x81, 0x00, 0xC0
                            };

uint8_t default_Mouse_Desc[] = {0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x09, 0x01,
                                0xA1, 0x00, 0x05, 0x09, 0x19, 0x01, 0x29, 0x03,
                                0x15, 0x00, 0x25, 0x01, 0x95, 0x03, 0x75, 0x01,
                                0x81, 0x02, 0x95, 0x01, 0x75, 0x05, 0x81, 0x01,
                                0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, 0x81,
                                0x25, 0x7F, 0x75, 0x08, 0x95, 0x02, 0x81, 0x06,
                                0xC0, 0xC0
                               };

UibcServerHandler::UibcServerHandler(sp<IRemoteDisplayClient> remoteClient)
    : mUinput_fd(-1),
      m_XCurCoord(-1),
      m_YCurCoord(-1),
      m_XOffset(0),
      m_YOffset(0),
      m_XRevert(false),
      m_YRevert(false),
      m_XYSwitch(false),
      m_Orientation(-1),
      m_GenericDriverInited(false),
      m_ShiftPressed(false),
      m_touchDown(false),
      m_mouseDown(false),
      m_deltaX(0),
      m_deltaY(0),
      mRemoteClient(remoteClient),
      m_hidcDefDescTest(false),
      m_TouchLatencyTest(false) {
    for (int i = 0 ; i < 8 ; i++) {
        for (int j = 0 ; j < 5 ; j++) {
            if(m_uhidFd[i][j] > 0) {
                m_uhidFd[i][j] = -1;
            }
        }
    }
}

UibcServerHandler::~UibcServerHandler() {

}

status_t UibcServerHandler::init(bool testMode) {
    ALOGD("init()");

    UibcHandler::init(testMode);

    char val[PROPERTY_VALUE_MAX];
    if (property_get("media.wfd.source.uibc-def-desc", val, NULL)) {
        ALOGD("media.wfd.source.uibc-default-hidc-desc:%s", val);
        int value = atoi(val);
        if (value > 0) {
            m_hidcDefDescTest = true;
        } else {
            m_hidcDefDescTest = false;
        }
    }

    if (property_get("media.wfd.touch.latency", val, NULL)) {
        ALOGD("media.wfd.touch.latency:%s", val);
        int value = atoi(val);
        if (value > 0) {
            m_TouchLatencyTest = true;
        } else {
            m_TouchLatencyTest = false;
        }
    }
    return OK;
}

status_t UibcServerHandler::destroy() {

    if(mUinput_fd >= 0) {
        ioctl(mUinput_fd, UI_DEV_DESTROY);
        close(mUinput_fd);
        mUinput_fd = -1;
    }

    for (int i = 0 ; i < 8 ; i++) {
        for (int j = 0 ; j < 5 ; j++) {
            if(m_uhidFd[i][j] > 0) {
                close(m_uhidFd[i][j]);
                m_uhidFd[i][j] = -1;
            }
        }
    }
    return OK;
}

status_t UibcServerHandler::initGenericDrivers() {
    ALOGI("initGenericDrivers()");
    bool bKeyboardSupported = m_Capability->isGenericSupported(DEVICE_TYPE_KEYBOARD);
    bool bTouchSupported = m_Capability->isGenericSupported(DEVICE_TYPE_SINGLETOUCH);
    bool bMouseSupported = m_Capability->isGenericSupported(DEVICE_TYPE_MOUSE);
    struct uinput_user_dev uidev;
    int width, height;

    ALOGD("initGenericDrivers() bKeyboardSupported=%d,bTouchSupported=%d,bMouseSupported=%d",
          bKeyboardSupported, bTouchSupported, bMouseSupported);

    if(mUinput_fd >= 0) {
        close(mUinput_fd);
        mUinput_fd = -1;
    }
    mUinput_fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if(mUinput_fd < 0) {
        ALOGE("open uinput driver failed w/ error %d (%s)", errno, strerror(errno));
        return -1;
    }
    ALOGD("initGenericDrivers mUinput_fd=%d", mUinput_fd);

    if (bKeyboardSupported ||
            bTouchSupported ||
            bMouseSupported) {
        UibcMessage::getScreenResolution(&width, &height);
        if(ioctl(mUinput_fd, UI_SET_PROPBIT  , INPUT_PROP_DIRECT) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_EVBIT, EV_KEY) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_EVBIT, EV_ABS) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_ABSBIT, ABS_X) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_ABSBIT, ABS_Y) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_ABSBIT, ABS_MT_POSITION_X) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_ABSBIT, ABS_MT_POSITION_Y) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_ABSBIT, ABS_PRESSURE) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_ABSBIT, ABS_MT_TOUCH_MAJOR) < 0)
            goto error;
        if(ioctl(mUinput_fd, UI_SET_ABSBIT, ABS_MT_TOUCH_MINOR) < 0)
            goto error;

        if (mTestMode) {
            // Printable Characters for Miracast test
            if(ioctl(mUinput_fd, UI_SET_EVBIT, EV_REL) < 0)
                goto error;
            if(ioctl(mUinput_fd, UI_SET_RELBIT, REL_X) < 0)
                goto error;
            if(ioctl(mUinput_fd, UI_SET_RELBIT, REL_Y) < 0)
                goto error;
            for (int i = 0; i < (int)ARRAY_SIZE(uibc_keycode_chars); i++) {
                if(ioctl(mUinput_fd, UI_SET_KEYBIT, uibc_keycode_chars[i]) < 0)
                    goto error;
            }
        } else {
            // Control Characters
            for (int i = 0; i < (int)ARRAY_SIZE(uibc_keycode); i++) {
                if(ioctl(mUinput_fd, UI_SET_KEYBIT, uibc_keycode[i]) < 0)
                    goto error;
            }
        }
        memset(&uidev, 0, sizeof(uidev));
        snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "wfd-uibc");
        uidev.id.bustype = BUS_HOST;
        uidev.id.vendor  = 0x1;
        uidev.id.product = 0x1;
        uidev.id.version = 1;

        uidev.absmin[ABS_X] = 0;
        uidev.absmax[ABS_X] = width;

        uidev.absmin[ABS_Y] = 0;
        uidev.absmax[ABS_Y] = height;

        uidev.absmin[ABS_PRESSURE] = 0;
        uidev.absmax[ABS_PRESSURE] = 255;

        uidev.absmin[ABS_MT_TOUCH_MAJOR] = 0;
        uidev.absmax[ABS_MT_TOUCH_MAJOR] = 100;

        uidev.absmin[ABS_MT_TOUCH_MINOR] = 0;
        uidev.absmax[ABS_MT_TOUCH_MINOR] = 100;

        uidev.absmin[ABS_MT_POSITION_X] = 0;
        uidev.absmax[ABS_MT_POSITION_X] = width;

        uidev.absmin[ABS_MT_POSITION_Y] = 0;
        uidev.absmax[ABS_MT_POSITION_Y] = height;

        uidev.absmin[ABS_MT_TRACKING_ID] = 0;
        uidev.absmax[ABS_MT_TRACKING_ID] = 5;

        if(write(mUinput_fd, &uidev, sizeof(uidev)) < 0)
            goto error;

        if(ioctl(mUinput_fd, UI_DEV_CREATE) < 0)
            goto error;
    }
    return OK;
error:
    ALOGE("Initial uinput device failed!");
    close(mUinput_fd);
    mUinput_fd = -1;
    return -1;
}

status_t UibcServerHandler::handleUIBCMessage(const sp<ABuffer> &buffer) {
    if (mUibcEnabled == UIBC_DISABLED) {
        ALOGD("handleUIBCMessage mUibcEnabled=UIBC_DISABLED");
        return OK;
    }
    static uint16_t latestTouchRemoteTS = 0;
    static uint16_t latestTouchLocalTS = 0;

    size_t size = buffer->size();
    size_t payloadOffset = 0;
    UIBCInputCategoryCode  inputCategory = WFD_UIBC_INPUT_CATEGORY_UNKNOWN;

    if(size < UIBC_HEADER_SIZE) {
        ALOGE("The size of UIBC message is less than header size");
        return ERROR_MALFORMED;
    }

    const uint8_t *data = buffer->data();

    //Skip the timestamp
    bool hasTimeStamp = data[0] & UIBC_TIMESTAMP_MASK;
    if(hasTimeStamp) {
        payloadOffset = UIBC_HEADER_SIZE + UIBC_TIMESTAMP_SIZE;
        if (m_TouchLatencyTest) {
            WFD_UIBC_FORMAT_HDR_TS *pHdr = (WFD_UIBC_FORMAT_HDR_TS*) buffer->data();
            uint16_t timestamp = ntohs(pHdr->timestamp);
            uint16_t nowTick = UibcMessage::getU16TickCountMs();
            uint16_t srcTouchSndInterval =  timestamp - latestTouchRemoteTS;
            uint16_t sinkTouchRecInterval = nowTick - latestTouchLocalTS;
            uint16_t relTouchLatency = sinkTouchRecInterval < srcTouchSndInterval ?
                                       0 : (sinkTouchRecInterval - srcTouchSndInterval);
            ALOGD("[Touch]Interval:Sink=%d ms,Source=:%d ms,Related latency:%d ms",
                  srcTouchSndInterval,
                  sinkTouchRecInterval,
                  relTouchLatency);
            latestTouchRemoteTS = timestamp;
            latestTouchLocalTS = nowTick;
        }
    } else {
        payloadOffset = UIBC_HEADER_SIZE;
    }

    if (size < payloadOffset) {
        // Not enough data to fit the basic header
        ALOGE("Not enough data to fit the basic header");
        return ERROR_MALFORMED;
    }

    buffer->setRange(payloadOffset, size - payloadOffset);

    inputCategory = (UIBCInputCategoryCode) ((data[1]>0)?1:0);

    switch(inputCategory) {
        case WFD_UIBC_INPUT_CATEGORY_GENERIC:
            /* if ((mRemote_InputCat & INPUT_CAT_GENERIC) == UIBC_NONE) {
                 ALOGD("INPUT_CAT_GENERIC not supported.");
                 return OK;
             }*/

            handleGenericInput(buffer);
            break;
        case WFD_UIBC_INPUT_CATEGORY_HIDC:
            if (!m_Capability->isHidcSupported(0, 0)) {
                ALOGD("INPUT_CAT_HIDC not supported.");
                return OK;
            }

            handleHIDCInput(buffer);
            break;
        default:
            ALOGE("Uknown input category:%d", inputCategory);
            break;
    }

    return OK;
}

status_t UibcServerHandler::handleGenericInput(const sp<ABuffer> &buffer) {
    size_t size = buffer->size();
    WFD_UIBC_GENERIC_MSG *pMsg = (WFD_UIBC_GENERIC_MSG*) buffer->data();
    hexdump(buffer->data(), buffer->size());
    UINT16 bodyLength = ntohs(pMsg->genericHeader.length);

    if (size < bodyLength) {
        ALOGE("Error: not enough space for a complete generic body:%d", bodyLength);
        return ERROR;
    }

    //ALOGV("handleGenericInput with IE:%d", pHdr->ieID);

    if (!m_GenericDriverInited) {
        if (initGenericDrivers() == OK) {
            m_GenericDriverInited = true;
        }
    }

    switch (pMsg->genericHeader.ieID) {
        case WFD_UIBC_GENERIC_IE_ID_LMOUSE_TOUCH_DOWN:
        case WFD_UIBC_GENERIC_IE_ID_LMOUSE_TOUCH_UP:
        case WFD_UIBC_GENERIC_IE_ID_MOUSE_TOUCH_MOVE: {
            ALOGD("uibc (Move,Down,Up) ieID: %d, numptr: %d",
                  pMsg->genericHeader.ieID,
                  pMsg->touchMsg.numPointers);

            if ((pMsg->touchMsg.numPointers > 1) &&
                    !m_Capability->isGenericSupported(DEVICE_TYPE_MULTITOUCH)) {
                ALOGD("GENERIC_MULTITOUCH not supported.");
                return OK;
            }
            sendMultipleTouchEvent(pMsg);
            break;
        }
        case WFD_UIBC_GENERIC_IE_ID_KEY_DOWN:
        case WFD_UIBC_GENERIC_IE_ID_KEY_UP: {
            if (!m_Capability->isGenericSupported(DEVICE_TYPE_KEYBOARD))
                return OK;

            if((sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + bodyLength)
                    == sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + sizeof(WFD_UIBC_GENERIC_BODY_KEY)) {
                ALOGD("uibc (Key,Down,Up) ieID: %d, code1: %d, code2: %d",
                      pMsg->genericHeader.ieID,
                      ntohs(pMsg->keyMsg.code1),
                      ntohs(pMsg->keyMsg.code2));
                int isDown =   ( (pMsg->genericHeader.ieID == WFD_UIBC_GENERIC_IE_ID_KEY_DOWN) ? 1 : 0  );
                if( ntohs(pMsg->keyMsg.code1) > 0) {
                    sendKeyEvent(ntohs(pMsg->keyMsg.code1), isDown);
                }

                if(ntohs(pMsg->keyMsg.code2) > 0) {
                    sendKeyEvent(ntohs(pMsg->keyMsg.code2), isDown);
                }
            }
            break;
        }
        case WFD_UIBC_GENERIC_IE_ID_ZOOM:
            if((sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + bodyLength)
                    == sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + sizeof(WFD_UIBC_GENERIC_BODY_ZOOM)) {
                ALOGD("uibc (ZOOM) ieID: %d, x: %d, y: %d, itimes: %d, ftimes: %d",
                      pMsg->genericHeader.ieID,
                      ntohs(pMsg->zoomMsg.x),
                      ntohs(pMsg->zoomMsg.y),
                      pMsg->zoomMsg.intTimes,
                      pMsg->zoomMsg.fractTimes);
            }
            break;
        case WFD_UIBC_GENERIC_IE_ID_VSCROLL:
        case WFD_UIBC_GENERIC_IE_ID_HSCROLL:
            if((sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + bodyLength)
                    == sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + sizeof(WFD_UIBC_GENERIC_BODY_SCROLL)) {
                ALOGD("uibc (SCROLL V/H) ieID: %d, amount: %d",
                      pMsg->genericHeader.ieID,
                      ntohs(pMsg->scrollMsg.amount));
            }
            break;
        case WFD_UIBC_GENERIC_IE_ID_ROTATE:
            if((sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + bodyLength)
                    == sizeof(WFD_UIBC_GENERIC_BODY_ROTATE)) {
                ALOGD("uibc (ROTATE V/H) ieID: %d, iamount: %d, famount: %d",
                      pMsg->genericHeader.ieID,
                      pMsg->rotateMsg.intAmount,
                      pMsg->rotateMsg.fractAmount);
                break;
            }
        case WFD_UIBC_GENERIC_IE_ID_VENDOR_SPECIFIC:
            if((sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + bodyLength)
                    == sizeof(WFD_UIBC_GENERIC_BODY_FORMAT_HDR) + sizeof(WFD_UIBC_GENERIC_BODY_VENDOR_KEY)) {
                ALOGD("uibc (VENDOR KEY) ieID: %d, keyAction: %d, scanCode1: %d, scanCode2: %d",
                      pMsg->genericHeader.ieID,
                      pMsg->vendorMsg.keyAction,
                      ntohs(pMsg->vendorMsg.scanCode1),
                      ntohs(pMsg->vendorMsg.scanCode2));
                int isDown = ((pMsg->vendorMsg.keyAction == WFD_UIBC_GENERIC_IE_ID_KEY_DOWN) ? 1 : 0  );
                if( ntohs(pMsg->vendorMsg.scanCode1) > 0) {
                    sendKeyEvent(ntohs(pMsg->vendorMsg.scanCode1), isDown);
                }
                if(ntohs(pMsg->vendorMsg.scanCode2) > 0) {
                    sendKeyEvent(ntohs(pMsg->vendorMsg.scanCode2), isDown);
                }
                break;
            }
        default:
            ALOGE("Unknown User input for generic type");
            break;
    }

    return OK;
}

status_t UibcServerHandler::handleHIDCInput(const sp<ABuffer> &buffer) {
    const uint8_t *data = buffer->data();
    //ALOGI("handleHIDCInput raw buffer:");
    //hexdump(buffer->data(), buffer->size());

    size_t bufferSize = buffer->size();
    size_t payloadOffset = 0;
    const char* mtkProductName  = "MTK UIBC HID";
    const char* appleProductName  = "Apple MagicPad";

    if (bufferSize < sizeof(WFD_UIBC_HIDC_BODY_FORMAT_HDR)) {
        ALOGE("Error: not enough space for a complete HIDC header:%lu", (unsigned long)bufferSize);
        return ERROR;
    }

    WFD_UIBC_HIDC_BODY_FORMAT_HDR *pHdr = (WFD_UIBC_HIDC_BODY_FORMAT_HDR*) buffer->data();
    UINT16 bodyLength = ntohs(pHdr->length);

    if (bufferSize < bodyLength + sizeof(WFD_UIBC_HIDC_BODY_FORMAT_HDR)) {
        ALOGE("Error: not enough space for a complete HIDC body:%lu", (unsigned long)bufferSize);
        return ERROR;
    }

    ALOGD("handleHIDCInput with Info:(%d:%d:%d:%d)", pHdr->inputPath, pHdr->hidType, pHdr->usage, bodyLength);

    //Skip the timestamp
    bool hasTimeStamp = data[0] & UIBC_TIMESTAMP_MASK;
    if(hasTimeStamp) {
        payloadOffset = UIBC_HEADER_SIZE + UIBC_TIMESTAMP_SIZE;
    } else {
        payloadOffset = UIBC_HEADER_SIZE;
    }

    payloadOffset += sizeof(WFD_UIBC_HIDC_BODY_FORMAT_HDR);
    buffer->setRange(payloadOffset, bufferSize - sizeof(WFD_UIBC_HIDC_BODY_FORMAT_HDR)); // Move the buffer to the HIDC body part
    uint8_t* pBuffer = buffer->data();

#if 0
    ALOGD("handleHIDCInput buffer->size()=%d, buffer:",  buffer->size());
    hexdump(pBuffer, buffer->size());
#endif

    if (pHdr->hidType > 7)
        return ERROR;

    if (pHdr->usage == WFD_UIBC_HIDC_USAGE_REPORT_DESCRIPTOR) {
        // A "HID report descriptor"
        if (m_uhidFd[pHdr->hidType][pHdr->inputPath] <= 0) {
            m_uhidFd[pHdr->hidType][pHdr->inputPath] = open(UIBC_UHID_DEV_PATH, O_RDWR | O_CLOEXEC);
        } else {
            close(m_uhidFd[pHdr->hidType][pHdr->inputPath]);
            m_uhidFd[pHdr->hidType][pHdr->inputPath] = open(UIBC_UHID_DEV_PATH, O_RDWR | O_CLOEXEC);
        }
        if(m_uhidFd[pHdr->hidType][pHdr->inputPath] < 0) {
            ALOGE("m_uhidFd %d error", pHdr->hidType);
            return ERROR;
        }
        if (m_hidcDefDescTest && pHdr->hidType == 0x00) {
            hidha_uhid_create(&m_uhidFd[pHdr->hidType][pHdr->inputPath], mtkProductName,
                              0x08ED, 0x03, 0, 0, pHdr->inputPath, sizeof(default_KB_Desc), &default_KB_Desc[0]);
        } else if (m_hidcDefDescTest && pHdr->hidType == 0x01) {
            hidha_uhid_create(&m_uhidFd[pHdr->hidType][pHdr->inputPath], mtkProductName,
                              0x08ED, 0x03, 0, 0, pHdr->inputPath, sizeof(default_Mouse_Desc), &default_Mouse_Desc[0]);
        } else if (pHdr->hidType == 0x03 &&
                   pBuffer[0x44] == 0x02 &&
                   pBuffer[0x45] == 0xFF) {
            // Apple MagicPad
            hidha_uhid_create(&m_uhidFd[pHdr->hidType][pHdr->inputPath], appleProductName,
                              0x05AC, 0x030E, 0, 0, pHdr->inputPath, buffer->size(), pBuffer);
        } else {
            hidha_uhid_create(&m_uhidFd[pHdr->hidType][pHdr->inputPath], mtkProductName,
                              0x08ED, 0x03, 0, 0, pHdr->inputPath, buffer->size(), pBuffer);
        }
    } else if (pHdr->usage == WFD_UIBC_HIDC_USAGE_REPORT_INPUT) {
        // A "HID report"
        if (m_uhidFd[pHdr->hidType][pHdr->inputPath] <= 0) {
            m_uhidFd[pHdr->hidType][pHdr->inputPath] = open(UIBC_UHID_DEV_PATH, O_RDWR | O_CLOEXEC);
            if (pHdr->hidType == 0x00) {
                hidha_uhid_create(&m_uhidFd[pHdr->hidType][pHdr->inputPath], mtkProductName,
                                  0x08ED, 0x03, 0, 0, pHdr->inputPath, sizeof(default_KB_Desc), &default_KB_Desc[0]);
            } else if (pHdr->hidType == 0x01) {
                hidha_uhid_create(&m_uhidFd[pHdr->hidType][pHdr->inputPath], mtkProductName,
                                  0x08ED, 0x03, 0, 0, pHdr->inputPath, sizeof(default_Mouse_Desc), &default_Mouse_Desc[0]);
            }
        }
        if(m_uhidFd[pHdr->hidType][pHdr->inputPath] > 0) {
            hidha_uhid_input(m_uhidFd[pHdr->hidType][pHdr->inputPath], pBuffer, buffer->size());
        }
    }

    return OK;
}

status_t UibcServerHandler::sendKeyEvent(UINT16 code, int isPress) {
    scanCodeBuild_t scanCodeBuild;

    if (code < 0x20 || code == 0x7F/*DEL*/ || mTestMode) {
        if (code == 0x7F) {
            scanCodeBuild.scanCode = KEY_DELETE;
        } else {
            scanCodeBuild = UibcMessage::asciiToScancodeBuild((char)code);
        }
        write_uinput_key_event(isPress, scanCodeBuild.scanCode);
    } else {
        if(isPress == 1 && mRemoteClient != NULL) {
#ifdef MTK_AOSP_ENHANCEMENT
            ALOGD("sendKeyEvent: %d / %d", code, isPress);
            mRemoteClient->onDisplayKeyEvent(code, isPress);
#endif
        }
    }
    return OK;
}

void UibcServerHandler::updateScreenMode() {
    double localRatio = 0;
    double wfdRatio = 0;
    double wfdScale = 0;

    sp<IBinder> display = SurfaceComposerClient::getBuiltInDisplay(
                              ISurfaceComposer::eDisplayIdMain);

    DisplayInfo info;
    SurfaceComposerClient::getDisplayInfo(display, &info);

    if (m_Orientation == info.orientation)
        return;

    m_Orientation = info.orientation;

    if (m_wfdWidth > m_wfdHeight) {
        // WFD Landscape mode
        // Follow spec to calculate the offset for the black area
        if (m_Orientation == DISPLAY_ORIENTATION_90 ||
                m_Orientation == DISPLAY_ORIENTATION_270) {
            m_localWidth = info.h;
            m_localHeight = info.w;
        } else {
            m_localWidth = info.w;
            m_localHeight = info.h;
        }
        m_wfdWidthScale = (double)m_localWidth / (double)m_wfdWidth;
        m_wfdHeightScale = (double)m_localHeight / (double)m_wfdHeight;
        localRatio = (double)m_localWidth / (double)m_localHeight;
        wfdRatio = (double)m_wfdWidth / (double)m_wfdHeight;
        if (localRatio > wfdRatio) {
            wfdScale = m_wfdHeightScale = m_wfdWidthScale;
        } else {
            wfdScale = m_wfdWidthScale = m_wfdHeightScale;
        }

        m_XOffset = ((m_wfdWidth - (int)((float)m_localWidth / wfdScale)) / 2);
        m_YOffset = ((m_wfdHeight - (int)((float)m_localHeight / wfdScale)) / 2);

        switch (m_Orientation) {
            case DISPLAY_ORIENTATION_0:
                m_XYSwitch = false;
                m_XRevert = false;
                m_YRevert = false;
                break;
            case DISPLAY_ORIENTATION_90:
                m_XYSwitch = true;
                m_XRevert = true;
                m_YRevert = false;
                break;
            case DISPLAY_ORIENTATION_180:
                m_XYSwitch = false;
                m_XRevert = true;
                m_YRevert = true;
                break;
            case DISPLAY_ORIENTATION_270:
                m_XYSwitch = true;
                m_XRevert = false;
                m_YRevert = true;
                break;
            default:
                break;
        }
    } else if (m_wfdWidth < m_wfdHeight) {
        // WFD Portrait mode
        // Local landscape is full display on sink, no black area displayed
        // Always mapping X and Y according to the scale directly
        m_localWidth = info.w;
        m_localHeight = info.h;
        m_wfdWidthScale = (double)m_localWidth / (double)m_wfdWidth;
        m_wfdHeightScale = (double)m_localHeight / (double)m_wfdHeight;

        localRatio = (double)m_localWidth / (double)m_localHeight;
        wfdRatio = (double)m_wfdWidth / (double)m_wfdHeight;
        if (localRatio > wfdRatio) {
            wfdScale = m_wfdHeightScale = m_wfdWidthScale;
        } else {
            wfdScale = m_wfdWidthScale = m_wfdHeightScale;
        }

        m_XOffset = ((m_wfdWidth - (int)((float)m_localWidth / wfdScale)) / 2);
        m_YOffset = ((m_wfdHeight - (int)((float)m_localHeight / wfdScale)) / 2);

        m_XYSwitch = false;
        m_XRevert = false;
        m_YRevert = false;
    }

    ALOGD("uibc touch info: m_localWidth:%d, m_localHeight:%d, " \
          "m_wfdWidth:%d, m_wfdHeight:%d, ",
          m_localWidth, m_localHeight,
          m_wfdWidth, m_wfdHeight);

    ALOGD("uibc touch info: localRatio:%f, wfdRatio:%f, " \
          "m_wfdWidthScale:%f, m_wfdHeightScale:%f, " \
          "m_XOffset:%d, m_YOffset:%d, ",
          localRatio, wfdRatio,
          m_wfdWidthScale, m_wfdHeightScale,
          m_XOffset, m_YOffset);
}

bool UibcServerHandler::transTouchToSourcePosition(short * x, short * y) {
    ALOGD("uibc XY trans+: x:%d, y:%d", *x, *y);
    short tmp;
    // in the black part
    if ((m_XOffset > 0) &&
            (*x < m_XOffset ||
             *x > (m_wfdWidth - m_XOffset))) {
        return false;
    }
    // in the black part
    if ((m_YOffset > 0) &&
            (*y < m_YOffset ||
             *y > (m_wfdHeight - m_YOffset))) {
        return false;
    }

    *x -= m_XOffset;
    *y -= m_YOffset;
    //ALOGD("transTouchToSourcePosition remove offset: * x:%d, * y:%d", *x, *y);

    *x *= m_wfdWidthScale;
    *y *= m_wfdHeightScale;
    //ALOGD("transTouchToSourcePosition map to source: * x:%d, * y:%d", *x, *y);

    if (m_XYSwitch) {
        tmp = *x;
        *x = *y;
        *y = tmp;
    }
    //ALOGD("transTouchToSourcePosition XY switch: * x:%d, * y:%d", *x, *y);

    if (m_XRevert) {
        if (m_XYSwitch)
            *x = m_localHeight - *x;
        else
            *x = m_localWidth - *x;
    }

    if (m_YRevert) {
        if (m_XYSwitch)
            *y = m_localWidth - *y;
        else
            *y = m_localHeight - *y;
    }
    ALOGD("uibc XY trans-: x:%d, y:%d", *x, *y);
    return true;
}

ssize_t UibcServerHandler::write_uinput_key_event(int down, uint16_t scanCode) {
    struct input_event ev[2];
    ssize_t ret;
    memset(&ev, 0, sizeof(ev));
    //ALOGD("write_uinput_key_event mUinput_fd=%d", mUinput_fd);

    ev[0].type = EV_KEY;
    ev[0].code = scanCode;
    ev[0].value = down;

    ev[1].type = EV_SYN;
    ev[1].code = SYN_REPORT;
    ev[1].value = 0;
    for (int i = 0 ; i < (int)(sizeof(ev) / sizeof(input_event)) ; i++) {
        ret = write(mUinput_fd, &ev[i], sizeof(input_event));
        //ALOGD("write_uinput_key_event ret=%zd", ret);
    }
    return ret;
}

ssize_t UibcServerHandler::write_uinput_touch_down(short* pointsInfo) {
    struct input_event ev[7];
    ssize_t ret;
    memset(&ev, 0, sizeof(ev));
    //ALOGD("write_uinput_touch_down mUinput_fd=%d", mUinput_fd);

    ev[0].type = EV_KEY;
    ev[0].code = BTN_TOUCH;
    ev[0].value = 1;

    ev[1].type = EV_ABS;
    ev[1].code = ABS_MT_TOUCH_MAJOR;
    ev[1].value = 0x40;

    ev[2].type = EV_ABS;
    ev[2].code = ABS_MT_TRACKING_ID;
    ev[2].value = pointsInfo[idVal(0)] & 0xFFFF;

    ev[3].type = EV_ABS;
    ev[3].code = ABS_MT_POSITION_X;
    ev[3].value = pointsInfo[xVal(0)] & 0xFFFF;

    ev[4].type = EV_ABS;
    ev[4].code = ABS_MT_POSITION_Y;
    ev[4].value = pointsInfo[yVal(0)] & 0xFFFF;

    ev[5].type = EV_SYN;
    ev[5].code = SYN_MT_REPORT;
    ev[5].value = 0;

    ev[6].type = EV_SYN;
    ev[6].code = SYN_REPORT;
    ev[6].value = 0;

    for (int i = 0 ; i < (int)(sizeof(ev) / sizeof(input_event)) ; i++) {
        ret = write(mUinput_fd, &ev[i], sizeof(input_event));
        ALOGD("write_uinput_touch_down ret=%zd", ret);
    }
    return ret;
}

ssize_t UibcServerHandler::write_uinput_touch_up() {
    struct input_event ev[2];
    ssize_t ret;
    memset(&ev, 0, sizeof(ev));
    //ALOGD("write_uinput_touch_up mUinput_fd=%d", mUinput_fd);

    ev[0].type = EV_KEY;
    ev[0].code = BTN_TOUCH;
    ev[0].value = 0;

    ev[1].type = EV_SYN;
    ev[1].code = SYN_REPORT;
    ev[1].value = 0;
    for (int i = 0 ; i < (int)(sizeof(ev) / sizeof(input_event)) ; i++) {
        ret = write(mUinput_fd, &ev[i], sizeof(input_event));
        ALOGD("write_uinput_touch_up ret=%zd", ret);
    }
    return ret;
}

ssize_t UibcServerHandler::write_uinput_touch_move(short* pointsInfo) {
    struct input_event ev[26];
    ssize_t ret = 0;
    memset(&ev, 0, sizeof(ev));
    int pointsCnt = (int)pointsInfo[0];
    ALOGD("write_uinput_touch_move mUinput_fd=%d", mUinput_fd);

    if (pointsCnt > 5)
        return -1;

    for (int i = 0; i < pointsCnt; i++) {

        ev[i * 5].type = EV_ABS;
        ev[i * 5].code = ABS_MT_TOUCH_MAJOR;
        ev[i * 5].value = 0x4F;

        ev[i * 5 + 1].type = EV_ABS;
        ev[i * 5 + 1].code = ABS_MT_TRACKING_ID;
        ev[i * 5 + 1].value = pointsInfo[idVal(i)] & 0xFFFF;

        ev[i * 5 + 2].type = EV_ABS;
        ev[i * 5 + 2].code = ABS_MT_POSITION_X;
        ev[i * 5 + 2].value = pointsInfo[xVal(i)] & 0xFFFF;

        ev[i * 5 + 3].type = EV_ABS;
        ev[i * 5 + 3].code = ABS_MT_POSITION_Y;
        ev[i * 5 + 3].value = pointsInfo[yVal(i)] & 0xFFFF;

        ev[i * 5 + 4].type = EV_SYN;
        ev[i * 5 + 4].code = SYN_MT_REPORT;
        ev[i * 5 + 4].value = 0;
    }

    ev[pointsCnt * 5].type = EV_SYN;
    ev[pointsCnt * 5].code = SYN_REPORT;
    ev[pointsCnt * 5].value = 0;

    for (int i = 0 ; i < pointsCnt * 5 + 1 ; i++) {
        ret = write(mUinput_fd, &ev[i], sizeof(input_event));
        //ALOGD("write_uinput_touch_move ret=%d", ret);
    }
    return ret;
}

status_t UibcServerHandler::sendMultipleTouchEvent(WFD_UIBC_GENERIC_MSG* pMsg) {
    short touchPosition[16] = {0};
    short x = 0, y = 0;
    // Sigma tool (mTestMode) always sends touch absolute coordinates
    bool touchSupported = m_Capability->isGenericSupported(DEVICE_TYPE_SINGLETOUCH) | mTestMode;
    bool mouseSupported = m_Capability->isGenericSupported(DEVICE_TYPE_MOUSE);
    WFD_UIBC_GENERIC_BODY_MOUSE_TOUCH* pBody =
        (WFD_UIBC_GENERIC_BODY_MOUSE_TOUCH*)&pMsg->touchMsg;

    //ALOGD("sendMultipleTouchEvent ieID: %d, numptr: %d, m_touchDown = %d",
    //      pBody->ieID,
    //      pBody->numPointers,
    //      m_touchDown);

    //ALOGD("sendMultipleTouchEvent mTestMode=%d, m_touchDown=%d", mTestMode, m_touchDown);

    if ((pMsg->genericHeader.ieID == WFD_UIBC_GENERIC_IE_ID_LMOUSE_TOUCH_DOWN && !m_touchDown) ||
            (mTestMode && !m_touchDown &&
             pMsg->genericHeader.ieID == WFD_UIBC_GENERIC_IE_ID_MOUSE_TOUCH_MOVE)) {
        touchPosition[0] = (short)pBody->numPointers;
        //ALOGD("TOUCH_DOWN: pointerID:%d x:%d y:%d", pBody->coordinates[0].pointerID,
        //ntohs(pBody->coordinates[0].x), ntohs(pBody->coordinates[0].y));
        x = ntohs(pBody->coordinates[0].x);
        y = ntohs(pBody->coordinates[0].y);
        int deltaX = x - m_XCurCoord;
        int deltaY = y - m_YCurCoord;

        m_XCurCoord = m_YCurCoord = -20;
        if ((deltaX < 20 && deltaX > -20) &&
                (deltaY < 20 && deltaY > -20)) {
            if (mouseSupported) {
                updateScreenMode();
                if (!transTouchToSourcePosition(&x, &y))
                    return -1;
                touchPosition[1] = (short)pBody->coordinates[0].pointerID;
                touchPosition[2] = x;
                touchPosition[3] = y;
                write_uinput_touch_down(&touchPosition[0]);
                m_touchDown = true;
            }
        } else {
            if (touchSupported) {
                updateScreenMode();
                if (!transTouchToSourcePosition(&x, &y))
                    return -1;
                touchPosition[1] = (short)pBody->coordinates[0].pointerID;
                touchPosition[2] = x;
                touchPosition[3] = y;
                write_uinput_touch_down(&touchPosition[0]);
                m_touchDown = true;
            }
        }
    } else  if (pMsg->genericHeader.ieID == WFD_UIBC_GENERIC_IE_ID_LMOUSE_TOUCH_UP) {
        //ALOGD("TOUCH_UP: pointerID:%d x:%d y:%d", pBody->coordinates[0].pointerID,
        //ntohs(pBody->coordinates[0].x), ntohs(pBody->coordinates[0].y));

        write_uinput_touch_up();
        if (m_touchDown) {
            m_touchDown = false;
        }
    } else  if (pMsg->genericHeader.ieID == WFD_UIBC_GENERIC_IE_ID_MOUSE_TOUCH_MOVE)  {
        if (m_touchDown) {
            touchPosition[0] = (short)pBody->numPointers;
            for(int i = 0; i < pBody->numPointers && i < (short)pBody->numPointers; i++) {
                //ALOGD("TOUCH_MOVE %dth: pointerID:%d x:%d y:%d", i, pBody->coordinates[i].pointerID,
                //ntohs(pBody->coordinates[i].x), ntohs(pBody->coordinates[i].y));
                touchPosition[i * 3 + 1] = (short)pBody->coordinates[i].pointerID;
                touchPosition[i * 3 + 2] = ntohs(pBody->coordinates[i].x);
                touchPosition[i * 3 + 3] = ntohs(pBody->coordinates[i].y);
                if (!transTouchToSourcePosition(&touchPosition[i * 3 + 2], &touchPosition[i * 3 + 3]))
                    return -1;
            }
            write_uinput_touch_move(&touchPosition[0]);
        } else {
            m_XCurCoord = ntohs(pBody->coordinates[0].x);
            m_YCurCoord = ntohs(pBody->coordinates[0].y);
            //ALOGD("Mouse_MOVE m_XCurCoord:%d m_YCurCoord:%d", m_XCurCoord, m_YCurCoord);
        }
    }
    return OK;
}

status_t UibcServerHandler::genericKeyboardThreadFunc() {
    uint16_t text[] = {KEY_HOME, KEY_BACK, KEY_BACK, KEY_BACK,
                       KEY_BACK, KEY_BACK, KEY_BACK,
                       KEY_SPACE, KEY_SPACE, KEY_SPACE,
                       KEY_M, KEY_E, KEY_D, KEY_I,
                       KEY_A, KEY_T, KEY_E, KEY_K,
                       KEY_SPACE,
                       KEY_M, KEY_I, KEY_R, KEY_A, KEY_C, KEY_A, KEY_S, KEY_T,
                      };
    int i;

    ALOGD("simulateKeyEvent");

    if(mUinput_fd <= 0) {
        return -1;
    }
    for (i = 0; i < (int)(sizeof(text) / sizeof(text[0])) ; i++) {
        ALOGD("simulateKeyEvent %d", text[i]);
        write_uinput_key_event(1, text[i]);
        write_uinput_key_event(0, text[i]);
        usleep(200000);
    }

    return OK;
}

// Static
void *UibcServerHandler::genericKeyboardThreadWrapper(void *me) {
    return (void *)(uintptr_t)static_cast<UibcServerHandler *>(me)->genericKeyboardThreadFunc();
}

status_t UibcServerHandler::simulateKeyEvent() {
    int err;
    pthread_t tid;

    if (!m_GenericDriverInited) {
        if (initGenericDrivers() == OK) {
            m_GenericDriverInited = true;
        }
    }

    err = pthread_create(&tid, NULL, &genericKeyboardThreadWrapper, this);
    if (err != 0) {
        ALOGE("\ncan't create thread :[%s]", strerror(err));
        return -1;
    } else {
        ALOGD("\n Thread created successfully\n");
    }

    return OK;
}

status_t UibcServerHandler::genericMouseThreadFunc() {
    struct input_event ev[3];
    uint16_t text[] = {KEY_HOME, KEY_BACK, KEY_BACK, KEY_BACK,
                       KEY_BACK, KEY_BACK, KEY_BACK
                      };
    ssize_t ret;
    ALOGD("simulateMouseEvent");

    if(mUinput_fd <= 0) {
        return -1;
    }

    for (int i = 0; i < (int)(sizeof(text) / sizeof(text[0])) ; i++) {
        ALOGD("simulateMouseEvent %d", text[i]);
        write_uinput_key_event(1, text[i]);
        write_uinput_key_event(0, text[i]);
        usleep(200000);
    }

    // Move cursor to left and top cornor
    for (int i = 0; i < 2000; i++) {
        memset(&ev, 0, sizeof(ev));
        ev[0].type = EV_REL;
        ev[0].code = REL_X;
        ev[0].value = -1;

        ev[1].type = EV_REL;
        ev[1].code = REL_Y;
        ev[1].value = -1;

        ev[2].type = EV_SYN;
        ev[2].code = SYN_REPORT;
        ev[2].value = 0;
        for (int j = 0; j < (int)(sizeof(ev) / sizeof(input_event)) ; j++) {
            ret = write(mUinput_fd, &ev[j], sizeof(input_event));
        }
        usleep(2000);
    }
    // Move cursor to right and bottom cornor
    for (int i = 0; i < 2000; i++) {
        memset(&ev, 0, sizeof(ev));
        ev[0].type = EV_REL;
        ev[0].code = REL_X;
        ev[0].value = 1;

        ev[1].type = EV_REL;
        ev[1].code = REL_Y;
        ev[1].value = 1;

        ev[2].type = EV_SYN;
        ev[2].code = SYN_REPORT;
        ev[2].value = 0;
        for (int j = 0; j < (int)(sizeof(ev) / sizeof(input_event)) ; j++) {
            ret = write(mUinput_fd, &ev[j], sizeof(input_event));
        }
        usleep(2000);
    }

    for (int i = 0; i < 2000; i++) {
        memset(&ev, 0, sizeof(ev));
        ev[0].type = EV_REL;
        ev[0].code = REL_X;
        ev[0].value = 0;

        ev[1].type = EV_REL;
        ev[1].code = REL_Y;
        ev[1].value = -1;

        ev[2].type = EV_SYN;
        ev[2].code = SYN_REPORT;
        ev[2].value = 0;
        for (int j = 0; j < (int)(sizeof(ev) / sizeof(input_event)) ; j++) {
            ret = write(mUinput_fd, &ev[j], sizeof(input_event));
        }
        usleep(2000);
    }

    write_uinput_key_event(1, BTN_MOUSE);
    for (int i = 0; i < 2000; i++) {
        memset(&ev, 0, sizeof(ev));
        ev[0].type = EV_REL;
        ev[0].code = REL_X;
        ev[0].value = 0;

        ev[1].type = EV_REL;
        ev[1].code = REL_Y;
        ev[1].value = 1;

        ev[2].type = EV_SYN;
        ev[2].code = SYN_REPORT;
        ev[2].value = 0;
        for (int j = 0; j < (int)(sizeof(ev) / sizeof(input_event)) ; j++) {
            ret = write(mUinput_fd, &ev[j], sizeof(input_event));
        }
        usleep(2000);
    }
    write_uinput_key_event(0, BTN_MOUSE);

    return OK;
}

// Static
void *UibcServerHandler::genericMouseThreadWrapper(void *me) {
    return (void *)(uintptr_t)static_cast<UibcServerHandler *>(me)->genericMouseThreadFunc();
}

status_t UibcServerHandler::simulateMouseEvent() {
    int err;
    pthread_t tid;


    if (!m_GenericDriverInited) {
        if (initGenericDrivers() == OK) {
            m_GenericDriverInited = true;
        }
    }


    if (!m_GenericDriverInited) {
        if (initGenericDrivers() == OK) {
            m_GenericDriverInited = true;
        }
    }

    err = pthread_create(&tid, NULL, &genericMouseThreadWrapper, this);
    if (err != 0) {
        ALOGE("\ncan't create thread :[%s]", strerror(err));
        return -1;
    } else {
        ALOGD("\n Thread created successfully\n");
    }

    return OK;
}

/*Internal function to perform UHID write and error checking*/
int UibcServerHandler::hidha_uhid_write(int fd, const struct uhid_event * ev) {
    ssize_t ret;
    ret = write(fd, ev, sizeof(*ev));
    if (ret == -1) {
        int rtn = -errno;
        ALOGD("[HID]uhid_write: Cannot write to uhid:%s", strerror(errno));
        return rtn;

    } else if (ret != sizeof(*ev)) {
        ALOGD("[HID]uhid_write: Cannot write to uhid:%s", strerror(errno));
        return -EFAULT;
    }
    return 0;
}

int UibcServerHandler::hidha_uhid_input(int fd, unsigned char * rpt, unsigned short len) {
    struct uhid_event ev;
    int result;

    //hexdump(rpt, len);

    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_INPUT;
    ev.u.input.size = len;
    if(len > sizeof(ev.u.input.data)) {
        ALOGD("[HID]hidha_uhid_input: fd = %d", fd);

        return -1;
    }
    memcpy(ev.u.input.data, rpt, len);

    result = hidha_uhid_write(fd, &ev);

    if (result) {
        ALOGD("[HID]hidha_uhid_input: fail !");
    }
    return result;

}

void UibcServerHandler::hidha_uhid_create(int* fd, const char * dev_name, unsigned short vendor_id, unsigned short product_id,
        unsigned short version, unsigned char ctry_code, unsigned char hidcBusId, unsigned int dscp_len, unsigned char * p_dscp) {
    int result;
    struct uhid_event ev;

    if (*fd < 0) {
        ALOGD("[HID]hidha_uhid_create: Error: fd = %d", *fd);
        return;
    }
    ALOGD("[HID]hidha_uhid_create: fd = %d, name = [%s], dscp_len = %d", *fd, dev_name, dscp_len);
    ALOGD("[HID]hidha_uhid_create: vendor_id = 0x%04x, product_id = 0x%04x, version= 0x%04x, ctry_code= 0x%04x",
          vendor_id, product_id, version, ctry_code);

    //Create and send hid descriptor to kernel
    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_CREATE;
    strncpy((char*)ev.u.create.name, dev_name, sizeof(ev.u.create.name) - 1);
    ev.u.create.rd_data = p_dscp;
    ev.u.create.rd_size = dscp_len;

    switch (hidcBusId) {
        case HID_INPUT_PATH_USB:
            ev.u.create.bus = BUS_USB;
            break;
        case HID_INPUT_PATH_BLUETOOTH:
            ev.u.create.bus = BUS_BLUETOOTH;
            break;
        default:
            ev.u.create.bus = BUS_USB;
            break;
    }
    ev.u.create.vendor = vendor_id;
    ev.u.create.product = product_id;
    ev.u.create.version = version;
    ev.u.create.country = ctry_code;
    result = hidha_uhid_write(*fd, &ev);

    ALOGD("[HID]hidha_uhid_create: fd = %d, dscp_len = %d, result = %d", *fd, dscp_len, result);

    if (result) {
        ALOGD("[HID]hidha_uhid_create: Error: failed to send DSCP, result = %d", result);

        /* The HID report descriptor is corrupted. Close the driver. */
        close(*fd);
        *fd = -1;
    } else {
        ALOGD("[HID]hidha_uhid_create: success !");
    }

}

status_t UibcServerHandler::hidcKeyboardThreadFunc() {
    unsigned char descriptor[] = {0x05, 0x01, 0x09, 0x06, 0xa1, 0x01, 0x05, 0x07, 0x19, 0xe0, 0x29, 0xe7, 0x15, 0x00, 0x25, 0x01,
                                  0x75, 0x01, 0x95, 0x08, 0x81, 0x02, 0x95, 0x01, 0x75, 0x08, 0x81, 0x01, 0x95, 0x05, 0x75, 0x01,
                                  0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02, 0x95, 0x01, 0x75, 0x03, 0x91, 0x01, 0x95, 0x06,
                                  0x75, 0x08, 0x15, 0x00, 0x26, 0xa4, 0x00, 0x05, 0x07, 0x19, 0x00, 0x29, 0xa4, 0x81, 0x00, 0xc0,
                                 };

    unsigned char report[] = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    unsigned char report_char[] = {0x17, 0x0e, 0x2c, 0x10, 0x0c, 0x15, 0x04, 0x06};
    unsigned int i, j;
    const char*  mtkProductName    = "MTK UIBC HID";

    if (m_uhidFd[DEVICE_TYPE_KEYBOARD][HID_INPUT_PATH_USB] <= 0) {
        m_uhidFd[DEVICE_TYPE_KEYBOARD][HID_INPUT_PATH_USB] = open(UIBC_UHID_DEV_PATH, O_RDWR | O_CLOEXEC);
    }
    hidha_uhid_create(&m_uhidFd[DEVICE_TYPE_KEYBOARD][HID_INPUT_PATH_USB], mtkProductName,
                      0x08ED, 0x03, 0, 0, HID_INPUT_PATH_USB, sizeof(descriptor), &descriptor[0]);
    if(m_uhidFd[DEVICE_TYPE_KEYBOARD][HID_INPUT_PATH_USB] > 0) {
        for (j = 0; j < 3; j++) {
            for (i = 0; i < sizeof(report_char); i++) {
                report[2] = report_char[i];
                hidha_uhid_input(m_uhidFd[DEVICE_TYPE_KEYBOARD][HID_INPUT_PATH_USB], report, sizeof(report));
                usleep(20000);

                report[2] = 0x00;
                hidha_uhid_input(m_uhidFd[DEVICE_TYPE_KEYBOARD][HID_INPUT_PATH_USB], report, sizeof(report));
                usleep(20000);
            }
        }
        close(m_uhidFd[DEVICE_TYPE_KEYBOARD][HID_INPUT_PATH_USB]);
        m_uhidFd[DEVICE_TYPE_KEYBOARD][HID_INPUT_PATH_USB] = -1;
    }

    return OK;
}

// Static
void *UibcServerHandler::hidcKeyboardThreadWrapper(void *me) {
    return (void *)(uintptr_t)static_cast<UibcServerHandler *>(me)->hidcKeyboardThreadFunc();
}

status_t UibcServerHandler::simulateHidcKeyEvent() {
    int err;
    pthread_t tid;

    err = pthread_create(&tid, NULL, &hidcKeyboardThreadWrapper, this);
    if (err != 0) {
        ALOGE("\ncan't create thread :[%s]", strerror(err));
        return -1;
    } else {
        ALOGD("\n Thread created successfully\n");
    }

    return OK;

}

status_t UibcServerHandler::hidcMouseThreadFunc() {
    ALOGI("simulateHidcMouseEventThread [+]");
    unsigned char descriptor[] = {0x05, 0x01, 0x09, 0x02, 0xa1, 0x01, 0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
                                  0x95, 0x03, 0x75, 0x01, 0x81, 0x02, 0x95, 0x01, 0x75, 0x05, 0x81, 0x03, 0x05, 0x01, 0x09, 0x01,
                                  0xa1, 0x00, 0x09, 0x30, 0x09, 0x31, 0x15, 0x81, 0x25, 0x7f, 0x75, 0x08, 0x95, 0x02, 0x81, 0x06,
                                  0xc0, 0x09, 0x38, 0x15, 0x81, 0x25, 0x7f, 0x75, 0x08, 0x95, 0x01, 0x81, 0x06, 0xc0
                                 };
    unsigned char report[] = { 0x00, 0x00, 0x00, 0x00 };
    int i, j;
    const char*  mtkProductName = "MTK UIBC HID";

    if (m_uhidFd[DEVICE_TYPE_MOUSE][HID_INPUT_PATH_USB] <= 0) {
        m_uhidFd[DEVICE_TYPE_MOUSE][HID_INPUT_PATH_USB] = open(UIBC_UHID_DEV_PATH, O_RDWR | O_CLOEXEC);
    }
    hidha_uhid_create(&m_uhidFd[DEVICE_TYPE_MOUSE][HID_INPUT_PATH_USB], mtkProductName,
                      0x08ED, 0x03, 0, 0, HID_INPUT_PATH_USB, sizeof(descriptor), &descriptor[0]);
    if(m_uhidFd[DEVICE_TYPE_MOUSE][HID_INPUT_PATH_USB] > 0) {
        for (j = 0 ; j < 8 ; j++) {
            for (i = 0; i < 127 / 2; i++) {
                report[1] = 0xfe;
                report[2] = 0x01;
                hidha_uhid_input(m_uhidFd[DEVICE_TYPE_MOUSE][HID_INPUT_PATH_USB], report, sizeof(report));
                usleep(9000); // 0.009 s
            }
            for (i = 0; i < 127 / 2; i++) {
                report[1] = 0x01;
                report[2] = 0xfe;
                hidha_uhid_input(m_uhidFd[DEVICE_TYPE_MOUSE][HID_INPUT_PATH_USB], report, sizeof(report));
                usleep(9000); // 0.009 s
            }
        }
        close(m_uhidFd[DEVICE_TYPE_MOUSE][HID_INPUT_PATH_USB]);
        m_uhidFd[DEVICE_TYPE_MOUSE][HID_INPUT_PATH_USB] = -1;
    }
    ALOGI("simulateHidcMouseEventThread [-]");
    return OK;
}

// Static
void *UibcServerHandler::hidcMouseThreadWrapper(void *me) {
    return (void *)(uintptr_t)static_cast<UibcServerHandler *>(me)->hidcMouseThreadFunc();
}

status_t UibcServerHandler::simulateHidcMouseEvent() {
    int err;
    pthread_t tid;

    err = pthread_create(&tid, NULL, &hidcMouseThreadWrapper, this);
    if (err != 0) {
        ALOGE("\ncan't create thread :[%s]", strerror(err));
        return -1;
    } else {
        ALOGD("\n Thread created successfully\n");
    }

    return OK;
}

}
