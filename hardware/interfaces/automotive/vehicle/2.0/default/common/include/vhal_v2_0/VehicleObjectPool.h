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


#ifndef android_hardware_automotive_vehicle_V2_0_VehicleObjectPool_H_
#define android_hardware_automotive_vehicle_V2_0_VehicleObjectPool_H_

#include <deque>
#include <map>
#include <mutex>

#include <android/hardware/automotive/vehicle/2.0/types.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

// Handy metric mostly for unit tests and debug.
#define INC_METRIC_IF_DEBUG(val) PoolStats::instance()->val++;
struct PoolStats {
    std::atomic<uint32_t> Obtained {0};
    std::atomic<uint32_t> Created {0};
    std::atomic<uint32_t> Recycled {0};

    static PoolStats* instance() {
        static PoolStats inst;
        return &inst;
    }
};

template<typename T>
struct Deleter  {
    using OnDeleteFunc = std::function<void(T*)>;

    Deleter(const OnDeleteFunc& f) : mOnDelete(f) {};

    Deleter() = default;
    Deleter(const Deleter&) = default;

    void operator()(T* o) {
        mOnDelete(o);
    }
private:
    OnDeleteFunc mOnDelete;
};

/**
 * This is std::unique_ptr<> with custom delete operation that typically moves
 * the pointer it holds back to ObjectPool.
 */
template <typename T>
using recyclable_ptr = typename std::unique_ptr<T, Deleter<T>>;

/**
 * Generic abstract object pool class. Users of this class must implement
 * #createObject method.
 *
 * This class is thread-safe. Concurrent calls to #obtain(...) method from
 * multiple threads is OK, also client can obtain an object in one thread and
 * then move ownership to another thread.
 *
 */
template<typename T>
class ObjectPool {
public:
    ObjectPool() = default;
    virtual ~ObjectPool() = default;

    virtual recyclable_ptr<T> obtain() {
        std::lock_guard<std::mutex> g(mLock);
        INC_METRIC_IF_DEBUG(Obtained)
        if (mObjects.empty()) {
            INC_METRIC_IF_DEBUG(Created)
            return wrap(createObject());
        }

        auto o = wrap(mObjects.front().release());
        mObjects.pop_front();

        return o;
    }

    ObjectPool& operator =(const ObjectPool &) = delete;
    ObjectPool(const ObjectPool &) = delete;

protected:
    virtual T* createObject() = 0;

    virtual void recycle(T* o) {
        INC_METRIC_IF_DEBUG(Recycled)
        std::lock_guard<std::mutex> g(mLock);
        mObjects.push_back(std::unique_ptr<T> { o } );
    }

private:
    const Deleter<T>& getDeleter() {
        if (!mDeleter.get()) {
            Deleter<T> *d = new Deleter<T>(std::bind(
                &ObjectPool::recycle, this, std::placeholders::_1));
            mDeleter.reset(d);
        }
        return *mDeleter.get();
    }

    recyclable_ptr<T> wrap(T* raw) {
        return recyclable_ptr<T> { raw, getDeleter() };
    }

private:
    mutable std::mutex mLock;
    std::deque<std::unique_ptr<T>> mObjects;
    std::unique_ptr<Deleter<T>> mDeleter;
};

/**
 * This class provides a pool of recycable VehiclePropertyValue objects.
 *
 * It has only one overloaded public method - obtain(...), users must call this
 * method when new object is needed with given VehiclePropertyType and vector
 * size (for vector properties). This method returns a recycable smart pointer
 * to VehiclePropertyValue, essentially this is a std::unique_ptr with custom
 * delete function, so recycable object has only one owner and developers can
 * safely pass it around. Once this object goes out of scope, it will be
 * returned the the object pool.
 *
 * Some objects are not recycable: strings and vector data types with
 * vector length > maxRecyclableVectorSize (provided in the constructor). These
 * objects will be deleted immediately once the go out of scope. There's no
 * synchornization penalty for these objects since we do not store them in the
 * pool.
 *
 * This class is thread-safe. Users can obtain an object in one thread and pass
 * it to another.
 *
 * Sample usage:
 *
 *   VehiclePropValuePool pool;
 *   auto v = pool.obtain(VehiclePropertyType::INT32);
 *   v->propId = VehicleProperty::HVAC_FAN_SPEED;
 *   v->areaId = VehicleAreaZone::ROW_1_LEFT;
 *   v->timestamp = elapsedRealtimeNano();
 *   v->value->int32Values[0] = 42;
 *
 *
 */
class VehiclePropValuePool {
public:
    using RecyclableType = recyclable_ptr<VehiclePropValue>;

    /**
     * Creates VehiclePropValuePool
     *
     * @param maxRecyclableVectorSize - vector value types (e.g.
     * VehiclePropertyType::INT32_VEC) with size equal or less to this value
     * will be stored in the pool. If users tries to obtain value with vector
     * size greater than maxRecyclableVectorSize user will receive appropriate
     * object, but once it goes out of scope it will be deleted immediately, not
     * returning back to the object pool.
     *
     */
    VehiclePropValuePool(size_t maxRecyclableVectorSize = 4) :
        mMaxRecyclableVectorSize(maxRecyclableVectorSize) {};

    RecyclableType obtain(VehiclePropertyType type);

    RecyclableType obtain(VehiclePropertyType type, size_t vecSize);
    RecyclableType obtain(const VehiclePropValue& src);
    RecyclableType obtainBoolean(bool value);
    RecyclableType obtainInt32(int32_t value);
    RecyclableType obtainInt64(int64_t value);
    RecyclableType obtainFloat(float value);
    RecyclableType obtainString(const char* cstr);
    RecyclableType obtainComplex();

    VehiclePropValuePool(VehiclePropValuePool& ) = delete;
    VehiclePropValuePool& operator=(VehiclePropValuePool&) = delete;
private:
    bool isDisposable(VehiclePropertyType type, size_t vecSize) const {
        return vecSize > mMaxRecyclableVectorSize ||
               VehiclePropertyType::STRING == type ||
               VehiclePropertyType::COMPLEX == type;
    }

    RecyclableType obtainDisposable(VehiclePropertyType valueType,
                                    size_t vectorSize) const;
    RecyclableType obtainRecylable(VehiclePropertyType type,
                                   size_t vecSize);

    class InternalPool: public ObjectPool<VehiclePropValue> {
    public:
        InternalPool(VehiclePropertyType type, size_t vectorSize)
            : mPropType(type), mVectorSize(vectorSize) {}

        RecyclableType obtain() {
            return ObjectPool<VehiclePropValue>::obtain();
        }
    protected:
        VehiclePropValue* createObject() override;
        void recycle(VehiclePropValue* o) override;
    private:
        bool check(VehiclePropValue::RawValue* v);

        template <typename VecType>
        bool check(hidl_vec<VecType>* vec, bool expected) {
            return vec->size() == (expected ? mVectorSize : 0);
        }
    private:
        VehiclePropertyType mPropType;
        size_t mVectorSize;
    };

private:
    const Deleter<VehiclePropValue> mDisposableDeleter {
        [] (VehiclePropValue* v) {
            delete v;
        }
    };

private:
    mutable std::mutex mLock;
    const size_t mMaxRecyclableVectorSize;
    std::map<int32_t, std::unique_ptr<InternalPool>> mValueTypePools;
};

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif // android_hardware_automotive_vehicle_V2_0_VehicleObjectPool_H_
