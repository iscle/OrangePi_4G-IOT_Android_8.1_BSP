/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <stdint.h>
#include <sys/types.h>

#include <binder/PermissionCache.h>
#include <binder/IPCThreadState.h>

#include <private/android_filesystem_config.h>

#include "Client.h"
#include "Layer.h"
#include "SurfaceFlinger.h"

namespace android {

// ---------------------------------------------------------------------------

const String16 sAccessSurfaceFlinger("android.permission.ACCESS_SURFACE_FLINGER");

// ---------------------------------------------------------------------------

Client::Client(const sp<SurfaceFlinger>& flinger)
    : Client(flinger, nullptr)
{
#ifdef MTK_SF_DEBUG_SUPPORT
    // get client process info
    mClientPid = IPCThreadState::self()->getCallingPid();
    SurfaceFlinger::getProcessName(mClientPid, mClientProcName);
    ALOGI("[SF client] NEW(%p) for (%d:%s)", this, mClientPid, mClientProcName.string());
#endif
}

Client::Client(const sp<SurfaceFlinger>& flinger, const sp<Layer>& parentLayer)
    : mFlinger(flinger),
      mParentLayer(parentLayer)
{
}

Client::~Client()
{
#ifdef MTK_SF_DEBUG_SUPPORT
    // log client process info, and the layers going to be removed
    ALOGI("[SF client] DEL(%p) for (%d:%s)", this, mClientPid, mClientProcName.string());
    for (size_t i=0 ; i<mLayers.size() ; i++) {
        sp<Layer> layer(mLayers.valueAt(i).promote());
        if (layer != 0) {
            ALOGD("    remove: %s", layer->getName().string());
        }
    }
#endif
    const size_t count = mLayers.size();
    for (size_t i=0 ; i<count ; i++) {
        sp<Layer> l = mLayers.valueAt(i).promote();
        if (l != nullptr) {
            mFlinger->removeLayer(l);
        }
    }
}

void Client::setParentLayer(const sp<Layer>& parentLayer) {
    Mutex::Autolock _l(mLock);
    mParentLayer = parentLayer;
}

sp<Layer> Client::getParentLayer(bool* outParentDied) const {
    Mutex::Autolock _l(mLock);
    sp<Layer> parent = mParentLayer.promote();
    if (outParentDied != nullptr) {
        *outParentDied = (mParentLayer != nullptr && parent == nullptr);
    }
    return parent;
}

status_t Client::initCheck() const {
    return NO_ERROR;
}

void Client::attachLayer(const sp<IBinder>& handle, const sp<Layer>& layer)
{
    Mutex::Autolock _l(mLock);
    mLayers.add(handle, layer);
}

void Client::detachLayer(const Layer* layer)
{
    Mutex::Autolock _l(mLock);
    // we do a linear search here, because this doesn't happen often
    const size_t count = mLayers.size();
    for (size_t i=0 ; i<count ; i++) {
        if (mLayers.valueAt(i) == layer) {
            mLayers.removeItemsAt(i, 1);
            break;
        }
    }
}
sp<Layer> Client::getLayerUser(const sp<IBinder>& handle) const
{
    Mutex::Autolock _l(mLock);
    sp<Layer> lbc;
    wp<Layer> layer(mLayers.valueFor(handle));
    if (layer != 0) {
        lbc = layer.promote();
        ALOGE_IF(lbc==0, "getLayerUser(name=%p) is dead", handle.get());
    }
    return lbc;
}


status_t Client::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    // these must be checked
     IPCThreadState* ipc = IPCThreadState::self();
     const int pid = ipc->getCallingPid();
     const int uid = ipc->getCallingUid();
     const int self_pid = getpid();
     // If we are called from another non root process without the GRAPHICS, SYSTEM, or ROOT
     // uid we require the sAccessSurfaceFlinger permission.
     // We grant an exception in the case that the Client has a "parent layer", as its
     // effects will be scoped to that layer.
     if (CC_UNLIKELY(pid != self_pid && uid != AID_GRAPHICS && uid != AID_SYSTEM && uid != 0)
             && (getParentLayer() == nullptr)) {
         // we're called from a different process, do the real check
         if (!PermissionCache::checkCallingPermission(sAccessSurfaceFlinger))
         {
             ALOGE("Permission Denial: "
                     "can't openGlobalTransaction pid=%d, uid<=%d", pid, uid);
             return PERMISSION_DENIED;
         }
     }
     return BnSurfaceComposerClient::onTransact(code, data, reply, flags);
}


status_t Client::createSurface(
        const String8& name,
        uint32_t w, uint32_t h, PixelFormat format, uint32_t flags,
        const sp<IBinder>& parentHandle, uint32_t windowType, uint32_t ownerUid,
        sp<IBinder>* handle,
        sp<IGraphicBufferProducer>* gbp)
{
    sp<Layer> parent = nullptr;
    if (parentHandle != nullptr) {
        parent = getLayerUser(parentHandle);
        if (parent == nullptr) {
            return NAME_NOT_FOUND;
        }
    }
    if (parent == nullptr) {
        bool parentDied;
        parent = getParentLayer(&parentDied);
        // If we had a parent, but it died, we've lost all
        // our capabilities.
        if (parentDied) {
            return NAME_NOT_FOUND;
        }
    }

    /*
     * createSurface must be called from the GL thread so that it can
     * have access to the GL context.
     */
    class MessageCreateLayer : public MessageBase {
        SurfaceFlinger* flinger;
        Client* client;
        sp<IBinder>* handle;
        sp<IGraphicBufferProducer>* gbp;
        status_t result;
        const String8& name;
        uint32_t w, h;
        PixelFormat format;
        uint32_t flags;
        sp<Layer>* parent;
        uint32_t windowType;
        uint32_t ownerUid;
    public:
        MessageCreateLayer(SurfaceFlinger* flinger,
                const String8& name, Client* client,
                uint32_t w, uint32_t h, PixelFormat format, uint32_t flags,
                sp<IBinder>* handle, uint32_t windowType, uint32_t ownerUid,
                sp<IGraphicBufferProducer>* gbp,
                sp<Layer>* parent)
            : flinger(flinger), client(client),
              handle(handle), gbp(gbp), result(NO_ERROR),
              name(name), w(w), h(h), format(format), flags(flags),
              parent(parent), windowType(windowType), ownerUid(ownerUid) {
        }
        status_t getResult() const { return result; }
        virtual bool handler() {
            result = flinger->createLayer(name, client, w, h, format, flags,
                    windowType, ownerUid, handle, gbp, parent);
            return true;
        }
    };

    sp<MessageBase> msg = new MessageCreateLayer(mFlinger.get(),
            name, this, w, h, format, flags, handle,
            windowType, ownerUid, gbp, &parent);
    mFlinger->postMessageSync(msg);
    return static_cast<MessageCreateLayer*>( msg.get() )->getResult();
}

status_t Client::destroySurface(const sp<IBinder>& handle) {
    return mFlinger->onLayerRemoved(this, handle);
}

status_t Client::clearLayerFrameStats(const sp<IBinder>& handle) const {
    sp<Layer> layer = getLayerUser(handle);
    if (layer == NULL) {
        return NAME_NOT_FOUND;
    }
    layer->clearFrameStats();
    return NO_ERROR;
}

status_t Client::getLayerFrameStats(const sp<IBinder>& handle, FrameStats* outStats) const {
    sp<Layer> layer = getLayerUser(handle);
    if (layer == NULL) {
        return NAME_NOT_FOUND;
    }
    layer->getFrameStats(outStats);
    return NO_ERROR;
}

// ---------------------------------------------------------------------------
}; // namespace android
