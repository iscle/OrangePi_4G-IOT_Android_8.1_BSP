#ifndef ANDROID_GUI_IDUMPTUNNEL_H
#define ANDROID_GUI_IDUMPTUNNEL_H

#include <binder/IInterface.h>
#include <utils/Singleton.h>

namespace android
{

class IDumpTunnel : public IInterface {
protected:
    enum {
        DUMPTUNNEL_DUMP = IBinder::FIRST_CALL_TRANSACTION
    };

public:
    DECLARE_META_INTERFACE(DumpTunnel)

    virtual status_t kickDump(String8& /*result*/, const char* /*prefix*/) = 0;
};

class BnDumpTunnel : public BnInterface<IDumpTunnel>
{
    virtual status_t onTransact(uint32_t code,
                                const Parcel& data,
                                Parcel* reply,
                                uint32_t flags = 0);
};

// helper class for libgui_ext dynamic linking
class DumpTunnelHelper : public Singleton<DumpTunnelHelper> {
    void* mSoHandle;
    bool (*mRegDumpPtr)(const sp<IDumpTunnel>&, const String8&);
    bool (*mUnregDumpPtr)(const String8&);

public:
    DumpTunnelHelper();
    virtual ~DumpTunnelHelper();

    // register tunnel into guiext-server with a given key name
    // and need to unregister it back
    // in general usage, need to use identical key name for reg/unreg pair
    bool regDump(const sp<IDumpTunnel>& tunnel, const String8& key);
    bool unregDump(const String8& key);
};


};
#endif
