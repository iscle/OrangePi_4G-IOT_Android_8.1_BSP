/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/

#include <HwcTrace.h>
#include <tangier/TngDisplayContext.h>
#include <anniedale/AnnPlaneManager.h>
#include <PlatfBufferManager.h>
#include <IDisplayDevice.h>
#include <PrimaryDevice.h>
#include <ExternalDevice.h>
#include <VirtualDevice.h>
#include <Hwcomposer.h>
#include <PlatFactory.h>
#include <common/VsyncControl.h>
#include <common/HdcpControl.h>
#include <common/BlankControl.h>
#include <common/PrepareListener.h>
#include <common/VideoPayloadManager.h>



namespace android {
namespace intel {

PlatFactory::PlatFactory()
{
    CTRACE();
}

PlatFactory::~PlatFactory()
{
    CTRACE();
}

DisplayPlaneManager* PlatFactory::createDisplayPlaneManager()
{
    CTRACE();
    return (new AnnPlaneManager());
}

BufferManager* PlatFactory::createBufferManager()
{
    CTRACE();
    return (new PlatfBufferManager());
}

IDisplayDevice* PlatFactory::createDisplayDevice(int disp)
{
    CTRACE();
    // when createDisplayDevice is called, Hwcomposer has already finished construction.
    Hwcomposer &hwc = Hwcomposer::getInstance();
    class PlatDeviceControlFactory: public DeviceControlFactory {
       public:
           virtual IVsyncControl* createVsyncControl()       {return new VsyncControl();}
           virtual IBlankControl* createBlankControl()       {return new BlankControl();}
           virtual IHdcpControl* createHdcpControl()         {return new HdcpControl();}
       };

    switch (disp) {
        case IDisplayDevice::DEVICE_PRIMARY:
           return new PrimaryDevice(hwc, new PlatDeviceControlFactory());
        case IDisplayDevice::DEVICE_EXTERNAL:
            return new ExternalDevice(hwc, new PlatDeviceControlFactory());
        case IDisplayDevice::DEVICE_VIRTUAL:
            return new VirtualDevice(hwc);
        default:
            ETRACE("invalid display device %d", disp);
            return NULL;
    }
}

IDisplayContext* PlatFactory::createDisplayContext()
{
    CTRACE();
    return new TngDisplayContext();
}

IVideoPayloadManager * PlatFactory::createVideoPayloadManager()
{
    return new VideoPayloadManager();
}

Hwcomposer* Hwcomposer::createHwcomposer()
{
    CTRACE();
    Hwcomposer *hwc = new Hwcomposer(new PlatFactory());
    return hwc;
}

} //namespace intel
} //namespace android
