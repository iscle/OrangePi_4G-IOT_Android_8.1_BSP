#define LOG_TAG "android.hardware.renderscript@1.0-impl"

#include "Context.h"
#include "Device.h"

namespace android {
namespace hardware {
namespace renderscript {
namespace V1_0 {
namespace implementation {


Context::Context(uint32_t sdkVersion, ContextType ct, int32_t flags) {
    RsDevice _dev = nullptr;
    uint32_t _version = 0;
    uint32_t _sdkVersion = sdkVersion;
    RsContextType _ct = static_cast<RsContextType>(ct);
    int32_t _flags = flags;
    const char* driverName = nullptr;

#ifdef OVERRIDE_RS_DRIVER
#define XSTR(S) #S
#define STR(S) XSTR(S)
#define OVERRIDE_RS_DRIVER_STRING STR(OVERRIDE_RS_DRIVER)
    static std::string driverString(OVERRIDE_RS_DRIVER_STRING);
    driverName = driverString.c_str();
#undef XSTR
#undef STR
#endif  // OVERRIDE_RS_DRIVER
    mContext = Device::getHal().ContextCreateVendor(_dev, _version, _sdkVersion,
                                                    _ct, _flags, driverName);
}


// Helper functions
template<typename ReturnType>
static ReturnType hidl_to_rs(OpaqueHandle src) {
    return reinterpret_cast<ReturnType>(static_cast<uintptr_t>(src));
}

template<typename ReturnType, typename SourceType>
static ReturnType hidl_to_rs(SourceType* src) {
    return reinterpret_cast<ReturnType>(src);
}

template<typename RsType, typename HidlType, typename Operation>
static std::vector<RsType> hidl_to_rs(const hidl_vec<HidlType>& src, Operation operation) {
    std::vector<RsType> dst(src.size());
    std::transform(src.begin(), src.end(), dst.begin(), operation);
    return dst;
}

template<typename ReturnType, typename SourceType>
static ReturnType rs_to_hidl(SourceType* src) {
    return static_cast<ReturnType>(reinterpret_cast<uintptr_t>(src));
}

template<typename HidlType, typename RsType, typename Operation>
static hidl_vec<HidlType> rs_to_hidl(const std::vector<RsType>& src, Operation operation) {
    std::vector<HidlType> dst(src.size());
    std::transform(src.begin(), src.end(), dst.begin(), operation);
    return dst;
}


// Methods from ::android::hardware::renderscript::V1_0::IContext follow.

Return<Allocation> Context::allocationAdapterCreate(Type type, Allocation baseAlloc) {
    RsType _type = hidl_to_rs<RsType>(type);
    RsAllocation _baseAlloc = hidl_to_rs<RsAllocation>(baseAlloc);
    RsAllocation _subAlloc = Device::getHal().AllocationAdapterCreate(mContext, _type, _baseAlloc);
    return rs_to_hidl<Allocation>(_subAlloc);
}

Return<void> Context::allocationAdapterOffset(Allocation alloc, const hidl_vec<uint32_t>& offsets) {
    RsAllocation _alloc = hidl_to_rs<RsAllocation>(alloc);
    const hidl_vec<uint32_t>& _offsets = offsets;
    Device::getHal().AllocationAdapterOffset(mContext, _alloc, _offsets.data(), _offsets.size() * sizeof(uint32_t));
    return Void();
}

Return<Type> Context::allocationGetType(Allocation allocation) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    const void* _type = Device::getHal().AllocationGetType(mContext, _allocation);
    return rs_to_hidl<Type>(_type);
}

Return<Allocation> Context::allocationCreateTyped(Type type, AllocationMipmapControl amips, int32_t usage, Ptr ptr) {
    RsType _type = hidl_to_rs<RsType>(type);
    RsAllocationMipmapControl _amips = static_cast<RsAllocationMipmapControl>(amips);
    uint32_t _usage = usage;
    uintptr_t _ptr = hidl_to_rs<uintptr_t>(ptr);
    RsAllocation _allocation = Device::getHal().AllocationCreateTyped(mContext, _type, _amips, _usage, _ptr);
    return rs_to_hidl<Allocation>(_allocation);
}

Return<Allocation> Context::allocationCreateFromBitmap(Type type, AllocationMipmapControl amips, const hidl_vec<uint8_t>& bitmap, int32_t usage) {
    RsType _type = hidl_to_rs<RsType>(type);
    RsAllocationMipmapControl _amips = static_cast<RsAllocationMipmapControl>(amips);
    const hidl_vec<uint8_t>& _bitmap = bitmap;
    uint32_t _usage = usage;
    RsAllocation _allocation = Device::getHal().AllocationCreateFromBitmap(mContext, _type, _amips, _bitmap.data(), _bitmap.size(), _usage);
    return rs_to_hidl<Allocation>(_allocation);
}

Return<Allocation> Context::allocationCubeCreateFromBitmap(Type type, AllocationMipmapControl amips, const hidl_vec<uint8_t>& bitmap, int32_t usage) {
    RsType _type = hidl_to_rs<RsType>(type);
    RsAllocationMipmapControl _amips = static_cast<RsAllocationMipmapControl>(amips);
    const hidl_vec<uint8_t>& _bitmap = bitmap;
    uint32_t _usage = usage;
    RsAllocation _allocation = Device::getHal().AllocationCubeCreateFromBitmap(mContext, _type, _amips, _bitmap.data(), _bitmap.size(), _usage);
    return rs_to_hidl<Allocation>(_allocation);
}

Return<NativeWindow> Context::allocationGetNativeWindow(Allocation allocation) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    RsNativeWindow _nativeWindow = Device::getHal().AllocationGetSurface(mContext, _allocation);
    return rs_to_hidl<NativeWindow>(_nativeWindow);
}

Return<void> Context::allocationSetNativeWindow(Allocation allocation, NativeWindow nativewindow) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    RsNativeWindow _nativewindow = hidl_to_rs<RsNativeWindow>(nativewindow);
    Device::getHal().AllocationSetSurface(mContext, _allocation, _nativewindow);
    return Void();
}

Return<void> Context::allocationSetupBufferQueue(Allocation alloc, uint32_t numBuffer) {
    RsAllocation _alloc = hidl_to_rs<RsAllocation>(alloc);
    uint32_t _numBuffer = numBuffer;
    Device::getHal().AllocationSetupBufferQueue(mContext, _alloc, _numBuffer);
    return Void();
}

Return<void> Context::allocationShareBufferQueue(Allocation baseAlloc, Allocation subAlloc) {
    RsAllocation _baseAlloc = hidl_to_rs<RsAllocation>(baseAlloc);
    RsAllocation _subAlloc = hidl_to_rs<RsAllocation>(subAlloc);
    Device::getHal().AllocationShareBufferQueue(mContext, _baseAlloc, _subAlloc);
    return Void();
}

Return<void> Context::allocationCopyToBitmap(Allocation allocation, Ptr data, Size sizeBytes) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    void* _data = hidl_to_rs<void*>(data);
    size_t _sizeBytes = static_cast<size_t>(sizeBytes);
    Device::getHal().AllocationCopyToBitmap(mContext, _allocation, _data, _sizeBytes);
    return Void();
}

Return<void> Context::allocation1DWrite(Allocation allocation, uint32_t offset, uint32_t lod, uint32_t count, const hidl_vec<uint8_t>& data) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _offset = offset;
    uint32_t _lod = lod;
    uint32_t _count = count;
    const void* _dataPtr = hidl_to_rs<const void*>(data.data());
    size_t _sizeBytes = data.size();
    Device::getHal().Allocation1DData(mContext, _allocation, _offset, _lod, _count, _dataPtr, _sizeBytes);
    return Void();
}

Return<void> Context::allocationElementWrite(Allocation allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, const hidl_vec<uint8_t>& data, Size compIdx) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _x = x;
    uint32_t _y = y;
    uint32_t _z = z;
    uint32_t _lod = lod;
    const void* _dataPtr = hidl_to_rs<const void*>(data.data());
    size_t _sizeBytes = data.size();
    size_t _compIdx = static_cast<size_t>(compIdx);
    Device::getHal().AllocationElementData(mContext, _allocation, _x, _y, _z, _lod, _dataPtr, _sizeBytes, _compIdx);
    return Void();
}

Return<void> Context::allocation2DWrite(Allocation allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, AllocationCubemapFace face, uint32_t w, uint32_t h, const hidl_vec<uint8_t>& data, Size stride) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _xoff = xoff;
    uint32_t _yoff = yoff;
    uint32_t _lod = lod;
    RsAllocationCubemapFace _face = static_cast<RsAllocationCubemapFace>(face);
    uint32_t _w = w;
    uint32_t _h = h;
    const void* _dataPtr = hidl_to_rs<const void*>(data.data());
    size_t _sizeBytes = data.size();
    size_t _stride = static_cast<size_t>(stride);
    Device::getHal().Allocation2DData(mContext, _allocation, _xoff, _yoff, _lod, _face, _w, _h, _dataPtr, _sizeBytes, _stride);
    return Void();
}

Return<void> Context::allocation3DWrite(Allocation allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, const hidl_vec<uint8_t>& data, Size stride) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _xoff = xoff;
    uint32_t _yoff = yoff;
    uint32_t _zoff = zoff;
    uint32_t _lod = lod;
    uint32_t _w = w;
    uint32_t _h = h;
    uint32_t _d = d;
    const void* _dataPtr = hidl_to_rs<const void*>(data.data());
    size_t _sizeBytes = data.size();
    size_t _stride = static_cast<size_t>(stride);
    Device::getHal().Allocation3DData(mContext, _allocation, _xoff, _yoff, _zoff, _lod, _w, _h, _d, _dataPtr, _sizeBytes, _stride);
    return Void();
}

Return<void> Context::allocationGenerateMipmaps(Allocation allocation) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    Device::getHal().AllocationGenerateMipmaps(mContext, _allocation);
    return Void();
}

Return<void> Context::allocationRead(Allocation allocation, Ptr data, Size sizeBytes) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    void* _data = hidl_to_rs<void*>(data);
    size_t _sizeBytes = static_cast<size_t>(sizeBytes);
    Device::getHal().AllocationRead(mContext, _allocation, _data, _sizeBytes);
    return Void();
}

Return<void> Context::allocation1DRead(Allocation allocation, uint32_t xoff, uint32_t lod, uint32_t count, Ptr data, Size sizeBytes) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _xoff = xoff;
    uint32_t _lod = lod;
    uint32_t _count = count;
    void* _data = hidl_to_rs<void*>(data);
    size_t _sizeBytes = static_cast<size_t>(sizeBytes);
    Device::getHal().Allocation1DRead(mContext, _allocation, _xoff, _lod, _count, _data, _sizeBytes);
    return Void();
}

Return<void> Context::allocationElementRead(Allocation allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, Ptr data, Size sizeBytes, Size compIdx) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _x = x;
    uint32_t _y = y;
    uint32_t _z = z;
    uint32_t _lod = lod;
    void* _data = hidl_to_rs<void*>(data);
    size_t _sizeBytes = static_cast<size_t>(sizeBytes);
    size_t _compIdx = static_cast<size_t>(compIdx);
    Device::getHal().AllocationElementRead(mContext, _allocation, _x, _y, _z, _lod, _data, _sizeBytes, _compIdx);
    return Void();
}

Return<void> Context::allocation2DRead(Allocation allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, AllocationCubemapFace face, uint32_t w, uint32_t h, Ptr data, Size sizeBytes, Size stride) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _xoff = xoff;
    uint32_t _yoff = yoff;
    uint32_t _lod = lod;
    RsAllocationCubemapFace _face = static_cast<RsAllocationCubemapFace>(face);
    uint32_t _w = w;
    uint32_t _h = h;
    void* _data = hidl_to_rs<void*>(data);
    size_t _sizeBytes = static_cast<size_t>(sizeBytes);
    size_t _stride = static_cast<size_t>(stride);
    Device::getHal().Allocation2DRead(mContext, _allocation, _xoff, _yoff, _lod, _face, _w, _h, _data, _sizeBytes, _stride);
    return Void();
}

Return<void> Context::allocation3DRead(Allocation allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, Ptr data, Size sizeBytes, Size stride) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _xoff = xoff;
    uint32_t _yoff = yoff;
    uint32_t _zoff = zoff;
    uint32_t _lod = lod;
    uint32_t _w = w;
    uint32_t _h = h;
    uint32_t _d = d;
    void* _dataPtr = hidl_to_rs<void*>(data);
    size_t _sizeBytes = static_cast<size_t>(sizeBytes);
    size_t _stride = static_cast<size_t>(stride);
    Device::getHal().Allocation3DRead(mContext, _allocation, _xoff, _yoff, _zoff, _lod, _w, _h, _d, _dataPtr, _sizeBytes, _stride);
    return Void();
}

Return<void> Context::allocationSyncAll(Allocation allocation, AllocationUsageType usageType) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    RsAllocationUsageType _usageType = static_cast<RsAllocationUsageType>(usageType);
    Device::getHal().AllocationSyncAll(mContext, _allocation, _usageType);
    return Void();
}

Return<void> Context::allocationResize1D(Allocation allocation, uint32_t dimX) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _dimX = dimX;
    Device::getHal().AllocationResize1D(mContext, _allocation, _dimX);
    return Void();
}

Return<void> Context::allocationCopy2DRange(Allocation dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstMip, AllocationCubemapFace dstFace, uint32_t width, uint32_t height, Allocation srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcMip, AllocationCubemapFace srcFace) {
    RsAllocation _dstAlloc = hidl_to_rs<RsAllocation>(dstAlloc);
    uint32_t _dstXoff = dstXoff;
    uint32_t _dstYoff = dstYoff;
    uint32_t _dstMip = dstMip;
    RsAllocationCubemapFace _dstFace = static_cast<RsAllocationCubemapFace>(dstFace);
    uint32_t _width = width;
    uint32_t _height = height;
    RsAllocation _srcAlloc = hidl_to_rs<RsAllocation>(srcAlloc);
    uint32_t _srcXoff = srcXoff;
    uint32_t _srcYoff = srcYoff;
    uint32_t _srcMip = srcMip;
    RsAllocationCubemapFace _srcFace = static_cast<RsAllocationCubemapFace>(srcFace);
    Device::getHal().AllocationCopy2DRange(mContext, _dstAlloc, _dstXoff, _dstYoff, _dstMip, _dstFace, _width, _height, _srcAlloc, _srcXoff, _srcYoff, _srcMip, _srcFace);
    return Void();
}

Return<void> Context::allocationCopy3DRange(Allocation dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstZoff, uint32_t dstMip, uint32_t width, uint32_t height, uint32_t depth, Allocation srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcZoff, uint32_t srcMip) {
    RsAllocation _dstAlloc = hidl_to_rs<RsAllocation>(dstAlloc);
    uint32_t _dstXoff = dstXoff;
    uint32_t _dstYoff = dstYoff;
    uint32_t _dstZoff = dstZoff;
    uint32_t _dstMip = dstMip;
    uint32_t _width = width;
    uint32_t _height = height;
    uint32_t _depth = depth;
    RsAllocation _srcAlloc = hidl_to_rs<RsAllocation>(srcAlloc);
    uint32_t _srcXoff = srcXoff;
    uint32_t _srcYoff = srcYoff;
    uint32_t _srcZoff = srcZoff;
    uint32_t _srcMip = srcMip;
    Device::getHal().AllocationCopy3DRange(mContext, _dstAlloc, _dstXoff, _dstYoff, _dstZoff, _dstMip, _width, _height, _depth, _srcAlloc, _srcXoff, _srcYoff, _srcZoff, _srcMip);
    return Void();
}

Return<void> Context::allocationIoSend(Allocation allocation) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    Device::getHal().AllocationIoSend(mContext, _allocation);
    return Void();
}

Return<void> Context::allocationIoReceive(Allocation allocation) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    Device::getHal().AllocationIoReceive(mContext, _allocation);
    return Void();
}

Return<void> Context::allocationGetPointer(Allocation allocation, uint32_t lod, AllocationCubemapFace face, uint32_t z, allocationGetPointer_cb _hidl_cb) {
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _lod = lod;
    RsAllocationCubemapFace _face = static_cast<RsAllocationCubemapFace>(face);
    uint32_t _z = z;
    uint32_t _array = 0;
    size_t _stride = 0;
    void* _dataPtr = Device::getHal().AllocationGetPointer(mContext, _allocation, _lod, _face, _z, _array, &_stride, sizeof(size_t));
    Ptr dataPtr = reinterpret_cast<Ptr>(_dataPtr);
    Size stride = static_cast<Size>(_stride);
    _hidl_cb(dataPtr, stride);
    return Void();
}

Return<void> Context::elementGetNativeMetadata(Element element, elementGetNativeMetadata_cb _hidl_cb) {
    RsElement _element = hidl_to_rs<RsElement>(element);
    std::vector<uint32_t> _elemData(5);
    Device::getHal().ElementGetNativeData(mContext, _element, _elemData.data(), _elemData.size());
    hidl_vec<uint32_t> elemData = _elemData;
    _hidl_cb(elemData);
    return Void();
}

Return<void> Context::elementGetSubElements(Element element, Size numSubElem, elementGetSubElements_cb _hidl_cb) {
    RsElement _element = hidl_to_rs<RsElement>(element);
    uint32_t _numSubElem = static_cast<uint32_t>(numSubElem);
    std::vector<uintptr_t> _ids(_numSubElem);
    std::vector<const char*> _names(_numSubElem);
    std::vector<size_t> _arraySizes(_numSubElem);
    Device::getHal().ElementGetSubElements(mContext, _element, _ids.data(), _names.data(), _arraySizes.data(), _numSubElem);
    hidl_vec<Element>     ids        = rs_to_hidl<Element>(_ids,       [](uintptr_t val) { return static_cast<Element>(val); });
    hidl_vec<hidl_string> names      = rs_to_hidl<hidl_string>(_names, [](const char* val) { return val; });
    hidl_vec<Size>        arraySizes = rs_to_hidl<Size>(_arraySizes,   [](size_t val) { return static_cast<Size>(val); });
    _hidl_cb(ids, names, arraySizes);
    return Void();
}

Return<Element> Context::elementCreate(DataType dt, DataKind dk, bool norm, uint32_t size) {
    RsDataType _dt = static_cast<RsDataType>(dt);
    RsDataKind _dk = static_cast<RsDataKind>(dk);
    bool _norm = norm;
    uint32_t _size = size;
    RsElement _element = Device::getHal().ElementCreate(mContext, _dt, _dk, _norm, _size);
    return rs_to_hidl<Element>(_element);
}

Return<Element> Context::elementComplexCreate(const hidl_vec<Element>& eins, const hidl_vec<hidl_string>& names, const hidl_vec<Size>& arraySizes) {
    std::vector<RsElement>   _eins           = hidl_to_rs<RsElement>(eins,      [](Element val) { return hidl_to_rs<RsElement>(val); });
    std::vector<const char*> _namesPtr       = hidl_to_rs<const char*>(names,   [](const hidl_string& val) { return val.c_str(); });
    std::vector<size_t>      _nameLengthsPtr = hidl_to_rs<size_t>(names,        [](const hidl_string& val) { return val.size(); });
    std::vector<uint32_t>    _arraySizes     = hidl_to_rs<uint32_t>(arraySizes, [](Size val) { return static_cast<uint32_t>(val); });
    RsElement _element = Device::getHal().ElementCreate2(mContext, _eins.data(), _eins.size(), _namesPtr.data(), _namesPtr.size(), _nameLengthsPtr.data(), _arraySizes.data(), _arraySizes.size());
    return rs_to_hidl<Element>(_element);
}

Return<void> Context::typeGetNativeMetadata(Type type, typeGetNativeMetadata_cb _hidl_cb) {
    RsType _type = hidl_to_rs<RsType>(type);
    std::vector<uintptr_t> _metadata(6);
    Device::getHal().TypeGetNativeData(mContext, _type, _metadata.data(), _metadata.size());
    hidl_vec<OpaqueHandle> metadata = rs_to_hidl<OpaqueHandle>(_metadata, [](uintptr_t val) { return static_cast<OpaqueHandle>(val); });
    _hidl_cb(metadata);
    return Void();
}

Return<Type> Context::typeCreate(Element element, uint32_t dimX, uint32_t dimY, uint32_t dimZ, bool mipmaps, bool faces, YuvFormat yuv) {
    RsElement _element = hidl_to_rs<RsElement>(element);
    uint32_t _dimX = dimX;
    uint32_t _dimY = dimY;
    uint32_t _dimZ = dimZ;
    bool _mipmaps = mipmaps;
    bool _faces = faces;
    RsYuvFormat _yuv = static_cast<RsYuvFormat>(yuv);
    RsType _type = Device::getHal().TypeCreate(mContext, _element, _dimX, _dimY, _dimZ, _mipmaps, _faces, _yuv);
    return rs_to_hidl<Type>(_type);
}

Return<void> Context::contextDestroy() {
    Device::getHal().ContextDestroy(mContext);
    mContext = nullptr;
    return Void();
}

Return<void> Context::contextGetMessage(Ptr data, Size size, contextGetMessage_cb _hidl_cb) {
    void* _data = hidl_to_rs<void*>(data);
    size_t _size = static_cast<size_t>(size);
    size_t _receiveLen = 0;
    uint32_t _subID = 0;
    RsMessageToClientType _messageType = Device::getHal().ContextGetMessage(mContext, _data, _size, &_receiveLen, sizeof(size_t), &_subID, sizeof(uint32_t));
    MessageToClientType messageType = static_cast<MessageToClientType>(_messageType);
    Size receiveLen = static_cast<Size>(_receiveLen);
    _hidl_cb(messageType, receiveLen);
    return Void();
}

Return<void> Context::contextPeekMessage(contextPeekMessage_cb _hidl_cb) {
    size_t _receiveLen = 0;
    uint32_t _subID = 0;
    RsMessageToClientType _messageType = Device::getHal().ContextPeekMessage(mContext, &_receiveLen, sizeof(size_t), &_subID, sizeof(uint32_t));
    MessageToClientType messageType = static_cast<MessageToClientType>(_messageType);
    Size receiveLen = static_cast<Size>(_receiveLen);
    uint32_t subID = _subID;
    _hidl_cb(messageType, receiveLen, subID);
    return Void();
}

Return<void> Context::contextSendMessage(uint32_t id, const hidl_vec<uint8_t>& data) {
    uint32_t _id = id;
    const uint8_t* _dataPtr = data.data();
    size_t _dataSize = data.size();
    Device::getHal().ContextSendMessage(mContext, _id, _dataPtr, _dataSize);
    return Void();
}

Return<void> Context::contextInitToClient() {
    Device::getHal().ContextInitToClient(mContext);
    return Void();
}

Return<void> Context::contextDeinitToClient() {
    Device::getHal().ContextDeinitToClient(mContext);
    return Void();
}

Return<void> Context::contextFinish() {
    Device::getHal().ContextFinish(mContext);
    return Void();
}

Return<void> Context::contextLog() {
    uint32_t _bits = 0;
    Device::getHal().ContextDump(mContext, _bits);
    return Void();
}

Return<void> Context::contextSetPriority(ThreadPriorities priority) {
    RsThreadPriorities _priority = static_cast<RsThreadPriorities>(priority);
    Device::getHal().ContextSetPriority(mContext, _priority);
    return Void();
}

Return<void> Context::contextSetCacheDir(const hidl_string& cacheDir) {
    Device::getHal().ContextSetCacheDir(mContext, cacheDir.c_str(), cacheDir.size());
    return Void();
}

Return<void> Context::assignName(ObjectBase obj, const hidl_string& name) {
    RsObjectBase _obj = hidl_to_rs<RsObjectBase>(obj);
    const hidl_string& _name = name;
    Device::getHal().AssignName(mContext, _obj, _name.c_str(), _name.size());
    return Void();
}

Return<void> Context::getName(ObjectBase obj, getName_cb _hidl_cb) {
    void* _obj = hidl_to_rs<void*>(obj);
    const char* _name = nullptr;
    Device::getHal().GetName(mContext, _obj, &_name);
    hidl_string name = _name;
    _hidl_cb(name);
    return Void();
}

Return<Closure> Context::closureCreate(ScriptKernelID kernelID, Allocation returnValue, const hidl_vec<ScriptFieldID>& fieldIDS, const hidl_vec<int64_t>& values, const hidl_vec<int32_t>& sizes, const hidl_vec<Closure>& depClosures, const hidl_vec<ScriptFieldID>& depFieldIDS) {
    RsScriptKernelID _kernelID = hidl_to_rs<RsScriptKernelID>(kernelID);
    RsAllocation _returnValue = hidl_to_rs<RsAllocation>(returnValue);
    std::vector<RsScriptFieldID> _fieldIDS = hidl_to_rs<RsScriptFieldID>(fieldIDS, [](ScriptFieldID val) { return hidl_to_rs<RsScriptFieldID>(val); });
    int64_t* _valuesPtr = const_cast<int64_t*>(values.data());
    size_t _valuesLength = values.size();
    std::vector<int>             _sizes       = hidl_to_rs<int>(sizes,                   [](int32_t val) { return static_cast<int>(val); });
    std::vector<RsClosure>       _depClosures = hidl_to_rs<RsClosure>(depClosures,       [](Closure val) { return hidl_to_rs<RsClosure>(val); });
    std::vector<RsScriptFieldID> _depFieldIDS = hidl_to_rs<RsScriptFieldID>(depFieldIDS, [](ScriptFieldID val) { return hidl_to_rs<RsScriptFieldID>(val); });
    RsClosure _closure = Device::getHal().ClosureCreate(mContext, _kernelID, _returnValue, _fieldIDS.data(), _fieldIDS.size(), _valuesPtr, _valuesLength, _sizes.data(), _sizes.size(), _depClosures.data(), _depClosures.size(), _depFieldIDS.data(), _depFieldIDS.size());
    return rs_to_hidl<Closure>(_closure);
}

Return<Closure> Context::invokeClosureCreate(ScriptInvokeID invokeID, const hidl_vec<uint8_t>& params, const hidl_vec<ScriptFieldID>& fieldIDS, const hidl_vec<int64_t>& values, const hidl_vec<int32_t>& sizes) {
    RsScriptInvokeID _invokeID = hidl_to_rs<RsScriptInvokeID>(invokeID);
    const void* _paramsPtr = params.data();
    size_t _paramsSize = params.size();
    std::vector<RsScriptFieldID> _fieldIDS = hidl_to_rs<RsScriptFieldID>(fieldIDS, [](ScriptFieldID val) { return hidl_to_rs<RsScriptFieldID>(val); });
    const int64_t* _valuesPtr = values.data();
    size_t _valuesLength = values.size();
    std::vector<int> _sizes = hidl_to_rs<int>(sizes, [](int32_t val) { return static_cast<int>(val); });
    RsClosure _closure = Device::getHal().InvokeClosureCreate(mContext, _invokeID, _paramsPtr, _paramsSize, _fieldIDS.data(), _fieldIDS.size(), _valuesPtr, _valuesLength, _sizes.data(), _sizes.size());
    return rs_to_hidl<Closure>(_closure);
}

Return<void> Context::closureSetArg(Closure closure, uint32_t index, Ptr value, int32_t size) {
    RsClosure _closure = hidl_to_rs<RsClosure>(closure);
    uint32_t _index = index;
    uintptr_t _value = hidl_to_rs<uintptr_t>(value);
    int _size = static_cast<int>(size);
    Device::getHal().ClosureSetArg(mContext, _closure, _index, _value, _size);
    return Void();
}

Return<void> Context::closureSetGlobal(Closure closure, ScriptFieldID fieldID, int64_t value, int32_t size) {
    RsClosure _closure = hidl_to_rs<RsClosure>(closure);
    RsScriptFieldID _fieldID = hidl_to_rs<RsScriptFieldID>(fieldID);
    int64_t _value = value;
    int _size = static_cast<int>(size);
    Device::getHal().ClosureSetGlobal(mContext, _closure, _fieldID, _value, _size);
    return Void();
}

Return<ScriptKernelID> Context::scriptKernelIDCreate(Script script, int32_t slot, int32_t sig) {
    RsScript _script = hidl_to_rs<RsScript>(script);
    int _slot = static_cast<int>(slot);
    int _sig = static_cast<int>(sig);
    RsScriptKernelID _scriptKernelID = Device::getHal().ScriptKernelIDCreate(mContext, _script, _slot, _sig);
    return rs_to_hidl<ScriptKernelID>(_scriptKernelID);
}

Return<ScriptInvokeID> Context::scriptInvokeIDCreate(Script script, int32_t slot) {
    RsScript _script = hidl_to_rs<RsScript>(script);
    int _slot = static_cast<int>(slot);
    RsScriptInvokeID _scriptInvokeID = Device::getHal().ScriptInvokeIDCreate(mContext, _script, _slot);
    return rs_to_hidl<ScriptInvokeID>(_scriptInvokeID);
}

Return<ScriptFieldID> Context::scriptFieldIDCreate(Script script, int32_t slot) {
    RsScript _script = hidl_to_rs<RsScript>(script);
    int _slot = static_cast<int>(slot);
    RsScriptFieldID _scriptFieldID = Device::getHal().ScriptFieldIDCreate(mContext, _script, _slot);
    return rs_to_hidl<ScriptFieldID>(_scriptFieldID);
}

Return<ScriptGroup> Context::scriptGroupCreate(const hidl_vec<ScriptKernelID>& kernels, const hidl_vec<ScriptKernelID>& srcK, const hidl_vec<ScriptKernelID>& dstK, const hidl_vec<ScriptFieldID>& dstF, const hidl_vec<Type>& types) {
    std::vector<RsScriptKernelID> _kernels = hidl_to_rs<RsScriptKernelID>(kernels, [](ScriptFieldID val) { return hidl_to_rs<RsScriptKernelID>(val); });
    std::vector<RsScriptKernelID> _srcK    = hidl_to_rs<RsScriptKernelID>(srcK,    [](ScriptFieldID val) { return hidl_to_rs<RsScriptKernelID>(val); });
    std::vector<RsScriptKernelID> _dstK    = hidl_to_rs<RsScriptKernelID>(dstK,    [](ScriptFieldID val) { return hidl_to_rs<RsScriptKernelID>(val); });
    std::vector<RsScriptFieldID>  _dstF    = hidl_to_rs<RsScriptFieldID>(dstF,     [](ScriptFieldID val) { return hidl_to_rs<RsScriptFieldID>(val); });
    std::vector<RsType>           _types   = hidl_to_rs<RsType>(types,             [](Type val) { return hidl_to_rs<RsType>(val); });
    RsScriptGroup _scriptGroup = Device::getHal().ScriptGroupCreate(mContext, _kernels.data(), _kernels.size() * sizeof(RsScriptKernelID), _srcK.data(), _srcK.size() * sizeof(RsScriptKernelID), _dstK.data(), _dstK.size() * sizeof(RsScriptKernelID), _dstF.data(), _dstF.size() * sizeof(RsScriptFieldID), _types.data(), _types.size() * sizeof(RsType));
    return rs_to_hidl<ScriptGroup>(_scriptGroup);
}

Return<ScriptGroup2> Context::scriptGroup2Create(const hidl_string& name, const hidl_string& cacheDir, const hidl_vec<Closure>& closures) {
    const hidl_string& _name = name;
    const hidl_string& _cacheDir = cacheDir;
    std::vector<RsClosure> _closures = hidl_to_rs<RsClosure>(closures, [](Closure val) { return hidl_to_rs<RsClosure>(val); });
    RsScriptGroup2 _scriptGroup2 = Device::getHal().ScriptGroup2Create(mContext, _name.c_str(), _name.size(), _cacheDir.c_str(), _cacheDir.size(), _closures.data(), _closures.size());
    return rs_to_hidl<ScriptGroup2>(_scriptGroup2);
}

Return<void> Context::scriptGroupSetOutput(ScriptGroup sg, ScriptKernelID kid, Allocation alloc) {
    RsScriptGroup _sg = hidl_to_rs<RsScriptGroup>(sg);
    RsScriptKernelID _kid = hidl_to_rs<RsScriptKernelID>(kid);
    RsAllocation _alloc = hidl_to_rs<RsAllocation>(alloc);
    Device::getHal().ScriptGroupSetOutput(mContext, _sg, _kid, _alloc);
    return Void();
}

Return<void> Context::scriptGroupSetInput(ScriptGroup sg, ScriptKernelID kid, Allocation alloc) {
    RsScriptGroup _sg = hidl_to_rs<RsScriptGroup>(sg);
    RsScriptKernelID _kid = hidl_to_rs<RsScriptKernelID>(kid);
    RsAllocation _alloc = hidl_to_rs<RsAllocation>(alloc);
    Device::getHal().ScriptGroupSetInput(mContext, _sg, _kid, _alloc);
    return Void();
}

Return<void> Context::scriptGroupExecute(ScriptGroup sg) {
    RsScriptGroup _sg = hidl_to_rs<RsScriptGroup>(sg);
    Device::getHal().ScriptGroupExecute(mContext, _sg);
    return Void();
}

Return<void> Context::objDestroy(ObjectBase obj) {
    RsAsyncVoidPtr _obj = hidl_to_rs<RsAsyncVoidPtr>(obj);
    Device::getHal().ObjDestroy(mContext, _obj);
    return Void();
}

Return<Sampler> Context::samplerCreate(SamplerValue magFilter, SamplerValue minFilter, SamplerValue wrapS, SamplerValue wrapT, SamplerValue wrapR, float aniso) {
    RsSamplerValue _magFilter = static_cast<RsSamplerValue>(magFilter);
    RsSamplerValue _minFilter = static_cast<RsSamplerValue>(minFilter);
    RsSamplerValue _wrapS = static_cast<RsSamplerValue>(wrapS);
    RsSamplerValue _wrapT = static_cast<RsSamplerValue>(wrapT);
    RsSamplerValue _wrapR = static_cast<RsSamplerValue>(wrapR);
    float _aniso = static_cast<float>(aniso);
    RsSampler _sampler = Device::getHal().SamplerCreate(mContext, _magFilter, _minFilter, _wrapS, _wrapT, _wrapR, _aniso);
    return rs_to_hidl<Sampler>(_sampler);
}

Return<void> Context::scriptBindAllocation(Script script, Allocation allocation, uint32_t slot) {
    RsScript _script = hidl_to_rs<RsScript>(script);
    RsAllocation _allocation = hidl_to_rs<RsAllocation>(allocation);
    uint32_t _slot = slot;
    Device::getHal().ScriptBindAllocation(mContext, _script, _allocation, _slot);
    return Void();
}

Return<void> Context::scriptSetTimeZone(Script script, const hidl_string& timeZone) {
    RsScript _script = hidl_to_rs<RsScript>(script);
    const hidl_string& _timeZone = timeZone;
    Device::getHal().ScriptSetTimeZone(mContext, _script, _timeZone.c_str(), _timeZone.size());
    return Void();
}

Return<void> Context::scriptInvoke(Script vs, uint32_t slot) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    Device::getHal().ScriptInvoke(mContext, _vs, _slot);
    return Void();
}

Return<void> Context::scriptInvokeV(Script vs, uint32_t slot, const hidl_vec<uint8_t>& data) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    const void* _dataPtr = hidl_to_rs<const void*>(data.data());
    size_t _len = data.size();
    Device::getHal().ScriptInvokeV(mContext, _vs, _slot, _dataPtr, _len);
    return Void();
}

Return<void> Context::scriptForEach(Script vs, uint32_t slot, const hidl_vec<Allocation>& vains, Allocation vaout, const hidl_vec<uint8_t>& params, Ptr sc) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    std::vector<RsAllocation> _vains = hidl_to_rs<RsAllocation>(vains, [](Allocation val) { return hidl_to_rs<RsAllocation>(val); });
    RsAllocation _vaout = hidl_to_rs<RsAllocation>(vaout);
    const void* _paramsPtr = hidl_to_rs<const void*>(params.data());
    size_t _paramLen = params.size();
    const RsScriptCall* _sc = hidl_to_rs<const RsScriptCall*>(sc);
    size_t _scLen = _sc != nullptr ? sizeof(ScriptCall) : 0;
    Device::getHal().ScriptForEachMulti(mContext, _vs, _slot, _vains.data(), _vains.size(), _vaout, _paramsPtr, _paramLen, _sc, _scLen);
    return Void();
}

Return<void> Context::scriptReduce(Script vs, uint32_t slot, const hidl_vec<Allocation>& vains, Allocation vaout, Ptr sc) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    std::vector<RsAllocation> _vains = hidl_to_rs<RsAllocation>(vains, [](Allocation val) { return hidl_to_rs<RsAllocation>(val); });
    RsAllocation _vaout = hidl_to_rs<RsAllocation>(vaout);
    const RsScriptCall* _sc = hidl_to_rs<const RsScriptCall*>(sc);
    size_t _scLen = _sc != nullptr ? sizeof(ScriptCall) : 0;
    Device::getHal().ScriptReduce(mContext, _vs, _slot, _vains.data(), _vains.size(), _vaout, _sc, _scLen);
    return Void();
}

Return<void> Context::scriptSetVarI(Script vs, uint32_t slot, int32_t value) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    int _value = static_cast<int>(value);
    Device::getHal().ScriptSetVarI(mContext, _vs, _slot, _value);
    return Void();
}

Return<void> Context::scriptSetVarObj(Script vs, uint32_t slot, ObjectBase obj) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    RsObjectBase _obj = hidl_to_rs<RsObjectBase>(obj);
    Device::getHal().ScriptSetVarObj(mContext, _vs, _slot, _obj);
    return Void();
}

Return<void> Context::scriptSetVarJ(Script vs, uint32_t slot, int64_t value) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    int64_t _value = static_cast<int64_t>(value);
    Device::getHal().ScriptSetVarJ(mContext, _vs, _slot, _value);
    return Void();
}

Return<void> Context::scriptSetVarF(Script vs, uint32_t slot, float value) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    float _value = value;
    Device::getHal().ScriptSetVarF(mContext, _vs, _slot, _value);
    return Void();
}

Return<void> Context::scriptSetVarD(Script vs, uint32_t slot, double value) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    double _value = value;
    Device::getHal().ScriptSetVarD(mContext, _vs, _slot, _value);
    return Void();
}

Return<void> Context::scriptSetVarV(Script vs, uint32_t slot, const hidl_vec<uint8_t>& data) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    const void* _dataPtr = hidl_to_rs<const void*>(data.data());
    size_t _len = data.size();
    Device::getHal().ScriptSetVarV(mContext, _vs, _slot, _dataPtr, _len);
    return Void();
}

Return<void> Context::scriptGetVarV(Script vs, uint32_t slot, Size len, scriptGetVarV_cb _hidl_cb) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    size_t _len = static_cast<size_t>(len);
    std::vector<uint8_t> _data(_len);
    Device::getHal().ScriptGetVarV(mContext, _vs, _slot, _data.data(), _data.size());
    hidl_vec<uint8_t> data = _data;
    _hidl_cb(data);
    return Void();
}

Return<void> Context::scriptSetVarVE(Script vs, uint32_t slot, const hidl_vec<uint8_t>& data, Element ve, const hidl_vec<uint32_t>& dims) {
    RsScript _vs = hidl_to_rs<RsScript>(vs);
    uint32_t _slot = slot;
    const void* _dataPtr = hidl_to_rs<const void*>(data.data());
    size_t _len = data.size();
    RsElement _ve = hidl_to_rs<RsElement>(ve);
    const uint32_t* _dimsPtr = dims.data();
    size_t _dimLen = dims.size() * sizeof(uint32_t);
    Device::getHal().ScriptSetVarVE(mContext, _vs, _slot, _dataPtr, _len, _ve, _dimsPtr, _dimLen);
    return Void();
}

Return<Script> Context::scriptCCreate(const hidl_string& resName, const hidl_string& cacheDir, const hidl_vec<uint8_t>& text) {
    const hidl_string& _resName = resName;
    const hidl_string& _cacheDir = cacheDir;
    const char* _textPtr = hidl_to_rs<const char*>(text.data());
    size_t _textSize = text.size();
    RsScript _script = Device::getHal().ScriptCCreate(mContext, _resName.c_str(), _resName.size(), _cacheDir.c_str(), _cacheDir.size(), _textPtr, _textSize);
    return rs_to_hidl<Script>(_script);
}

Return<Script> Context::scriptIntrinsicCreate(ScriptIntrinsicID id, Element elem) {
    RsScriptIntrinsicID _id = static_cast<RsScriptIntrinsicID>(id);
    RsElement _elem = hidl_to_rs<RsElement>(elem);
    RsScript _script = Device::getHal().ScriptIntrinsicCreate(mContext, _id, _elem);
    return rs_to_hidl<Script>(_script);
}


// Methods from ::android::hidl::base::V1_0::IBase follow.


}  // namespace implementation
}  // namespace V1_0
}  // namespace renderscript
}  // namespace hardware
}  // namespace android
