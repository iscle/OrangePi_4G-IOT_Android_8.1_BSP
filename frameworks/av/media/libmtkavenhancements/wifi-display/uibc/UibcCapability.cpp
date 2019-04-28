
#define LOG_TAG "UibcCapability"
#include "UibcCapability.h"

#include <utils/Log.h>
#include "UibcMessage.h"
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
#include <cutils/properties.h>
#include <media/stagefright/foundation/ParsedMessage.h>

namespace android {

UibcCapability::UibcCapability()
    : mTestMode(false),
      mRemoteCat(0x00),
      mSupportedCatFlag(0x00),
      mSupportedGenDevFlag(0x00),
      mPort(0),
      m_HidcPropEnable(false) {
    ALOGD("UibcCapability()");
}

UibcCapability::~UibcCapability() {
    ALOGD("~UibcCapability()");
    while (!mRemoteDevs.empty()) {
        mRemoteDevs.erase(mRemoteDevs.begin());
    }

    while (!mSupportedDevs.empty()) {
        mSupportedDevs.erase(mSupportedDevs.begin());
    }
}

void UibcCapability::dumpDevs(List<sp<UibcDevice> >& devivces) {
    for (List<sp<UibcDevice> >::iterator itDev = devivces.begin();
         itDev != devivces.end(); ++itDev)
        ALOGD("dumpDevs itDev->mCategory=%d, itDev->mPath=%d, itDev->mDevice=%d",
              (*itDev)->mCategory, (*itDev)->mPath, (*itDev)->mDevice);
}

int UibcCapability::parseDeviceType(char* devStr) {
    if(strstr(devStr, "Keyboard")) {
        return DEVICE_TYPE_KEYBOARD;
    } else if(strstr(devStr, "Mouse")) {
        return DEVICE_TYPE_MOUSE;
    } else if(strstr(devStr, "SingleTouch")) {
        return DEVICE_TYPE_SINGLETOUCH;
    } else if(strstr(devStr, "MultiTouch")) {
        return DEVICE_TYPE_MULTITOUCH;
    } else if(strstr(devStr, "Joystick")) {
        return DEVICE_TYPE_JOYSTICK;
    } else if(strstr(devStr, "Camera")) {
        return DEVICE_TYPE_CAMERA;
    } else if(strstr(devStr, "Gesture")) {
        return DEVICE_TYPE_GESTURE;
    } else if(strstr(devStr, "RemoteControl")) {
        return DEVICE_TYPE_REMOTECONTROL;
    }
    return DEVICE_TYPE_UNKNOWN;
}

int UibcCapability::parseInputPath(char* devStr) {
    if(strstr(devStr, "Infrared")) {
        return HID_INPUT_PATH_INFRARED;
    } else if(strstr(devStr, "USB")) {
        return HID_INPUT_PATH_USB;
    } else if(strstr(devStr, "BT")) {
        return HID_INPUT_PATH_BLUETOOTH;
    } else if(strstr(devStr, "Zigbee")) {
        return HID_INPUT_PATH_ZIGBEE;
    } else if(strstr(devStr, "Wi-Fi")) {
        return HID_INPUT_PATH_WIFI;
    } else if(strstr(devStr, "No-SP")) {
        return HID_INPUT_PATH_VENDOR_SPECIFIC;
    }
    return DEVICE_TYPE_UNKNOWN;
}

void UibcCapability::parseCapabilities(AString capStr, List<sp<UibcDevice> >& devivces,
                                       int* category, int* port) {
    ALOGD("parseCapabilities parseCapabilities=%s", capStr.c_str());
    char** spCapStr = UibcMessage::str_split((char*)capStr.c_str(), ";");
    char** splitedStr = NULL;
    char* pch = NULL;
    int i, j;

    *category = 0;
    for (i = 0; * (spCapStr + i); i++) {
        //ALOGD("parseCapabilities *(spCapStr + i):%s", *(spCapStr + i));
        if(strstr(*(spCapStr + i), "input_category_list=")) {
            pch = strstr(*(spCapStr + i), "GENERIC");
            if(pch)
                *category |= INPUT_CAT_GENERIC_FLAG;
            pch = strstr(*(spCapStr + i), "HIDC");
            if(pch)
                *category |= INPUT_CAT_HIDC_FLAG;
        } else if(strstr(*(spCapStr + i), "generic_cap_list=") != NULL &&
                  strstr(*(spCapStr + i), "none") == NULL ) {
            splitedStr = UibcMessage::str_split(*(spCapStr + i), ",");
            for (j = 0; * (splitedStr + j); j++) {
                //ALOGD("parseCapabilities *(splitedStr + i):%s", *(splitedStr + j));
                sp<UibcDevice> device = new UibcDevice;
                device->mCategory = INPUT_CAT_GENERIC;
                device->mPath = 0x00;
                device->mDevice = parseDeviceType(*(splitedStr + j));
                devivces.push_back(device);
                free(*(splitedStr + j));
            }
            free(splitedStr);
        } else if(strstr(*(spCapStr + i), "hidc_cap_list=") != NULL &&
                  strstr(*(spCapStr + i), "none") == NULL ) {
            splitedStr = UibcMessage::str_split(*(spCapStr + i), ",");
            for (j = 0; * (splitedStr + j); j++) {
                //ALOGD("parseCapabilities *(splitedStr + i):%s", *(splitedStr + j));
                sp<UibcDevice> device = new UibcDevice;
                device->mCategory = INPUT_CAT_HIDC;
                device->mPath = parseInputPath(*(splitedStr + j));
                device->mDevice = parseDeviceType(*(splitedStr + j));
                devivces.push_back(device);
                free(*(splitedStr + j));
            }
            free(splitedStr);
        } else if(strstr(*(spCapStr + i), "port=")) {
            char* start = strstr(*(spCapStr + i), "=") + 1;
            *port = strtol(start, NULL, 10);
        }
        free(*(spCapStr + i));
    }
    free(spCapStr);

#if 0
    ALOGD("dumpDevs(devivces)");
    dumpDevs(devivces);
    ALOGD("port=%d", *port);
#endif
}


void UibcCapability::parseRemoteCapabilities(AString capStr) {
    ALOGD("parseRemoteCapabilities capStr=%s", capStr.c_str());
    mSupportedCap.clear();
    int port = 0;
    parseCapabilities(capStr, mRemoteDevs, &mRemoteCat, &port);
    updateSupportedCap();
    if (port > 0) {
        mPort = port;
    }
}

const char* UibcCapability::getLocalCapabilities() {
    char val[PROPERTY_VALUE_MAX];
    int len;
    mLocalCap.clear();
    len = property_get("media.wfd.uibc.localcap", val, NULL);

    if (len > 0 && !strstr(val, "0")) {
        ALOGI("media.wfd.uibc.localcap:%s", val);
        bool bNone = true;
        mLocalCap.append("wfd_uibc_capability");
        if (strstr(val, "n")) {
            mLocalCap.append(": none\r\n");
        } else {
            mLocalCap.append(": input_category_list=");
            if (strstr(val, "G")) {
                mLocalCap.append("GENERIC,");
            } else if (strstr(val, "H")) {
                mLocalCap.append("HIDC,");
            } else {
                mLocalCap.append("none,");
            }
            mLocalCap.erase(mLocalCap.size() - 1, 1);

            mLocalCap.append(";generic_cap_list=");
            if (strstr(val, "K")) {
                mLocalCap.append("Keyboard,");
                bNone = false;
            }
            if (strstr(val, "M")) {
                mLocalCap.append("Mouse,");
                bNone = false;
            }
            if (strstr(val, "T")) {
                mLocalCap.append("SingleTouch,");
                bNone = false;
            }
            if (strstr(val, "U")) {
                mLocalCap.append("MultiTouch,");
                bNone = false;
            }
            if (bNone) {
                mLocalCap.append("none,");
            }
            mLocalCap.erase(mLocalCap.size() - 1, 1);
            mLocalCap.append(";hidc_cap_list=none;port=none\r\n");
        }
    } else if (isHidcPropEnabled()) {
        mLocalCap.append(UIBC_LOCAL_HIDC_CAPABILTY);
    } else if (mTestMode) {
        mLocalCap.append(UIBC_SINK_CAPABILTY_SIGMA);
    } else {
        mLocalCap.append(UIBC_LOCAL_CAPABILTY);
    }

    ALOGI("getLocalCapabilities mLocalCapabilities=%s", mLocalCap.c_str());
    return mLocalCap.c_str();
}

/*List<sp<UibcDevice> > UibcCapability::getSupportedDevices() {
    return mSupportedDevs;
}*/

void UibcCapability::updateSupportedCap() {
    ALOGD("updateSupportedCap()");
    int localCat;
    List<sp<UibcDevice> > localDevs;
    int port;

    while (!mSupportedDevs.empty()) {
        mSupportedDevs.erase(mSupportedDevs.begin());
    }

    // Parse remote parameters
    parseCapabilities(getLocalCapabilities(), localDevs, &localCat, &port);

    mSupportedCatFlag = (localCat & mRemoteCat);
    mSupportedGenDevFlag = 0;

    for (List<sp<UibcDevice> >::iterator itLocal = localDevs.begin();
         itLocal != localDevs.end(); ++itLocal) {
        for (List<sp<UibcDevice> >::iterator itRemote = mRemoteDevs.begin();
             itRemote != mRemoteDevs.end(); ++itRemote) {
            if ((*itLocal)->mCategory == INPUT_CAT_GENERIC &&
                (*itRemote)->mCategory == INPUT_CAT_GENERIC) {
                if ((*itRemote)->mDevice == (*itLocal)->mDevice) {
                    sp<UibcDevice> supperDev = new UibcDevice;
                    supperDev->mDevice = (*itRemote)->mDevice;
                    supperDev->mPath = (*itRemote)->mPath;
                    supperDev->mCategory = (*itRemote)->mCategory;
                    mSupportedGenDevFlag |= (0x01 << supperDev->mDevice);
                    mSupportedDevs.push_back(supperDev);
                }
            }
            if ((*itLocal)->mCategory == INPUT_CAT_HIDC &&
                (*itRemote)->mCategory == INPUT_CAT_HIDC) {
                if ((*itRemote)->mPath == HID_INPUT_PATH_USB ||
                    (*itRemote)->mPath == HID_INPUT_PATH_BLUETOOTH) {
                    if ((*itRemote)->mDevice == (*itLocal)->mDevice &&
                        (*itRemote)->mPath == (*itLocal)->mPath) {
                        sp<UibcDevice> supperDev = new UibcDevice;
                        supperDev->mDevice =  (*itRemote)->mDevice;
                        supperDev->mPath = (*itRemote)->mPath;
                        supperDev->mCategory = (*itRemote)->mCategory;
                        mSupportedDevs.push_back(supperDev);
                    }
                }
            }
        }
    }

    while (!localDevs.empty()) {
        localDevs.erase(localDevs.begin());
    }

    ALOGD("mSupportedCatFlag=%d", mSupportedCatFlag);
    ALOGD("dumpDevs(mSupportedDevs);");
    dumpDevs(mSupportedDevs);
    ALOGD("mPort=%d", mPort);
}

const char* UibcCapability::getSupportedCapabilities() {
    // Generate supported parameters
    char* output = (char*)malloc(512);
    char* outputCat = (char*)malloc(256);
    char* outputGen = (char*)malloc(256);
    char* outputHid = (char*)malloc(256);
    *output = 0x00;
    *outputCat = 0x00;
    *outputGen = 0x00;
    *outputHid = 0x00;

    if (mSupportedCatFlag == 0x0) {
        strcat(outputCat, "none");
    } else {
        if ((mSupportedCatFlag & INPUT_CAT_GENERIC_FLAG) != 0x0)
            strcat(outputCat, "GENERIC, ");
        if ((mSupportedCatFlag & INPUT_CAT_HIDC_FLAG) != 0x0)
            strcat(outputCat, "HIDC, ");

        for (List<sp<UibcDevice> >::iterator itSupp = mSupportedDevs.begin();
             itSupp != mSupportedDevs.end(); ++itSupp) {
            if ((*itSupp)->mCategory == INPUT_CAT_GENERIC) {
                if ((*itSupp)->mDevice == DEVICE_TYPE_KEYBOARD)
                    strcat(outputGen, "Keyboard, ");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_MOUSE)
                    strcat(outputGen, "Mouse, ");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_SINGLETOUCH)
                    strcat(outputGen, "SingleTouch, ");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_MULTITOUCH)
                    strcat(outputGen, "MultiTouch, ");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_JOYSTICK)
                    strcat(outputGen, "Joystick, ");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_CAMERA)
                    strcat(outputGen, "Camera, ");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_GESTURE)
                    strcat(outputGen, "Gesture, ");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_REMOTECONTROL)
                    strcat(outputGen, "RemoteControl, ");
            } else if ((*itSupp)->mCategory == INPUT_CAT_HIDC) {
                if ((*itSupp)->mDevice == DEVICE_TYPE_KEYBOARD)
                    strcat(outputHid, "Keyboard/");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_MOUSE)
                    strcat(outputHid, "Mouse/");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_SINGLETOUCH)
                    strcat(outputHid, "SingleTouch/");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_MULTITOUCH)
                    strcat(outputHid, "MultiTouch/");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_JOYSTICK)
                    strcat(outputHid, "Joystick/");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_CAMERA)
                    strcat(outputHid, "Camera/");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_GESTURE)
                    strcat(outputHid, "Gesture/");
                else if ((*itSupp)->mDevice == DEVICE_TYPE_REMOTECONTROL)
                    strcat(outputHid, "RemoteControl/");

                if ((*itSupp)->mPath == HID_INPUT_PATH_INFRARED)
                    strcat(outputHid, "Infrared, ");
                else if ((*itSupp)->mPath == HID_INPUT_PATH_USB)
                    strcat(outputHid, "USB, ");
                else if ((*itSupp)->mPath == HID_INPUT_PATH_BLUETOOTH)
                    strcat(outputHid, "BT, ");
                else if ((*itSupp)->mPath == HID_INPUT_PATH_ZIGBEE)
                    strcat(outputHid, "Zigbee, ");
                else if ((*itSupp)->mPath == HID_INPUT_PATH_WIFI)
                    strcat(outputHid, "Wi-Fi, ");
            }
        }
    }

    if (strlen(outputCat) > 1) {
        outputCat[strlen(outputCat) - 2] = 0x0;
    }

    if (strlen(outputGen) < 1) {
        strcat(outputGen, "none");
    } else {
        outputGen[strlen(outputGen) - 2] = 0x0;
    }
    if (strlen(outputHid) < 1) {
        strcat(outputHid, "none");
    } else {
        outputHid[strlen(outputHid) - 2] = 0x0;
    }

    if (mPort <= 0) {
        mPort = WFD_UIBC_SERVER_PORT;
    }

    if (mSupportedCatFlag == 0x0) {
        sprintf (output, "wfd_uibc_capability: none\r\n");
    } else {
        sprintf (output,
                 "wfd_uibc_capability: input_category_list=%s;generic_cap_list=%s;hidc_cap_list=%s;port=%d\r\n",
                 outputCat, outputGen, outputHid, mPort);
    }

    mSupportedCap.setTo(output);

    free(outputCat);
    free(outputGen);
    free(outputHid);
    free(output);

    ALOGD("getSupportedCapabilities =%s", mSupportedCap.c_str());
    return mSupportedCap.c_str();
}

int UibcCapability::getPort() {
    ALOGD("getPort mPort=%d", mPort);
    return mPort;
}

bool UibcCapability::isUibcSupported() {
    ALOGD("isUibcSupported() mSupportedCatFlag=%d", mSupportedCatFlag);
    return !(mSupportedCatFlag == 0x00);
}

bool UibcCapability::isGenericSupported(int devType) {
    if ((mSupportedCatFlag & INPUT_CAT_GENERIC_FLAG) == 0x00) {
        ALOGD("Generic is not supported.");
        return false;
    }
    bool supported = !!(mSupportedGenDevFlag & (0x01 << devType));
    if (!supported) {
        ALOGD("devType:%d is not supported.", devType);
    }
    return supported;
}

bool UibcCapability::isHidcSupported(int devType, int path) {
    if ((mSupportedCatFlag & INPUT_CAT_HIDC_FLAG) == 0x00) {
        ALOGD("HIDC is not supported.");
        return false;
    }
    for (List<sp<UibcDevice> >::iterator it = mSupportedDevs.begin();
         it != mSupportedDevs.end(); ++it) {
        if ((*it)->mDevice ==  devType && (*it)->mPath == path) {
            return true;
        }
    }
    ALOGD("HIDC path:%d devType:%d is not supported.", path, devType);
    return false;
}

bool UibcCapability::isHidcPropEnabled() {
    char val[PROPERTY_VALUE_MAX];
    m_HidcPropEnable = false;

    if (property_get("media.wfd.uibc.hidc", val, NULL)) {
        ALOGI("media.wfd.uibc.hidc:%s", val);
        int hidcEnabled = atoi(val);
        if (hidcEnabled > 0) {
            m_HidcPropEnable = true;
        } else {
            m_HidcPropEnable = false;
        }
    }
    ALOGD("isHidcPropEnabled() m_HidcPropEnable=%d", m_HidcPropEnable);
    return m_HidcPropEnable;
}

void UibcCapability::setTestMode(bool mode) {
    mTestMode = mode;
}

bool UibcCapability::getTestMode() {
    return mTestMode;
}

}
