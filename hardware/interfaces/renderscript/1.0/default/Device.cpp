#include "Context.h"
#include "Device.h"

#include <android/dlext.h>
#include <dlfcn.h>

namespace android {
namespace hardware {
namespace renderscript {
namespace V1_0 {
namespace implementation {


static dispatchTable loadHAL();
dispatchTable Device::mDispatchHal = loadHAL();

Device::Device() {
}

dispatchTable& Device::getHal() {
    return mDispatchHal;
}


// Methods from ::android::hardware::renderscript::V1_0::IDevice follow.

Return<sp<IContext>> Device::contextCreate(uint32_t sdkVersion, ContextType ct, int32_t flags) {
    return new Context(sdkVersion, ct, flags);
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

IDevice* HIDL_FETCH_IDevice(const char* /* name */) {
    return new Device();
}

// Helper function
dispatchTable loadHAL() {

    static_assert(sizeof(void*) <= sizeof(uint64_t), "RenderScript HIDL Error: sizeof(void*) > sizeof(uint64_t)");
    static_assert(sizeof(size_t) <= sizeof(uint64_t), "RenderScript HIDL Error: sizeof(size_t) > sizeof(uint64_t)");

    const char* filename = "libRS_internal.so";
    // Try to load libRS_internal.so from the "rs" namespace directly.
    typedef struct android_namespace_t* (*GetExportedNamespaceFnPtr)(const char*);
    GetExportedNamespaceFnPtr getExportedNamespace =
        (GetExportedNamespaceFnPtr)dlsym(RTLD_DEFAULT, "android_get_exported_namespace");
    void* handle = nullptr;
    if (getExportedNamespace != nullptr) {
        android_namespace_t* rs_namespace = getExportedNamespace("rs");
        if (rs_namespace != nullptr) {
            const android_dlextinfo dlextinfo = {
                .flags = ANDROID_DLEXT_USE_NAMESPACE, .library_namespace = rs_namespace,
            };
            handle = android_dlopen_ext(filename, RTLD_LAZY | RTLD_LOCAL, &dlextinfo);
        }
    }
    if (handle == nullptr) {
        // if there is no "rs" namespace (in case when this HAL impl is loaded
        // into a vendor process), then use the plain dlopen.
        handle = dlopen(filename, RTLD_LAZY | RTLD_LOCAL);
    }

    dispatchTable dispatchHal = {
        .SetNativeLibDir = (SetNativeLibDirFnPtr) nullptr,

        .Allocation1DData =
            (Allocation1DDataFnPtr)dlsym(handle, "rsAllocation1DData"),
        .Allocation1DElementData = (Allocation1DElementDataFnPtr) nullptr,
        .Allocation1DRead =
            (Allocation1DReadFnPtr)dlsym(handle, "rsAllocation1DRead"),
        .Allocation2DData =
            (Allocation2DDataFnPtr)dlsym(handle, "rsAllocation2DData"),
        .Allocation2DRead =
            (Allocation2DReadFnPtr)dlsym(handle, "rsAllocation2DRead"),
        .Allocation3DData =
            (Allocation3DDataFnPtr)dlsym(handle, "rsAllocation3DData"),
        .Allocation3DRead =
            (Allocation3DReadFnPtr)dlsym(handle, "rsAllocation3DRead"),
        .AllocationAdapterCreate = (AllocationAdapterCreateFnPtr)dlsym(
            handle, "rsAllocationAdapterCreate"),
        .AllocationAdapterOffset = (AllocationAdapterOffsetFnPtr)dlsym(
            handle, "rsAllocationAdapterOffset"),
        .AllocationCopy2DRange = (AllocationCopy2DRangeFnPtr)dlsym(
            handle, "rsAllocationCopy2DRange"),
        .AllocationCopy3DRange = (AllocationCopy3DRangeFnPtr)dlsym(
            handle, "rsAllocationCopy3DRange"),
        .AllocationCopyToBitmap = (AllocationCopyToBitmapFnPtr)dlsym(
            handle, "rsAllocationCopyToBitmap"),
        .AllocationCreateFromBitmap = (AllocationCreateFromBitmapFnPtr)dlsym(
            handle, "rsAllocationCreateFromBitmap"),
        .AllocationCreateStrided = (AllocationCreateStridedFnPtr)dlsym(
            handle, "rsAllocationCreateStrided"),
        .AllocationCreateTyped = (AllocationCreateTypedFnPtr)dlsym(
            handle, "rsAllocationCreateTyped"),
        .AllocationCubeCreateFromBitmap =
            (AllocationCubeCreateFromBitmapFnPtr)dlsym(
                handle, "rsAllocationCubeCreateFromBitmap"),
        .AllocationElementData = (AllocationElementDataFnPtr)dlsym(
            handle, "rsAllocationElementData"),
        .AllocationElementRead = (AllocationElementReadFnPtr)dlsym(
            handle, "rsAllocationElementRead"),
        .AllocationGenerateMipmaps = (AllocationGenerateMipmapsFnPtr)dlsym(
            handle, "rsAllocationGenerateMipmaps"),
        .AllocationGetPointer =
            (AllocationGetPointerFnPtr)dlsym(handle, "rsAllocationGetPointer"),
        .AllocationGetSurface =
            (AllocationGetSurfaceFnPtr)dlsym(handle, "rsAllocationGetSurface"),
        .AllocationGetType =
            (AllocationGetTypeFnPtr)dlsym(handle, "rsaAllocationGetType"),
        .AllocationIoReceive =
            (AllocationIoReceiveFnPtr)dlsym(handle, "rsAllocationIoReceive"),
        .AllocationIoSend =
            (AllocationIoSendFnPtr)dlsym(handle, "rsAllocationIoSend"),
        .AllocationRead =
            (AllocationReadFnPtr)dlsym(handle, "rsAllocationRead"),
        .AllocationResize1D =
            (AllocationResize1DFnPtr)dlsym(handle, "rsAllocationResize1D"),
        .AllocationSetSurface =
            (AllocationSetSurfaceFnPtr)dlsym(handle, "rsAllocationSetSurface"),
        .AllocationSetupBufferQueue = (AllocationSetupBufferQueueFnPtr)dlsym(
            handle, "rsAllocationSetupBufferQueue"),
        .AllocationShareBufferQueue = (AllocationShareBufferQueueFnPtr)dlsym(
            handle, "rsAllocationShareBufferQueue"),
        .AllocationSyncAll =
            (AllocationSyncAllFnPtr)dlsym(handle, "rsAllocationSyncAll"),
        .AssignName = (AssignNameFnPtr)dlsym(handle, "rsAssignName"),
        .ClosureCreate = (ClosureCreateFnPtr)dlsym(handle, "rsClosureCreate"),
        .ClosureSetArg = (ClosureSetArgFnPtr)dlsym(handle, "rsClosureSetArg"),
        .ClosureSetGlobal =
            (ClosureSetGlobalFnPtr)dlsym(handle, "rsClosureSetGlobal"),
        .ContextCreateVendor =
            (ContextCreateVendorFnPtr)dlsym(handle, "rsContextCreateVendor"),
        .ContextDeinitToClient = (ContextDeinitToClientFnPtr)dlsym(
            handle, "rsContextDeinitToClient"),
        .ContextDestroy =
            (ContextDestroyFnPtr)dlsym(handle, "rsContextDestroy"),
        .ContextDump = (ContextDumpFnPtr)dlsym(handle, "rsContextDump"),
        .ContextFinish = (ContextFinishFnPtr)dlsym(handle, "rsContextFinish"),
        .ContextGetMessage =
            (ContextGetMessageFnPtr)dlsym(handle, "rsContextGetMessage"),
        .ContextInitToClient =
            (ContextInitToClientFnPtr)dlsym(handle, "rsContextInitToClient"),
        .ContextPeekMessage =
            (ContextPeekMessageFnPtr)dlsym(handle, "rsContextPeekMessage"),
        .ContextSendMessage =
            (ContextSendMessageFnPtr)dlsym(handle, "rsContextSendMessage"),
        .ContextSetCacheDir =
            (ContextSetCacheDirFnPtr)dlsym(handle, "rsContextSetCacheDir"),
        .ContextSetPriority =
            (ContextSetPriorityFnPtr)dlsym(handle, "rsContextSetPriority"),
        .DeviceCreate = (DeviceCreateFnPtr) nullptr,
        .DeviceDestroy = (DeviceDestroyFnPtr) nullptr,
        .DeviceSetConfig = (DeviceSetConfigFnPtr) nullptr,
        .ElementCreate2 =
            (ElementCreate2FnPtr)dlsym(handle, "rsElementCreate2"),
        .ElementCreate = (ElementCreateFnPtr)dlsym(handle, "rsElementCreate"),
        .ElementGetNativeData =
            (ElementGetNativeDataFnPtr)dlsym(handle, "rsaElementGetNativeData"),
        .ElementGetSubElements = (ElementGetSubElementsFnPtr)dlsym(
            handle, "rsaElementGetSubElements"),
        .GetName = (GetNameFnPtr)dlsym(handle, "rsaGetName"),
        .InvokeClosureCreate =
            (InvokeClosureCreateFnPtr)dlsym(handle, "rsInvokeClosureCreate"),
        .ObjDestroy = (ObjDestroyFnPtr)dlsym(handle, "rsObjDestroy"),
        .SamplerCreate = (SamplerCreateFnPtr)dlsym(handle, "rsSamplerCreate"),
        .ScriptBindAllocation =
            (ScriptBindAllocationFnPtr)dlsym(handle, "rsScriptBindAllocation"),
        .ScriptCCreate = (ScriptCCreateFnPtr)dlsym(handle, "rsScriptCCreate"),
        .ScriptFieldIDCreate =
            (ScriptFieldIDCreateFnPtr)dlsym(handle, "rsScriptFieldIDCreate"),
        .ScriptForEach = (ScriptForEachFnPtr) nullptr,
        .ScriptForEachMulti =
            (ScriptForEachMultiFnPtr)dlsym(handle, "rsScriptForEachMulti"),
        .ScriptGetVarV = (ScriptGetVarVFnPtr)dlsym(handle, "rsScriptGetVarV"),
        .ScriptGroup2Create =
            (ScriptGroup2CreateFnPtr)dlsym(handle, "rsScriptGroup2Create"),
        .ScriptGroupCreate =
            (ScriptGroupCreateFnPtr)dlsym(handle, "rsScriptGroupCreate"),
        .ScriptGroupExecute =
            (ScriptGroupExecuteFnPtr)dlsym(handle, "rsScriptGroupExecute"),
        .ScriptGroupSetInput =
            (ScriptGroupSetInputFnPtr)dlsym(handle, "rsScriptGroupSetInput"),
        .ScriptGroupSetOutput =
            (ScriptGroupSetOutputFnPtr)dlsym(handle, "rsScriptGroupSetOutput"),
        .ScriptIntrinsicCreate = (ScriptIntrinsicCreateFnPtr)dlsym(
            handle, "rsScriptIntrinsicCreate"),
        .ScriptInvoke = (ScriptInvokeFnPtr)dlsym(handle, "rsScriptInvoke"),
        .ScriptInvokeIDCreate =
            (ScriptInvokeIDCreateFnPtr)dlsym(handle, "rsScriptInvokeIDCreate"),
        .ScriptInvokeV = (ScriptInvokeVFnPtr)dlsym(handle, "rsScriptInvokeV"),
        .ScriptKernelIDCreate =
            (ScriptKernelIDCreateFnPtr)dlsym(handle, "rsScriptKernelIDCreate"),
        .ScriptReduce = (ScriptReduceFnPtr)dlsym(handle, "rsScriptReduce"),
        .ScriptSetTimeZone =
            (ScriptSetTimeZoneFnPtr)dlsym(handle, "rsScriptSetTimeZone"),
        .ScriptSetVarD = (ScriptSetVarDFnPtr)dlsym(handle, "rsScriptSetVarD"),
        .ScriptSetVarF = (ScriptSetVarFFnPtr)dlsym(handle, "rsScriptSetVarF"),
        .ScriptSetVarI = (ScriptSetVarIFnPtr)dlsym(handle, "rsScriptSetVarI"),
        .ScriptSetVarJ = (ScriptSetVarJFnPtr)dlsym(handle, "rsScriptSetVarJ"),
        .ScriptSetVarObj =
            (ScriptSetVarObjFnPtr)dlsym(handle, "rsScriptSetVarObj"),
        .ScriptSetVarVE =
            (ScriptSetVarVEFnPtr)dlsym(handle, "rsScriptSetVarVE"),
        .ScriptSetVarV = (ScriptSetVarVFnPtr)dlsym(handle, "rsScriptSetVarV"),
        .TypeCreate = (TypeCreateFnPtr)dlsym(handle, "rsTypeCreate"),
        .TypeGetNativeData =
            (TypeGetNativeDataFnPtr)dlsym(handle, "rsaTypeGetNativeData"),
    };

    return dispatchHal;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace renderscript
}  // namespace hardware
}  // namespace android
