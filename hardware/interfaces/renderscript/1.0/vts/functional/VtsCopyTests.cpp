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

/*
 * This test creates a 1D Allocation with 128 Float Elements, and two float
 * vector dataIn & dataOut. dataIn is pre-populated with data, and copied into
 * the Allocation using allocation1DWrite. Then the Allocation is copied into
 * dataOut with allocation1DRead.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped, allocation1DWrite,
 * allocation1DRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, Simple1DCopyTest) {
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

    std::vector<float> dataIn(128), dataOut(128);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return (float)val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(float));
    context->allocation1DWrite(allocation, 0, 0, (Size)dataIn.size(), _data);
    context->allocation1DRead(allocation, 0, 0, (uint32_t)dataOut.size(), (Ptr)dataOut.data(),
                              (Size)dataOut.size()*sizeof(float));
    EXPECT_EQ(dataIn, dataOut);
}

/*
 * This test creates a 2D Allocation with 128 * 128 Float Elements, and two
 * float vector dataIn & dataOut. dataIn is pre-populated with data, and copied
 * into the Allocation using allocation2DWrite. Then the Allocation is copied
 * into dataOut with allocation2DRead.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped, allocation2DWrite,
 * allocation2DRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, Simple2DCopyTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 128 x 128 x float1
    Type type = context->typeCreate(element, 128, 128, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    // 128 x 128 x float1
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    std::vector<float> dataIn(128*128), dataOut(128*128);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return (float)val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(float));
    context->allocation2DWrite(allocation, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 128, 128,
                               _data, 0);
    context->allocation2DRead(allocation, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 128, 128,
                              (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(float), 0);
    EXPECT_EQ(dataIn, dataOut);
}

/*
 * This test creates a 3D Allocation with 32 * 32 * 32 Float Elements, and two
 * float vector dataIn & dataOut. dataIn is pre-populated with data, and copied
 * into the Allocation using allocation3DWrite. Then the Allocation is copied
 * into dataOut with allocation3DRead.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped, allocation3DWrite,
 * allocation3DRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, Simple3DCopyTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 32 x 32 x 32 x float1
    Type type = context->typeCreate(element, 32, 32, 32, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    // 32 x 32 x 32 x float1
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    std::vector<float> dataIn(32*32*32), dataOut(32*32*32);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return (float)val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(float));
    context->allocation3DWrite(allocation, 0, 0, 0, 0, 32, 32, 32, _data, 0);
    context->allocation3DRead(allocation, 0, 0, 0, 0, 32, 32, 32, (Ptr)dataOut.data(),
                              (Size)dataOut.size()*sizeof(float), 0);
    EXPECT_EQ(dataIn, dataOut);
}

/*
 * This test creates a 2D Allocation with 512 * 512 Float Elements with
 * allocationCreateFromBitmap, and two float vector dataIn & dataOut. dataIn is
 * pre-populated with data, and copied into the Allocation using
 * allocationCopyToBitmap. Then the Allocation is copied into dataOut with
 * allocationRead.
 *
 * Calls: elementCreate, typeCreate, allocationCreateFromBitmap,
 * allocationCopyToBitmap, allocationRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, SimpleBitmapTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 512 x 512 x float1
    Type type = context->typeCreate(element, 512, 512, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    std::vector<float> dataIn(512*512), dataOut1(512*512), dataOut2(512*512);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return (float)val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(float));
    // 512 x 512 x float1
    Allocation allocation = context->allocationCreateFromBitmap(type,
                                                                AllocationMipmapControl::NONE,
                                                                _data,
                                                                (int)AllocationUsageType::SCRIPT);
    ASSERT_NE(Allocation(0), allocation);

    context->allocationCopyToBitmap(allocation, (Ptr)dataOut1.data(),
                                    (Size)dataOut1.size()*sizeof(float));
    EXPECT_EQ(dataIn, dataOut1);

    context->allocationRead(allocation, (Ptr)dataOut2.data(), (Size)dataOut2.size()*sizeof(float));
    EXPECT_EQ(dataIn, dataOut2);
}

/*
 * This test creates two 2D Allocations, one with 512 * 512 Float Elements, the
 * other with 256 * 256 Float Elements. The larger Allocation is pre-populated
 * with dataIn, and copied into the smaller Allocation using
 * allocationCopy2DRange. Then the Allocation is copied into dataOut with
 * allocationRead.
 *
 * Calls: elementCreate, typeCreate, allocationCreateFromBitmap,
 * allocationCreateTyped, allocationCopy2DRange, allocationRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, AllocationCopy2DRangeTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 512 x 512 x float1
    Type typeSrc = context->typeCreate(element, 512, 512, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), typeSrc);

    // 256 x 256 x float1
    Type typeDst = context->typeCreate(element, 256, 256, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), typeDst);

    std::vector<float> dataIn(512*512), dataOut(256*256), expected(256*256);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return (float)val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(float));
    // 512 x 512 x float1
    Allocation allocSrc = context->allocationCreateFromBitmap(typeSrc,
                                                              AllocationMipmapControl::NONE, _data,
                                                              (int)AllocationUsageType::SCRIPT);
    ASSERT_NE(Allocation(0), allocSrc);

    // 256 x 256 x float1
    Allocation allocDst = context->allocationCreateTyped(typeDst, AllocationMipmapControl::NONE,
                                                         (int)AllocationUsageType::SCRIPT,
                                                         (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocDst);

    context->allocationCopy2DRange(allocDst, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 256, 256,
                                   allocSrc, 128, 128, 0, AllocationCubemapFace::POSITIVE_X);
    context->allocationRead(allocDst, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(float));
    for (int i = 0; i < 256; ++i) {
        for (int j = 0; j < 256; ++j) {
            expected[i*256 + j] = dataIn[(i+128)*512 + (j+128)];
        }
    }
    EXPECT_EQ(expected, dataOut);
}

/*
 * This test creates two 3D Allocations, one with 128 * 128 * 128 Float
 * Elements, the other with 64 * 64 * 64 Float Elements. The larger Allocation
 * is pre-populated with dataIn, and copied into the smaller Allocation using
 * allocationCopy3DRange. Then the Allocation is copied into dataOut with
 * allocationRead.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped, allocation3DWrite,
 * allocationCopy3DRange, allocationRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, AllocationCopy3DRangeTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 128 x 128 x 128 x float1
    Type typeSrc = context->typeCreate(element, 128, 128, 128, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), typeSrc);

    // 64 x 64 x 64 x float1
    Type typeDst = context->typeCreate(element, 64, 64, 64, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), typeDst);

    std::vector<float> dataIn(128*128*128), dataOut(64*64*64), expected(64*64*64);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return (float)val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(float));
    // 512 x 512 x float1
    Allocation allocSrc = context->allocationCreateTyped(typeSrc, AllocationMipmapControl::NONE,
                                                         (int)AllocationUsageType::SCRIPT,
                                                         (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocSrc);

    // 256 x 256 x float1
    Allocation allocDst = context->allocationCreateTyped(typeDst, AllocationMipmapControl::NONE,
                                                         (int)AllocationUsageType::SCRIPT,
                                                         (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocDst);

    context->allocation3DWrite(allocSrc, 0, 0, 0, 0, 128, 128, 128, _data, 128*sizeof(float));
    context->allocationCopy3DRange(allocDst, 0, 0, 0, 0, 64, 64, 64, allocSrc, 32, 32, 32, 0);
    context->allocationRead(allocDst, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(float));
    for (int i = 0; i < 64; ++i) {
        for (int j = 0; j < 64; ++j) {
            for (int k = 0; k < 64; ++k) {
                expected[i*64*64 + j*64 + k] = dataIn[(i+32)*128*128 + (j+32)*128 + (k+32)];
            }
        }
    }
    EXPECT_EQ(expected, dataOut);
}

/*
 * This test creates one 2D Allocations, one with 512 * 512 Float Elements, and
 * one 2D AllocationAdapter with a window of 256 * 256 based on the Allocation.
 * The Allocation is pre-populated with dataIn. Then the Allocation is copied
 * into dataOut with allocationRead on the AllocationAdapter.
 *
 * Calls: elementCreate, typeCreate, allocationCreateFromBitmap,
 * allocationAdapterCreate, allocationAdapterOffset, allocation2DRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, SimpleAdapterTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 512 x 512 x float1
    Type type = context->typeCreate(element, 512, 512, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    std::vector<float> dataIn(512*512), dataOut(256*256), expected;
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return (float)val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(float));
    // 512 x 512 x float1
    Allocation allocation = context->allocationCreateFromBitmap(type,
                                                                AllocationMipmapControl::NONE,
                                                                _data,
                                                                (int)AllocationUsageType::SCRIPT);
    ASSERT_NE(Allocation(0), allocation);

    // 256 x 256 x float1
    Type subType = context->typeCreate(element, 256, 256, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), subType);

    // 256 x 256 x float1
    AllocationAdapter allocationAdapter = context->allocationAdapterCreate(subType, allocation);
    ASSERT_NE(AllocationAdapter(0), allocationAdapter);

    std::vector<uint32_t> offsets(9, 0);
    offsets[0] = 128;
    offsets[1] = 128;
    hidl_vec<uint32_t> _offsets;
    _offsets.setToExternal(offsets.data(), offsets.size());
    // origin at (128,128)
    context->allocationAdapterOffset(allocationAdapter, _offsets);

    context->allocation2DRead(allocationAdapter, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 256,
                              256, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(float), 0);
    for (int i = 128; i < 128 + 256; ++i) {
        for (int j = 128; j < 128 + 256; ++j) {
            expected.push_back(i * 512 + j);
        }
    }
    EXPECT_EQ(expected, dataOut);
}

/*
 * This test creates one 2D Allocations, one with 64 * 64 USIGNED_8 Elements,
 * and with AllocationMipmapControl::FULL. The Allocation is pre-populated with
 * dataIn and the mipmaps are filled with allocationGenerateMipmaps. Then
 * dataOut is then overridden with allocation2DRead.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped, allocation2DWrite,
 * allocationGenerateMipmaps, allocationSyncAll, allocation2DRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, SimpleMipmapTest) {
    // uint8_t
    Element element = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 64 x 64 x uint8_t
    Type type = context->typeCreate(element, 64, 64, 0, true, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    std::vector<uint8_t> dataIn(64*64), dataOut(32*32), expected(32*32);
    std::generate(dataIn.begin(), dataIn.end(),
                  [](){ static int val = 0; return (uint8_t)(0xFF & val++); });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(uint8_t));
    // 64 x 64 x uint8_t
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::FULL,
                                                         (int)AllocationUsageType::SCRIPT,
                                                         (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    context->allocation2DWrite(allocation, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 64, 64,
                               _data, 64*sizeof(uint8_t));
    context->allocationGenerateMipmaps(allocation);
    context->allocationSyncAll(allocation, AllocationUsageType::SCRIPT);
    context->allocation2DRead(allocation, 0, 0, 1, AllocationCubemapFace::POSITIVE_X, 32, 32,
                              (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(uint8_t),
                              32*sizeof(uint8_t));
    for (int i = 0; i < 32; ++i) {
        for (int j = 0; j < 32; ++j) {
            expected[i*32 + j] = ((uint32_t)dataIn[i*2*64 + j*2] + dataIn[i*2*64 + j*2 + 1] +
                                  dataIn[i*2*64 + j*2 + 64] + dataIn[i*2*64 + j*2 + 64+1]) / 4;
        }
    }
    EXPECT_EQ(expected, dataOut);
}

/*
 * This test creates one 2D Allocations, one with 128 * 128 Float Elements with
 * allocationCubeCreateFromBitmap. The Allocation is pre-populated with dataIn
 * and the mipmaps are filled with allocationGenerateMipmaps. Then dataOut is
 * then overridden with allocation2DRead.
 *
 * Calls: elementCreate, typeCreate, allocationCubeCreateFromBitmap,
 * allocation2DRead
 *
 * Expect: dataIn & dataOut are the same.
 */
TEST_F(RenderscriptHidlTest, SimpleCubemapTest) {
    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 128 x 128 x float1
    Type type = context->typeCreate(element, 128, 128, 0, false, true, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    std::vector<float> dataIn(128*128*6), dataOut(128*128), expected(128*128);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return (float)val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(float));
    // 128 x 128 x float1 x 6
    Allocation allocation = context->allocationCubeCreateFromBitmap(
        type, AllocationMipmapControl::NONE, _data, (int)AllocationUsageType::SCRIPT);
    ASSERT_NE(Allocation(0), allocation);

    context->allocation2DRead(allocation, 0, 0, 0, AllocationCubemapFace::NEGATIVE_Z, 128,
                              128, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(float),
                              128*sizeof(float));
    for (int i = 0; i < 128; ++i) {
        for (int j = 0; j < 128; ++j) {
            expected[i*128 + j] = i*128*6 + j + 128*5;
        }
    }
    EXPECT_EQ(expected, dataOut);
}

/*
 * This test creates a complex element type (uint8_t, uint32_t) out of known
 * elements. It then verifies the element structure was created correctly.
 * Finally, the test creates a 1-wide, 1-dimension allocation of this type
 * and transfers memory to and from a single cell of this Allocation.
 *
 * Calls: elementCreate, elementComplexCreate, elementGetSubElements,
 * typeCreate, allocationCreateTyped, allocationElementWrite,
 * allocationElementRead
 */
TEST_F(RenderscriptHidlTest, ComplexElementTest) {
    Element element1 = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element1);

    Element element2 = context->elementCreate(DataType::UNSIGNED_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element2);

    hidl_vec<Element> eins = {element1, element2};
    hidl_vec<hidl_string> names = {hidl_string("first"), hidl_string("second")};
    hidl_vec<Size> arraySizesPtr = {1, 1};
    Element element3 = context->elementComplexCreate(eins, names, arraySizesPtr);
    ASSERT_NE(Element(0), element3);

    std::vector<Element> ids;
    std::vector<std::string> namesOut;
    std::vector<Size> arraySizesOut;
    context->elementGetSubElements(element3, 2, [&](const hidl_vec<Element>& _ids,
                                                    const hidl_vec<hidl_string>& _names,
                                                    const hidl_vec<Size>& _arraySizes){
                                                        ids = _ids;
                                                        namesOut.push_back(_names[0]);
                                                        namesOut.push_back(_names[1]);
                                                        arraySizesOut = _arraySizes;
                                                    });
    EXPECT_EQ(element1, ids[0]);
    EXPECT_EQ(element2, ids[1]);
    EXPECT_EQ("first", namesOut[0]);
    EXPECT_EQ("second", namesOut[1]);
    EXPECT_EQ(Size(1), arraySizesOut[0]);
    EXPECT_EQ(Size(1), arraySizesOut[1]);

    // 1 x (uint8_t, uint32_t)
    Type type = context->typeCreate(element3, 1, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    // 1 x (uint8_t, uint32_t)
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    std::vector<uint32_t> dataIn(1), dataOut(1);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static uint32_t val = 0; return val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(uint32_t));
    context->allocationElementWrite(allocation, 0, 0, 0, 0, _data, 1);
    context->allocationElementRead(allocation, 0, 0, 0, 0, (Ptr)dataOut.data(),
                                   (Size)dataOut.size()*sizeof(uint32_t), 1);
    EXPECT_EQ(dataIn, dataOut);
}
