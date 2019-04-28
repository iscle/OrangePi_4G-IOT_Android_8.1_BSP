#define LOG_TAG "DumpTunnel"

#include <dlfcn.h>
#include <utils/String8.h>
#include <binder/Parcel.h>
#include <cutils/log.h>
#include <gui/mediatek/IDumpTunnel.h>

namespace android {

// client : proxy GuiEx class
class BpDumpTunnel : public BpInterface<IDumpTunnel> {
public:
    BpDumpTunnel(const sp<IBinder>& impl)
        :   BpInterface<IDumpTunnel>(impl) {
    }

    virtual ~BpDumpTunnel();

    virtual status_t kickDump(String8& result, const char* prefix) {
        Parcel data, reply;
        data.writeInterfaceToken(IDumpTunnel::getInterfaceDescriptor());
        data.writeString8(result);
        data.writeCString(prefix);
        status_t err = remote()->transact(DUMPTUNNEL_DUMP, data, &reply);
        if (err != NO_ERROR) {
            ALOGE("kickDump could not contact remote\n");
            return err;
        }
        result = reply.readString8();
        err = reply.readInt32();
        return err;
    }
};

// Out-of-line virtual method definition to trigger vtable emission in this
// translation unit (see clang warning -Wweak-vtables)
BpDumpTunnel::~BpDumpTunnel() {}

IMPLEMENT_META_INTERFACE(DumpTunnel, "DumpTunnel");

status_t BnDumpTunnel::onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) {
    switch (code) {
        case DUMPTUNNEL_DUMP: {
            CHECK_INTERFACE(IDumpTunnel, data, reply);
            String8 result;
            const char* prefix = NULL;
            result = data.readString8();
            prefix = data.readCString();

            status_t ret = kickDump(result, prefix);
            reply->writeString8(result);
            reply->writeInt32(ret);
            return NO_ERROR;
        }
    }
    return BBinder::onTransact(code, data, reply, flags);
}

// ----------------------------------------------------------------------------

ANDROID_SINGLETON_STATIC_INSTANCE(DumpTunnelHelper);

DumpTunnelHelper::DumpTunnelHelper() :
    mSoHandle(NULL),
    mRegDumpPtr(NULL),
    mUnregDumpPtr(NULL)
{
    typedef bool (*RegDumpPrototype)(const sp<IDumpTunnel>&, const String8&);
    typedef bool (*UnregDumpPrototype)(const String8&);

    // dlopen must set RTLD_LAZY flag because of performance issue
    mSoHandle = dlopen("libgui_ext.so", RTLD_LAZY);
    if (mSoHandle) {
        mRegDumpPtr = reinterpret_cast<RegDumpPrototype>(dlsym(mSoHandle, "regDump"));
        mUnregDumpPtr = reinterpret_cast<UnregDumpPrototype>(dlsym(mSoHandle, "unregDump"));
        if (NULL == mRegDumpPtr) {
            ALOGE("finding regDump() failed");
        }
        if (NULL == mUnregDumpPtr) {
            ALOGE("finding unregDump() failed");
        }
    } else {
        ALOGE("open libgui_ext failed");
    }
}

DumpTunnelHelper::~DumpTunnelHelper() {
    if(mSoHandle != NULL)
        dlclose(mSoHandle);
}

bool DumpTunnelHelper::regDump(const sp<IDumpTunnel>& tunnel, const String8& key) {
    bool result = false;
    if (NULL == mRegDumpPtr) {
        ALOGE("finding regDump() failed");
        return result;
    }
    result = mRegDumpPtr(tunnel, key);

    return result;
}

bool DumpTunnelHelper::unregDump(const String8& key) {
    bool result = false;
    if (NULL == mUnregDumpPtr) {
        ALOGE("finding unregDump() failed");
        return result;
    }
    result = mUnregDumpPtr(key);

    return result;
}

};
