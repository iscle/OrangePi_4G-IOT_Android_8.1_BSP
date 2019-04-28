
#ifndef UIBC_SERVER_HANDLER_H
#define UIBC_SERVER_HANDLER_H

#include "UibcHandler.h"
#include "UibcCapability.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ABase.h>
#include <utils/RefBase.h>
#include <utils/Thread.h>

#include <linux/input.h>
#include <linux/uhid.h>

#include <media/IRemoteDisplayClient.h>
#include "UibcMessage.h"

#define UIBC_KBD_DEV_PATH   "/dev/uibc"
#define UIBC_UHID_DEV_PATH  "/dev/uhid"

#define MAX_POINTERS 5

#define idVal(_x) (_x * 3 + 1)
#define xVal(_x) (_x * 3 + 2)
#define yVal(_x) (_x * 3 + 3)

#define WFD_UIBC_HIDC_USAGE_REPORT_INPUT        0x00
#define WFD_UIBC_HIDC_USAGE_REPORT_DESCRIPTOR   0x01

#define HID_LEFTSHIFT_BIT                       0x02
#define HID_RIGHTSHIFT_BIT                      0x20

namespace android {

class IRemoteDisplayClient;

struct UibcServerHandler : public RefBase, public UibcHandler {
    UibcServerHandler(sp<IRemoteDisplayClient> remoteClient);

    status_t init(bool testMode);
    status_t destroy();
    status_t handleUIBCMessage(const sp<ABuffer> &buffer);

    status_t simulateKeyEvent();
    status_t simulateMouseEvent();
    status_t simulateHidcKeyEvent();
    status_t simulateHidcMouseEvent();

protected:
    virtual ~UibcServerHandler();

private:

    int mUinput_fd;
    int m_XCurCoord;
    int m_YCurCoord;

    int m_XOffset;
    int m_YOffset;
    bool m_XRevert;
    bool m_YRevert;
    bool m_XYSwitch;
    uint8_t m_Orientation;

    bool m_GenericDriverInited;
    bool m_ShiftPressed;
    bool m_touchDown;
    bool m_mouseDown;
    int m_deltaX;
    int m_deltaY;

    sp<IRemoteDisplayClient> mRemoteClient;

    int m_uhidFd[8][5];
    bool m_hidcDefDescTest;
    bool m_TouchLatencyTest;

    void updateScreenMode();
    bool transTouchToSourcePosition(short* x, short* y);

    status_t initGenericDrivers();
    status_t handleGenericInput(const sp<ABuffer> &buffer);
    status_t handleHIDCInput(const sp<ABuffer> &buffer);

    ssize_t write_uinput_key_event(int down, uint16_t scanCode);
    ssize_t write_uinput_touch_down(short* pointsInfo);
    ssize_t write_uinput_touch_up();
    ssize_t write_uinput_touch_move(short* pointsInfo);

    status_t sendKeyEvent(UINT16 code, int isPress);
    status_t sendMultipleTouchEvent(WFD_UIBC_GENERIC_MSG* pBody);

    int hidha_uhid_write(int fd, const struct uhid_event *ev);
    int hidha_uhid_input(int fd, unsigned char* rpt, unsigned short len);
    void hidha_uhid_create(int* fd, const char *dev_name, unsigned short vendor_id, unsigned short product_id,
                           unsigned short version, unsigned char ctry_code, unsigned char hidcBusId,
                           unsigned int dscp_len, unsigned char *p_dscp);

    status_t genericMouseThreadFunc();
    static void* genericMouseThreadWrapper(void *me);

    status_t genericKeyboardThreadFunc();
    static void* genericKeyboardThreadWrapper(void *me);

    status_t hidcMouseThreadFunc();
    static void* hidcMouseThreadWrapper(void *me);

    status_t hidcKeyboardThreadFunc();
    static void* hidcKeyboardThreadWrapper(void *me);

    DISALLOW_EVIL_CONSTRUCTORS(UibcServerHandler);
};

}

#endif
