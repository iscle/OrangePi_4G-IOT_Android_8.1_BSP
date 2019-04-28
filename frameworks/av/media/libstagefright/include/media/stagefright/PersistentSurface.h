/*
 * Copyright 2015 The Android Open Source Project
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

#ifndef PERSISTENT_SURFACE_H_

#define PERSISTENT_SURFACE_H_

#include <gui/IGraphicBufferProducer.h>
#include <android/IGraphicBufferSource.h>
#include <media/stagefright/foundation/ABase.h>
#include <binder/Parcel.h>

namespace android {

struct PersistentSurface : public RefBase {
    PersistentSurface() {}

    PersistentSurface(
            const sp<IGraphicBufferProducer>& bufferProducer,
            const sp<IGraphicBufferSource>& bufferSource) :
        mBufferProducer(bufferProducer),
        mBufferSource(bufferSource) { }

    sp<IGraphicBufferProducer> getBufferProducer() const {
        return mBufferProducer;
    }

    sp<IGraphicBufferSource> getBufferSource() const {
        return mBufferSource;
    }

    status_t writeToParcel(Parcel *parcel) const {
        parcel->writeStrongBinder(IInterface::asBinder(mBufferProducer));
        parcel->writeStrongBinder(IInterface::asBinder(mBufferSource));
        return NO_ERROR;
    }

    status_t readFromParcel(const Parcel *parcel) {
        mBufferProducer = interface_cast<IGraphicBufferProducer>(
                parcel->readStrongBinder());
        mBufferSource = interface_cast<IGraphicBufferSource>(
                parcel->readStrongBinder());
        return NO_ERROR;
    }

private:
    sp<IGraphicBufferProducer> mBufferProducer;
    sp<IGraphicBufferSource> mBufferSource;

    DISALLOW_EVIL_CONSTRUCTORS(PersistentSurface);
};

}  // namespace android

#endif  // PERSISTENT_SURFACE_H_
