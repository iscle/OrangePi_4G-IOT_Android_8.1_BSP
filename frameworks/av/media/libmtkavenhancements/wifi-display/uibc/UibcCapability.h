
#ifndef UIBC_CAPABILITY_H
#define UIBC_CAPABILITY_H

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AHandler.h>
//#include <media/stagefright/foundation/ParsedMessage.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/Thread.h>
#include <linux/input.h>

#define UIBC_MAX_DEV_CNT    16

#define UIBC_ENABLED        true
#define UIBC_DISABLED       false
#define UIBC_NONE           0x00

#define INPUT_CAT_GENERIC           0
#define INPUT_CAT_HIDC              2

#define INPUT_CAT_GENERIC_FLAG      (0x01 << 0)
#define INPUT_CAT_HIDC_FLAG         (0x01 << 1)

#define DEVICE_TYPE_KEYBOARD            0
#define DEVICE_TYPE_MOUSE               1
#define DEVICE_TYPE_SINGLETOUCH         2
#define DEVICE_TYPE_MULTITOUCH          3
#define DEVICE_TYPE_JOYSTICK            4
#define DEVICE_TYPE_CAMERA              5
#define DEVICE_TYPE_GESTURE             6
#define DEVICE_TYPE_REMOTECONTROL       7
#define DEVICE_TYPE_UNKNOWN             254
#define DEVICE_TYPE_VENDOR_SPECIFIC     255

#define HID_INPUT_PATH_INFRARED         0
#define HID_INPUT_PATH_USB              1
#define HID_INPUT_PATH_BLUETOOTH        2
#define HID_INPUT_PATH_ZIGBEE           3
#define HID_INPUT_PATH_WIFI             4
#define HID_INPUT_PATH_VENDOR_SPECIFIC  255

#define DEV_TYPE_KEYBOARD_FLAG      (0x01 << 0)
#define DEV_TYPE_MOUSE_FLAG         (0x01 << 1)
#define DEV_TYPE_SINGLETOUCH_FLAG   (0x01 << 2)
#define DEV_TYPE_MULTITOUCH_FLAG    (0x01 << 3)
#define DEV_TYPE_JOYSTICK_FLAG      (0x01 << 4)
#define DEV_TYPE_CAMERA_FLAG        (0x01 << 5)
#define DEV_TYPE_GESTURE_FLAG       (0x01 << 6)
#define DEV_TYPE_REMOTECONTROL_FLAG (0x01 << 7)

#define HID_PATH_INFRARED_FLAG      (0x01 <<0)
#define HID_PATH_USB_FLAG           (0x01 <<1)
#define HID_PATH_BLUETOOTH_FLAG     (0x01 <<2)
#define HID_PATH_ZIGBEE_FLAG        (0x01 <<3)
#define HID_PATH_WIFI_FLAG          (0x01 <<4)

#define UIBC_LOCAL_HIDC_CAPABILTY "wfd_uibc_capability: input_category_list=GENERIC, HIDC;generic_cap_list=Keyboard, Mouse, SingleTouch, MultiTouch;" \
    "hidc_cap_list=Keyboard/USB, Mouse/USB, Keyboard/BT, Mouse/BT;port=none\r\n"
#define UIBC_LOCAL_CAPABILTY "wfd_uibc_capability: input_category_list=GENERIC;generic_cap_list=Keyboard, Mouse, SingleTouch, MultiTouch;hidc_cap_list=none;port=none\r\n"
#define UIBC_SINK_CAPABILTY_NONE "wfd_uibc_capability: none\r\n"
#define UIBC_SINK_CAPABILTY_SIGMA "wfd_uibc_capability: input_category_list=GENERIC, HIDC;generic_cap_list=Mouse, Keyboard;hidc_cap_list=Mouse/USB, Keyboard/USB;port=none\r\n"

namespace android {

struct UibcCapability  : public RefBase {
    UibcCapability();

    struct UibcDevice  : public RefBase  {
        int mCategory;
        int mDevice;
        int mPath;
    };

    void parseRemoteCapabilities(AString capStr);
    const char* getLocalCapabilities();
    const char* getSupportedCapabilities();

    bool isUibcSupported();
    bool isGenericSupported(int devType);
    bool isHidcSupported(int devType, int path);
    int getPort();

    bool isHidcPropEnabled();

    void setTestMode(bool mode);
    bool getTestMode();
    bool mTestMode;

protected:
    virtual ~UibcCapability();

    int mRemoteCat;
    int mSupportedCatFlag;
    List<sp<UibcDevice> > mRemoteDevs;
    List<sp<UibcDevice> > mSupportedDevs;
    int mSupportedGenDevFlag;
    int mPort;
    AString mSupportedCap;
    AString mLocalCap;
    bool m_HidcPropEnable;
private:
    int parseDeviceType(char* devStr);
    int parseInputPath(char* devStr);
    void parseCapabilities(AString capStr, List<sp<UibcDevice> >& devivces,
                           int* category, int* port);
    void updateSupportedCap();
    void dumpDevs(List<sp<UibcDevice> >& devivces);

    DISALLOW_EVIL_CONSTRUCTORS(UibcCapability);
};

}

#endif
