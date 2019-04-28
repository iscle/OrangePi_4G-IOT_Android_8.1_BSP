/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "VtsHalRenderscriptV1_0TargetTest.h"
#include <system/window.h>

/*
 * ContextCreateAndDestroy:
 * Creates a RenderScript context and immediately destroys the context.
 * Since create and destroy calls are a part of SetUp() and TearDown(),
 * the test definition is intentionally kept empty
 *
 * Calls: getService<IDevice>, contextCreate, contextDestroy
 */
TEST_F(RenderscriptHidlTest, ContextCreateAndDestroy) {}

/*
 * Create an Element and verify the return value is valid.
 *
 * Calls: elementCreate
 */
TEST_F(RenderscriptHidlTest, ElementCreate) {
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    EXPECT_NE(Element(0), element);
}

/*
 * Create an Element, a Type and an Allocation of that type, and verify the
 * return values are valid.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped, allocationGetType
 */
TEST_F(RenderscriptHidlTest, ElementTypeAllocationCreate) {
    // Element create test
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // Type create test
    Type type = context->typeCreate(element, 1, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    // Allocation create test
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)((uint32_t)AllocationUsageType::ALL
                                                           & ~(uint32_t)AllocationUsageType::OEM),
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    // Allocation type test
    Type type2 = context->allocationGetType(allocation);
    EXPECT_EQ(type, type2);
}

/*
 * Create an Element, a Type of the Element, and verify the native metadata can
 * be retrieved correctly.
 *
 * Calls: elementCreate, typeCreate, elementGetNativeMetadata,
 * typeGetNativeMetadata
 */
TEST_F(RenderscriptHidlTest, MetadataTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 128 x float1
    Type type = context->typeCreate(element, 128, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    std::vector<uint32_t> elementMetadata(5);
    context->elementGetNativeMetadata(element, [&](const hidl_vec<uint32_t>& _metadata){
                                          elementMetadata = _metadata; });
    EXPECT_EQ(DataType::FLOAT_32, (DataType)elementMetadata[0]);
    EXPECT_EQ(DataKind::USER, (DataKind)elementMetadata[1]);
    EXPECT_EQ(false, elementMetadata[2]);
    EXPECT_EQ(1u, (uint32_t)elementMetadata[3]);
    EXPECT_EQ(0u, (uint32_t)elementMetadata[4]);

    std::vector<OpaqueHandle> typeMetadata(6);
    context->typeGetNativeMetadata(type, [&typeMetadata](const hidl_vec<OpaqueHandle>& _metadata){
                                   typeMetadata = _metadata; });
    EXPECT_EQ(128u, (uint32_t)typeMetadata[0]);
    EXPECT_EQ(0u, (uint32_t)typeMetadata[1]);
    EXPECT_EQ(0u, (uint32_t)typeMetadata[2]);
    EXPECT_NE(true, typeMetadata[3]);
    EXPECT_NE(true, typeMetadata[4]);
    EXPECT_EQ(element, (Element)typeMetadata[5]);
}

/*
 * Create a Allocation, and verified allocationGetPointer and allocationResize1D
 * return valid values.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped,
 * allocationGetPointer, allocationResize1D
 */
TEST_F(RenderscriptHidlTest, ResizeTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 128 x float1
    Type type = context->typeCreate(element, 128, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    // 128 x float1
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    Ptr dataPtr1, dataPtr2;
    Size stride;
    context->allocationGetPointer(allocation, 0, AllocationCubemapFace::POSITIVE_X, 0,
                                  [&](Ptr _dataPtr, Size _stride){
                                      dataPtr1 = _dataPtr; stride = _stride; });
    EXPECT_EQ(Size(0), stride);

    context->allocationResize1D(allocation, 1024*1024);
    context->allocationGetPointer(allocation, 0, AllocationCubemapFace::POSITIVE_X, 0,
                                  [&](Ptr _dataPtr, Size _stride){
                                      dataPtr2 = _dataPtr; stride = _stride; });
    EXPECT_EQ(Size(0), stride);
    EXPECT_NE(dataPtr1, dataPtr2);
}

/*
 * Test creates two allocations, one with IO_INPUT and one with IO_OUTPUT. The
 * NativeWindow (Surface) is retrieved from one allocation and set to the other.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped, allocation2DWrite,
 * allocationGetNativeWindow, allocationSetNativeWindow, allocationIoSend,
 * allocationIoReceive, allocation2DRead
 */
TEST_F(RenderscriptHidlTest, NativeWindowIoTest) {
    // uint8x4
    Element element = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 4);
    ASSERT_NE(Element(0), element);

    // 512 x 512 x uint8x4
    Type type = context->typeCreate(element, 512, 512, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    std::vector<uint32_t> dataIn(512*512), dataOut(512*512);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static uint32_t val = 0; return val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(uint32_t));
    // 512 x 512 x uint8x4
    Allocation allocationRecv = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                               (int)(AllocationUsageType::SCRIPT
                                                               | AllocationUsageType::IO_INPUT),
                                                               (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocationRecv);

    Allocation allocationSend = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                               (int)(AllocationUsageType::SCRIPT
                                                               | AllocationUsageType::IO_OUTPUT),
                                                               (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocationSend);

    NativeWindow nativeWindow = context->allocationGetNativeWindow(allocationRecv);
    ASSERT_NE(NativeWindow(0), nativeWindow);

    ((ANativeWindow *)nativeWindow)->incStrong(nullptr);
    native_window_api_connect((ANativeWindow*)nativeWindow,
                              NATIVE_WINDOW_API_CPU);

    context->allocationSetNativeWindow(allocationSend, nativeWindow);
    context->allocation2DWrite(allocationSend, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 512, 512,
                               _data, 0);
    context->allocationIoSend(allocationSend);
    context->allocationIoReceive(allocationRecv);
    context->allocation2DRead(allocationRecv, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 512, 512,
                              (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(uint32_t), 0);
    EXPECT_EQ(dataIn, dataOut);
}

/*
 * Three allocations are created, two with IO_INPUT and one with IO_OUTPUT. The
 * two allocations with IO_INPUT are made to share the same BufferQueue.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped,
 * allocationSetupBufferQueue, allocationShareBufferQueue,
 * allocationGetNativeWindow, allocationSetNativeWindow,
 * allocation2DWrite, allocation2DRead, allocationIoSend,
 * allocationIoReceive
 */
TEST_F(RenderscriptHidlTest, BufferQueueTest) {
    // uint8x4
    Element element = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 4);
    ASSERT_NE(Element(0), element);

    // 512 x 512 x uint8x4
    Type type = context->typeCreate(element, 512, 512, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    std::vector<uint32_t> dataIn(512*512), dataOut1(512*512), dataOut2(512*512);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static uint32_t val = 0; return val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(uint32_t));
    // 512 x 512 x uint8x4
    Allocation allocationRecv1 = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                                (int)(AllocationUsageType::SCRIPT
                                                                | AllocationUsageType::IO_INPUT),
                                                                (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocationRecv1);

    Allocation allocationRecv2 = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                                (int)(AllocationUsageType::SCRIPT
                                                                | AllocationUsageType::IO_INPUT),
                                                                (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocationRecv2);

    Allocation allocationSend  = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                                (int)(AllocationUsageType::SCRIPT
                                                                | AllocationUsageType::IO_INPUT),
                                                                (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocationSend);

    context->allocationSetupBufferQueue(allocationRecv1, 2);
    context->allocationShareBufferQueue(allocationRecv2, allocationRecv1);

    NativeWindow nativeWindow1 = context->allocationGetNativeWindow(allocationRecv1);
    ASSERT_NE(NativeWindow(0), nativeWindow1);

    NativeWindow nativeWindow2 = context->allocationGetNativeWindow(allocationRecv2);
    ASSERT_NE(NativeWindow(0), nativeWindow2);
    EXPECT_EQ(nativeWindow2, nativeWindow1);

    ((ANativeWindow *)nativeWindow1)->incStrong(nullptr);
    native_window_api_connect((ANativeWindow*)nativeWindow1,
                              NATIVE_WINDOW_API_CPU);

    context->allocationSetNativeWindow(allocationSend, nativeWindow1);
    context->allocation2DWrite(allocationSend, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 512, 512,
                               _data, 0);
    context->allocationIoSend(allocationSend);
    context->allocationIoReceive(allocationRecv1);
    context->allocation2DRead(allocationRecv1, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 512, 512,
                              (Ptr)dataOut1.data(), (Size)dataOut1.size()*sizeof(uint32_t), 0);
    EXPECT_EQ(dataIn, dataOut1);

    context->allocation2DWrite(allocationSend, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 512, 512,
                               _data, 0);
    context->allocationIoSend(allocationSend);
    context->allocationIoReceive(allocationRecv2);
    context->allocation2DRead(allocationRecv2, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 512, 512,
                              (Ptr)dataOut2.data(), (Size)dataOut2.size()*sizeof(uint32_t), 0);
    EXPECT_EQ(dataIn, dataOut2);
}

/*
 * This test sets up the message queue, sends a message, peeks at the message,
 * and reads it back.
 *
 * Calls: contextInitToClient, contextSendMessage, contextPeekMessage,
 * contextGetMessage, contextDeinitToClient, contextLog
 */
TEST_F(RenderscriptHidlTest, ContextMessageTest) {
    context->contextInitToClient();

    const char * message = "correct";
    std::vector<char> messageSend(message, message + sizeof(message));
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)messageSend.data(), messageSend.size());
    context->contextSendMessage(0, _data);
    MessageToClientType messageType;
    size_t size;
    uint32_t subID;
    context->contextPeekMessage([&](MessageToClientType _type, Size _size, uint32_t _subID){
                                messageType = _type; size = (uint32_t)_size; subID = _subID; });
    std::vector<char> messageRecv(size, '\0');
    context->contextGetMessage(messageRecv.data(), messageRecv.size(),
                               [&](MessageToClientType _type, Size _size){
                               messageType = _type; size = (uint32_t)_size; });
    EXPECT_EQ(messageSend, messageRecv);

    context->contextDeinitToClient();
    context->contextLog();
}

/*
 * Call through a bunch of APIs and make sure they don’t crash. Assign the name
 * of a object and check getName returns the name just set.
 *
 * Calls: contextSetPriority, contextSetCacheDir, elementCreate, assignName,
 * contextFinish, getName, objDestroy, samplerCreate
 */
TEST_F(RenderscriptHidlTest, MiscellaneousTests) {
    context->contextSetPriority(ThreadPriorities::NORMAL);
    context->contextSetCacheDir("/data/local/tmp/temp/");

    Element element = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    std::string nameIn = "element_test_name";
    std::string nameOut = "not_name";
    hidl_string _nameIn;
    _nameIn.setToExternal(nameIn.c_str(), nameIn.length());
    context->assignName(element, _nameIn);
    context->contextFinish();
    context->getName(element, [&](const hidl_string& _name){ nameOut = _name.c_str(); });
    EXPECT_EQ("element_test_name", nameOut);

    context->objDestroy(element);

    Sampler sampler = context->samplerCreate(SamplerValue::LINEAR, SamplerValue::LINEAR,
                                             SamplerValue::LINEAR, SamplerValue::LINEAR,
                                             SamplerValue::LINEAR, 8.0f);
    EXPECT_NE(Sampler(0), sampler);
}
