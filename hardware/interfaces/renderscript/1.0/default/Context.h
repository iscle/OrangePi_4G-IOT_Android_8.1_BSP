#ifndef ANDROID_HARDWARE_RENDERSCRIPT_V1_0_CONTEXT_H
#define ANDROID_HARDWARE_RENDERSCRIPT_V1_0_CONTEXT_H

#include "cpp/rsDispatch.h"
#include "dlfcn.h"
#include <android/hardware/renderscript/1.0/IContext.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace renderscript {
namespace V1_0 {
namespace implementation {

using ::android::hardware::renderscript::V1_0::AllocationCubemapFace;
using ::android::hardware::renderscript::V1_0::AllocationMipmapControl;
using ::android::hardware::renderscript::V1_0::AllocationUsageType;
using ::android::hardware::renderscript::V1_0::ContextType;
using ::android::hardware::renderscript::V1_0::DataKind;
using ::android::hardware::renderscript::V1_0::DataType;
using ::android::hardware::renderscript::V1_0::IContext;
using ::android::hardware::renderscript::V1_0::MessageToClientType;
using ::android::hardware::renderscript::V1_0::SamplerValue;
using ::android::hardware::renderscript::V1_0::ScriptCall;
using ::android::hardware::renderscript::V1_0::ScriptIntrinsicID;
using ::android::hardware::renderscript::V1_0::ThreadPriorities;
using ::android::hardware::renderscript::V1_0::YuvFormat;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct Context : public IContext {
    Context(uint32_t sdkVersion, ContextType ct, int32_t flags);

    // Methods from ::android::hardware::renderscript::V1_0::IContext follow.
    Return<Allocation> allocationAdapterCreate(Type type, Allocation baseAlloc) override;
    Return<void> allocationAdapterOffset(Allocation alloc, const hidl_vec<uint32_t>& offsets) override;
    Return<Type> allocationGetType(Allocation allocation) override;
    Return<Allocation> allocationCreateTyped(Type type, AllocationMipmapControl amips, int32_t usage, Ptr ptr) override;
    Return<Allocation> allocationCreateFromBitmap(Type type, AllocationMipmapControl amips, const hidl_vec<uint8_t>& bitmap, int32_t usage) override;
    Return<Allocation> allocationCubeCreateFromBitmap(Type type, AllocationMipmapControl amips, const hidl_vec<uint8_t>& bitmap, int32_t usage) override;
    Return<NativeWindow> allocationGetNativeWindow(Allocation allocation) override;
    Return<void> allocationSetNativeWindow(Allocation allocation, NativeWindow nativewindow) override;
    Return<void> allocationSetupBufferQueue(Allocation alloc, uint32_t numBuffer) override;
    Return<void> allocationShareBufferQueue(Allocation baseAlloc, Allocation subAlloc) override;
    Return<void> allocationCopyToBitmap(Allocation allocation, Ptr data, Size sizeBytes) override;
    Return<void> allocation1DWrite(Allocation allocation, uint32_t offset, uint32_t lod, uint32_t count, const hidl_vec<uint8_t>& data) override;
    Return<void> allocationElementWrite(Allocation allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, const hidl_vec<uint8_t>& data, Size compIdx) override;
    Return<void> allocation2DWrite(Allocation allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, AllocationCubemapFace face, uint32_t w, uint32_t h, const hidl_vec<uint8_t>& data, Size stride) override;
    Return<void> allocation3DWrite(Allocation allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, const hidl_vec<uint8_t>& data, Size stride) override;
    Return<void> allocationGenerateMipmaps(Allocation allocation) override;
    Return<void> allocationRead(Allocation allocation, Ptr data, Size sizeBytes) override;
    Return<void> allocation1DRead(Allocation allocation, uint32_t xoff, uint32_t lod, uint32_t count, Ptr data, Size sizeBytes) override;
    Return<void> allocationElementRead(Allocation allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, Ptr data, Size sizeBytes, Size compIdx) override;
    Return<void> allocation2DRead(Allocation allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, AllocationCubemapFace face, uint32_t w, uint32_t h, Ptr data, Size sizeBytes, Size stride) override;
    Return<void> allocation3DRead(Allocation allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, Ptr data, Size sizeBytes, Size stride) override;
    Return<void> allocationSyncAll(Allocation allocation, AllocationUsageType usageType) override;
    Return<void> allocationResize1D(Allocation allocation, uint32_t dimX) override;
    Return<void> allocationCopy2DRange(Allocation dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstMip, AllocationCubemapFace dstFace, uint32_t width, uint32_t height, Allocation srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcMip, AllocationCubemapFace srcFace) override;
    Return<void> allocationCopy3DRange(Allocation dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstZoff, uint32_t dstMip, uint32_t width, uint32_t height, uint32_t depth, Allocation srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcZoff, uint32_t srcMip) override;
    Return<void> allocationIoSend(Allocation allocation) override;
    Return<void> allocationIoReceive(Allocation allocation) override;
    Return<void> allocationGetPointer(Allocation allocation, uint32_t lod, AllocationCubemapFace face, uint32_t z, allocationGetPointer_cb _hidl_cb) override;
    Return<void> elementGetNativeMetadata(Element element, elementGetNativeMetadata_cb _hidl_cb) override;
    Return<void> elementGetSubElements(Element element, Size numSubElem, elementGetSubElements_cb _hidl_cb) override;
    Return<Element> elementCreate(DataType dt, DataKind dk, bool norm, uint32_t size) override;
    Return<Element> elementComplexCreate(const hidl_vec<Element>& eins, const hidl_vec<hidl_string>& names, const hidl_vec<Size>& arraySizes) override;
    Return<void> typeGetNativeMetadata(Type type, typeGetNativeMetadata_cb _hidl_cb) override;
    Return<Type> typeCreate(Element element, uint32_t dimX, uint32_t dimY, uint32_t dimZ, bool mipmaps, bool faces, YuvFormat yuv) override;
    Return<void> contextDestroy() override;
    Return<void> contextGetMessage(Ptr data, Size size, contextGetMessage_cb _hidl_cb) override;
    Return<void> contextPeekMessage(contextPeekMessage_cb _hidl_cb) override;
    Return<void> contextSendMessage(uint32_t id, const hidl_vec<uint8_t>& data) override;
    Return<void> contextInitToClient() override;
    Return<void> contextDeinitToClient() override;
    Return<void> contextFinish() override;
    Return<void> contextLog() override;
    Return<void> contextSetPriority(ThreadPriorities priority) override;
    Return<void> contextSetCacheDir(const hidl_string& cacheDir) override;
    Return<void> assignName(ObjectBase obj, const hidl_string& name) override;
    Return<void> getName(ObjectBase obj, getName_cb _hidl_cb) override;
    Return<Closure> closureCreate(ScriptKernelID kernelID, Allocation returnValue, const hidl_vec<ScriptFieldID>& fieldIDS, const hidl_vec<int64_t>& values, const hidl_vec<int32_t>& sizes, const hidl_vec<Closure>& depClosures, const hidl_vec<ScriptFieldID>& depFieldIDS) override;
    Return<Closure> invokeClosureCreate(ScriptInvokeID invokeID, const hidl_vec<uint8_t>& params, const hidl_vec<ScriptFieldID>& fieldIDS, const hidl_vec<int64_t>& values, const hidl_vec<int32_t>& sizes) override;
    Return<void> closureSetArg(Closure closure, uint32_t index, Ptr value, int32_t size) override;
    Return<void> closureSetGlobal(Closure closure, ScriptFieldID fieldID, int64_t value, int32_t size) override;
    Return<ScriptKernelID> scriptKernelIDCreate(Script script, int32_t slot, int32_t sig) override;
    Return<ScriptInvokeID> scriptInvokeIDCreate(Script script, int32_t slot) override;
    Return<ScriptFieldID> scriptFieldIDCreate(Script script, int32_t slot) override;
    Return<ScriptGroup> scriptGroupCreate(const hidl_vec<ScriptKernelID>& kernels, const hidl_vec<ScriptKernelID>& srcK, const hidl_vec<ScriptKernelID>& dstK, const hidl_vec<ScriptFieldID>& dstF, const hidl_vec<Type>& types) override;
    Return<ScriptGroup2> scriptGroup2Create(const hidl_string& name, const hidl_string& cacheDir, const hidl_vec<Closure>& closures) override;
    Return<void> scriptGroupSetOutput(ScriptGroup sg, ScriptKernelID kid, Allocation alloc) override;
    Return<void> scriptGroupSetInput(ScriptGroup sg, ScriptKernelID kid, Allocation alloc) override;
    Return<void> scriptGroupExecute(ScriptGroup sg) override;
    Return<void> objDestroy(ObjectBase obj) override;
    Return<Sampler> samplerCreate(SamplerValue magFilter, SamplerValue minFilter, SamplerValue wrapS, SamplerValue wrapT, SamplerValue wrapR, float aniso) override;
    Return<void> scriptBindAllocation(Script script, Allocation allocation, uint32_t slot) override;
    Return<void> scriptSetTimeZone(Script script, const hidl_string& timeZone) override;
    Return<void> scriptInvoke(Script vs, uint32_t slot) override;
    Return<void> scriptInvokeV(Script vs, uint32_t slot, const hidl_vec<uint8_t>& data) override;
    Return<void> scriptForEach(Script vs, uint32_t slot, const hidl_vec<Allocation>& vains, Allocation vaout, const hidl_vec<uint8_t>& params, Ptr sc) override;
    Return<void> scriptReduce(Script vs, uint32_t slot, const hidl_vec<Allocation>& vains, Allocation vaout, Ptr sc) override;
    Return<void> scriptSetVarI(Script vs, uint32_t slot, int32_t value) override;
    Return<void> scriptSetVarObj(Script vs, uint32_t slot, ObjectBase obj) override;
    Return<void> scriptSetVarJ(Script vs, uint32_t slot, int64_t value) override;
    Return<void> scriptSetVarF(Script vs, uint32_t slot, float value) override;
    Return<void> scriptSetVarD(Script vs, uint32_t slot, double value) override;
    Return<void> scriptSetVarV(Script vs, uint32_t slot, const hidl_vec<uint8_t>& data) override;
    Return<void> scriptGetVarV(Script vs, uint32_t slot, Size len, scriptGetVarV_cb _hidl_cb) override;
    Return<void> scriptSetVarVE(Script vs, uint32_t slot, const hidl_vec<uint8_t>& data, Element ve, const hidl_vec<uint32_t>& dims) override;
    Return<Script> scriptCCreate(const hidl_string& resName, const hidl_string& cacheDir, const hidl_vec<uint8_t>& text) override;
    Return<Script> scriptIntrinsicCreate(ScriptIntrinsicID id, Element elem) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.

 private:
    RsContext mContext;
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace renderscript
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_RENDERSCRIPT_V1_0_CONTEXT_H
