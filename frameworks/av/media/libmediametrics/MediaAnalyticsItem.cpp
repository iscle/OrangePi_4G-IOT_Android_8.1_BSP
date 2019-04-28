/*
 * Copyright (C) 2016 The Android Open Source Project
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

#undef LOG_TAG
#define LOG_TAG "MediaAnalyticsItem"

#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

#include <binder/Parcel.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/Mutex.h>
#include <utils/SortedVector.h>
#include <utils/threads.h>

#include <media/stagefright/foundation/AString.h>

#include <binder/IServiceManager.h>
#include <media/IMediaAnalyticsService.h>
#include <media/MediaAnalyticsItem.h>
#include <private/android_filesystem_config.h>

namespace android {

#define DEBUG_SERVICEACCESS     0
#define DEBUG_API               0
#define DEBUG_ALLOCATIONS       0

// after this many failed attempts, we stop trying [from this process] and just say that
// the service is off.
#define SVC_TRIES               2

// the few universal keys we have
const MediaAnalyticsItem::Key MediaAnalyticsItem::kKeyAny  = "any";
const MediaAnalyticsItem::Key MediaAnalyticsItem::kKeyNone  = "none";

const char * const MediaAnalyticsItem::EnabledProperty  = "media.metrics.enabled";
const char * const MediaAnalyticsItem::EnabledPropertyPersist  = "persist.media.metrics.enabled";
const int MediaAnalyticsItem::EnabledProperty_default  = 1;


// access functions for the class
MediaAnalyticsItem::MediaAnalyticsItem()
    : mPid(-1),
      mUid(-1),
      mPkgVersionCode(0),
      mSessionID(MediaAnalyticsItem::SessionIDNone),
      mTimestamp(0),
      mFinalized(0),
      mPropCount(0), mPropSize(0), mProps(NULL)
{
    mKey = MediaAnalyticsItem::kKeyNone;
}

MediaAnalyticsItem::MediaAnalyticsItem(MediaAnalyticsItem::Key key)
    : mPid(-1),
      mUid(-1),
      mPkgVersionCode(0),
      mSessionID(MediaAnalyticsItem::SessionIDNone),
      mTimestamp(0),
      mFinalized(0),
      mPropCount(0), mPropSize(0), mProps(NULL)
{
    if (DEBUG_ALLOCATIONS) {
        ALOGD("Allocate MediaAnalyticsItem @ %p", this);
    }
    mKey = key;
}

MediaAnalyticsItem::~MediaAnalyticsItem() {
    if (DEBUG_ALLOCATIONS) {
        ALOGD("Destroy  MediaAnalyticsItem @ %p", this);
    }
    clear();
}

void MediaAnalyticsItem::clear() {

    // clean allocated storage from key
    mKey.clear();

    // clean various major parameters
    mSessionID = MediaAnalyticsItem::SessionIDNone;

    // clean attributes
    // contents of the attributes
    for (size_t i = 0 ; i < mPropCount; i++ ) {
        clearProp(&mProps[i]);
    }
    // the attribute records themselves
    if (mProps != NULL) {
        free(mProps);
        mProps = NULL;
    }
    mPropSize = 0;
    mPropCount = 0;

    return;
}

// make a deep copy of myself
MediaAnalyticsItem *MediaAnalyticsItem::dup() {
    MediaAnalyticsItem *dst = new MediaAnalyticsItem(this->mKey);

    if (dst != NULL) {
        // key as part of constructor
        dst->mPid = this->mPid;
        dst->mUid = this->mUid;
        dst->mPkgName = this->mPkgName;
        dst->mPkgVersionCode = this->mPkgVersionCode;
        dst->mSessionID = this->mSessionID;
        dst->mTimestamp = this->mTimestamp;
        dst->mFinalized = this->mFinalized;

        // properties aka attributes
        dst->growProps(this->mPropCount);
        for(size_t i=0;i<mPropCount;i++) {
            copyProp(&dst->mProps[i], &this->mProps[i]);
        }
        dst->mPropCount = this->mPropCount;
    }

    return dst;
}

// so clients can send intermediate values to be overlaid later
MediaAnalyticsItem &MediaAnalyticsItem::setFinalized(bool value) {
    mFinalized = value;
    return *this;
}

bool MediaAnalyticsItem::getFinalized() const {
    return mFinalized;
}

MediaAnalyticsItem &MediaAnalyticsItem::setSessionID(MediaAnalyticsItem::SessionID_t id) {
    mSessionID = id;
    return *this;
}

MediaAnalyticsItem::SessionID_t MediaAnalyticsItem::getSessionID() const {
    return mSessionID;
}

MediaAnalyticsItem::SessionID_t MediaAnalyticsItem::generateSessionID() {

    if (mSessionID == SessionIDNone) {
        // get one from the server
        MediaAnalyticsItem::SessionID_t newid = SessionIDNone;
        sp<IMediaAnalyticsService> svc = getInstance();
        if (svc != NULL) {
            newid = svc->generateUniqueSessionID();
        }
        mSessionID = newid;
    }

    return mSessionID;
}

MediaAnalyticsItem &MediaAnalyticsItem::clearSessionID() {
    mSessionID = MediaAnalyticsItem::SessionIDNone;
    return *this;
}

MediaAnalyticsItem &MediaAnalyticsItem::setTimestamp(nsecs_t ts) {
    mTimestamp = ts;
    return *this;
}

nsecs_t MediaAnalyticsItem::getTimestamp() const {
    return mTimestamp;
}

MediaAnalyticsItem &MediaAnalyticsItem::setPid(pid_t pid) {
    mPid = pid;
    return *this;
}

pid_t MediaAnalyticsItem::getPid() const {
    return mPid;
}

MediaAnalyticsItem &MediaAnalyticsItem::setUid(uid_t uid) {
    mUid = uid;
    return *this;
}

uid_t MediaAnalyticsItem::getUid() const {
    return mUid;
}

MediaAnalyticsItem &MediaAnalyticsItem::setPkgName(AString pkgName) {
    mPkgName = pkgName;
    return *this;
}

AString MediaAnalyticsItem::getPkgName() const {
    return mPkgName;
}

MediaAnalyticsItem &MediaAnalyticsItem::setPkgVersionCode(int32_t pkgVersionCode) {
    mPkgVersionCode = pkgVersionCode;
    return *this;
}

int32_t MediaAnalyticsItem::getPkgVersionCode() const {
    return mPkgVersionCode;
}

// this key is for the overall record -- "codec", "player", "drm", etc
MediaAnalyticsItem &MediaAnalyticsItem::setKey(MediaAnalyticsItem::Key key) {
    mKey = key;
    return *this;
}

MediaAnalyticsItem::Key MediaAnalyticsItem::getKey() {
    return mKey;
}

// number of attributes we have in this record
int32_t MediaAnalyticsItem::count() const {
    return mPropCount;
}

// find the proper entry in the list
size_t MediaAnalyticsItem::findPropIndex(const char *name, size_t len)
{
    size_t i = 0;
    for (; i < mPropCount; i++) {
        Prop *prop = &mProps[i];
        if (prop->mNameLen != len || prop->mName == NULL) {
            continue;
        }
        if (memcmp(name, prop->mName, len) == 0) {
            break;
        }
    }
    return i;
}

MediaAnalyticsItem::Prop *MediaAnalyticsItem::findProp(const char *name) {
    size_t len = strlen(name);
    size_t i = findPropIndex(name, len);
    if (i < mPropCount) {
        return &mProps[i];
    }
    return NULL;
}

void MediaAnalyticsItem::Prop::setName(const char *name, size_t len) {
    mNameLen = len;
    mName = (const char *) malloc(len+1);
    memcpy ((void *)mName, name, len+1);
}

// used only as part of a storing operation
MediaAnalyticsItem::Prop *MediaAnalyticsItem::allocateProp(const char *name) {
    Mutex::Autolock _l(mLock);
    size_t len = strlen(name);
    size_t i = findPropIndex(name, len);
    Prop *prop;

    if (i < mPropCount) {
        prop = &mProps[i];
    } else {
        if (i == mPropSize) {
            growProps();
            // XXX: verify success
        }
        i = mPropCount++;
        prop = &mProps[i];
        prop->setName(name, len);
        prop->mType = kTypeNone;        // make caller set type info
        prop->u.CStringValue = 0;
    }

    return prop;
}

// used within the summarizers; return whether property existed
bool MediaAnalyticsItem::removeProp(const char *name) {
    size_t len = strlen(name);
    size_t i = findPropIndex(name, len);
    if (i < mPropCount) {
        Prop *prop = &mProps[i];
        clearProp(prop);
        if (i != mPropCount-1) {
            // in the middle, bring last one down to fill gap
            copyProp(prop, &mProps[mPropCount-1]);
            clearProp(&mProps[mPropCount-1]);
        }
        mPropCount--;
        return true;
    }
    return false;
}

// set the values
void MediaAnalyticsItem::setInt32(MediaAnalyticsItem::Attr name, int32_t value) {
    Prop *prop = allocateProp(name);
    prop->mType = kTypeInt32;
    prop->u.int32Value = value;
}

void MediaAnalyticsItem::setInt64(MediaAnalyticsItem::Attr name, int64_t value) {
    Prop *prop = allocateProp(name);
    prop->mType = kTypeInt64;
    prop->u.int64Value = value;
}

void MediaAnalyticsItem::setDouble(MediaAnalyticsItem::Attr name, double value) {
    Prop *prop = allocateProp(name);
    prop->mType = kTypeDouble;
    prop->u.doubleValue = value;
}

void MediaAnalyticsItem::setCString(MediaAnalyticsItem::Attr name, const char *value) {

    Prop *prop = allocateProp(name);
    // any old value will be gone
    prop->mType = kTypeCString;
    prop->u.CStringValue = strdup(value);
}

void MediaAnalyticsItem::setRate(MediaAnalyticsItem::Attr name, int64_t count, int64_t duration) {
    Prop *prop = allocateProp(name);
    prop->mType = kTypeRate;
    prop->u.rate.count = count;
    prop->u.rate.duration = duration;
}


// find/add/set fused into a single operation
void MediaAnalyticsItem::addInt32(MediaAnalyticsItem::Attr name, int32_t value) {
    Prop *prop = allocateProp(name);
    switch (prop->mType) {
        case kTypeInt32:
            prop->u.int32Value += value;
            break;
        default:
            clearPropValue(prop);
            prop->mType = kTypeInt32;
            prop->u.int32Value = value;
            break;
    }
}

void MediaAnalyticsItem::addInt64(MediaAnalyticsItem::Attr name, int64_t value) {
    Prop *prop = allocateProp(name);
    switch (prop->mType) {
        case kTypeInt64:
            prop->u.int64Value += value;
            break;
        default:
            clearPropValue(prop);
            prop->mType = kTypeInt64;
            prop->u.int64Value = value;
            break;
    }
}

void MediaAnalyticsItem::addRate(MediaAnalyticsItem::Attr name, int64_t count, int64_t duration) {
    Prop *prop = allocateProp(name);
    switch (prop->mType) {
        case kTypeRate:
            prop->u.rate.count += count;
            prop->u.rate.duration += duration;
            break;
        default:
            clearPropValue(prop);
            prop->mType = kTypeRate;
            prop->u.rate.count = count;
            prop->u.rate.duration = duration;
            break;
    }
}

void MediaAnalyticsItem::addDouble(MediaAnalyticsItem::Attr name, double value) {
    Prop *prop = allocateProp(name);
    switch (prop->mType) {
        case kTypeDouble:
            prop->u.doubleValue += value;
            break;
        default:
            clearPropValue(prop);
            prop->mType = kTypeDouble;
            prop->u.doubleValue = value;
            break;
    }
}

// find & extract values
bool MediaAnalyticsItem::getInt32(MediaAnalyticsItem::Attr name, int32_t *value) {
    Prop *prop = findProp(name);
    if (prop == NULL || prop->mType != kTypeInt32) {
        return false;
    }
    if (value != NULL) {
        *value = prop->u.int32Value;
    }
    return true;
}

bool MediaAnalyticsItem::getInt64(MediaAnalyticsItem::Attr name, int64_t *value) {
    Prop *prop = findProp(name);
    if (prop == NULL || prop->mType != kTypeInt64) {
        return false;
    }
    if (value != NULL) {
        *value = prop->u.int64Value;
    }
    return true;
}

bool MediaAnalyticsItem::getRate(MediaAnalyticsItem::Attr name, int64_t *count, int64_t *duration, double *rate) {
    Prop *prop = findProp(name);
    if (prop == NULL || prop->mType != kTypeRate) {
        return false;
    }
    if (count != NULL) {
        *count = prop->u.rate.count;
    }
    if (duration != NULL) {
        *duration = prop->u.rate.duration;
    }
    if (rate != NULL) {
        double r = 0.0;
        if (prop->u.rate.duration != 0) {
            r = prop->u.rate.count / (double) prop->u.rate.duration;
        }
        *rate = r;
    }
    return true;
}

bool MediaAnalyticsItem::getDouble(MediaAnalyticsItem::Attr name, double *value) {
    Prop *prop = findProp(name);
    if (prop == NULL || prop->mType != kTypeDouble) {
        return false;
    }
    if (value != NULL) {
        *value = prop->u.doubleValue;
    }
    return true;
}

// caller responsible for the returned string
bool MediaAnalyticsItem::getCString(MediaAnalyticsItem::Attr name, char **value) {
    Prop *prop = findProp(name);
    if (prop == NULL || prop->mType != kTypeDouble) {
        return false;
    }
    if (value != NULL) {
        *value = strdup(prop->u.CStringValue);
    }
    return true;
}

// remove indicated keys and their values
// return value is # keys removed
int32_t MediaAnalyticsItem::filter(int n, MediaAnalyticsItem::Attr attrs[]) {
    int zapped = 0;
    if (attrs == NULL || n <= 0) {
        return -1;
    }
    for (ssize_t i = 0 ; i < n ;  i++) {
        const char *name = attrs[i];
        size_t len = strlen(name);
        size_t j = findPropIndex(name, len);
        if (j >= mPropCount) {
            // not there
            continue;
        } else if (j+1 == mPropCount) {
            // last one, shorten
            zapped++;
            clearProp(&mProps[j]);
            mPropCount--;
        } else {
            // in the middle, bring last one down and shorten
            zapped++;
            clearProp(&mProps[j]);
            mProps[j] = mProps[mPropCount-1];
            mPropCount--;
        }
    }
    return zapped;
}

// remove any keys NOT in the provided list
// return value is # keys removed
int32_t MediaAnalyticsItem::filterNot(int n, MediaAnalyticsItem::Attr attrs[]) {
    int zapped = 0;
    if (attrs == NULL || n <= 0) {
        return -1;
    }
    for (ssize_t i = mPropCount-1 ; i >=0 ;  i--) {
        Prop *prop = &mProps[i];
        for (ssize_t j = 0; j < n ; j++) {
            if (strcmp(prop->mName, attrs[j]) == 0) {
                clearProp(prop);
                zapped++;
                if (i != (ssize_t)(mPropCount-1)) {
                    *prop = mProps[mPropCount-1];
                }
                initProp(&mProps[mPropCount-1]);
                mPropCount--;
                break;
            }
        }
    }
    return zapped;
}

// remove a single key
// return value is 0 (not found) or 1 (found and removed)
int32_t MediaAnalyticsItem::filter(MediaAnalyticsItem::Attr name) {
    return filter(1, &name);
}

// handle individual items/properties stored within the class
//

void MediaAnalyticsItem::initProp(Prop *prop) {
    if (prop != NULL) {
        prop->mName = NULL;
        prop->mNameLen = 0;

        prop->mType = kTypeNone;
    }
}

void MediaAnalyticsItem::clearProp(Prop *prop)
{
    if (prop != NULL) {
        if (prop->mName != NULL) {
            free((void *)prop->mName);
            prop->mName = NULL;
            prop->mNameLen = 0;
        }

        clearPropValue(prop);
    }
}

void MediaAnalyticsItem::clearPropValue(Prop *prop)
{
    if (prop != NULL) {
        if (prop->mType == kTypeCString && prop->u.CStringValue != NULL) {
            free(prop->u.CStringValue);
            prop->u.CStringValue = NULL;
        }
        prop->mType = kTypeNone;
    }
}

void MediaAnalyticsItem::copyProp(Prop *dst, const Prop *src)
{
    // get rid of any pointers in the dst
    clearProp(dst);

    *dst = *src;

    // fix any pointers that we blindly copied, so we have our own copies
    if (dst->mName) {
        void *p =  malloc(dst->mNameLen + 1);
        memcpy (p, src->mName, dst->mNameLen + 1);
        dst->mName = (const char *) p;
    }
    if (dst->mType == kTypeCString) {
        dst->u.CStringValue = strdup(src->u.CStringValue);
    }
}

void MediaAnalyticsItem::growProps(int increment)
{
    if (increment <= 0) {
        increment = kGrowProps;
    }
    int nsize = mPropSize + increment;
    Prop *ni = (Prop *)realloc(mProps, sizeof(Prop) * nsize);

    if (ni != NULL) {
        for (int i = mPropSize; i < nsize; i++) {
            initProp(&ni[i]);
        }
        mProps = ni;
        mPropSize = nsize;
    }
}

// Parcel / serialize things for binder calls
//

int32_t MediaAnalyticsItem::readFromParcel(const Parcel& data) {
    // into 'this' object
    // .. we make a copy of the string to put away.
    mKey = data.readCString();
    mPid = data.readInt32();
    mUid = data.readInt32();
    mPkgName = data.readCString();
    mPkgVersionCode = data.readInt32();
    mSessionID = data.readInt64();
    mFinalized = data.readInt32();
    mTimestamp = data.readInt64();

    int count = data.readInt32();
    for (int i = 0; i < count ; i++) {
            MediaAnalyticsItem::Attr attr = data.readCString();
            int32_t ztype = data.readInt32();
                switch (ztype) {
                    case MediaAnalyticsItem::kTypeInt32:
                            setInt32(attr, data.readInt32());
                            break;
                    case MediaAnalyticsItem::kTypeInt64:
                            setInt64(attr, data.readInt64());
                            break;
                    case MediaAnalyticsItem::kTypeDouble:
                            setDouble(attr, data.readDouble());
                            break;
                    case MediaAnalyticsItem::kTypeCString:
                            setCString(attr, data.readCString());
                            break;
                    case MediaAnalyticsItem::kTypeRate:
                            {
                                int64_t count = data.readInt64();
                                int64_t duration = data.readInt64();
                                setRate(attr, count, duration);
                            }
                            break;
                    default:
                            ALOGE("reading bad item type: %d, idx %d",
                                  ztype, i);
                            return -1;
                }
    }

    return 0;
}

int32_t MediaAnalyticsItem::writeToParcel(Parcel *data) {
    if (data == NULL) return -1;


    data->writeCString(mKey.c_str());
    data->writeInt32(mPid);
    data->writeInt32(mUid);
    data->writeCString(mPkgName.c_str());
    data->writeInt32(mPkgVersionCode);
    data->writeInt64(mSessionID);
    data->writeInt32(mFinalized);
    data->writeInt64(mTimestamp);

    // set of items
    int count = mPropCount;
    data->writeInt32(count);
    for (int i = 0 ; i < count; i++ ) {
            Prop *prop = &mProps[i];
            data->writeCString(prop->mName);
            data->writeInt32(prop->mType);
            switch (prop->mType) {
                case MediaAnalyticsItem::kTypeInt32:
                        data->writeInt32(prop->u.int32Value);
                        break;
                case MediaAnalyticsItem::kTypeInt64:
                        data->writeInt64(prop->u.int64Value);
                        break;
                case MediaAnalyticsItem::kTypeDouble:
                        data->writeDouble(prop->u.doubleValue);
                        break;
                case MediaAnalyticsItem::kTypeRate:
                        data->writeInt64(prop->u.rate.count);
                        data->writeInt64(prop->u.rate.duration);
                        break;
                case MediaAnalyticsItem::kTypeCString:
                        if (prop->u.CStringValue) {
                            data->writeCString(prop->u.CStringValue);
                        } else {
                            ALOGE("found CStringValue null");
                        }
                        break;
                default:
                        ALOGE("found bad Prop type: %d, idx %d, name %s",
                              prop->mType, i, prop->mName);
                        break;
            }
    }

    return 0;
}


AString MediaAnalyticsItem::toString() {
   return toString(-1);
}

AString MediaAnalyticsItem::toString(int version) {

    // v0 : released with 'o'
    // v1 : bug fix (missing pid/finalized separator),
    //      adds apk name, apk version code

    if (version <= PROTO_FIRST) {
        // default to original v0 format, until proper parsers are in place
        version = PROTO_V0;
    } else if (version > PROTO_LAST) {
        version = PROTO_LAST;
    }

    AString result;
    char buffer[512];

    if (version == PROTO_V0) {
        result = "(";
    } else {
        snprintf(buffer, sizeof(buffer), "[%d:", version);
        result.append(buffer);
    }

    // same order as we spill into the parcel, although not required
    // key+session are our primary matching criteria
    result.append(mKey.c_str());
    result.append(":");
    snprintf(buffer, sizeof(buffer), "%" PRId64 ":", mSessionID);
    result.append(buffer);

    snprintf(buffer, sizeof(buffer), "%d:", mUid);
    result.append(buffer);

    if (version >= PROTO_V1) {
        result.append(mPkgName);
        snprintf(buffer, sizeof(buffer), ":%d:", mPkgVersionCode);
        result.append(buffer);
    }

    // in 'o' (v1) , the separator between pid and finalized was omitted
    if (version <= PROTO_V0) {
        snprintf(buffer, sizeof(buffer), "%d", mPid);
    } else {
        snprintf(buffer, sizeof(buffer), "%d:", mPid);
    }
    result.append(buffer);

    snprintf(buffer, sizeof(buffer), "%d:", mFinalized);
    result.append(buffer);
    snprintf(buffer, sizeof(buffer), "%" PRId64 ":", mTimestamp);
    result.append(buffer);

    // set of items
    int count = mPropCount;
    snprintf(buffer, sizeof(buffer), "%d:", count);
    result.append(buffer);
    for (int i = 0 ; i < count; i++ ) {
            Prop *prop = &mProps[i];
            switch (prop->mType) {
                case MediaAnalyticsItem::kTypeInt32:
                        snprintf(buffer,sizeof(buffer),
                        "%s=%d:", prop->mName, prop->u.int32Value);
                        break;
                case MediaAnalyticsItem::kTypeInt64:
                        snprintf(buffer,sizeof(buffer),
                        "%s=%" PRId64 ":", prop->mName, prop->u.int64Value);
                        break;
                case MediaAnalyticsItem::kTypeDouble:
                        snprintf(buffer,sizeof(buffer),
                        "%s=%e:", prop->mName, prop->u.doubleValue);
                        break;
                case MediaAnalyticsItem::kTypeRate:
                        snprintf(buffer,sizeof(buffer),
                        "%s=%" PRId64 "/%" PRId64 ":", prop->mName,
                        prop->u.rate.count, prop->u.rate.duration);
                        break;
                case MediaAnalyticsItem::kTypeCString:
                        snprintf(buffer,sizeof(buffer), "%s=", prop->mName);
                        result.append(buffer);
                        // XXX: sanitize string for ':' '='
                        result.append(prop->u.CStringValue);
                        buffer[0] = ':';
                        buffer[1] = '\0';
                        break;
                default:
                        ALOGE("to_String bad item type: %d for %s",
                              prop->mType, prop->mName);
                        break;
            }
            result.append(buffer);
    }

    if (version == PROTO_V0) {
        result.append(")");
    } else {
        result.append("]");
    }

    return result;
}

// for the lazy, we offer methods that finds the service and
// calls the appropriate daemon
bool MediaAnalyticsItem::selfrecord() {
    return selfrecord(false);
}

bool MediaAnalyticsItem::selfrecord(bool forcenew) {

    if (DEBUG_API) {
        AString p = this->toString();
        ALOGD("selfrecord of: %s [forcenew=%d]", p.c_str(), forcenew);
    }

    sp<IMediaAnalyticsService> svc = getInstance();

    if (svc != NULL) {
        MediaAnalyticsItem::SessionID_t newid = svc->submit(this, forcenew);
        if (newid == SessionIDInvalid) {
            AString p = this->toString();
            ALOGW("Failed to record: %s [forcenew=%d]", p.c_str(), forcenew);
            return false;
        }
        return true;
    } else {
        AString p = this->toString();
        ALOGW("Unable to record: %s [forcenew=%d]", p.c_str(), forcenew);
        return false;
    }
}

// get a connection we can reuse for most of our lifetime
// static
sp<IMediaAnalyticsService> MediaAnalyticsItem::sAnalyticsService;
static Mutex sInitMutex;
static int remainingBindAttempts = SVC_TRIES;

//static
bool MediaAnalyticsItem::isEnabled() {
    int enabled = property_get_int32(MediaAnalyticsItem::EnabledProperty, -1);

    if (enabled == -1) {
        enabled = property_get_int32(MediaAnalyticsItem::EnabledPropertyPersist, -1);
    }
    if (enabled == -1) {
        enabled = MediaAnalyticsItem::EnabledProperty_default;
    }
#ifdef MTK_DISABLE_METRICS
    enabled = -250;
#endif
    if (enabled <= 0) {
        return false;
    }
    return true;
}


// monitor health of our connection to the metrics service
class MediaMetricsDeathNotifier : public IBinder::DeathRecipient {
        virtual void binderDied(const wp<IBinder> &) {
            ALOGW("Reacquire service connection on next request");
            MediaAnalyticsItem::dropInstance();
        }
};

static sp<MediaMetricsDeathNotifier> sNotifier = NULL;

// static
void MediaAnalyticsItem::dropInstance() {
    Mutex::Autolock _l(sInitMutex);
    remainingBindAttempts = SVC_TRIES;
    sAnalyticsService = NULL;
}

//static
sp<IMediaAnalyticsService> MediaAnalyticsItem::getInstance() {

    static const char *servicename = "media.metrics";
    int enabled = isEnabled();

    if (enabled == false) {
        if (DEBUG_SERVICEACCESS) {
                ALOGD("disabled");
        }
        return NULL;
    }

    // completely skip logging from certain UIDs. We do this here
    // to avoid the multi-second timeouts while we learn that
    // sepolicy will not let us find the service.
    // We do this only for a select set of UIDs
    // The sepolicy protection is still in place, we just want a faster
    // response from this specific, small set of uids.
    {
        uid_t uid = getuid();
        switch (uid) {
            case AID_RADIO:     // telephony subsystem, RIL
                return NULL;
                break;
            default:
                // let sepolicy deny access if appropriate
                break;
        }
    }

    {
        Mutex::Autolock _l(sInitMutex);
        const char *badness = "";

        // think of remainingBindAttempts as telling us whether service==NULL because
        // (1) we haven't tried to initialize it yet
        // (2) we've tried to initialize it, but failed.
        if (sAnalyticsService == NULL && remainingBindAttempts > 0) {
            sp<IServiceManager> sm = defaultServiceManager();
            if (sm != NULL) {
                sp<IBinder> binder = sm->getService(String16(servicename));
                if (binder != NULL) {
                    sAnalyticsService = interface_cast<IMediaAnalyticsService>(binder);
                    if (sNotifier != NULL) {
                        sNotifier = NULL;
                    }
                    sNotifier = new MediaMetricsDeathNotifier();
                    binder->linkToDeath(sNotifier);
                } else {
                    badness = "did not find service";
                }
            } else {
                badness = "No Service Manager access";
            }

            if (sAnalyticsService == NULL) {
                if (remainingBindAttempts > 0) {
                    remainingBindAttempts--;
                }
                if (DEBUG_SERVICEACCESS) {
                    ALOGD("Unable to bind to service %s: %s", servicename, badness);
                }
            }
        }

        return sAnalyticsService;
    }
}

// merge the info from 'incoming' into this record.
// we finish with a union of this+incoming and special handling for collisions
bool MediaAnalyticsItem::merge(MediaAnalyticsItem *incoming) {

    // if I don't have key or session id, take them from incoming
    // 'this' should never be missing both of them...
    if (mKey.empty()) {
        mKey = incoming->mKey;
    } else if (mSessionID == 0) {
        mSessionID = incoming->mSessionID;
    }

    // we always take the more recent 'finalized' value
    setFinalized(incoming->getFinalized());

    // for each attribute from 'incoming', resolve appropriately
    int nattr = incoming->mPropCount;
    for (int i = 0 ; i < nattr; i++ ) {
        Prop *iprop = &incoming->mProps[i];
        Prop *oprop = findProp(iprop->mName);
        const char *p = iprop->mName;
        size_t len = strlen(p);
        char semantic = p[len-1];

        if (oprop == NULL) {
            // no oprop, so we insert the new one
            oprop = allocateProp(p);
            copyProp(oprop, iprop);
        } else {
            // merge iprop into oprop
            switch (semantic) {
                case '<':       // first  aka keep old)
                    /* nop */
                    break;

                default:        // default is 'last'
                case '>':       // last (aka keep new)
                    copyProp(oprop, iprop);
                    break;

                case '+':       /* sum */
                    // XXX validate numeric types, sum in place
                    break;

            }
        }
    }

    // not sure when we'd return false...
    return true;
}

} // namespace android

