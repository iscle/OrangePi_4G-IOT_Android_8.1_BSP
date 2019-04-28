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
 * Create a Blur intrinsic with scriptIntrinsicCreate, and call
 * scriptSetTimeZone to make sure it is not crashing.
 *
 * Calls: elementCreate, scriptIntrinsicCreate, scriptSetTimeZone
 */
TEST_F(RenderscriptHidlTest, IntrinsicTest) {
    // uint8
    Element element = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 1);
    EXPECT_NE(Element(0), element);

    Script script = context->scriptIntrinsicCreate(ScriptIntrinsicID::ID_BLUR, element);
    EXPECT_NE(Script(0), script);

    context->scriptSetTimeZone(script, "UTF-8");
}

/*
 * Create a user script “struct_test”, and verified the setters and getters work
 * for the global variables.
 *
 * Calls: scriptCCreate, scriptGetVarV, scriptSetVarI, scriptSetVarJ,
 * scriptSetVarF, scriptSetVarD, elementCreate, typeCreate,
 * allocationCreateTyped, scriptSetVarObj, scriptSetVarV, scriptSetVarVE
 */
TEST_F(RenderscriptHidlTest, ScriptVarTest) {
    hidl_vec<uint8_t> bitcode;
    bitcode.setToExternal((uint8_t*)bitCode, bitCodeLength);
    Script script = context->scriptCCreate("struct_test", "/data/local/tmp/", bitcode);
    ASSERT_NE(Script(0), script);

    // arg tests
    context->scriptSetVarI(script, mExportVarIdx_var_int, 100);
    int resultI = 0;
    context->scriptGetVarV(script, mExportVarIdx_var_int, sizeof(int),
                           [&](const hidl_vec<uint8_t>& _data){ resultI = *((int*)_data.data()); });
    EXPECT_EQ(100, resultI);

    context->scriptSetVarJ(script, mExportVarIdx_var_long, 101l);
    int resultJ = 0;
    context->scriptGetVarV(script, mExportVarIdx_var_long, sizeof(long),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultJ = *((long*)_data.data()); });
    EXPECT_EQ(101l, resultJ);

    context->scriptSetVarF(script, mExportVarIdx_var_float, 102.0f);
    int resultF = 0.0f;
    context->scriptGetVarV(script, mExportVarIdx_var_float, sizeof(float),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultF = *((float*)_data.data()); });
    EXPECT_EQ(102.0f, resultF);

    context->scriptSetVarD(script, mExportVarIdx_var_double, 103.0);
    int resultD = 0.0;
    context->scriptGetVarV(script, mExportVarIdx_var_double, sizeof(double),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultD = *((double*)_data.data()); });
    EXPECT_EQ(103.0, resultD);

    // float1
    Element element = context->elementCreate(DataType::FLOAT_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 128 x float1
    Type type = context->typeCreate(element, 128, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    // 128 x float1
    Allocation allocationIn = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                             (int)AllocationUsageType::SCRIPT,
                                                             (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocationIn);

    Allocation allocationOut = Allocation(0);
    context->scriptSetVarObj(script, mExportVarIdx_var_allocation, (ObjectBase)allocationIn);
    context->scriptGetVarV(script, mExportVarIdx_var_allocation, sizeof(ObjectBase),
                           [&](const hidl_vec<uint8_t>& _data){
                               allocationOut = (Allocation) *((ObjectBase*)_data.data()); });
    EXPECT_EQ(allocationOut, allocationIn);

    uint32_t valueV = 104u;
    hidl_vec<uint8_t> _dataV;
    _dataV.setToExternal((uint8_t*)&valueV, sizeof(uint32_t));
    context->scriptSetVarV(script, mExportVarIdx_var_uint32_t, _dataV);
    uint32_t resultV = 0u;
    context->scriptGetVarV(script, mExportVarIdx_var_uint32_t, sizeof(uint32_t),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultV = *((uint32_t*)_data.data()); });
    EXPECT_EQ(104u, resultV);

    std::vector<int> dataVE = {1000, 1001};
    std::vector<uint32_t> dimsVE = {1};
    std::vector<int> outVE(2);
    hidl_vec<uint8_t> _dataVE;
    hidl_vec<uint32_t> _dimsVE;
    _dataVE.setToExternal((uint8_t*)dataVE.data(), dataVE.size()*sizeof(int));
    _dimsVE.setToExternal((uint32_t*)dimsVE.data(), dimsVE.size());
    // intx2 to represent point2 which is {int, int}
    Element elementVE = context->elementCreate(DataType::SIGNED_32, DataKind::USER, false, 2);
    ASSERT_NE(Element(0), elementVE);

    context->scriptSetVarVE(script, mExportVarIdx_var_point2, _dataVE, elementVE, _dimsVE);
    context->scriptGetVarV(script, mExportVarIdx_var_point2, 2*sizeof(int),
                           [&](const hidl_vec<uint8_t>& _data){
                               outVE = std::vector<int>(
                                   (int*)_data.data(), (int*)_data.data() + 2); });
    EXPECT_EQ(1000, outVE[0]);
    EXPECT_EQ(1001, outVE[1]);
}

/*
 * Create a user script “struct_test”, and input and output Allocations.
 * Verified the foreach launch correctly for the invoke kernel.
 *
 * Calls: scriptCCreate, scriptInvoke, scriptGetVarV, scriptInvokeV
 */
TEST_F(RenderscriptHidlTest, ScriptInvokeTest) {
    hidl_vec<uint8_t> bitcode;
    bitcode.setToExternal((uint8_t*)bitCode, bitCodeLength);
    Script script = context->scriptCCreate("struct_test", "/data/local/tmp/", bitcode);
    ASSERT_NE(Script(0), script);

    // invoke test
    int resultI = 0;
    long resultJ = 0l;
    float resultF = 0.0f;
    double resultD = 0.0;
    uint32_t resultV = 0u;
    std::vector<int> resultVE(2);
    context->scriptInvoke(script, mExportFuncIdx_function);
    context->scriptGetVarV(script, mExportVarIdx_var_int, sizeof(int),
                           [&](const hidl_vec<uint8_t>& _data){ resultI = *((int*)_data.data()); });
    context->scriptGetVarV(script, mExportVarIdx_var_long, sizeof(long),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultJ = *((long*)_data.data()); });
    context->scriptGetVarV(script, mExportVarIdx_var_float, sizeof(float),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultF = *((float*)_data.data()); });
    context->scriptGetVarV(script, mExportVarIdx_var_double, sizeof(double),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultD = *((double*)_data.data()); });
    context->scriptGetVarV(script, mExportVarIdx_var_uint32_t, sizeof(uint32_t),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultV = *((uint32_t*)_data.data()); });
    context->scriptGetVarV(script, mExportVarIdx_var_point2, 2*sizeof(int),
                           [&](const hidl_vec<uint8_t>& _data){
                               resultVE = std::vector<int>(
                                   (int*)_data.data(), (int*)_data.data() + 2); });
    EXPECT_EQ(1, resultI);
    EXPECT_EQ(2l, resultJ);
    EXPECT_EQ(3.0f, resultF);
    EXPECT_EQ(4.0, resultD);
    EXPECT_EQ(5u, resultV);
    EXPECT_EQ(6, resultVE[0]);
    EXPECT_EQ(7, resultVE[1]);

    // invokeV test
    int functionV_arg = 5;
    int functionV_res = 0;
    hidl_vec<uint8_t> functionV_data;
    functionV_data.setToExternal((uint8_t*)&functionV_arg, sizeof(int));
    context->scriptInvokeV(script, mExportFuncIdx_functionV, functionV_data);
    context->scriptGetVarV(script, mExportVarIdx_var_int, sizeof(int),
                           [&](const hidl_vec<uint8_t>& _data){
                               functionV_res = *((int*)_data.data()); });
    EXPECT_EQ(5, functionV_res);
}

/*
 * Create a user script “struct_test”, and input and output Allocations.
 * Verified the foreach launch correctly for the foreach kernel.
 *
 * Calls: scriptCCreate, elementCreate, typeCreate, allocationCreateTyped,
 * allocation1DWrite, scriptForEach, allocationRead
 */
TEST_F(RenderscriptHidlTest, ScriptForEachTest) {
    hidl_vec<uint8_t> bitcode;
    bitcode.setToExternal((uint8_t*)bitCode, bitCodeLength);
    Script script = context->scriptCCreate("struct_test", "/data/local/tmp/", bitcode);
    ASSERT_NE(Script(0), script);

    // uint8_t
    Element element = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 64 x uint8_t
    Type type = context->typeCreate(element, 64, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    std::vector<uint8_t> dataIn(64), dataOut(64), expected(64);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static uint8_t val = 0; return val++; });
    std::generate(expected.begin(), expected.end(), [](){ static uint8_t val = 1; return val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size());
    // 64 x float1
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    Allocation vout = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                     (int)AllocationUsageType::SCRIPT,
                                                     (Ptr)nullptr);
    ASSERT_NE(Allocation(0), vout);

    context->allocation1DWrite(allocation, 0, 0, (Size)dataIn.size(), _data);
    hidl_vec<Allocation> vains;
    vains.setToExternal(&allocation, 1);
    hidl_vec<uint8_t> params;
    context->scriptForEach(script, mExportForEachIdx_increment, vains, vout, params, nullptr);
    context->allocationRead(vout, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(uint8_t));
    EXPECT_EQ(expected, dataOut);
}

/*
 * Create a user script “struct_test”, and input and output Allocations.
 * Verified the foreach launch correctly for the reduction kernel.
 *
 * Calls: scriptCCreate, elementCreate, typeCreate, allocationCreateTyped,
 * allocation1DWrite, scriptReduce, contextFinish, allocationRead
 */
TEST_F(RenderscriptHidlTest, ScriptReduceTest) {
    hidl_vec<uint8_t> bitcode;
    bitcode.setToExternal((uint8_t*)bitCode, bitCodeLength);
    Script script = context->scriptCCreate("struct_test", "/data/local/tmp/", bitcode);
    ASSERT_NE(Script(0), script);

    // uint8_t
    Element element = context->elementCreate(DataType::SIGNED_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 64 x uint8_t
    Type type = context->typeCreate(element, 64, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    Type type2 = context->typeCreate(element, 1, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type2);

    std::vector<int> dataIn(64), dataOut(1);
    std::generate(dataIn.begin(), dataIn.end(), [](){ static int val = 0; return val++; });
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(int));
    // 64 x float1
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    Allocation vaout = context->allocationCreateTyped(type2, AllocationMipmapControl::NONE,
                                                      (int)AllocationUsageType::SCRIPT,
                                                      (Ptr)nullptr);
    ASSERT_NE(Allocation(0), vaout);

    context->allocation1DWrite(allocation, 0, 0, (Size)dataIn.size(), _data);
    hidl_vec<Allocation> vains;
    vains.setToExternal(&allocation, 1);
    context->scriptReduce(script, mExportReduceIdx_summation, vains, vaout, nullptr);
    context->contextFinish();
    context->allocationRead(vaout, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(int));
    // sum of 0, 1, 2, ..., 62, 63
    int sum = 63*64/2;
    EXPECT_EQ(sum, dataOut[0]);
}

/*
 * This test creates an allocation and binds it to a data segment in the
 * RenderScript script, represented in the bitcode.
 *
 * Calls: scriptCCreate, elementCreate, typeCreate, allocationCreateTyped,
 * allocation1DWrite, scriptBindAllocation, scriptSetVarV, scriptBindAllocation,
 * allocationRead, scriptInvokeV, allocationRead
 */
TEST_F(RenderscriptHidlTest, ScriptBindTest) {
    hidl_vec<uint8_t> bitcode;
    bitcode.setToExternal((uint8_t*)bitCode, bitCodeLength);
    Script script = context->scriptCCreate("struct_test", "/data/local/tmp/", bitcode);
    ASSERT_NE(Script(0), script);

    // in32
    Element element = context->elementCreate(DataType::SIGNED_32, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    // 64 x int32
    Type type = context->typeCreate(element, 64, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    // 64 x int32
    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    std::vector<int> dataIn(64), dataOut(64), expected(64, 5);
    hidl_vec<uint8_t> _data;
    _data.setToExternal((uint8_t*)dataIn.data(), dataIn.size()*sizeof(int));
    context->allocation1DWrite(allocation, 0, 0, (Size)dataIn.size(), _data);
    context->scriptBindAllocation(script, allocation, mExportVarIdx_var_int_ptr);
    int dim = 64;
    hidl_vec<uint8_t> _dim;
    _dim.setToExternal((uint8_t*)&dim, sizeof(int));
    context->scriptInvokeV(script, mExportFuncIdx_setBuffer, _dim);
    context->allocationRead(allocation, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(int));
    EXPECT_EQ(expected, dataOut);
}

/*
 * This test groups together two RenderScript intrinsic kernels to run one after
 * the other asynchronously with respect to the client. The test configures
 * Blend and Blur, and links them together such that Blur will execute after
 * Blend and use its result. The test checks the data returned to make sure it
 * was changed after passing through the entire ScriptGroup.
 *
 * Calls: elementCreate, typeCreate, allocationCreateTyped, allocation2DWrite,
 * scriptIntrinsicCreate, scriptKernelIDCreate, scriptFieldIDCreate,
 * scriptGroupCreate, scriptGroupSetInput, scriptGroupSetOutput,
 * scriptGroupExecute, contextFinish, allocation2DRead
 */
TEST_F(RenderscriptHidlTest, ScriptGroupTest) {
    std::vector<uint8_t> dataIn(256 * 256 * 4, 128), dataOut(256 * 256 * 4, 0),
        zeros(256 * 256 * 4, 0);
    hidl_vec<uint8_t> _dataIn, _dataOut;
    _dataIn.setToExternal(dataIn.data(), dataIn.size());
    _dataOut.setToExternal(dataOut.data(), dataOut.size());

    // 256 x 256 YUV pixels
    Element element1 = context->elementCreate(DataType::UNSIGNED_8,
                                              DataKind::PIXEL_RGBA, true, 4);
    ASSERT_NE(Element(0), element1);

    Type type1 = context->typeCreate(element1, 256, 256, 0, false, false,
                                     YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type1);

    Allocation allocation1 = context->allocationCreateTyped(type1, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation1);

    context->allocation2DWrite(allocation1, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 256, 256,
                               _dataIn, 0);

    // 256 x 256 RGBA pixels
    Element element2 = context->elementCreate(DataType::UNSIGNED_8, DataKind::PIXEL_RGBA, true, 4);
    ASSERT_NE(Element(0), element2);

    Type type2 = context->typeCreate(element2, 256, 256, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type2);

    Allocation allocation2 = context->allocationCreateTyped(type2, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation2);

    context->allocation2DWrite(allocation2, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 256, 256,
                               _dataOut, 0);

    // create scripts
    Script blend =
        context->scriptIntrinsicCreate(ScriptIntrinsicID::ID_BLEND, element1);
    ASSERT_NE(Script(0), blend);

    ScriptKernelID blendKID = context->scriptKernelIDCreate(blend, 1, 3);
    ASSERT_NE(ScriptKernelID(0), blendKID);

    Script blur = context->scriptIntrinsicCreate(ScriptIntrinsicID::ID_BLUR, element2);
    ASSERT_NE(Script(0), blur);

    ScriptKernelID blurKID = context->scriptKernelIDCreate(blur, 0, 2);
    ASSERT_NE(ScriptKernelID(0), blurKID);

    ScriptFieldID blurFID = context->scriptFieldIDCreate(blur, 1);
    ASSERT_NE(ScriptFieldID(0), blurFID);

    // ScriptGroup
    hidl_vec<ScriptKernelID> kernels = {blendKID, blurKID};
    hidl_vec<ScriptKernelID> srcK = {blendKID};
    hidl_vec<ScriptKernelID> dstK = {ScriptKernelID(0)};
    hidl_vec<ScriptFieldID> dstF = {blurFID};
    hidl_vec<Type> types = {type2};
    ScriptGroup scriptGroup = context->scriptGroupCreate(kernels, srcK, dstK, dstF, types);
    ASSERT_NE(ScriptGroup(0), scriptGroup);

    context->scriptGroupSetInput(scriptGroup, blendKID, allocation1);
    context->scriptGroupSetOutput(scriptGroup, blurKID, allocation2);
    context->scriptGroupExecute(scriptGroup);
    context->contextFinish();

    // verify contents were changed
    context->allocation2DRead(allocation2, 0, 0, 0, AllocationCubemapFace::POSITIVE_X, 256, 256,
                              (Ptr)dataOut.data(), (Size)dataOut.size(), 0);
    EXPECT_NE(zeros, dataOut);
}

/*
 * Similar to the ScriptGroup test, this test verifies the execution flow of
 * RenderScript kernels and invokables.
 *
 * Calls: scriptCCreate, elementCreate, typeCreate, allocationCreateTyped,
 * allocation1DWrite, scriptFieldIDCreate, scriptInvokeIDCreate,
 * invokeClosureCreate, closureCreate, closureSetGlobal, scriptGroup2Create,
 * scriptGroupExecute, allocationRead
 */
TEST_F(RenderscriptHidlTest, ScriptGroup2Test) {
    hidl_vec<uint8_t> bitcode;
    bitcode.setToExternal((uint8_t*)bitCode, bitCodeLength);
    Script script = context->scriptCCreate("struct_test", "/data/local/tmp/", bitcode);
    ASSERT_NE(Script(0), script);

    std::vector<uint8_t> dataIn(128, 128), dataOut(128, 0), expected(128, 7+1);
    hidl_vec<uint8_t> _dataIn, _dataOut;
    _dataIn.setToExternal(dataIn.data(), dataIn.size());

    // 256 x 256 YUV pixels
    Element element = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    Type type = context->typeCreate(element, 128, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    context->allocation1DWrite(allocation, 0, 0, (Size)_dataIn.size(), _dataIn);

    ScriptFieldID fieldID = context->scriptFieldIDCreate(script, mExportVarIdx_var_allocation);
    ASSERT_NE(ScriptFieldID(0), fieldID);

    // invoke
    ScriptInvokeID invokeID = context->scriptInvokeIDCreate(script, mExportFuncIdx_setAllocation);
    ASSERT_NE(ScriptInvokeID(0), invokeID);

    int dim = 128;
    hidl_vec<uint8_t> params;
    params.setToExternal((uint8_t*)&dim, sizeof(dim));
    hidl_vec<ScriptFieldID> fieldIDS1 = {fieldID};
    hidl_vec<int64_t> values1 = {int64_t(0)};
    hidl_vec<int32_t> sizes1 = {int32_t(0)};
    Closure closure1 = context->invokeClosureCreate(invokeID, params, fieldIDS1, values1, sizes1);
    ASSERT_NE(Closure(0), closure1);

    // kernel
    ScriptKernelID kernelID = context->scriptKernelIDCreate(script, mExportForEachIdx_increment, 3);
    ASSERT_NE(ScriptKernelID(0), kernelID);

    hidl_vec<ScriptFieldID> fieldIDS2 = {ScriptFieldID(0)};
    hidl_vec<int64_t> values2 = {(int64_t)(intptr_t)allocation};
    hidl_vec<int32_t> sizes2 = {-1 /* allocation */};
    hidl_vec<Closure> depClosures2 = {closure1};
    hidl_vec<ScriptFieldID> depFieldIDS2 = {fieldID};
    Closure closure2 = context->closureCreate(kernelID, allocation /* returnValue */, fieldIDS2,
                                              values2, sizes2, depClosures2, depFieldIDS2);
    ASSERT_NE(Closure(0), closure2);

    // set argument
    context->closureSetGlobal(closure1, fieldID, (int64_t)(intptr_t)allocation,
                              -1 /* allocation */);

    // execute
    hidl_string name = "script_group_2_test";
    hidl_string cacheDir = "/data/local/tmp";
    hidl_vec<Closure> closures = {closure1, closure2};
    ScriptGroup2 scriptGroup2 = context->scriptGroup2Create(name, cacheDir, closures);
    ASSERT_NE(ScriptGroup2(0), scriptGroup2);

    context->scriptGroupExecute(scriptGroup2);
    context->allocationRead(allocation, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(uint8_t));
    EXPECT_EQ(expected, dataOut);
}

/*
 * Similar to the ScriptGroup test, this test verifies a single kernel can be
 * called by ScriptGroup with an unbound allocation specified before launch
 *
 * Calls: scriptCCreate, elementCreate, typeCreate, allocationCreateTyped,
 * allocation1DWrite, scriptKernelIDCreate, closureCreate, closureSetArg,
 * scriptGroup2Create, scriptGroupExecute, allocationRead
 */
TEST_F(RenderscriptHidlTest, ScriptGroup2KernelTest) {
    hidl_vec<uint8_t> bitcode;
    bitcode.setToExternal((uint8_t*)bitCode, bitCodeLength);
    Script script = context->scriptCCreate("struct_test", "/data/local/tmp/", bitcode);
    ASSERT_NE(Script(0), script);

    std::vector<uint8_t> dataIn(128, 128), dataOut(128, 0), expected(128, 128 + 1);
    hidl_vec<uint8_t> _dataIn, _dataOut;
    _dataIn.setToExternal(dataIn.data(), dataIn.size());

    // 256 x 256 YUV pixels
    Element element = context->elementCreate(DataType::UNSIGNED_8, DataKind::USER, false, 1);
    ASSERT_NE(Element(0), element);

    Type type = context->typeCreate(element, 128, 0, 0, false, false, YuvFormat::YUV_NONE);
    ASSERT_NE(Type(0), type);

    Allocation allocation = context->allocationCreateTyped(type, AllocationMipmapControl::NONE,
                                                           (int)AllocationUsageType::SCRIPT,
                                                           (Ptr)nullptr);
    ASSERT_NE(Allocation(0), allocation);

    context->allocation1DWrite(allocation, 0, 0, (Size)_dataIn.size(), _dataIn);

    // kernel
    ScriptKernelID kernelID = context->scriptKernelIDCreate(script, mExportForEachIdx_increment, 3);
    ASSERT_NE(ScriptKernelID(0), kernelID);

    hidl_vec<ScriptFieldID> fieldIDS = {ScriptFieldID(0)};
    hidl_vec<int64_t> values = {int64_t(0)};
    hidl_vec<int32_t> sizes = {int32_t(0)};
    hidl_vec<Closure> depClosures = {Closure(0)};
    hidl_vec<ScriptFieldID> depFieldIDS = {ScriptFieldID(0)};
    Closure closure = context->closureCreate(kernelID, allocation /* returnValue */, fieldIDS,
                                              values, sizes, depClosures, depFieldIDS);
    ASSERT_NE(Closure(0), closure);

    // set argument
    context->closureSetArg(closure, 0 /* first argument */, (Ptr)allocation, -1);

    // execute
    hidl_string name = "script_group_2_test";
    hidl_string cacheDir = "/data/local/tmp";
    hidl_vec<Closure> closures = {closure};
    ScriptGroup2 scriptGroup2 = context->scriptGroup2Create(name, cacheDir, closures);
    ASSERT_NE(ScriptGroup2(0), scriptGroup2);

    context->scriptGroupExecute(scriptGroup2);
    context->allocationRead(allocation, (Ptr)dataOut.data(), (Size)dataOut.size()*sizeof(uint8_t));
    EXPECT_EQ(expected, dataOut);
}
