#ifndef ANDROID_GUI_BUFFERQUEUEMONITOR_H
#define ANDROID_GUI_BUFFERQUEUEMONITOR_H

#include <utils/String8.h>
#include <mediatek/GuiExtMonitor.h>

namespace android {

class BufferQueueCore;

//-------------------------------------------------------------------------
// BufferQueueMonitor
//-------------------------------------------------------------------------
class BufferQueueMonitor : public GuiExtMonitor<BufferQueueMonitor, wp<BufferQueueCore> > {
public:
    BufferQueueMonitor() {}
    ~BufferQueueMonitor() {}

    status_t dump(String8& result, const char* prefix);
};


};
#endif
