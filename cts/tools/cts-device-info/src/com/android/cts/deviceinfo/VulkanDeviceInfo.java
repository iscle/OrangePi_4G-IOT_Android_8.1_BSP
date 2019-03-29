/*
 * Copyright 2016 The Android Open Source Project
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

package com.android.cts.deviceinfo;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Vulkan info collector.
 *
 * This collector gathers a VkJSONInstance representing the Vulkan capabilities of the Android
 * device, and translates it into a DeviceInfoStore. The goal is to be as faithful to the original
 * VkJSON as possible, so that the DeviceInfo can later be turned back into VkJSON without loss,
 * while still allow complex queries against the DeviceInfo database.
 *
 * We inherit some design decisions from VkJSON, and there are a few places were translation isn't
 * perfect:
 *
 * - Most JSON implementations handle JSON Numbers as doubles (64-bit floating point), which can't
 *   faithfully transfer 64-bit integers. So Vulkan uint64_t and VkDeviceSize values are encoded as
 *   Strings containing the hexadecimal representation of the value (with "0x" prefix).
 *
 * - Vulkan enum values are represented as Numbers. This is most convenient for processing, though
 *   isn't very human-readable. Pretty-printing these as strings is left for other tools.
 *
 * - For implementation convenience, VkJSON represents VkBool32 values as JSON Numbers (0/1). This
 *   collector converts them to JSON Boolean values (false/true).
 *
 * - DeviceInfoStore doesn't allow arrays of non-uniform or non-primitive types. VkJSON stores
 *   format capabilities as an array of formats, where each format is an array containing a number
 *   (the format enum value) and an object (the format properties). Since DeviceInfoStore doesn't
 *   allow array-of-array, we instead store formats as an array of uniform structs, So instead of
 *       [[3, {
 *           "linearTilingFeatures": 0,
 *           "optimalTilingFeatures": 5121,
 *           "bufferFeatures": 0
 *       }]]
 *   the format with enum value "3" will be represented as
 *       {
 *           "id": 3,
 *           "linear_tiling_features": 0,
 *           "optimal_tiling_features": 5121,
 *           "buffer_features": 0
 *       }
 *
 * - Device layers are deprecated, but instance layers can still add device extensions. VkJSON
 *   doesn't yet include device extensions provided by layers, though. So VulkanDeviceInfo omits
 *   device layers altogether. Eventually VkJSON and VulkanDeviceInfo should report device layers
 *   and their extensions the same way instance layers and their extensions are reported.
 *
 * - VkJSON uses the original Vulkan field names, while VulkanDeviceInfo follows the DeviceInfo
 *   naming convention. So VkJSON fields named like "sparseProperties" will be converted to names
 *   like "sparse_properties".
 */
public final class VulkanDeviceInfo extends DeviceInfo {

    private static final String KEY_ALPHA_TO_ONE = "alphaToOne";
    private static final String KEY_API_VERSION = "apiVersion";
    private static final String KEY_BUFFER_FEATURES = "bufferFeatures";
    private static final String KEY_BUFFER_IMAGE_GRANULARITY = "bufferImageGranularity";
    private static final String KEY_DEPTH = "depth";
    private static final String KEY_DEPTH_BIAS_CLAMP = "depthBiasClamp";
    private static final String KEY_DEPTH_BOUNDS = "depthBounds";
    private static final String KEY_DEPTH_CLAMP = "depthClamp";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_DEVICE_ID = "deviceID";
    private static final String KEY_DEVICE_NAME = "deviceName";
    private static final String KEY_DEVICE_TYPE = "deviceType";
    private static final String KEY_DEVICES = "devices";
    private static final String KEY_DISCRETE_QUEUE_PRIORITIES = "discreteQueuePriorities";
    private static final String KEY_DRAW_INDIRECT_FIRST_INSTANCE = "drawIndirectFirstInstance";
    private static final String KEY_DRIVER_VERSION = "driverVersion";
    private static final String KEY_DUAL_SRC_BLEND = "dualSrcBlend";
    private static final String KEY_EXTENSION_NAME = "extensionName";
    private static final String KEY_EXTENSIONS = "extensions";
    private static final String KEY_FEATURES = "features";
    private static final String KEY_FILL_MODE_NON_SOLID = "fillModeNonSolid";
    private static final String KEY_FLAGS = "flags";
    private static final String KEY_FORMATS = "formats";
    private static final String KEY_FRAGMENT_STORES_AND_ATOMICS = "fragmentStoresAndAtomics";
    private static final String KEY_FRAMEBUFFER_COLOR_SAMPLE_COUNTS = "framebufferColorSampleCounts";
    private static final String KEY_FRAMEBUFFER_DEPTH_SAMPLE_COUNTS = "framebufferDepthSampleCounts";
    private static final String KEY_FRAMEBUFFER_NO_ATTACHMENTS_SAMPLE_COUNTS = "framebufferNoAttachmentsSampleCounts";
    private static final String KEY_FRAMEBUFFER_STENCIL_SAMPLE_COUNTS = "framebufferStencilSampleCounts";
    private static final String KEY_FULL_DRAW_INDEX_UINT32 = "fullDrawIndexUint32";
    private static final String KEY_GEOMETRY_SHADER = "geometryShader";
    private static final String KEY_HEAP_INDEX = "heapIndex";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_IMAGE_CUBE_ARRAY = "imageCubeArray";
    private static final String KEY_IMPLEMENTATION_VERSION = "implementationVersion";
    private static final String KEY_INDEPENDENT_BLEND = "independentBlend";
    private static final String KEY_INHERITED_QUERIES = "inheritedQueries";
    private static final String KEY_LARGE_POINTS = "largePoints";
    private static final String KEY_LAYER_NAME = "layerName";
    private static final String KEY_LAYERS = "layers";
    private static final String KEY_LIMITS = "limits";
    private static final String KEY_LINE_WIDTH_GRANULARITY = "lineWidthGranularity";
    private static final String KEY_LINE_WIDTH_RANGE = "lineWidthRange";
    private static final String KEY_LINEAR_TILING_FEATURES = "linearTilingFeatures";
    private static final String KEY_LOGIC_OP = "logicOp";
    private static final String KEY_MAX_BOUND_DESCRIPTOR_SETS = "maxBoundDescriptorSets";
    private static final String KEY_MAX_CLIP_DISTANCES = "maxClipDistances";
    private static final String KEY_MAX_COLOR_ATTACHMENTS = "maxColorAttachments";
    private static final String KEY_MAX_COMBINED_CLIP_AND_CULL_DISTANCES = "maxCombinedClipAndCullDistances";
    private static final String KEY_MAX_COMPUTE_SHARED_MEMORY_SIZE = "maxComputeSharedMemorySize";
    private static final String KEY_MAX_COMPUTE_WORK_GROUP_COUNT = "maxComputeWorkGroupCount";
    private static final String KEY_MAX_COMPUTE_WORK_GROUP_INVOCATIONS = "maxComputeWorkGroupInvocations";
    private static final String KEY_MAX_COMPUTE_WORK_GROUP_SIZE = "maxComputeWorkGroupSize";
    private static final String KEY_MAX_CULL_DISTANCES = "maxCullDistances";
    private static final String KEY_MAX_DESCRIPTOR_SET_INPUT_ATTACHMENTS = "maxDescriptorSetInputAttachments";
    private static final String KEY_MAX_DESCRIPTOR_SET_SAMPLED_IMAGES = "maxDescriptorSetSampledImages";
    private static final String KEY_MAX_DESCRIPTOR_SET_SAMPLERS = "maxDescriptorSetSamplers";
    private static final String KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS = "maxDescriptorSetStorageBuffers";
    private static final String KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS_DYNAMIC = "maxDescriptorSetStorageBuffersDynamic";
    private static final String KEY_MAX_DESCRIPTOR_SET_STORAGE_IMAGES = "maxDescriptorSetStorageImages";
    private static final String KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS = "maxDescriptorSetUniformBuffers";
    private static final String KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS_DYNAMIC = "maxDescriptorSetUniformBuffersDynamic";
    private static final String KEY_MAX_DRAW_INDEXED_INDEX_VALUE = "maxDrawIndexedIndexValue";
    private static final String KEY_MAX_DRAW_INDIRECT_COUNT = "maxDrawIndirectCount";
    private static final String KEY_MAX_FRAGMENT_COMBINED_OUTPUT_RESOURCES = "maxFragmentCombinedOutputResources";
    private static final String KEY_MAX_FRAGMENT_DUAL_SRC_ATTACHMENTS = "maxFragmentDualSrcAttachments";
    private static final String KEY_MAX_FRAGMENT_INPUT_COMPONENTS = "maxFragmentInputComponents";
    private static final String KEY_MAX_FRAGMENT_OUTPUT_ATTACHMENTS = "maxFragmentOutputAttachments";
    private static final String KEY_MAX_FRAMEBUFFER_HEIGHT = "maxFramebufferHeight";
    private static final String KEY_MAX_FRAMEBUFFER_LAYERS = "maxFramebufferLayers";
    private static final String KEY_MAX_FRAMEBUFFER_WIDTH = "maxFramebufferWidth";
    private static final String KEY_MAX_GEOMETRY_INPUT_COMPONENTS = "maxGeometryInputComponents";
    private static final String KEY_MAX_GEOMETRY_OUTPUT_COMPONENTS = "maxGeometryOutputComponents";
    private static final String KEY_MAX_GEOMETRY_OUTPUT_VERTICES = "maxGeometryOutputVertices";
    private static final String KEY_MAX_GEOMETRY_SHADER_INVOCATIONS = "maxGeometryShaderInvocations";
    private static final String KEY_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS = "maxGeometryTotalOutputComponents";
    private static final String KEY_MAX_IMAGE_ARRAY_LAYERS = "maxImageArrayLayers";
    private static final String KEY_MAX_IMAGE_DIMENSION_1D = "maxImageDimension1D";
    private static final String KEY_MAX_IMAGE_DIMENSION_2D = "maxImageDimension2D";
    private static final String KEY_MAX_IMAGE_DIMENSION_3D = "maxImageDimension3D";
    private static final String KEY_MAX_IMAGE_DIMENSION_CUBE = "maxImageDimensionCube";
    private static final String KEY_MAX_INTERPOLATION_OFFSET = "maxInterpolationOffset";
    private static final String KEY_MAX_MEMORY_ALLOCATION_COUNT = "maxMemoryAllocationCount";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_INPUT_ATTACHMENTS = "maxPerStageDescriptorInputAttachments";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES = "maxPerStageDescriptorSampledImages";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS = "maxPerStageDescriptorSamplers";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_BUFFERS = "maxPerStageDescriptorStorageBuffers";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES = "maxPerStageDescriptorStorageImages";
    private static final String KEY_MAX_PER_STAGE_DESCRIPTOR_UNIFORM_BUFFERS = "maxPerStageDescriptorUniformBuffers";
    private static final String KEY_MAX_PER_STAGE_RESOURCES = "maxPerStageResources";
    private static final String KEY_MAX_PUSH_CONSTANTS_SIZE = "maxPushConstantsSize";
    private static final String KEY_MAX_SAMPLE_MASK_WORDS = "maxSampleMaskWords";
    private static final String KEY_MAX_SAMPLER_ALLOCATION_COUNT = "maxSamplerAllocationCount";
    private static final String KEY_MAX_SAMPLER_ANISOTROPY = "maxSamplerAnisotropy";
    private static final String KEY_MAX_SAMPLER_LOD_BIAS = "maxSamplerLodBias";
    private static final String KEY_MAX_STORAGE_BUFFER_RANGE = "maxStorageBufferRange";
    private static final String KEY_MAX_TESSELLATION_CONTROL_PER_PATCH_OUTPUT_COMPONENTS = "maxTessellationControlPerPatchOutputComponents";
    private static final String KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_INPUT_COMPONENTS = "maxTessellationControlPerVertexInputComponents";
    private static final String KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_OUTPUT_COMPONENTS = "maxTessellationControlPerVertexOutputComponents";
    private static final String KEY_MAX_TESSELLATION_CONTROL_TOTAL_OUTPUT_COMPONENTS = "maxTessellationControlTotalOutputComponents";
    private static final String KEY_MAX_TESSELLATION_EVALUATION_INPUT_COMPONENTS = "maxTessellationEvaluationInputComponents";
    private static final String KEY_MAX_TESSELLATION_EVALUATION_OUTPUT_COMPONENTS = "maxTessellationEvaluationOutputComponents";
    private static final String KEY_MAX_TESSELLATION_GENERATION_LEVEL = "maxTessellationGenerationLevel";
    private static final String KEY_MAX_TESSELLATION_PATCH_SIZE = "maxTessellationPatchSize";
    private static final String KEY_MAX_TEXEL_BUFFER_ELEMENTS = "maxTexelBufferElements";
    private static final String KEY_MAX_TEXEL_GATHER_OFFSET = "maxTexelGatherOffset";
    private static final String KEY_MAX_TEXEL_OFFSET = "maxTexelOffset";
    private static final String KEY_MAX_UNIFORM_BUFFER_RANGE = "maxUniformBufferRange";
    private static final String KEY_MAX_VERTEX_INPUT_ATTRIBUTE_OFFSET = "maxVertexInputAttributeOffset";
    private static final String KEY_MAX_VERTEX_INPUT_ATTRIBUTES = "maxVertexInputAttributes";
    private static final String KEY_MAX_VERTEX_INPUT_BINDING_STRIDE = "maxVertexInputBindingStride";
    private static final String KEY_MAX_VERTEX_INPUT_BINDINGS = "maxVertexInputBindings";
    private static final String KEY_MAX_VERTEX_OUTPUT_COMPONENTS = "maxVertexOutputComponents";
    private static final String KEY_MAX_VIEWPORT_DIMENSIONS = "maxViewportDimensions";
    private static final String KEY_MAX_VIEWPORTS = "maxViewports";
    private static final String KEY_MEMORY = "memory";
    private static final String KEY_MEMORY_HEAP_COUNT = "memoryHeapCount";
    private static final String KEY_MEMORY_HEAPS = "memoryHeaps";
    private static final String KEY_MEMORY_TYPE_COUNT = "memoryTypeCount";
    private static final String KEY_MEMORY_TYPES = "memoryTypes";
    private static final String KEY_MIN_IMAGE_TRANSFER_GRANULARITY = "minImageTransferGranularity";
    private static final String KEY_MIN_INTERPOLATION_OFFSET = "minInterpolationOffset";
    private static final String KEY_MIN_MEMORY_MAP_ALIGNMENT = "minMemoryMapAlignment";
    private static final String KEY_MIN_STORAGE_BUFFER_OFFSET_ALIGNMENT = "minStorageBufferOffsetAlignment";
    private static final String KEY_MIN_TEXEL_BUFFER_OFFSET_ALIGNMENT = "minTexelBufferOffsetAlignment";
    private static final String KEY_MIN_TEXEL_GATHER_OFFSET = "minTexelGatherOffset";
    private static final String KEY_MIN_TEXEL_OFFSET = "minTexelOffset";
    private static final String KEY_MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT = "minUniformBufferOffsetAlignment";
    private static final String KEY_MIPMAP_PRECISION_BITS = "mipmapPrecisionBits";
    private static final String KEY_MULTI_DRAW_INDIRECT = "multiDrawIndirect";
    private static final String KEY_MULTI_VIEWPORT = "multiViewport";
    private static final String KEY_NON_COHERENT_ATOM_SIZE = "nonCoherentAtomSize";
    private static final String KEY_OCCLUSION_QUERY_PRECISE = "occlusionQueryPrecise";
    private static final String KEY_OPTIMAL_BUFFER_COPY_OFFSET_ALIGNMENT = "optimalBufferCopyOffsetAlignment";
    private static final String KEY_OPTIMAL_BUFFER_COPY_ROW_PITCH_ALIGNMENT = "optimalBufferCopyRowPitchAlignment";
    private static final String KEY_OPTIMAL_TILING_FEATURES = "optimalTilingFeatures";
    private static final String KEY_PIPELINE_CACHE_UUID = "pipelineCacheUUID";
    private static final String KEY_PIPELINE_STATISTICS_QUERY = "pipelineStatisticsQuery";
    private static final String KEY_POINT_SIZE_GRANULARITY = "pointSizeGranularity";
    private static final String KEY_POINT_SIZE_RANGE = "pointSizeRange";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_PROPERTY_FLAGS = "propertyFlags";
    private static final String KEY_QUEUE_COUNT = "queueCount";
    private static final String KEY_QUEUE_FLAGS = "queueFlags";
    private static final String KEY_QUEUES = "queues";
    private static final String KEY_RESIDENCY_ALIGNED_MIP_SIZE = "residencyAlignedMipSize";
    private static final String KEY_RESIDENCY_NON_RESIDENT_STRICT = "residencyNonResidentStrict";
    private static final String KEY_RESIDENCY_STANDARD_2D_BLOCK_SHAPE = "residencyStandard2DBlockShape";
    private static final String KEY_RESIDENCY_STANDARD_2D_MULTISAMPLE_BLOCK_SHAPE = "residencyStandard2DMultisampleBlockShape";
    private static final String KEY_RESIDENCY_STANDARD_3D_BLOCK_SHAPE = "residencyStandard3DBlockShape";
    private static final String KEY_ROBUST_BUFFER_ACCESS = "robustBufferAccess";
    private static final String KEY_SAMPLE_RATE_SHADING = "sampleRateShading";
    private static final String KEY_SAMPLED_IMAGE_COLOR_SAMPLE_COUNTS = "sampledImageColorSampleCounts";
    private static final String KEY_SAMPLED_IMAGE_DEPTH_SAMPLE_COUNTS = "sampledImageDepthSampleCounts";
    private static final String KEY_SAMPLED_IMAGE_INTEGER_SAMPLE_COUNTS = "sampledImageIntegerSampleCounts";
    private static final String KEY_SAMPLED_IMAGE_STENCIL_SAMPLE_COUNTS = "sampledImageStencilSampleCounts";
    private static final String KEY_SAMPLER_ANISOTROPY = "samplerAnisotropy";
    private static final String KEY_SHADER_CLIP_DISTANCE = "shaderClipDistance";
    private static final String KEY_SHADER_CULL_DISTANCE = "shaderCullDistance";
    private static final String KEY_SHADER_FLOAT64 = "shaderFloat64";
    private static final String KEY_SHADER_IMAGE_GATHER_EXTENDED = "shaderImageGatherExtended";
    private static final String KEY_SHADER_INT16 = "shaderInt16";
    private static final String KEY_SHADER_INT64 = "shaderInt64";
    private static final String KEY_SHADER_RESOURCE_MIN_LOD = "shaderResourceMinLod";
    private static final String KEY_SHADER_RESOURCE_RESIDENCY = "shaderResourceResidency";
    private static final String KEY_SHADER_SAMPLED_IMAGE_ARRAY_DYNAMIC_INDEXING = "shaderSampledImageArrayDynamicIndexing";
    private static final String KEY_SHADER_STORAGE_BUFFER_ARRAY_DYNAMIC_INDEXING = "shaderStorageBufferArrayDynamicIndexing";
    private static final String KEY_SHADER_STORAGE_IMAGE_ARRAY_DYNAMIC_INDEXING = "shaderStorageImageArrayDynamicIndexing";
    private static final String KEY_SHADER_STORAGE_IMAGE_EXTENDED_FORMATS = "shaderStorageImageExtendedFormats";
    private static final String KEY_SHADER_STORAGE_IMAGE_MULTISAMPLE = "shaderStorageImageMultisample";
    private static final String KEY_SHADER_STORAGE_IMAGE_READ_WITHOUT_FORMAT = "shaderStorageImageReadWithoutFormat";
    private static final String KEY_SHADER_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT = "shaderStorageImageWriteWithoutFormat";
    private static final String KEY_SHADER_TESSELLATION_AND_GEOMETRY_POINT_SIZE = "shaderTessellationAndGeometryPointSize";
    private static final String KEY_SHADER_UNIFORM_BUFFER_ARRAY_DYNAMIC_INDEXING = "shaderUniformBufferArrayDynamicIndexing";
    private static final String KEY_SIZE = "size";
    private static final String KEY_SPARSE_ADDRESS_SPACE_SIZE = "sparseAddressSpaceSize";
    private static final String KEY_SPARSE_BINDING = "sparseBinding";
    private static final String KEY_SPARSE_PROPERTIES = "sparseProperties";
    private static final String KEY_SPARSE_RESIDENCY_16_SAMPLES = "sparseResidency16Samples";
    private static final String KEY_SPARSE_RESIDENCY_2_SAMPLES = "sparseResidency2Samples";
    private static final String KEY_SPARSE_RESIDENCY_4_SAMPLES = "sparseResidency4Samples";
    private static final String KEY_SPARSE_RESIDENCY_8_SAMPLES = "sparseResidency8Samples";
    private static final String KEY_SPARSE_RESIDENCY_ALIASED = "sparseResidencyAliased";
    private static final String KEY_SPARSE_RESIDENCY_BUFFER = "sparseResidencyBuffer";
    private static final String KEY_SPARSE_RESIDENCY_IMAGE_2D = "sparseResidencyImage2D";
    private static final String KEY_SPARSE_RESIDENCY_IMAGE_3D = "sparseResidencyImage3D";
    private static final String KEY_SPEC_VERSION = "specVersion";
    private static final String KEY_STANDARD_SAMPLE_LOCATIONS = "standardSampleLocations";
    private static final String KEY_STORAGE_IMAGE_SAMPLE_COUNTS = "storageImageSampleCounts";
    private static final String KEY_STRICT_LINES = "strictLines";
    private static final String KEY_SUB_PIXEL_INTERPOLATION_OFFSET_BITS = "subPixelInterpolationOffsetBits";
    private static final String KEY_SUB_PIXEL_PRECISION_BITS = "subPixelPrecisionBits";
    private static final String KEY_SUB_TEXEL_PRECISION_BITS = "subTexelPrecisionBits";
    private static final String KEY_TESSELLATION_SHADER = "tessellationShader";
    private static final String KEY_TEXTURE_COMPRESSION_ASTC_LDR = "textureCompressionASTC_LDR";
    private static final String KEY_TEXTURE_COMPRESSION_BC = "textureCompressionBC";
    private static final String KEY_TEXTURE_COMPRESSION_ETC2 = "textureCompressionETC2";
    private static final String KEY_TIMESTAMP_COMPUTE_AND_GRAPHICS = "timestampComputeAndGraphics";
    private static final String KEY_TIMESTAMP_PERIOD = "timestampPeriod";
    private static final String KEY_TIMESTAMP_VALID_BITS = "timestampValidBits";
    private static final String KEY_VARIABLE_MULTISAMPLE_RATE = "variableMultisampleRate";
    private static final String KEY_VENDOR_ID = "vendorID";
    private static final String KEY_VERTEX_PIPELINE_STORES_AND_ATOMICS = "vertexPipelineStoresAndAtomics";
    private static final String KEY_VIEWPORT_BOUNDS_RANGE = "viewportBoundsRange";
    private static final String KEY_VIEWPORT_SUB_PIXEL_BITS = "viewportSubPixelBits";
    private static final String KEY_WIDE_LINES = "wideLines";
    private static final String KEY_WIDTH = "width";

    static {
        System.loadLibrary("ctsdeviceinfo");
    }

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        try {
            JSONObject instance = new JSONObject(nativeGetVkJSON());
            emitLayers(store, instance);
            emitExtensions(store, instance);
            emitDevices(store, instance);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static void emitDevices(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray devices = parent.getJSONArray(KEY_DEVICES);
        store.startArray(convertName(KEY_DEVICES));
        for (int deviceIdx = 0; deviceIdx < devices.length(); deviceIdx++) {
            JSONObject device = devices.getJSONObject(deviceIdx);
            store.startGroup();
            {
                JSONObject properties = device.getJSONObject(KEY_PROPERTIES);
                store.startGroup(convertName(KEY_PROPERTIES));
                {
                    emitLong(store, properties, KEY_API_VERSION);
                    emitLong(store, properties, KEY_DRIVER_VERSION);
                    emitLong(store, properties, KEY_VENDOR_ID);
                    emitLong(store, properties, KEY_DEVICE_ID);
                    emitLong(store, properties, KEY_DEVICE_TYPE);
                    emitString(store, properties, KEY_DEVICE_NAME);
                    emitLongArray(store, properties, KEY_PIPELINE_CACHE_UUID);

                    JSONObject limits = properties.getJSONObject(KEY_LIMITS);
                    store.startGroup(convertName(KEY_LIMITS));
                    {
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_1D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_2D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_3D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_CUBE);
                        emitLong(store, limits, KEY_MAX_IMAGE_ARRAY_LAYERS);
                        emitLong(store, limits, KEY_MAX_TEXEL_BUFFER_ELEMENTS);
                        emitLong(store, limits, KEY_MAX_UNIFORM_BUFFER_RANGE);
                        emitLong(store, limits, KEY_MAX_STORAGE_BUFFER_RANGE);
                        emitLong(store, limits, KEY_MAX_PUSH_CONSTANTS_SIZE);
                        emitLong(store, limits, KEY_MAX_MEMORY_ALLOCATION_COUNT);
                        emitLong(store, limits, KEY_MAX_SAMPLER_ALLOCATION_COUNT);
                        emitString(store, limits, KEY_BUFFER_IMAGE_GRANULARITY);
                        emitString(store, limits, KEY_SPARSE_ADDRESS_SPACE_SIZE);
                        emitLong(store, limits, KEY_MAX_BOUND_DESCRIPTOR_SETS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_UNIFORM_BUFFERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_BUFFERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_INPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_RESOURCES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_SAMPLERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS_DYNAMIC);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS_DYNAMIC);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_SAMPLED_IMAGES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_IMAGES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_INPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_ATTRIBUTES);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_BINDINGS);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_ATTRIBUTE_OFFSET);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_BINDING_STRIDE);
                        emitLong(store, limits, KEY_MAX_VERTEX_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_GENERATION_LEVEL);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_PATCH_SIZE);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_PATCH_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_TOTAL_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_EVALUATION_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_EVALUATION_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_SHADER_INVOCATIONS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_OUTPUT_VERTICES);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_OUTPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_DUAL_SRC_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_COMBINED_OUTPUT_RESOURCES);
                        emitLong(store, limits, KEY_MAX_COMPUTE_SHARED_MEMORY_SIZE);
                        emitLongArray(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_COUNT);
                        emitLong(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
                        emitLongArray(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_SIZE);
                        emitLong(store, limits, KEY_SUB_PIXEL_PRECISION_BITS);
                        emitLong(store, limits, KEY_SUB_TEXEL_PRECISION_BITS);
                        emitLong(store, limits, KEY_MIPMAP_PRECISION_BITS);
                        emitLong(store, limits, KEY_MAX_DRAW_INDEXED_INDEX_VALUE);
                        emitLong(store, limits, KEY_MAX_DRAW_INDIRECT_COUNT);
                        emitDouble(store, limits, KEY_MAX_SAMPLER_LOD_BIAS);
                        emitDouble(store, limits, KEY_MAX_SAMPLER_ANISOTROPY);
                        emitLong(store, limits, KEY_MAX_VIEWPORTS);
                        emitLongArray(store, limits, KEY_MAX_VIEWPORT_DIMENSIONS);
                        emitDoubleArray(store, limits, KEY_VIEWPORT_BOUNDS_RANGE);
                        emitLong(store, limits, KEY_VIEWPORT_SUB_PIXEL_BITS);
                        emitString(store, limits, KEY_MIN_MEMORY_MAP_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_TEXEL_BUFFER_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_STORAGE_BUFFER_OFFSET_ALIGNMENT);
                        emitLong(store, limits, KEY_MIN_TEXEL_OFFSET);
                        emitLong(store, limits, KEY_MAX_TEXEL_OFFSET);
                        emitLong(store, limits, KEY_MIN_TEXEL_GATHER_OFFSET);
                        emitLong(store, limits, KEY_MAX_TEXEL_GATHER_OFFSET);
                        emitDouble(store, limits, KEY_MIN_INTERPOLATION_OFFSET);
                        emitDouble(store, limits, KEY_MAX_INTERPOLATION_OFFSET);
                        emitLong(store, limits, KEY_SUB_PIXEL_INTERPOLATION_OFFSET_BITS);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_WIDTH);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_HEIGHT);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_LAYERS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_COLOR_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_DEPTH_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_STENCIL_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_NO_ATTACHMENTS_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_MAX_COLOR_ATTACHMENTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_COLOR_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_INTEGER_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_DEPTH_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_STENCIL_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_STORAGE_IMAGE_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_MAX_SAMPLE_MASK_WORDS);
                        emitBoolean(store, limits, KEY_TIMESTAMP_COMPUTE_AND_GRAPHICS);
                        emitDouble(store, limits, KEY_TIMESTAMP_PERIOD);
                        emitLong(store, limits, KEY_MAX_CLIP_DISTANCES);
                        emitLong(store, limits, KEY_MAX_CULL_DISTANCES);
                        emitLong(store, limits, KEY_MAX_COMBINED_CLIP_AND_CULL_DISTANCES);
                        emitLong(store, limits, KEY_DISCRETE_QUEUE_PRIORITIES);
                        emitDoubleArray(store, limits, KEY_POINT_SIZE_RANGE);
                        emitDoubleArray(store, limits, KEY_LINE_WIDTH_RANGE);
                        emitDouble(store, limits, KEY_POINT_SIZE_GRANULARITY);
                        emitDouble(store, limits, KEY_LINE_WIDTH_GRANULARITY);
                        emitBoolean(store, limits, KEY_STRICT_LINES);
                        emitBoolean(store, limits, KEY_STANDARD_SAMPLE_LOCATIONS);
                        emitString(store, limits, KEY_OPTIMAL_BUFFER_COPY_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_OPTIMAL_BUFFER_COPY_ROW_PITCH_ALIGNMENT);
                        emitString(store, limits, KEY_NON_COHERENT_ATOM_SIZE);
                    }
                    store.endGroup();

                    JSONObject sparse = properties.getJSONObject(KEY_SPARSE_PROPERTIES);
                    store.startGroup(convertName(KEY_SPARSE_PROPERTIES));
                    {
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_2D_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_2D_MULTISAMPLE_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_3D_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_ALIGNED_MIP_SIZE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_NON_RESIDENT_STRICT);
                    }
                    store.endGroup();
                }
                store.endGroup();

                JSONObject features = device.getJSONObject(KEY_FEATURES);
                store.startGroup(convertName(KEY_FEATURES));
                {
                    emitBoolean(store, features, KEY_ROBUST_BUFFER_ACCESS);
                    emitBoolean(store, features, KEY_FULL_DRAW_INDEX_UINT32);
                    emitBoolean(store, features, KEY_IMAGE_CUBE_ARRAY);
                    emitBoolean(store, features, KEY_INDEPENDENT_BLEND);
                    emitBoolean(store, features, KEY_GEOMETRY_SHADER);
                    emitBoolean(store, features, KEY_TESSELLATION_SHADER);
                    emitBoolean(store, features, KEY_SAMPLE_RATE_SHADING);
                    emitBoolean(store, features, KEY_DUAL_SRC_BLEND);
                    emitBoolean(store, features, KEY_LOGIC_OP);
                    emitBoolean(store, features, KEY_MULTI_DRAW_INDIRECT);
                    emitBoolean(store, features, KEY_DRAW_INDIRECT_FIRST_INSTANCE);
                    emitBoolean(store, features, KEY_DEPTH_CLAMP);
                    emitBoolean(store, features, KEY_DEPTH_BIAS_CLAMP);
                    emitBoolean(store, features, KEY_FILL_MODE_NON_SOLID);
                    emitBoolean(store, features, KEY_DEPTH_BOUNDS);
                    emitBoolean(store, features, KEY_WIDE_LINES);
                    emitBoolean(store, features, KEY_LARGE_POINTS);
                    emitBoolean(store, features, KEY_ALPHA_TO_ONE);
                    emitBoolean(store, features, KEY_MULTI_VIEWPORT);
                    emitBoolean(store, features, KEY_SAMPLER_ANISOTROPY);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_ETC2);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_ASTC_LDR);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_BC);
                    emitBoolean(store, features, KEY_OCCLUSION_QUERY_PRECISE);
                    emitBoolean(store, features, KEY_PIPELINE_STATISTICS_QUERY);
                    emitBoolean(store, features, KEY_VERTEX_PIPELINE_STORES_AND_ATOMICS);
                    emitBoolean(store, features, KEY_FRAGMENT_STORES_AND_ATOMICS);
                    emitBoolean(store, features, KEY_SHADER_TESSELLATION_AND_GEOMETRY_POINT_SIZE);
                    emitBoolean(store, features, KEY_SHADER_IMAGE_GATHER_EXTENDED);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_EXTENDED_FORMATS);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_MULTISAMPLE);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_READ_WITHOUT_FORMAT);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT);
                    emitBoolean(store, features, KEY_SHADER_UNIFORM_BUFFER_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_SAMPLED_IMAGE_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_BUFFER_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_CLIP_DISTANCE);
                    emitBoolean(store, features, KEY_SHADER_CULL_DISTANCE);
                    emitBoolean(store, features, KEY_SHADER_FLOAT64);
                    emitBoolean(store, features, KEY_SHADER_INT64);
                    emitBoolean(store, features, KEY_SHADER_INT16);
                    emitBoolean(store, features, KEY_SHADER_RESOURCE_RESIDENCY);
                    emitBoolean(store, features, KEY_SHADER_RESOURCE_MIN_LOD);
                    emitBoolean(store, features, KEY_SPARSE_BINDING);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_BUFFER);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_IMAGE_2D);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_IMAGE_3D);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_2_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_4_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_8_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_16_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_ALIASED);
                    emitBoolean(store, features, KEY_VARIABLE_MULTISAMPLE_RATE);
                    emitBoolean(store, features, KEY_INHERITED_QUERIES);
                }
                store.endGroup();

                JSONObject memory = device.getJSONObject(KEY_MEMORY);
                store.startGroup(convertName(KEY_MEMORY));
                {
                    emitLong(store, memory, KEY_MEMORY_TYPE_COUNT);
                    JSONArray memoryTypes = memory.getJSONArray(KEY_MEMORY_TYPES);
                    store.startArray(convertName(KEY_MEMORY_TYPES));
                    for (int memoryTypeIdx = 0; memoryTypeIdx < memoryTypes.length();
                            memoryTypeIdx++) {
                        JSONObject memoryType = memoryTypes.getJSONObject(memoryTypeIdx);
                        store.startGroup();
                        {
                            emitLong(store, memoryType, KEY_PROPERTY_FLAGS);
                            emitLong(store, memoryType, KEY_HEAP_INDEX);
                        }
                        store.endGroup();
                    }
                    store.endArray();

                    emitLong(store, memory, KEY_MEMORY_HEAP_COUNT);
                    JSONArray memoryHeaps = memory.getJSONArray(KEY_MEMORY_HEAPS);
                    store.startArray(convertName(KEY_MEMORY_HEAPS));
                    for (int memoryHeapIdx = 0; memoryHeapIdx < memoryHeaps.length();
                            memoryHeapIdx++) {
                        JSONObject memoryHeap = memoryHeaps.getJSONObject(memoryHeapIdx);
                        store.startGroup();
                        {
                            emitString(store, memoryHeap, KEY_SIZE);
                            emitLong(store, memoryHeap, KEY_FLAGS);
                        }
                        store.endGroup();
                    }
                    store.endArray();
                }
                store.endGroup();

                JSONArray queues = device.getJSONArray(KEY_QUEUES);
                store.startArray(convertName(KEY_QUEUES));
                for (int queueIdx = 0; queueIdx < queues.length(); queueIdx++) {
                    JSONObject queue = queues.getJSONObject(queueIdx);
                    store.startGroup();
                    {
                        emitLong(store, queue, KEY_QUEUE_FLAGS);
                        emitLong(store, queue, KEY_QUEUE_COUNT);
                        emitLong(store, queue, KEY_TIMESTAMP_VALID_BITS);
                        JSONObject extent = queue.getJSONObject(KEY_MIN_IMAGE_TRANSFER_GRANULARITY);
                        store.startGroup(convertName(KEY_MIN_IMAGE_TRANSFER_GRANULARITY));
                        {
                            emitLong(store, extent, KEY_WIDTH);
                            emitLong(store, extent, KEY_HEIGHT);
                            emitLong(store, extent, KEY_DEPTH);
                        }
                        store.endGroup();
                    }
                    store.endGroup();
                }
                store.endArray();

                // Skip layers for now. VkJSON doesn't yet include device layer extensions, so
                // this is entirely redundant with the instance extension information.
                // emitLayers(store, device);
                store.startArray(convertName(KEY_LAYERS));
                store.endArray();

                emitExtensions(store, device);

                JSONArray formats = device.getJSONArray(KEY_FORMATS);
                // Note: Earlier code used field named 'formats' with different data structure.
                // In order to have the mix of old and new data, we cannot reuse that name.
                store.startArray("supported_formats");
                for (int formatIdx = 0; formatIdx < formats.length(); formatIdx++) {
                    JSONArray formatPair = formats.getJSONArray(formatIdx);
                    JSONObject formatProperties = formatPair.getJSONObject(1);
                    store.startGroup();
                    {
                        store.addResult("format", (long)formatPair.getInt(0));
                        emitLong(store, formatProperties, KEY_LINEAR_TILING_FEATURES);
                        emitLong(store, formatProperties, KEY_OPTIMAL_TILING_FEATURES);
                        emitLong(store, formatProperties, KEY_BUFFER_FEATURES);
                    }
                    store.endGroup();
                }
                store.endArray();
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitLayers(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray layers = parent.getJSONArray(KEY_LAYERS);
        store.startArray(convertName(KEY_LAYERS));
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            store.startGroup();
            {
                JSONObject properties = layer.getJSONObject(KEY_PROPERTIES);
                store.startGroup(convertName(KEY_PROPERTIES));
                {
                    emitString(store, properties, KEY_LAYER_NAME);
                    emitLong(store, properties, KEY_SPEC_VERSION);
                    emitLong(store, properties, KEY_IMPLEMENTATION_VERSION);
                    emitString(store, properties, KEY_DESCRIPTION);
                }
                store.endGroup();
                emitExtensions(store, layer);
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitExtensions(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray extensions = parent.getJSONArray(KEY_EXTENSIONS);
        store.startArray(convertName(KEY_EXTENSIONS));
        for (int i = 0; i < extensions.length(); i++) {
            JSONObject extension = extensions.getJSONObject(i);
            store.startGroup();
            {
                emitString(store, extension, KEY_EXTENSION_NAME);
                emitLong(store, extension, KEY_SPEC_VERSION);
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitBoolean(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(convertName(name), parent.getInt(name) != 0 ? true : false);
    }

    private static void emitLong(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(convertName(name), parent.getLong(name));
    }

    private static void emitDouble(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(convertName(name), parent.getDouble(name));
    }

    private static void emitString(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(convertName(name), parent.getString(name));
    }

    private static void emitLongArray(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        JSONArray jsonArray = parent.getJSONArray(name);
        long[] array = new long[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            array[i] = jsonArray.getLong(i);
        }
        store.addArrayResult(convertName(name), array);
    }

    private static void emitDoubleArray(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        JSONArray jsonArray = parent.getJSONArray(name);
        double[] array = new double[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            array[i] = jsonArray.getDouble(i);
        }
        store.addArrayResult(convertName(name), array);
    }

    private static String convertName(String name) {
        // This translation could be done algorithmically, but in this case being able to
        // code-search for both the original and converted names is more important.
        switch (name) {
            case KEY_ALPHA_TO_ONE: return "alpha_to_one";
            case KEY_API_VERSION: return "api_version";
            case KEY_BUFFER_FEATURES: return "buffer_features";
            case KEY_BUFFER_IMAGE_GRANULARITY: return "buffer_image_granularity";
            case KEY_DEPTH: return "depth";
            case KEY_DEPTH_BIAS_CLAMP: return "depth_bias_clamp";
            case KEY_DEPTH_BOUNDS: return "depth_bounds";
            case KEY_DEPTH_CLAMP: return "depth_clamp";
            case KEY_DESCRIPTION: return "description";
            case KEY_DEVICE_ID: return "device_id";
            case KEY_DEVICE_NAME: return "device_name";
            case KEY_DEVICE_TYPE: return "device_type";
            case KEY_DEVICES: return "devices";
            case KEY_DISCRETE_QUEUE_PRIORITIES: return "discrete_queue_priorities";
            case KEY_DRAW_INDIRECT_FIRST_INSTANCE: return "draw_indirect_first_instance";
            case KEY_DRIVER_VERSION: return "driver_version";
            case KEY_DUAL_SRC_BLEND: return "dual_src_blend";
            case KEY_EXTENSION_NAME: return "extension_name";
            case KEY_EXTENSIONS: return "extensions";
            case KEY_FEATURES: return "features";
            case KEY_FILL_MODE_NON_SOLID: return "fill_mode_non_solid";
            case KEY_FLAGS: return "flags";
            case KEY_FORMATS: return "formats";
            case KEY_FRAGMENT_STORES_AND_ATOMICS: return "fragment_stores_and_atomics";
            case KEY_FRAMEBUFFER_COLOR_SAMPLE_COUNTS: return "framebuffer_color_sample_counts";
            case KEY_FRAMEBUFFER_DEPTH_SAMPLE_COUNTS: return "framebuffer_depth_sample_counts";
            case KEY_FRAMEBUFFER_NO_ATTACHMENTS_SAMPLE_COUNTS: return "framebuffer_no_attachments_sample_counts";
            case KEY_FRAMEBUFFER_STENCIL_SAMPLE_COUNTS: return "framebuffer_stencil_sample_counts";
            case KEY_FULL_DRAW_INDEX_UINT32: return "full_draw_index_uint32";
            case KEY_GEOMETRY_SHADER: return "geometry_shader";
            case KEY_HEAP_INDEX: return "heap_index";
            case KEY_HEIGHT: return "height";
            case KEY_IMAGE_CUBE_ARRAY: return "image_cube_array";
            case KEY_IMPLEMENTATION_VERSION: return "implementation_version";
            case KEY_INDEPENDENT_BLEND: return "independent_blend";
            case KEY_INHERITED_QUERIES: return "inherited_queries";
            case KEY_LARGE_POINTS: return "large_points";
            case KEY_LAYER_NAME: return "layer_name";
            case KEY_LAYERS: return "layers";
            case KEY_LIMITS: return "limits";
            case KEY_LINE_WIDTH_GRANULARITY: return "line_width_granularity";
            case KEY_LINE_WIDTH_RANGE: return "line_width_range";
            case KEY_LINEAR_TILING_FEATURES: return "linear_tiling_features";
            case KEY_LOGIC_OP: return "logic_op";
            case KEY_MAX_BOUND_DESCRIPTOR_SETS: return "max_bound_descriptor_sets";
            case KEY_MAX_CLIP_DISTANCES: return "max_clip_distances";
            case KEY_MAX_COLOR_ATTACHMENTS: return "max_color_attachments";
            case KEY_MAX_COMBINED_CLIP_AND_CULL_DISTANCES: return "max_combined_clip_and_cull_distances";
            case KEY_MAX_COMPUTE_SHARED_MEMORY_SIZE: return "max_compute_shared_memory_size";
            case KEY_MAX_COMPUTE_WORK_GROUP_COUNT: return "max_compute_work_group_count";
            case KEY_MAX_COMPUTE_WORK_GROUP_INVOCATIONS: return "max_compute_work_group_invocations";
            case KEY_MAX_COMPUTE_WORK_GROUP_SIZE: return "max_compute_work_group_size";
            case KEY_MAX_CULL_DISTANCES: return "max_cull_distances";
            case KEY_MAX_DESCRIPTOR_SET_INPUT_ATTACHMENTS: return "max_descriptor_set_input_attachments";
            case KEY_MAX_DESCRIPTOR_SET_SAMPLED_IMAGES: return "max_descriptor_set_sampled_images";
            case KEY_MAX_DESCRIPTOR_SET_SAMPLERS: return "max_descriptor_set_samplers";
            case KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS: return "max_descriptor_set_storage_buffers";
            case KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS_DYNAMIC: return "max_descriptor_set_storage_buffers_dynamic";
            case KEY_MAX_DESCRIPTOR_SET_STORAGE_IMAGES: return "max_descriptor_set_storage_images";
            case KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS: return "max_descriptor_set_uniform_buffers";
            case KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS_DYNAMIC: return "max_descriptor_set_uniform_buffers_dynamic";
            case KEY_MAX_DRAW_INDEXED_INDEX_VALUE: return "max_draw_indexed_index_value";
            case KEY_MAX_DRAW_INDIRECT_COUNT: return "max_draw_indirect_count";
            case KEY_MAX_FRAGMENT_COMBINED_OUTPUT_RESOURCES: return "max_fragment_combined_output_resources";
            case KEY_MAX_FRAGMENT_DUAL_SRC_ATTACHMENTS: return "max_fragment_dual_src_attachments";
            case KEY_MAX_FRAGMENT_INPUT_COMPONENTS: return "max_fragment_input_components";
            case KEY_MAX_FRAGMENT_OUTPUT_ATTACHMENTS: return "max_fragment_output_attachments";
            case KEY_MAX_FRAMEBUFFER_HEIGHT: return "max_framebuffer_height";
            case KEY_MAX_FRAMEBUFFER_LAYERS: return "max_framebuffer_layers";
            case KEY_MAX_FRAMEBUFFER_WIDTH: return "max_framebuffer_width";
            case KEY_MAX_GEOMETRY_INPUT_COMPONENTS: return "max_geometry_input_components";
            case KEY_MAX_GEOMETRY_OUTPUT_COMPONENTS: return "max_geometry_output_components";
            case KEY_MAX_GEOMETRY_OUTPUT_VERTICES: return "max_geometry_output_vertices";
            case KEY_MAX_GEOMETRY_SHADER_INVOCATIONS: return "max_geometry_shader_invocations";
            case KEY_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS: return "max_geometry_total_output_components";
            case KEY_MAX_IMAGE_ARRAY_LAYERS: return "max_image_array_layers";
            case KEY_MAX_IMAGE_DIMENSION_1D: return "max_image_dimension_1d";
            case KEY_MAX_IMAGE_DIMENSION_2D: return "max_image_dimension_2d";
            case KEY_MAX_IMAGE_DIMENSION_3D: return "max_image_dimension_3d";
            case KEY_MAX_IMAGE_DIMENSION_CUBE: return "max_image_dimension_cube";
            case KEY_MAX_INTERPOLATION_OFFSET: return "max_interpolation_offset";
            case KEY_MAX_MEMORY_ALLOCATION_COUNT: return "max_memory_allocation_count";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_INPUT_ATTACHMENTS: return "max_per_stage_descriptor_input_attachments";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES: return "max_per_stage_descriptor_sampled_images";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS: return "max_per_stage_descriptor_samplers";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_BUFFERS: return "max_per_stage_descriptor_storage_buffers";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES: return "max_per_stage_descriptor_storage_images";
            case KEY_MAX_PER_STAGE_DESCRIPTOR_UNIFORM_BUFFERS: return "max_per_stage_descriptor_uniform_buffers";
            case KEY_MAX_PER_STAGE_RESOURCES: return "max_per_stage_resources";
            case KEY_MAX_PUSH_CONSTANTS_SIZE: return "max_push_constants_size";
            case KEY_MAX_SAMPLE_MASK_WORDS: return "max_sample_mask_words";
            case KEY_MAX_SAMPLER_ALLOCATION_COUNT: return "max_sampler_allocation_count";
            case KEY_MAX_SAMPLER_ANISOTROPY: return "max_sampler_anisotropy";
            case KEY_MAX_SAMPLER_LOD_BIAS: return "max_sampler_lod_bias";
            case KEY_MAX_STORAGE_BUFFER_RANGE: return "max_storage_buffer_range";
            case KEY_MAX_TESSELLATION_CONTROL_PER_PATCH_OUTPUT_COMPONENTS: return "max_tessellation_control_per_patch_output_components";
            case KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_INPUT_COMPONENTS: return "max_tessellation_control_per_vertex_input_components";
            case KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_OUTPUT_COMPONENTS: return "max_tessellation_control_per_vertex_output_components";
            case KEY_MAX_TESSELLATION_CONTROL_TOTAL_OUTPUT_COMPONENTS: return "max_tessellation_control_total_output_components";
            case KEY_MAX_TESSELLATION_EVALUATION_INPUT_COMPONENTS: return "max_tessellation_evaluation_input_components";
            case KEY_MAX_TESSELLATION_EVALUATION_OUTPUT_COMPONENTS: return "max_tessellation_evaluation_output_components";
            case KEY_MAX_TESSELLATION_GENERATION_LEVEL: return "max_tessellation_generation_level";
            case KEY_MAX_TESSELLATION_PATCH_SIZE: return "max_tessellation_patch_size";
            case KEY_MAX_TEXEL_BUFFER_ELEMENTS: return "max_texel_buffer_elements";
            case KEY_MAX_TEXEL_GATHER_OFFSET: return "max_texel_gather_offset";
            case KEY_MAX_TEXEL_OFFSET: return "max_texel_offset";
            case KEY_MAX_UNIFORM_BUFFER_RANGE: return "max_uniform_buffer_range";
            case KEY_MAX_VERTEX_INPUT_ATTRIBUTE_OFFSET: return "max_vertex_input_attribute_offset";
            case KEY_MAX_VERTEX_INPUT_ATTRIBUTES: return "max_vertex_input_attributes";
            case KEY_MAX_VERTEX_INPUT_BINDING_STRIDE: return "max_vertex_input_binding_stride";
            case KEY_MAX_VERTEX_INPUT_BINDINGS: return "max_vertex_input_bindings";
            case KEY_MAX_VERTEX_OUTPUT_COMPONENTS: return "max_vertex_output_components";
            case KEY_MAX_VIEWPORT_DIMENSIONS: return "max_viewport_dimensions";
            case KEY_MAX_VIEWPORTS: return "max_viewports";
            case KEY_MEMORY: return "memory";
            case KEY_MEMORY_HEAP_COUNT: return "memory_heap_count";
            case KEY_MEMORY_HEAPS: return "memory_heaps";
            case KEY_MEMORY_TYPE_COUNT: return "memory_type_count";
            case KEY_MEMORY_TYPES: return "memory_types";
            case KEY_MIN_IMAGE_TRANSFER_GRANULARITY: return "min_image_transfer_granularity";
            case KEY_MIN_INTERPOLATION_OFFSET: return "min_interpolation_offset";
            case KEY_MIN_MEMORY_MAP_ALIGNMENT: return "min_memory_map_alignment";
            case KEY_MIN_STORAGE_BUFFER_OFFSET_ALIGNMENT: return "min_storage_buffer_offset_alignment";
            case KEY_MIN_TEXEL_BUFFER_OFFSET_ALIGNMENT: return "min_texel_buffer_offset_alignment";
            case KEY_MIN_TEXEL_GATHER_OFFSET: return "min_texel_gather_offset";
            case KEY_MIN_TEXEL_OFFSET: return "min_texel_offset";
            case KEY_MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT: return "min_uniform_buffer_offset_alignment";
            case KEY_MIPMAP_PRECISION_BITS: return "mipmap_precision_bits";
            case KEY_MULTI_DRAW_INDIRECT: return "multi_draw_indirect";
            case KEY_MULTI_VIEWPORT: return "multi_viewport";
            case KEY_NON_COHERENT_ATOM_SIZE: return "non_coherent_atom_size";
            case KEY_OCCLUSION_QUERY_PRECISE: return "occlusion_query_precise";
            case KEY_OPTIMAL_BUFFER_COPY_OFFSET_ALIGNMENT: return "optimal_buffer_copy_offset_alignment";
            case KEY_OPTIMAL_BUFFER_COPY_ROW_PITCH_ALIGNMENT: return "optimal_buffer_copy_row_pitch_alignment";
            case KEY_OPTIMAL_TILING_FEATURES: return "optimal_tiling_features";
            case KEY_PIPELINE_CACHE_UUID: return "pipeline_cache_uuid";
            case KEY_PIPELINE_STATISTICS_QUERY: return "pipeline_statistics_query";
            case KEY_POINT_SIZE_GRANULARITY: return "point_size_granularity";
            case KEY_POINT_SIZE_RANGE: return "point_size_range";
            case KEY_PROPERTIES: return "properties";
            case KEY_PROPERTY_FLAGS: return "property_flags";
            case KEY_QUEUE_COUNT: return "queue_count";
            case KEY_QUEUE_FLAGS: return "queue_flags";
            case KEY_QUEUES: return "queues";
            case KEY_RESIDENCY_ALIGNED_MIP_SIZE: return "residency_aligned_mip_size";
            case KEY_RESIDENCY_NON_RESIDENT_STRICT: return "residency_non_resident_strict";
            case KEY_RESIDENCY_STANDARD_2D_BLOCK_SHAPE: return "residency_standard_2d_block_shape";
            case KEY_RESIDENCY_STANDARD_2D_MULTISAMPLE_BLOCK_SHAPE: return "residency_standard_2d_multisample_block_shape";
            case KEY_RESIDENCY_STANDARD_3D_BLOCK_SHAPE: return "residency_standard_3d_block_shape";
            case KEY_ROBUST_BUFFER_ACCESS: return "robust_buffer_access";
            case KEY_SAMPLE_RATE_SHADING: return "sample_rate_shading";
            case KEY_SAMPLED_IMAGE_COLOR_SAMPLE_COUNTS: return "sampled_image_color_sample_counts";
            case KEY_SAMPLED_IMAGE_DEPTH_SAMPLE_COUNTS: return "sampled_image_depth_sample_counts";
            case KEY_SAMPLED_IMAGE_INTEGER_SAMPLE_COUNTS: return "sampled_image_integer_sample_counts";
            case KEY_SAMPLED_IMAGE_STENCIL_SAMPLE_COUNTS: return "sampled_image_stencil_sample_counts";
            case KEY_SAMPLER_ANISOTROPY: return "sampler_anisotropy";
            case KEY_SHADER_CLIP_DISTANCE: return "shader_clip_distance";
            case KEY_SHADER_CULL_DISTANCE: return "shader_cull_distance";
            case KEY_SHADER_FLOAT64: return "shader_float64";
            case KEY_SHADER_IMAGE_GATHER_EXTENDED: return "shader_image_gather_extended";
            case KEY_SHADER_INT16: return "shader_int16";
            case KEY_SHADER_INT64: return "shader_int64";
            case KEY_SHADER_RESOURCE_MIN_LOD: return "shader_resource_min_lod";
            case KEY_SHADER_RESOURCE_RESIDENCY: return "shader_resource_residency";
            case KEY_SHADER_SAMPLED_IMAGE_ARRAY_DYNAMIC_INDEXING: return "shader_sampled_image_array_dynamic_indexing";
            case KEY_SHADER_STORAGE_BUFFER_ARRAY_DYNAMIC_INDEXING: return "shader_storage_buffer_array_dynamic_indexing";
            case KEY_SHADER_STORAGE_IMAGE_ARRAY_DYNAMIC_INDEXING: return "shader_storage_image_array_dynamic_indexing";
            case KEY_SHADER_STORAGE_IMAGE_EXTENDED_FORMATS: return "shader_storage_image_extended_formats";
            case KEY_SHADER_STORAGE_IMAGE_MULTISAMPLE: return "shader_storage_image_multisample";
            case KEY_SHADER_STORAGE_IMAGE_READ_WITHOUT_FORMAT: return "shader_storage_image_read_without_format";
            case KEY_SHADER_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT: return "shader_storage_image_write_without_format";
            case KEY_SHADER_TESSELLATION_AND_GEOMETRY_POINT_SIZE: return "shader_tessellation_and_geometry_point_size";
            case KEY_SHADER_UNIFORM_BUFFER_ARRAY_DYNAMIC_INDEXING: return "shader_uniform_buffer_array_dynamic_indexing";
            case KEY_SIZE: return "size";
            case KEY_SPARSE_ADDRESS_SPACE_SIZE: return "sparse_address_space_size";
            case KEY_SPARSE_BINDING: return "sparse_binding";
            case KEY_SPARSE_PROPERTIES: return "sparse_properties";
            case KEY_SPARSE_RESIDENCY_16_SAMPLES: return "sparse_residency_16_samples";
            case KEY_SPARSE_RESIDENCY_2_SAMPLES: return "sparse_residency_2_samples";
            case KEY_SPARSE_RESIDENCY_4_SAMPLES: return "sparse_residency_4_samples";
            case KEY_SPARSE_RESIDENCY_8_SAMPLES: return "sparse_residency_8_samples";
            case KEY_SPARSE_RESIDENCY_ALIASED: return "sparse_residency_aliased";
            case KEY_SPARSE_RESIDENCY_BUFFER: return "sparse_residency_buffer";
            case KEY_SPARSE_RESIDENCY_IMAGE_2D: return "sparse_residency_image_2d";
            case KEY_SPARSE_RESIDENCY_IMAGE_3D: return "sparse_residency_image_3d";
            case KEY_SPEC_VERSION: return "spec_version";
            case KEY_STANDARD_SAMPLE_LOCATIONS: return "standard_sample_locations";
            case KEY_STORAGE_IMAGE_SAMPLE_COUNTS: return "storage_image_sample_counts";
            case KEY_STRICT_LINES: return "strict_lines";
            case KEY_SUB_PIXEL_INTERPOLATION_OFFSET_BITS: return "sub_pixel_interpolation_offset_bits";
            case KEY_SUB_PIXEL_PRECISION_BITS: return "sub_pixel_precision_bits";
            case KEY_SUB_TEXEL_PRECISION_BITS: return "sub_texel_precision_bits";
            case KEY_TESSELLATION_SHADER: return "tessellation_shader";
            case KEY_TEXTURE_COMPRESSION_ASTC_LDR: return "texture_compression_astc_ldr";
            case KEY_TEXTURE_COMPRESSION_BC: return "texture_compression_bc";
            case KEY_TEXTURE_COMPRESSION_ETC2: return "texture_compression_etc2";
            case KEY_TIMESTAMP_COMPUTE_AND_GRAPHICS: return "timestamp_compute_and_graphics";
            case KEY_TIMESTAMP_PERIOD: return "timestamp_period";
            case KEY_TIMESTAMP_VALID_BITS: return "timestamp_valid_bits";
            case KEY_VARIABLE_MULTISAMPLE_RATE: return "variable_multisample_rate";
            case KEY_VENDOR_ID: return "vendor_id";
            case KEY_VERTEX_PIPELINE_STORES_AND_ATOMICS: return "vertex_pipeline_stores_and_atomics";
            case KEY_VIEWPORT_BOUNDS_RANGE: return "viewport_bounds_range";
            case KEY_VIEWPORT_SUB_PIXEL_BITS: return "viewport_sub_pixel_bits";
            case KEY_WIDE_LINES: return "wide_lines";
            case KEY_WIDTH: return "width";
            default: throw new RuntimeException("unknown key name: " + name);
        }
    }

    private static native String nativeGetVkJSON();

}
