#ifndef __GUIEXT_MONITOR_H__
#define __GUIEXT_MONITOR_H__


#include <utils/String8.h>
#include <utils/Singleton.h>
#include <utils/KeyedVector.h>

#include <ui/mediatek/IDumpTunnel.h>


namespace android {

template <typename TYPE, typename ITEM>
class GuiExtMonitor : public Singleton<TYPE>
{
public:
    GuiExtMonitor();
    virtual ~GuiExtMonitor();

    virtual status_t monitor(ITEM item);
    virtual status_t unmonitor(ITEM item);

    virtual status_t dump(String8& result, const char* prefix);

protected:
    status_t getProcessName();
    virtual String8 getKeyName() const;

protected:
    bool mIsRegistered;
    String8 mProcessName;
    mutable Mutex mLock;
    sp<BnDumpTunnel> mDumpTunnel;
    KeyedVector<ITEM, int> mItemList;
};


//--------------------------------------------------------------------------------------------------

template <typename TYPE, typename ITEM>
GuiExtMonitor<TYPE, ITEM>::GuiExtMonitor()
    : mIsRegistered(false) {
    getProcessName();
}


template <typename TYPE, typename ITEM>
GuiExtMonitor<TYPE, ITEM>::~GuiExtMonitor() {
    Mutex::Autolock _l(mLock);

    if (mIsRegistered) {
        DumpTunnelHelper::getInstance().unregDump(getKeyName());
        mIsRegistered = false;
    }
}


template <typename TYPE, typename ITEM>
status_t GuiExtMonitor<TYPE, ITEM>::monitor(ITEM item) {
    Mutex::Autolock _l(mLock);

    mItemList.add(item, 0);
    if (!mIsRegistered)
    {

        class MonitorTunnel : public BnDumpTunnel {
        public:
            MonitorTunnel(GuiExtMonitor<TYPE, ITEM>* pMonitor)
                : mMonitor(pMonitor) {}
            virtual ~MonitorTunnel() {}

            // IDumpTunnel interface
            virtual status_t kickDump(String8& result, const char* prefix) {
                return mMonitor->dump(result, prefix);
            }

        private:
            GuiExtMonitor<TYPE, ITEM>* mMonitor;
        };

        mDumpTunnel = new MonitorTunnel(this);
        if (DumpTunnelHelper::getInstance().regDump(mDumpTunnel, getKeyName())) {
            mIsRegistered = true;
        }
    }
    return NO_ERROR;
}


template <typename TYPE, typename ITEM>
status_t GuiExtMonitor<TYPE, ITEM>::unmonitor(ITEM item) {
    Mutex::Autolock _l(mLock);

    mItemList.removeItem(item);
    return NO_ERROR;
}


template <typename TYPE, typename ITEM>
status_t GuiExtMonitor<TYPE, ITEM>::dump(String8& /*result*/, const char* /*prefix*/) {
    return NO_ERROR;
}


template <typename TYPE, typename ITEM>
status_t GuiExtMonitor<TYPE, ITEM>::getProcessName() {
    int pid = getpid();
    FILE *fp = fopen(String8::format("/proc/%d/cmdline", pid), "r");
    if (NULL != fp) {
        const size_t size = 64;
        char proc_name[size];
        fgets(proc_name, size, fp);
        fclose(fp);
        mProcessName = proc_name;
    } else {
        mProcessName = "unknownProcess";
    }
    return NO_ERROR;
}


template <typename TYPE, typename ITEM>
String8 GuiExtMonitor<TYPE, ITEM>::getKeyName() const {
    return String8::format("[%d:%s]", getpid(), mProcessName.string());
}


#define IMPLEMENT_META_GUIEXTMONITOR(TYPE, ITEM, NAME)                                  \
    ANDROID_SINGLETON_STATIC_INSTANCE(TYPE);                                            \
    template <>                                                                         \
    String8 GuiExtMonitor<TYPE, ITEM>::getKeyName() const {                             \
        return String8::format("%s-[%d:%s]", NAME, getpid(), mProcessName.string());    \
    }


}; // namespace android

#endif
