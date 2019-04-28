/*
 * Copyright (c) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "IOMXStore"

#include <utils/Log.h>

#include <media/IOMX.h>
#include <media/IOMXStore.h>
#include <android/hardware/media/omx/1.0/IOmxStore.h>

#include <binder/IInterface.h>
#include <binder/IBinder.h>
#include <binder/Parcel.h>

#include <vector>
#include <string>

namespace android {

namespace {

enum {
    CONNECT = IBinder::FIRST_CALL_TRANSACTION,
    LIST_SERVICE_ATTRIBUTES,
    GET_NODE_PREFIX,
    LIST_ROLES,
    GET_OMX,
};

// Forward declarations of std::vector<T> <-> Parcel conversion funcitons that
// depend on writeToParcel() and readToParcel() for T <-> Parcel.

template <typename T>
status_t writeToParcel(const std::vector<T>& v, Parcel* p);

template <typename T>
status_t readFromParcel(std::vector<T>* v, const Parcel& p);

// std::string <-> Parcel

status_t writeToParcel(const std::string& s, Parcel* p) {
    if (s.size() > INT32_MAX) {
        return BAD_VALUE;
    }
    return p->writeByteArray(
            s.size(), reinterpret_cast<const uint8_t*>(s.c_str()));
}

status_t readFromParcel(std::string* s, const Parcel& p) {
    int32_t len;
    status_t status = p.readInt32(&len);
    if (status != NO_ERROR) {
        return status;
    } else if ((len < 0) || (static_cast<uint64_t>(len) > SIZE_MAX)) {
        return BAD_VALUE;
    }
    s->resize(len);
    if (len == 0) {
        return NO_ERROR;
    }
    return p.read(static_cast<void*>(&s->front()), static_cast<size_t>(len));
}

// IOMXStore::Attribute <-> Parcel

status_t writeToParcel(const IOMXStore::Attribute& a, Parcel* p) {
    status_t status = writeToParcel(a.key, p);
    if (status != NO_ERROR) {
        return status;
    }
    return writeToParcel(a.value, p);
}

status_t readFromParcel(IOMXStore::Attribute* a, const Parcel& p) {
    status_t status = readFromParcel(&(a->key), p);
    if (status != NO_ERROR) {
        return status;
    }
    return readFromParcel(&(a->value), p);
}

// IOMXStore::NodeInfo <-> Parcel

status_t writeToParcel(const IOMXStore::NodeInfo& n, Parcel* p) {
    status_t status = writeToParcel(n.name, p);
    if (status != NO_ERROR) {
        return status;
    }
    status = writeToParcel(n.owner, p);
    if (status != NO_ERROR) {
        return status;
    }
    return writeToParcel(n.attributes, p);
}

status_t readFromParcel(IOMXStore::NodeInfo* n, const Parcel& p) {
    status_t status = readFromParcel(&(n->name), p);
    if (status != NO_ERROR) {
        return status;
    }
    status = readFromParcel(&(n->owner), p);
    if (status != NO_ERROR) {
        return status;
    }
    return readFromParcel(&(n->attributes), p);
}

// IOMXStore::RoleInfo <-> Parcel

status_t writeToParcel(const IOMXStore::RoleInfo& r, Parcel* p) {
    status_t status = writeToParcel(r.role, p);
    if (status != NO_ERROR) {
        return status;
    }
    status = writeToParcel(r.type, p);
    if (status != NO_ERROR) {
        return status;
    }
    status = p->writeBool(r.isEncoder);
    if (status != NO_ERROR) {
        return status;
    }
    status = p->writeBool(r.preferPlatformNodes);
    if (status != NO_ERROR) {
        return status;
    }
    return writeToParcel(r.nodes, p);
}

status_t readFromParcel(IOMXStore::RoleInfo* r, const Parcel& p) {
    status_t status = readFromParcel(&(r->role), p);
    if (status != NO_ERROR) {
        return status;
    }
    status = readFromParcel(&(r->type), p);
    if (status != NO_ERROR) {
        return status;
    }
    status = p.readBool(&(r->isEncoder));
    if (status != NO_ERROR) {
        return status;
    }
    status = p.readBool(&(r->preferPlatformNodes));
    if (status != NO_ERROR) {
        return status;
    }
    return readFromParcel(&(r->nodes), p);
}

// std::vector<NodeInfo> <-> Parcel
// std::vector<RoleInfo> <-> Parcel

template <typename T>
status_t writeToParcel(const std::vector<T>& v, Parcel* p) {
    status_t status = p->writeVectorSize(v);
    if (status != NO_ERROR) {
        return status;
    }
    for (const T& x : v) {
        status = writeToParcel(x, p);
        if (status != NO_ERROR) {
            return status;
        }
    }
    return NO_ERROR;
}

template <typename T>
status_t readFromParcel(std::vector<T>* v, const Parcel& p) {
    status_t status = p.resizeOutVector(v);
    if (status != NO_ERROR) {
        return status;
    }
    for (T& x : *v) {
        status = readFromParcel(&x, p);
        if (status != NO_ERROR) {
            return status;
        }
    }
    return NO_ERROR;
}

} // unnamed namespace

////////////////////////////////////////////////////////////////////////////////

class BpOMXStore : public BpInterface<IOMXStore> {
public:
    explicit BpOMXStore(const sp<IBinder> &impl)
        : BpInterface<IOMXStore>(impl) {
    }

    status_t listServiceAttributes(
            std::vector<Attribute>* attributes) override {
        Parcel data, reply;
        status_t status;
        status = data.writeInterfaceToken(IOMXStore::getInterfaceDescriptor());
        if (status != NO_ERROR) {
            return status;
        }
        status = remote()->transact(LIST_SERVICE_ATTRIBUTES, data, &reply);
        if (status != NO_ERROR) {
            return status;
        }
        return readFromParcel(attributes, reply);
    }

    status_t getNodePrefix(std::string* prefix) override {
        Parcel data, reply;
        status_t status;
        status = data.writeInterfaceToken(IOMXStore::getInterfaceDescriptor());
        if (status != NO_ERROR) {
            return status;
        }
        status = remote()->transact(GET_NODE_PREFIX, data, &reply);
        if (status != NO_ERROR) {
            return status;
        }
        return readFromParcel(prefix, reply);
    }

    status_t listRoles(std::vector<RoleInfo>* roleList) override {
        Parcel data, reply;
        status_t status;
        status = data.writeInterfaceToken(IOMXStore::getInterfaceDescriptor());
        if (status != NO_ERROR) {
            return status;
        }
        status = remote()->transact(LIST_ROLES, data, &reply);
        if (status != NO_ERROR) {
            return status;
        }
        return readFromParcel(roleList, reply);
    }

    status_t getOmx(const std::string& name, sp<IOMX>* omx) override {
        Parcel data, reply;
        status_t status;
        status = data.writeInterfaceToken(IOMXStore::getInterfaceDescriptor());
        if (status != NO_ERROR) {
            return status;
        }
        status = writeToParcel(name, &data);
        if (status != NO_ERROR) {
            return status;
        }
        status = remote()->transact(GET_OMX, data, &reply);
        if (status != NO_ERROR) {
            return status;
        }
        return reply.readStrongBinder(omx);
    }

};

IMPLEMENT_META_INTERFACE(OMXStore, "android.hardware.IOMXStore");

////////////////////////////////////////////////////////////////////////////////

#define CHECK_OMX_INTERFACE(interface, data, reply) \
        do { if (!(data).enforceInterface(interface::getInterfaceDescriptor())) { \
            ALOGW("Call incorrectly routed to " #interface); \
            return PERMISSION_DENIED; \
        } } while (0)

status_t BnOMXStore::onTransact(
    uint32_t code, const Parcel &data, Parcel *reply, uint32_t flags) {
    switch (code) {
        case LIST_SERVICE_ATTRIBUTES: {
            CHECK_OMX_INTERFACE(IOMXStore, data, reply);
            status_t status;
            std::vector<Attribute> attributes;

            status = listServiceAttributes(&attributes);
            if (status != NO_ERROR) {
                ALOGE("listServiceAttributes() fails with status %d",
                        static_cast<int>(status));
                return NO_ERROR;
            }
            status = writeToParcel(attributes, reply);
            if (status != NO_ERROR) {
                ALOGE("listServiceAttributes() fails to send reply");
                return NO_ERROR;
            }
            return NO_ERROR;
        }
        case GET_NODE_PREFIX: {
            CHECK_OMX_INTERFACE(IOMXStore, data, reply);
            status_t status;
            std::string prefix;

            status = getNodePrefix(&prefix);
            if (status != NO_ERROR) {
                ALOGE("getNodePrefix() fails with status %d",
                        static_cast<int>(status));
                return NO_ERROR;
            }
            status = writeToParcel(prefix, reply);
            if (status != NO_ERROR) {
                ALOGE("getNodePrefix() fails to send reply");
                return NO_ERROR;
            }
            return NO_ERROR;
        }
        case LIST_ROLES: {
            CHECK_OMX_INTERFACE(IOMXStore, data, reply);
            status_t status;
            std::vector<RoleInfo> roleList;

            status = listRoles(&roleList);
            if (status != NO_ERROR) {
                ALOGE("listRoles() fails with status %d",
                        static_cast<int>(status));
                return NO_ERROR;
            }
            status = writeToParcel(roleList, reply);
            if (status != NO_ERROR) {
                ALOGE("listRoles() fails to send reply");
                return NO_ERROR;
            }
            return NO_ERROR;
        }
        case GET_OMX: {
            CHECK_OMX_INTERFACE(IOMXStore, data, reply);
            status_t status;
            std::string name;
            sp<IOMX> omx;

            status = readFromParcel(&name, data);
            if (status != NO_ERROR) {
                ALOGE("getOmx() fails to retrieve name");
                return NO_ERROR;
            }
            status = getOmx(name, &omx);
            if (status != NO_ERROR) {
                ALOGE("getOmx() fails with status %d",
                        static_cast<int>(status));
                return NO_ERROR;
            }
            status = reply->writeStrongBinder(IInterface::asBinder(omx));
            if (status != NO_ERROR) {
                ALOGE("getOmx() fails to send reply");
                return NO_ERROR;
            }
            return NO_ERROR;
        }
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

}  // namespace android
