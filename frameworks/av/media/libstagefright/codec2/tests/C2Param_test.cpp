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

//#define LOG_NDEBUG 0
#define LOG_TAG "C2Param_test"

#include <gtest/gtest.h>

#include <util/C2ParamUtils.h>
#include <C2ParamDef.h>

namespace android {

void PrintTo(const _C2FieldId &id, ::std::ostream* os) {
    *os << "@" << id._mOffset << "+" << id._mSize;
}

void PrintTo(const C2FieldDescriptor &fd, ::std::ostream *os) {
    using FD=C2FieldDescriptor;
    switch (fd.type()) {
    case FD::INT32: *os << "i32"; break;
    case FD::INT64: *os << "i64"; break;
    case FD::UINT32: *os << "u32"; break;
    case FD::UINT64: *os << "u64"; break;
    case FD::FLOAT: *os << "float"; break;
    case FD::STRING: *os << "char"; break;
    case FD::BLOB: *os << "u8"; break;
    default:
        if (fd.type() & FD::STRUCT_FLAG) {
            *os << "struct-" << (fd.type() & ~FD::STRUCT_FLAG);
        } else {
            *os << "type-" << fd.type();
        }
    }
    *os << " " << fd.name();
    if (fd.length() > 1) {
        *os << "[" << fd.length() << "]";
    } else if (fd.length() == 0) {
        *os << "[]";
    }
    *os << " (";
    PrintTo(fd._mFieldId, os);
    *os << "*" << fd.length() << ")";
}

enum C2ParamIndexType {
    kParamIndexNumber,
    kParamIndexNumbers,
    kParamIndexNumber2,
    kParamIndexVendorStart = C2Param::BaseIndex::kVendorStart,
    kParamIndexVendorNumbers,
};

void ffff(int(*)(int)) {}

/* ============================= STRUCT DECLARATION AND DESCRIPTION ============================= */

typedef C2FieldDescriptor FD;

class C2ParamTest : public ::testing::Test {
};

class C2ParamTest_ParamFieldList
        : public ::testing::TestWithParam<std::initializer_list<const C2FieldDescriptor>> {
};

enum {
    kParamIndexSize,
    kParamIndexTestA,
    kParamIndexTestB,
    kParamIndexTestFlexS32,
    kParamIndexTestFlexEndS32,
    kParamIndexTestFlexS64,
    kParamIndexTestFlexEndS64,
    kParamIndexTestFlexSize,
    kParamIndexTestFlexEndSize,
};

struct C2SizeStruct {
    int32_t mNumber;
    int32_t mHeight;
    enum : uint32_t { baseIndex = kParamIndexSize };                        // <= needed for C2FieldDescriptor
    const static std::initializer_list<const C2FieldDescriptor> fieldList;  // <= needed for C2FieldDescriptor
    const static FD::Type TYPE = (FD::Type)(baseIndex | FD::STRUCT_FLAG);
};

DEFINE_NO_NAMED_VALUES_FOR(C2SizeStruct)

// Test 1. define a structure without any helper methods

bool operator==(const C2FieldDescriptor &a, const C2FieldDescriptor &b) {
    return a.type() == b.type()
            && a.length() == b.length()
            && strcmp(a.name(), b.name()) == 0
            && a._mFieldId == b._mFieldId;
}

struct C2TestStruct_A {
    int32_t mSigned32;
    int64_t mSigned64[2];
    uint32_t mUnsigned32[1];
    uint64_t mUnsigned64;
    float mFloat;
    C2SizeStruct mSize[3];
    uint8_t mBlob[100];
    char mString[100];
    bool mYesNo[100];

    const static std::initializer_list<const C2FieldDescriptor> fieldList;
    // enum : uint32_t { baseIndex = kParamIndexTest };
    // typedef C2TestStruct_A _type;
} __attribute__((packed));

const std::initializer_list<const C2FieldDescriptor> C2TestStruct_A::fieldList =
    { { FD::INT32,    1, "s32",   0, 4 },
      { FD::INT64,    2, "s64",   4, 8 },
      { FD::UINT32,   1, "u32",  20, 4 },
      { FD::UINT64,   1, "u64",  24, 8 },
      { FD::FLOAT,    1, "fp",   32, 4 },
      { C2SizeStruct::TYPE, 3, "size", 36, 8 },
      { FD::BLOB,   100, "blob", 60, 1 },
      { FD::STRING, 100, "str", 160, 1 },
      { FD::BLOB,   100, "y-n", 260, 1 } };

TEST_P(C2ParamTest_ParamFieldList, VerifyStruct) {
    std::vector<const C2FieldDescriptor> fields = GetParam(), expected = C2TestStruct_A::fieldList;

    // verify first field descriptor
    EXPECT_EQ(FD::INT32, fields[0].type());
    EXPECT_STREQ("s32", fields[0].name());
    EXPECT_EQ(1u, fields[0].length());
    EXPECT_EQ(_C2FieldId(0, 4), fields[0]._mFieldId);

    EXPECT_EQ(expected[0], fields[0]);
    EXPECT_EQ(expected[1], fields[1]);
    EXPECT_EQ(expected[2], fields[2]);
    EXPECT_EQ(expected[3], fields[3]);
    EXPECT_EQ(expected[4], fields[4]);
    EXPECT_EQ(expected[5], fields[5]);
    EXPECT_EQ(expected[6], fields[6]);
    EXPECT_EQ(expected[7], fields[7]);
    for (size_t i = 8; i < fields.size() && i < expected.size(); ++i) {
        EXPECT_EQ(expected[i], fields[i]);
    }
}

INSTANTIATE_TEST_CASE_P(InitializerList, C2ParamTest_ParamFieldList, ::testing::Values(C2TestStruct_A::fieldList));

// define fields using C2FieldDescriptor pointer constructor
const std::initializer_list<const C2FieldDescriptor> C2TestStruct_A_FD_PTR_fieldList =
    { C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mSigned32,   "s32"),
      C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mSigned64,   "s64"),
      C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mUnsigned32, "u32"),
      C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mUnsigned64, "u64"),
      C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mFloat,      "fp"),
      C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mSize,       "size"),
      C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mBlob,       "blob"),
      C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mString,     "str"),
    //  C2FieldDescriptor(&((C2TestStruct_A*)(nullptr))->mYesNo,      "y-n")
    };

INSTANTIATE_TEST_CASE_P(PointerConstructor, C2ParamTest_ParamFieldList, ::testing::Values(C2TestStruct_A_FD_PTR_fieldList));

// define fields using C2FieldDescriptor member-pointer constructor
const std::initializer_list<const C2FieldDescriptor> C2TestStruct_A_FD_MEM_PTR_fieldList =
    { C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mSigned32,   "s32"),
      C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mSigned64,   "s64"),
      C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mUnsigned32, "u32"),
      C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mUnsigned64, "u64"),
      C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mFloat,      "fp"),
      C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mSize,       "size"),
      C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mBlob,       "blob"),
      C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mString,     "str"),
    //  C2FieldDescriptor((C2TestStruct_A*)0, &C2TestStruct_A::mYesNo,      "y-n")
    };

INSTANTIATE_TEST_CASE_P(MemberPointerConstructor, C2ParamTest_ParamFieldList, ::testing::Values(C2TestStruct_A_FD_MEM_PTR_fieldList));

// Test 2. define a structure with two-step helper methods

struct C2TestAStruct {
    int32_t mSigned32;
    int64_t mSigned64[2];
    uint32_t mUnsigned32[1];
    uint64_t mUnsigned64;
    float mFloat;
    C2SizeStruct mSize[3];
    uint8_t mBlob[100];
    char mString[100];
    bool mYesNo[100];

private: // test access level
    DEFINE_C2STRUCT(TestA)
} C2_PACK;

DESCRIBE_C2STRUCT(TestA, {
    C2FIELD(mSigned32, "s32")
    C2FIELD(mSigned64, "s64")
    C2FIELD(mUnsigned32, "u32")
    C2FIELD(mUnsigned64, "u64")
    C2FIELD(mFloat, "fp")
    C2FIELD(mSize, "size")
    C2FIELD(mBlob, "blob")
    C2FIELD(mString, "str")
    // C2FIELD(mYesNo, "y-n")
}) // ; optional

INSTANTIATE_TEST_CASE_P(DescribeStruct2Step, C2ParamTest_ParamFieldList, ::testing::Values(C2TestAStruct::fieldList));

// Test 3. define a structure with one-step helper method

struct C2TestBStruct {
    int32_t mSigned32;
    int64_t mSigned64[2];
    uint32_t mUnsigned32[1];
    uint64_t mUnsigned64;
    float mFloat;
    C2SizeStruct mSize[3];
    uint8_t mBlob[100];
    char mString[100];
    bool mYesNo[100];

private: // test access level
    DEFINE_AND_DESCRIBE_C2STRUCT(TestB)

    C2FIELD(mSigned32, "s32")
    C2FIELD(mSigned64, "s64")
    C2FIELD(mUnsigned32, "u32")
    C2FIELD(mUnsigned64, "u64")
    C2FIELD(mFloat, "fp")
    C2FIELD(mSize, "size")
    C2FIELD(mBlob, "blob")
    C2FIELD(mString, "str")
    // C2FIELD(mYesNo, "y-n")
};

INSTANTIATE_TEST_CASE_P(DescribeStruct1Step, C2ParamTest_ParamFieldList, ::testing::Values(C2TestBStruct::fieldList));

// Test 4. flexible members

template<typename T>
class C2ParamTest_FlexParamFieldList : public ::testing::Test {
protected:
    using Type=FD::Type;

    // static std::initializer_list<std::initializer_list<const C2FieldDescriptor>>
    static std::vector<std::vector<const C2FieldDescriptor>>
            GetLists();

    constexpr static Type flexType =
            std::is_same<T, int32_t>::value ? FD::INT32 :
            std::is_same<T, int64_t>::value ? FD::INT64 :
            std::is_same<T, uint32_t>::value ? FD::UINT32 :
            std::is_same<T, uint64_t>::value ? FD::UINT64 :
            std::is_same<T, float>::value ? FD::FLOAT :
            std::is_same<T, uint8_t>::value ? FD::BLOB :
            std::is_same<T, char>::value ? FD::STRING :
            std::is_same<T, C2SizeStruct>::value ? C2SizeStruct::TYPE : (Type)0;
    constexpr static size_t flexSize = sizeof(T);
};

typedef ::testing::Types<int32_t, int64_t, C2SizeStruct> FlexTypes;
TYPED_TEST_CASE(C2ParamTest_FlexParamFieldList, FlexTypes);

TYPED_TEST(C2ParamTest_FlexParamFieldList, VerifyStruct) {
    for (auto a : this->GetLists()) {
        std::vector<const C2FieldDescriptor> fields = a;
        if (fields.size() > 1) {
            EXPECT_EQ(2u, fields.size());
            EXPECT_EQ(C2FieldDescriptor(FD::INT32, 1, "s32", 0, 4), fields[0]);
            EXPECT_EQ(C2FieldDescriptor(this->flexType, 0, "flex", 4, this->flexSize),
                      fields[1]);
        } else {
            EXPECT_EQ(1u, fields.size());
            EXPECT_EQ(C2FieldDescriptor(this->flexType, 0, "flex", 0, this->flexSize),
                      fields[0]);
        }
    }
}

struct C2TestStruct_FlexS32 {
    int32_t mFlex[];

    const static std::initializer_list<const C2FieldDescriptor> fieldList;
    // enum : uint32_t { baseIndex = kParamIndexTestFlex, flexSize = 4 };
    // typedef C2TestStruct_FlexS32 _type;
    // typedef int32_t flexType;
};

const std::initializer_list<const C2FieldDescriptor> C2TestStruct_FlexS32::fieldList = {
    { FD::INT32, 0, "flex", 0, 4 }
};

struct C2TestStruct_FlexEndS32 {
    int32_t mSigned32;
    int32_t mFlex[];

    const static std::initializer_list<const C2FieldDescriptor> fieldList;
    // enum : uint32_t { baseIndex = kParamIndexTestFlexEnd, flexSize = 4 };
    // typedef C2TestStruct_FlexEnd _type;
    // typedef int32_t flexType;
};

const std::initializer_list<const C2FieldDescriptor> C2TestStruct_FlexEndS32::fieldList = {
    { FD::INT32, 1, "s32", 0, 4 },
    { FD::INT32, 0, "flex", 4, 4 },
};

const static std::initializer_list<const C2FieldDescriptor> C2TestStruct_FlexEndS32_ptr_fieldList = {
    C2FieldDescriptor(&((C2TestStruct_FlexEndS32*)0)->mSigned32, "s32"),
    C2FieldDescriptor(&((C2TestStruct_FlexEndS32*)0)->mFlex, "flex"),
};

struct C2TestFlexS32Struct {
    int32_t mFlexSigned32[];
private: // test access level
    C2TestFlexS32Struct() {}

    DEFINE_AND_DESCRIBE_FLEX_C2STRUCT(TestFlexS32, mFlexSigned32)
    C2FIELD(mFlexSigned32, "flex")
};

struct C2TestFlexEndS32Struct {
    int32_t mSigned32;
    int32_t mFlexSigned32[];
private: // test access level
    C2TestFlexEndS32Struct() {}

    DEFINE_FLEX_C2STRUCT(TestFlexEndS32, mFlexSigned32)
} C2_PACK;

DESCRIBE_C2STRUCT(TestFlexEndS32, {
    C2FIELD(mSigned32, "s32")
    C2FIELD(mFlexSigned32, "flex")
}) // ; optional

template<>
std::vector<std::vector<const C2FieldDescriptor>>
//std::initializer_list<std::initializer_list<const C2FieldDescriptor>>
C2ParamTest_FlexParamFieldList<int32_t>::GetLists() {
    return {
        C2TestStruct_FlexS32::fieldList,
        C2TestStruct_FlexEndS32::fieldList,
        C2TestStruct_FlexEndS32_ptr_fieldList,
        C2TestFlexS32Struct::fieldList,
        C2TestFlexEndS32Struct::fieldList,
    };
}

struct C2TestStruct_FlexS64 {
    int64_t mFlexSigned64[];

    const static std::initializer_list<const C2FieldDescriptor> fieldList;
    // enum : uint32_t { baseIndex = kParamIndexTestFlexS64, flexSize = 8 };
    // typedef C2TestStruct_FlexS64 _type;
    // typedef int64_t flexType;
};

const std::initializer_list<const C2FieldDescriptor> C2TestStruct_FlexS64::fieldList = {
    { FD::INT64, 0, "flex", 0, 8 }
};

struct C2TestStruct_FlexEndS64 {
    int32_t mSigned32;
    int64_t mSigned64Flex[];

    const static std::initializer_list<const C2FieldDescriptor> fieldList;
    // enum : uint32_t { baseIndex = C2TestStruct_FlexEndS64, flexSize = 8 };
    // typedef C2TestStruct_FlexEndS64 _type;
    // typedef int64_t flexType;
};

const std::initializer_list<const C2FieldDescriptor> C2TestStruct_FlexEndS64::fieldList = {
    { FD::INT32, 1, "s32", 0, 4 },
    { FD::INT64, 0, "flex", 4, 8 },
};

struct C2TestFlexS64Struct {
    int64_t mFlexSigned64[];
    C2TestFlexS64Struct() {}

    DEFINE_AND_DESCRIBE_FLEX_C2STRUCT(TestFlexS64, mFlexSigned64)
    C2FIELD(mFlexSigned64, "flex")
};

struct C2TestFlexEndS64Struct {
    int32_t mSigned32;
    int64_t mFlexSigned64[];
    C2TestFlexEndS64Struct() {}

    DEFINE_FLEX_C2STRUCT(TestFlexEndS64, mFlexSigned64)
} C2_PACK;

DESCRIBE_C2STRUCT(TestFlexEndS64, {
    C2FIELD(mSigned32, "s32")
    C2FIELD(mFlexSigned64, "flex")
}) // ; optional

template<>
std::vector<std::vector<const C2FieldDescriptor>>
//std::initializer_list<std::initializer_list<const C2FieldDescriptor>>
C2ParamTest_FlexParamFieldList<int64_t>::GetLists() {
    return {
        C2TestStruct_FlexS64::fieldList,
        C2TestStruct_FlexEndS64::fieldList,
        C2TestFlexS64Struct::fieldList,
        C2TestFlexEndS64Struct::fieldList,
    };
}

struct C2TestStruct_FlexSize {
    C2SizeStruct mFlexSize[];

    const static std::initializer_list<const C2FieldDescriptor> fieldList;
    // enum : uint32_t { baseIndex = kParamIndexTestFlexSize, flexSize = 8 };
    // typedef C2TestStruct_FlexSize _type;
    // typedef C2SizeStruct flexType;
};

const std::initializer_list<const C2FieldDescriptor> C2TestStruct_FlexSize::fieldList = {
    { C2SizeStruct::TYPE, 0, "flex", 0, sizeof(C2SizeStruct) }
};

struct C2TestStruct_FlexEndSize {
    int32_t mSigned32;
    C2SizeStruct mSizeFlex[];

    const static std::initializer_list<const C2FieldDescriptor> fieldList;
    // enum : uint32_t { baseIndex = C2TestStruct_FlexEndSize, flexSize = 8 };
    // typedef C2TestStruct_FlexEndSize _type;
    // typedef C2SizeStruct flexType;
};

const std::initializer_list<const C2FieldDescriptor> C2TestStruct_FlexEndSize::fieldList = {
    { FD::INT32, 1, "s32", 0, 4 },
    { C2SizeStruct::TYPE, 0, "flex", 4, sizeof(C2SizeStruct) },
};

struct C2TestFlexSizeStruct {
    C2SizeStruct mFlexSize[];
    C2TestFlexSizeStruct() {}

    DEFINE_AND_DESCRIBE_FLEX_C2STRUCT(TestFlexSize, mFlexSize)
    C2FIELD(mFlexSize, "flex")
};

struct C2TestFlexEndSizeStruct {
    int32_t mSigned32;
    C2SizeStruct mFlexSize[];
    C2TestFlexEndSizeStruct() {}

    DEFINE_FLEX_C2STRUCT(TestFlexEndSize, mFlexSize)
} C2_PACK;

DESCRIBE_C2STRUCT(TestFlexEndSize, {
    C2FIELD(mSigned32, "s32")
    C2FIELD(mFlexSize, "flex")
}) // ; optional

template<>
std::vector<std::vector<const C2FieldDescriptor>>
//std::initializer_list<std::initializer_list<const C2FieldDescriptor>>
C2ParamTest_FlexParamFieldList<C2SizeStruct>::GetLists() {
    return {
        C2TestStruct_FlexSize::fieldList,
        C2TestStruct_FlexEndSize::fieldList,
        C2TestFlexSizeStruct::fieldList,
        C2TestFlexEndSizeStruct::fieldList,
    };
}

TEST_F(C2ParamTest, FieldId) {
    // pointer constructor
    EXPECT_EQ(_C2FieldId(0, 4), _C2FieldId(&((C2TestStruct_A*)0)->mSigned32));
    EXPECT_EQ(_C2FieldId(4, 8), _C2FieldId(&((C2TestStruct_A*)0)->mSigned64));
    EXPECT_EQ(_C2FieldId(20, 4), _C2FieldId(&((C2TestStruct_A*)0)->mUnsigned32));
    EXPECT_EQ(_C2FieldId(24, 8), _C2FieldId(&((C2TestStruct_A*)0)->mUnsigned64));
    EXPECT_EQ(_C2FieldId(32, 4), _C2FieldId(&((C2TestStruct_A*)0)->mFloat));
    EXPECT_EQ(_C2FieldId(36, 8), _C2FieldId(&((C2TestStruct_A*)0)->mSize));
    EXPECT_EQ(_C2FieldId(60, 1), _C2FieldId(&((C2TestStruct_A*)0)->mBlob));
    EXPECT_EQ(_C2FieldId(160, 1), _C2FieldId(&((C2TestStruct_A*)0)->mString));
    EXPECT_EQ(_C2FieldId(260, 1), _C2FieldId(&((C2TestStruct_A*)0)->mYesNo));

    EXPECT_EQ(_C2FieldId(0, 4), _C2FieldId(&((C2TestFlexEndSizeStruct*)0)->mSigned32));
    EXPECT_EQ(_C2FieldId(4, 8), _C2FieldId(&((C2TestFlexEndSizeStruct*)0)->mFlexSize));

    // member pointer constructor
    EXPECT_EQ(_C2FieldId(0, 4), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mSigned32));
    EXPECT_EQ(_C2FieldId(4, 8), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mSigned64));
    EXPECT_EQ(_C2FieldId(20, 4), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mUnsigned32));
    EXPECT_EQ(_C2FieldId(24, 8), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mUnsigned64));
    EXPECT_EQ(_C2FieldId(32, 4), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mFloat));
    EXPECT_EQ(_C2FieldId(36, 8), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mSize));
    EXPECT_EQ(_C2FieldId(60, 1), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mBlob));
    EXPECT_EQ(_C2FieldId(160, 1), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mString));
    EXPECT_EQ(_C2FieldId(260, 1), _C2FieldId((C2TestStruct_A*)0, &C2TestStruct_A::mYesNo));

    EXPECT_EQ(_C2FieldId(0, 4), _C2FieldId((C2TestFlexEndSizeStruct*)0, &C2TestFlexEndSizeStruct::mSigned32));
    EXPECT_EQ(_C2FieldId(4, 8), _C2FieldId((C2TestFlexEndSizeStruct*)0, &C2TestFlexEndSizeStruct::mFlexSize));

    // member pointer sans type pointer
    EXPECT_EQ(_C2FieldId(0, 4), _C2FieldId(&C2TestStruct_A::mSigned32));
    EXPECT_EQ(_C2FieldId(4, 8), _C2FieldId(&C2TestStruct_A::mSigned64));
    EXPECT_EQ(_C2FieldId(20, 4), _C2FieldId(&C2TestStruct_A::mUnsigned32));
    EXPECT_EQ(_C2FieldId(24, 8), _C2FieldId(&C2TestStruct_A::mUnsigned64));
    EXPECT_EQ(_C2FieldId(32, 4), _C2FieldId(&C2TestStruct_A::mFloat));
    EXPECT_EQ(_C2FieldId(36, 8), _C2FieldId(&C2TestStruct_A::mSize));
    EXPECT_EQ(_C2FieldId(60, 1), _C2FieldId(&C2TestStruct_A::mBlob));
    EXPECT_EQ(_C2FieldId(160, 1), _C2FieldId(&C2TestStruct_A::mString));
    EXPECT_EQ(_C2FieldId(260, 1), _C2FieldId(&C2TestStruct_A::mYesNo));

    EXPECT_EQ(_C2FieldId(0, 4), _C2FieldId(&C2TestFlexEndSizeStruct::mSigned32));
    EXPECT_EQ(_C2FieldId(4, 8), _C2FieldId(&C2TestFlexEndSizeStruct::mFlexSize));

    typedef C2GlobalParam<C2Info, C2TestAStruct> C2TestAInfo;
    typedef C2GlobalParam<C2Info, C2TestFlexEndSizeStruct> C2TestFlexEndSizeInfo;

    // pointer constructor in C2Param
    EXPECT_EQ(_C2FieldId(8, 4), _C2FieldId(&((C2TestAInfo*)0)->mSigned32));
    EXPECT_EQ(_C2FieldId(12, 8), _C2FieldId(&((C2TestAInfo*)0)->mSigned64));
    EXPECT_EQ(_C2FieldId(28, 4), _C2FieldId(&((C2TestAInfo*)0)->mUnsigned32));
    EXPECT_EQ(_C2FieldId(32, 8), _C2FieldId(&((C2TestAInfo*)0)->mUnsigned64));
    EXPECT_EQ(_C2FieldId(40, 4), _C2FieldId(&((C2TestAInfo*)0)->mFloat));
    EXPECT_EQ(_C2FieldId(44, 8), _C2FieldId(&((C2TestAInfo*)0)->mSize));
    EXPECT_EQ(_C2FieldId(68, 1), _C2FieldId(&((C2TestAInfo*)0)->mBlob));
    EXPECT_EQ(_C2FieldId(168, 1), _C2FieldId(&((C2TestAInfo*)0)->mString));
    EXPECT_EQ(_C2FieldId(268, 1), _C2FieldId(&((C2TestAInfo*)0)->mYesNo));

    EXPECT_EQ(_C2FieldId(8, 4), _C2FieldId(&((C2TestFlexEndSizeInfo*)0)->m.mSigned32));
    EXPECT_EQ(_C2FieldId(12, 8), _C2FieldId(&((C2TestFlexEndSizeInfo*)0)->m.mFlexSize));

    // member pointer in C2Param
    EXPECT_EQ(_C2FieldId(8, 4), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mSigned32));
    EXPECT_EQ(_C2FieldId(12, 8), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mSigned64));
    EXPECT_EQ(_C2FieldId(28, 4), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mUnsigned32));
    EXPECT_EQ(_C2FieldId(32, 8), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mUnsigned64));
    EXPECT_EQ(_C2FieldId(40, 4), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mFloat));
    EXPECT_EQ(_C2FieldId(44, 8), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mSize));
    EXPECT_EQ(_C2FieldId(68, 1), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mBlob));
    EXPECT_EQ(_C2FieldId(168, 1), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mString));
    EXPECT_EQ(_C2FieldId(268, 1), _C2FieldId((C2TestAInfo*)0, &C2TestAInfo::mYesNo));

    // NOTE: cannot use a member pointer for flex params due to introduction of 'm'
    // EXPECT_EQ(_C2FieldId(8, 4), _C2FieldId(&C2TestFlexEndSizeInfo::m.mSigned32));
    // EXPECT_EQ(_C2FieldId(12, 8), _C2FieldId(&C2TestFlexEndSizeInfo::m.mFlexSize));



}

struct S32 {
    template<typename T, class B=typename std::remove_extent<T>::type>
    inline S32(const T*) {
        static_assert(!std::is_array<T>::value, "should not be an array");
        static_assert(std::is_same<B, int32_t>::value, "should be int32_t");
    }
};

struct FLX {
    template<typename U, typename T, class B=typename std::remove_extent<T>::type>
    inline FLX(const T*, const U*) {
        static_assert(std::is_array<T>::value, "should be an array");
        static_assert(std::extent<T>::value == 0, "should be an array of 0 extent");
        static_assert(std::is_same<B, U>::value, "should be type U");
    }
};

struct MP {
    template<typename U, typename T, typename ExpectedU, typename UnexpectedU>
    inline MP(T U::*, const ExpectedU*, const UnexpectedU*) {
        static_assert(!std::is_same<U, UnexpectedU>::value, "should not be member pointer of the base type");
        static_assert(std::is_same<U, ExpectedU>::value, "should be member pointer of the derived type");
    }

    template<typename U, typename T, typename B, typename D>
    inline MP(T D::*, const D*) { }
};

void compiledStatic_arrayTypePropagationTest() {
    (void)S32(&((C2TestFlexEndS32Struct *)0)->mSigned32);
    (void)FLX(&((C2TestFlexEndS32Struct *)0)->mFlexSigned32, (int32_t*)0);
    (void)FLX(&((C2TestFlexS32Struct *)0)->mFlexSigned32, (int32_t*)0);

    typedef C2GlobalParam<C2Info, C2TestAStruct> C2TestAInfo;

    // TRICKY: &derivedClass::baseMember has type of baseClass::*
    static_assert(std::is_same<decltype(&C2TestAInfo::mSigned32), int32_t C2TestAStruct::*>::value,
                  "base member pointer should have base class in type");

    // therefore, member pointer expands to baseClass::* in templates
    (void)MP(&C2TestAInfo::mSigned32,
             (C2TestAStruct*)0 /* expected */, (C2TestAInfo*)0 /* unexpected */);
    // but can be cast to derivedClass::*
    (void)MP((int32_t C2TestAInfo::*)&C2TestAInfo::mSigned32,
             (C2TestAInfo*)0 /* expected */, (C2TestAStruct*)0 /* unexpected */);

    // TRICKY: baseClass::* does not autoconvert to derivedClass::* even in templates
    // (void)MP(&C2TestAInfo::mSigned32, (C2TestAInfo*)0);
}

TEST_F(C2ParamTest, MemberPointerCast) {
    typedef C2GlobalParam<C2Info, C2TestAStruct> C2TestAInfo;

    static_assert(offsetof(C2TestAInfo, mSigned32) == 8, "offset should be 8");
    constexpr int32_t C2TestAStruct::* s32ptr = &C2TestAInfo::mSigned32;
    constexpr int32_t C2TestAInfo::* s32ptr_derived = (int32_t C2TestAStruct::*)&C2TestAInfo::mSigned32;
    constexpr int32_t C2TestAInfo::* s32ptr_cast2derived = (int32_t C2TestAInfo::*)s32ptr;
    C2TestAInfo *info = (C2TestAInfo *)256;
    C2TestAStruct *strukt = (C2TestAStruct *)info;
    int32_t *info_s32_derived = &(info->*s32ptr_derived);
    int32_t *info_s32_cast2derived = &(info->*s32ptr_cast2derived);
    int32_t *info_s32 = &(info->*s32ptr);
    int32_t *strukt_s32 = &(strukt->*s32ptr);

    EXPECT_EQ(256u, (uintptr_t)info);
    EXPECT_EQ(264u, (uintptr_t)strukt);
    EXPECT_EQ(264u, (uintptr_t)info_s32_derived);
    EXPECT_EQ(264u, (uintptr_t)info_s32_cast2derived);
    EXPECT_EQ(264u, (uintptr_t)info_s32);
    EXPECT_EQ(264u, (uintptr_t)strukt_s32);

    typedef C2GlobalParam<C2Info, C2TestFlexEndSizeStruct> C2TestFlexEndSizeInfo;
    static_assert(offsetof(C2TestFlexEndSizeInfo, m.mSigned32) == 8, "offset should be 8");
    static_assert(offsetof(C2TestFlexEndSizeInfo, m.mFlexSize) == 12, "offset should be 12");
}

/* ===================================== PARAM USAGE TESTS ===================================== */

struct C2NumberStruct {
    int32_t mNumber;
    C2NumberStruct() {}
    C2NumberStruct(int32_t _number) : mNumber(_number) {}

    DEFINE_AND_DESCRIBE_C2STRUCT(Number)
    C2FIELD(mNumber, "number")
};

struct C2NumbersStruct {
    int32_t mNumbers[];
    C2NumbersStruct() {}

    DEFINE_AND_DESCRIBE_FLEX_C2STRUCT(Numbers, mNumbers)
    C2FIELD(mNumbers, "numbers")
};
static_assert(sizeof(C2NumbersStruct) == 0, "C2NumbersStruct has incorrect size");

typedef C2GlobalParam<C2Tuning, C2NumberStruct> C2NumberTuning;
typedef   C2PortParam<C2Tuning, C2NumberStruct> C2NumberPortTuning;
typedef C2StreamParam<C2Tuning, C2NumberStruct> C2NumberStreamTuning;

typedef C2GlobalParam<C2Tuning, C2NumbersStruct> C2NumbersTuning;
typedef   C2PortParam<C2Tuning, C2NumbersStruct> C2NumbersPortTuning;
typedef C2StreamParam<C2Tuning, C2NumbersStruct> C2NumbersStreamTuning;

//
#if 0

void test() {
    C2NumberStruct s(10);
    (void)C2NumberStruct::fieldList;
};

typedef C2StreamParam<C2Tuning, C2Int64Value, kParamIndexNumberB> C2NumberConfig4;
typedef C2PortParam<C2Tuning, C2Int32Value, kParamIndexNumber> C2NumberConfig3;
typedef C2GlobalParam<C2Tuning, C2StringValue, kParamIndexNumber> C2VideoNameConfig;

void test3() {
    C2NumberConfig3 s(10);
    s.mValue = 11;
    s = 12;
    (void)C2NumberConfig3::fieldList;
    std::shared_ptr<C2VideoNameConfig> n = C2VideoNameConfig::alloc_shared(25);
    strcpy(n->m.mValue, "lajos");
    C2NumberConfig4 t(false, 0, 11);
    t.mValue = 15;
};

struct C2NumbersStruct {
    int32_t mNumbers[];
    enum { baseIndex = kParamIndexNumber };
    const static std::initializer_list<const C2FieldDescriptor> fieldList;
    C2NumbersStruct() {}

    FLEX(C2NumbersStruct, mNumbers);
};

static_assert(sizeof(C2NumbersStruct) == 0, "yes");


typedef C2GlobalParam<C2Info, C2NumbersStruct> C2NumbersInfo;

const std::initializer_list<const C2FieldDescriptor> C2NumbersStruct::fieldList =
//    { { FD::INT32, 0, "widths" } };
    { C2FieldDescriptor(&((C2NumbersStruct*)(nullptr))->mNumbers, "number") };

typedef C2PortParam<C2Tuning, C2NumberStruct> C2NumberConfig;

std::list<const C2FieldDescriptor> myList = C2NumberConfig::fieldList;

    std::unique_ptr<android::C2ParamDescriptor> __test_describe(uint32_t paramType) {
        std::list<const C2FieldDescriptor> fields = describeC2Params<C2NumberConfig>();

        auto widths = C2NumbersInfo::alloc_shared(5);
        widths->flexCount();
        widths->m.mNumbers[4] = 1;

        test();
        test3();

        C2NumberConfig outputWidth(false, 123);

        C2Param::Index index(paramType);
        switch (paramType) {
        case C2NumberConfig::baseIndex:
            return std::unique_ptr<C2ParamDescriptor>(new C2ParamDescriptor{
                true /* isRequired */,
                "number",
                index,
            });
        }
        return nullptr;
    }


} // namespace android

#endif
//

template<typename T>
bool canSetPort(T &o, bool output) { return o.setPort(output); }
bool canSetPort(...) { return false; }

template<typename S, typename=decltype(((S*)0)->setPort(true))>
static std::true_type _canCallSetPort(int);
template<typename>
static std::false_type _canCallSetPort(...);
#define canCallSetPort(x) decltype(_canCallSetPort<std::remove_reference<decltype(x)>::type>(0))::value

/* ======================================= STATIC TESTS ======================================= */

static_assert(_C2Comparable<int>::value, "int is not comparable");
static_assert(!_C2Comparable<void>::value, "void is comparable");

struct C2_HIDE _test0 {
    bool operator==(const _test0&);
    bool operator!=(const _test0&);
};
struct C2_HIDE _test1 {
    bool operator==(const _test1&);
};
struct C2_HIDE _test2 {
    bool operator!=(const _test2&);
};
static_assert(_C2Comparable<_test0>::value, "class with == and != is not comparable");
static_assert(_C2Comparable<_test1>::value, "class with == is not comparable");
static_assert(_C2Comparable<_test2>::value, "class with != is not comparable");

/* ======================================= C2PARAM TESTS ======================================= */

struct _C2ParamInspector {
    static void StaticTest();
    static void StaticFlexTest();
};

// TEST_F(_C2ParamInspector, StaticTest) {
void _C2ParamInspector::StaticTest() {
    typedef C2Param::Index I;

    // C2NumberStruct: baseIndex = kIndex                          (args)
    static_assert(C2NumberStruct::baseIndex == kParamIndexNumber, "bad index");
    static_assert(sizeof(C2NumberStruct) == 4, "bad size");

    // C2NumberTuning:             kIndex | tun | global           (args)
    static_assert(C2NumberTuning::baseIndex == kParamIndexNumber, "bad index");
    static_assert(C2NumberTuning::typeIndex == (kParamIndexNumber | I::kTypeTuning | I::kDirGlobal), "bad index");
    static_assert(sizeof(C2NumberTuning) == 12, "bad size");

    static_assert(offsetof(C2NumberTuning, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumberTuning, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumberTuning, mNumber) == 8, "bad offset");

    // C2NumberPortTuning:         kIndex | tun | port             (bool, args)
    static_assert(sizeof(C2NumberPortTuning) == 12, "bad size");
    // C2NumberPortTuning::input:  kIndex | tun | port | input     (args)
    // C2NumberPortTuning::output: kIndex | tun | port | output    (args)
    static_assert(C2NumberPortTuning::input::baseIndex ==
                  kParamIndexNumber, "bad index");
    static_assert(C2NumberPortTuning::input::typeIndex ==
                  (kParamIndexNumber | I::kTypeTuning | I::kDirInput), "bad index");
    static_assert(C2NumberPortTuning::output::baseIndex ==
                  kParamIndexNumber, "bad index");
    static_assert(C2NumberPortTuning::output::typeIndex ==
                  (kParamIndexNumber | I::kTypeTuning | I::kDirOutput), "bad index");
    static_assert(sizeof(C2NumberPortTuning::input) == 12, "bad size");
    static_assert(sizeof(C2NumberPortTuning::output) == 12, "bad size");
    static_assert(offsetof(C2NumberPortTuning::input, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumberPortTuning::input, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumberPortTuning::input, mNumber) == 8, "bad offset");
    static_assert(offsetof(C2NumberPortTuning::output, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumberPortTuning::output, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumberPortTuning::output, mNumber) == 8, "bad offset");

    // C2NumberStreamTuning:       kIndex | tun | str              (bool, uint, args)
    static_assert(sizeof(C2NumberStreamTuning) == 12u, "bad size");
    // C2NumberStreamTuning::input kIndex | tun | str | input      (int, args)
    // C2NumberStreamTuning::output kIx   | tun | str | output     (int, args)
    static_assert(C2NumberStreamTuning::input::baseIndex ==
                  kParamIndexNumber, "bad index");
    static_assert(C2NumberStreamTuning::input::typeIndex ==
                  (kParamIndexNumber | I::kTypeTuning | I::kDirInput | I::kStreamFlag), "bad index");
    static_assert(C2NumberStreamTuning::output::baseIndex ==
                  kParamIndexNumber, "bad index");
    static_assert(C2NumberStreamTuning::output::typeIndex ==
                  (kParamIndexNumber | I::kTypeTuning | I::kDirOutput | I::kStreamFlag), "bad index");
    static_assert(sizeof(C2NumberStreamTuning::input) == 12u, "bad size");
    static_assert(sizeof(C2NumberStreamTuning::output) == 12u, "bad size");
    static_assert(offsetof(C2NumberStreamTuning::input, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumberStreamTuning::input, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumberStreamTuning::input, mNumber) == 8, "bad offset");
    static_assert(offsetof(C2NumberStreamTuning::output, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumberStreamTuning::output, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumberStreamTuning::output, mNumber) == 8, "bad offset");
}

void _C2ParamInspector::StaticFlexTest() {
    typedef C2Param::Index I;

    // C2NumbersStruct: baseIndex = kIndex                          (args)
    static_assert(C2NumbersStruct::baseIndex == (I::kFlexibleFlag | kParamIndexNumbers), "bad index");
    static_assert(sizeof(C2NumbersStruct) == 0, "bad size");

    // C2NumbersTuning:             kIndex | tun | global           (args)
    static_assert(C2NumbersTuning::baseIndex == (I::kFlexibleFlag | kParamIndexNumbers), "bad index");
    static_assert(C2NumbersTuning::typeIndex == (I::kFlexibleFlag | kParamIndexNumbers | I::kTypeTuning | I::kDirGlobal), "bad index");
    static_assert(sizeof(C2NumbersTuning) == 8, "bad size");

    static_assert(offsetof(C2NumbersTuning, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumbersTuning, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumbersTuning, m.mNumbers) == 8, "bad offset");

    // C2NumbersPortTuning:         kIndex | tun | port             (bool, args)
    static_assert(sizeof(C2NumbersPortTuning) == 8, "bad size");
    // C2NumbersPortTuning::input:  kIndex | tun | port | input     (args)
    // C2NumbersPortTuning::output: kIndex | tun | port | output    (args)
    static_assert(C2NumbersPortTuning::input::baseIndex ==
                  (I::kFlexibleFlag | kParamIndexNumbers), "bad index");
    static_assert(C2NumbersPortTuning::input::typeIndex ==
                  (I::kFlexibleFlag | kParamIndexNumbers | I::kTypeTuning | I::kDirInput), "bad index");
    static_assert(C2NumbersPortTuning::output::baseIndex ==
                  (I::kFlexibleFlag | kParamIndexNumbers), "bad index");
    static_assert(C2NumbersPortTuning::output::typeIndex ==
                  (I::kFlexibleFlag | kParamIndexNumbers | I::kTypeTuning | I::kDirOutput), "bad index");
    static_assert(sizeof(C2NumbersPortTuning::input) == 8, "bad size");
    static_assert(sizeof(C2NumbersPortTuning::output) == 8, "bad size");
    static_assert(offsetof(C2NumbersPortTuning::input, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumbersPortTuning::input, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumbersPortTuning::input, m.mNumbers) == 8, "bad offset");
    static_assert(offsetof(C2NumbersPortTuning::output, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumbersPortTuning::output, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumbersPortTuning::output, m.mNumbers) == 8, "bad offset");

    // C2NumbersStreamTuning:       kIndex | tun | str              (bool, uint, args)
    static_assert(sizeof(C2NumbersStreamTuning) == 8, "bad size");
    // C2NumbersStreamTuning::input kIndex | tun | str | input      (int, args)
    // C2NumbersStreamTuning::output kIx   | tun | str | output     (int, args)
    static_assert(C2NumbersStreamTuning::input::baseIndex ==
                  (I::kFlexibleFlag | kParamIndexNumbers), "bad index");
    static_assert(C2NumbersStreamTuning::input::typeIndex ==
                  (I::kFlexibleFlag | kParamIndexNumbers | I::kTypeTuning | I::kDirInput | I::kStreamFlag), "bad index");
    static_assert(C2NumbersStreamTuning::output::baseIndex ==
                  (I::kFlexibleFlag | kParamIndexNumbers), "bad index");
    static_assert(C2NumbersStreamTuning::output::typeIndex ==
                  (I::kFlexibleFlag | kParamIndexNumbers | I::kTypeTuning | I::kDirOutput | I::kStreamFlag), "bad index");
    static_assert(sizeof(C2NumbersStreamTuning::input) == 8, "bad size");
    static_assert(sizeof(C2NumbersStreamTuning::output) == 8, "bad size");
    static_assert(offsetof(C2NumbersStreamTuning::input, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumbersStreamTuning::input, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumbersStreamTuning::input, m.mNumbers) == 8, "bad offset");
    static_assert(offsetof(C2NumbersStreamTuning::output, _mSize) == 0, "bad size");
    static_assert(offsetof(C2NumbersStreamTuning::output, _mIndex) == 4, "bad offset");
    static_assert(offsetof(C2NumbersStreamTuning::output, m.mNumbers) == 8, "bad offset");
}

TEST_F(C2ParamTest, ParamOpsTest) {
    const C2NumberStruct str(100);
    C2NumberStruct bstr;

    {
        EXPECT_EQ(100, str.mNumber);
        bstr.mNumber = 100;

        C2Param::BaseIndex index = C2NumberStruct::baseIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_FALSE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumber);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumber);
    }

    const C2NumberTuning tun(100);
    C2NumberTuning btun;

    {
        // flags & invariables
        for (const auto &p : { tun, btun }) {
            EXPECT_TRUE((bool)p);
            EXPECT_FALSE(!p);
            EXPECT_EQ(12u, p.size());

            EXPECT_FALSE(p.isVendor());
            EXPECT_FALSE(p.isFlexible());
            EXPECT_TRUE(p.isGlobal());
            EXPECT_FALSE(p.forInput());
            EXPECT_FALSE(p.forOutput());
            EXPECT_FALSE(p.forStream());
            EXPECT_FALSE(p.forPort());
        }

        // value
        EXPECT_EQ(100, tun.mNumber);
        EXPECT_EQ(0, btun.mNumber);
        EXPECT_FALSE(tun == btun);
        EXPECT_FALSE(tun.operator==(btun));
        EXPECT_TRUE(tun != btun);
        EXPECT_TRUE(tun.operator!=(btun));
        btun.mNumber = 100;
        EXPECT_EQ(tun, btun);

        // index
        EXPECT_EQ(C2Param::Type(tun.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(tun.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(tun.type(), C2NumberTuning::typeIndex);
        EXPECT_EQ(tun.stream(), ~0u);

        C2Param::BaseIndex index = C2NumberTuning::baseIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_FALSE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumber);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumber);

        C2Param::Type type = C2NumberTuning::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_FALSE(type.isFlexible());
        EXPECT_TRUE(type.isGlobal());
        EXPECT_FALSE(type.forInput());
        EXPECT_FALSE(type.forOutput());
        EXPECT_FALSE(type.forStream());
        EXPECT_FALSE(type.forPort());

        EXPECT_EQ(C2NumberTuning::From(nullptr), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&tun), &tun);
        EXPECT_EQ(C2NumberPortTuning::From(&tun), nullptr);
        EXPECT_EQ(C2NumberPortTuning::input::From(&tun), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(&tun), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::From(&tun), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&tun), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&tun), nullptr);
    }

    const C2NumberPortTuning outp1(true, 100), inp1(false, 100);
    C2NumberPortTuning boutp1, binp1, binp3(false, 100);
    const C2NumberPortTuning::input inp2(100);
    C2NumberPortTuning::input binp2;
    const C2NumberPortTuning::output outp2(100);
    C2NumberPortTuning::output boutp2;

    {
        static_assert(canCallSetPort(binp3), "should be able to");
        static_assert(canCallSetPort(binp1), "should be able to");
        static_assert(!canCallSetPort(inp1), "should not be able to (const)");
        static_assert(!canCallSetPort(inp2), "should not be able to (const & type)");
        static_assert(!canCallSetPort(binp2), "should not be able to (type)");

        // flags & invariables
        for (const auto &p : { outp1, inp1, boutp1 }) {
            EXPECT_EQ(12u, p.size());
            EXPECT_FALSE(p.isVendor());
            EXPECT_FALSE(p.isFlexible());
            EXPECT_FALSE(p.isGlobal());
            EXPECT_FALSE(p.forStream());
            EXPECT_TRUE(p.forPort());
        }
        for (const auto &p : { inp2, binp2 }) {
            EXPECT_EQ(12u, p.size());
            EXPECT_FALSE(p.isVendor());
            EXPECT_FALSE(p.isFlexible());
            EXPECT_FALSE(p.isGlobal());
            EXPECT_FALSE(p.forStream());
            EXPECT_TRUE(p.forPort());
        }
        for (const auto &p : { outp2, boutp2 }) {
            EXPECT_EQ(12u, p.size());
            EXPECT_FALSE(p.isVendor());
            EXPECT_FALSE(p.isFlexible());
            EXPECT_FALSE(p.isGlobal());
            EXPECT_FALSE(p.forStream());
            EXPECT_TRUE(p.forPort());
        }

        // port specific flags & invariables
        EXPECT_FALSE(outp1.forInput());
        EXPECT_TRUE(outp1.forOutput());

        EXPECT_TRUE(inp1.forInput());
        EXPECT_FALSE(inp1.forOutput());

        for (const auto &p : { outp1, inp1 }) {
            EXPECT_TRUE((bool)p);
            EXPECT_FALSE(!p);
            EXPECT_EQ(100, p.mNumber);
        }
        for (const auto &p : { outp2, boutp2 }) {
            EXPECT_TRUE((bool)p);
            EXPECT_FALSE(!p);

            EXPECT_FALSE(p.forInput());
            EXPECT_TRUE(p.forOutput());
        }
        for (const auto &p : { inp2, binp2 }) {
            EXPECT_TRUE((bool)p);
            EXPECT_FALSE(!p);

            EXPECT_TRUE(p.forInput());
            EXPECT_FALSE(p.forOutput());
        }
        for (const auto &p : { boutp1 } ) {
            EXPECT_FALSE((bool)p);
            EXPECT_TRUE(!p);

            EXPECT_FALSE(p.forInput());
            EXPECT_FALSE(p.forOutput());
            EXPECT_EQ(0, p.mNumber);
        }

        // values
        EXPECT_EQ(100, inp2.mNumber);
        EXPECT_EQ(100, outp2.mNumber);
        EXPECT_EQ(0, binp1.mNumber);
        EXPECT_EQ(0, binp2.mNumber);
        EXPECT_EQ(0, boutp1.mNumber);
        EXPECT_EQ(0, boutp2.mNumber);

        EXPECT_TRUE(inp1 != outp1);
        EXPECT_TRUE(inp1 == inp2);
        EXPECT_TRUE(outp1 == outp2);
        EXPECT_TRUE(binp1 == boutp1);
        EXPECT_TRUE(binp2 != boutp2);

        EXPECT_TRUE(inp1 != binp1);
        binp1.mNumber = 100;
        EXPECT_TRUE(inp1 != binp1);
        binp1.setPort(false /* output */);
        EXPECT_TRUE((bool)binp1);
        EXPECT_FALSE(!binp1);
        EXPECT_TRUE(inp1 == binp1);

        EXPECT_TRUE(inp2 != binp2);
        binp2.mNumber = 100;
        EXPECT_TRUE(inp2 == binp2);

        binp1.setPort(true /* output */);
        EXPECT_TRUE(outp1 == binp1);

        EXPECT_TRUE(outp1 != boutp1);
        boutp1.mNumber = 100;
        EXPECT_TRUE(outp1 != boutp1);
        boutp1.setPort(true /* output */);
        EXPECT_TRUE((bool)boutp1);
        EXPECT_FALSE(!boutp1);
        EXPECT_TRUE(outp1 == boutp1);

        EXPECT_TRUE(outp2 != boutp2);
        boutp2.mNumber = 100;
        EXPECT_TRUE(outp2 == boutp2);

        boutp1.setPort(false /* output */);
        EXPECT_TRUE(inp1 == boutp1);

        // index
        EXPECT_EQ(C2Param::Type(inp1.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(inp1.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(inp1.type(), C2NumberPortTuning::input::typeIndex);
        EXPECT_EQ(inp1.stream(), ~0u);

        EXPECT_EQ(C2Param::Type(inp2.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(inp2.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(inp2.type(), C2NumberPortTuning::input::typeIndex);
        EXPECT_EQ(inp2.stream(), ~0u);

        EXPECT_EQ(C2Param::Type(outp1.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(outp1.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(outp1.type(), C2NumberPortTuning::output::typeIndex);
        EXPECT_EQ(outp1.stream(), ~0u);

        EXPECT_EQ(C2Param::Type(outp2.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(outp2.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(outp2.type(), C2NumberPortTuning::output::typeIndex);
        EXPECT_EQ(outp2.stream(), ~0u);

        C2Param::BaseIndex index = C2NumberPortTuning::input::typeIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_FALSE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumber);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumber);

        index = C2NumberPortTuning::output::typeIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_FALSE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumber);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumber);

        C2Param::Type type = C2NumberPortTuning::input::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_FALSE(type.isFlexible());
        EXPECT_FALSE(type.isGlobal());
        EXPECT_TRUE(type.forInput());
        EXPECT_FALSE(type.forOutput());
        EXPECT_FALSE(type.forStream());
        EXPECT_TRUE(type.forPort());

        type = C2NumberPortTuning::output::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_FALSE(type.isFlexible());
        EXPECT_FALSE(type.isGlobal());
        EXPECT_FALSE(type.forInput());
        EXPECT_TRUE(type.forOutput());
        EXPECT_FALSE(type.forStream());
        EXPECT_TRUE(type.forPort());

        EXPECT_EQ(C2NumberPortTuning::From(nullptr), nullptr);
        EXPECT_EQ(C2NumberPortTuning::input::From(nullptr), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(nullptr), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&inp1), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&inp2), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&outp1), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&outp2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::From(&inp1), &inp1);
        EXPECT_EQ(C2NumberPortTuning::From(&inp2), (C2NumberPortTuning*)&inp2);
        EXPECT_EQ(C2NumberPortTuning::From(&outp1), &outp1);
        EXPECT_EQ(C2NumberPortTuning::From(&outp2), (C2NumberPortTuning*)&outp2);
        EXPECT_EQ(C2NumberPortTuning::input::From(&inp1), (C2NumberPortTuning::input*)&inp1);
        EXPECT_EQ(C2NumberPortTuning::input::From(&inp2), &inp2);
        EXPECT_EQ(C2NumberPortTuning::input::From(&outp1), nullptr);
        EXPECT_EQ(C2NumberPortTuning::input::From(&outp2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(&inp1), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(&inp2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(&outp1), (C2NumberPortTuning::output*)&outp1);
        EXPECT_EQ(C2NumberPortTuning::output::From(&outp2), &outp2);
        EXPECT_EQ(C2NumberStreamTuning::From(&inp1), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::From(&inp2), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::From(&outp1), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::From(&outp2), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&inp1), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&inp2), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&outp1), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&outp2), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&inp1), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&inp2), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&outp1), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&outp2), nullptr);
    }

    const C2NumberStreamTuning outs1(true, 1u, 100), ins1(false, 1u, 100);
    C2NumberStreamTuning bouts1, bins1, bins3(false, 1u, 100);
    const C2NumberStreamTuning::input ins2(1u, 100);
    C2NumberStreamTuning::input bins2;
    const C2NumberStreamTuning::output outs2(1u, 100);
    C2NumberStreamTuning::output bouts2;

    {
        static_assert(canCallSetPort(bins3), "should be able to");
        static_assert(canCallSetPort(bins1), "should be able to");
        static_assert(!canCallSetPort(ins1), "should not be able to (const)");
        static_assert(!canCallSetPort(ins2), "should not be able to (const & type)");
        static_assert(!canCallSetPort(bins2), "should not be able to (type)");

        // flags & invariables
        for (const auto &p : { outs1, ins1, bouts1 }) {
            EXPECT_EQ(12u, p.size());
            EXPECT_FALSE(p.isVendor());
            EXPECT_FALSE(p.isFlexible());
            EXPECT_FALSE(p.isGlobal());
            EXPECT_TRUE(p.forStream());
            EXPECT_FALSE(p.forPort());
        }
        for (const auto &p : { ins2, bins2 }) {
            EXPECT_EQ(12u, p.size());
            EXPECT_FALSE(p.isVendor());
            EXPECT_FALSE(p.isFlexible());
            EXPECT_FALSE(p.isGlobal());
            EXPECT_TRUE(p.forStream());
            EXPECT_FALSE(p.forPort());
        }
        for (const auto &p : { outs2, bouts2 }) {
            EXPECT_EQ(12u, p.size());
            EXPECT_FALSE(p.isVendor());
            EXPECT_FALSE(p.isFlexible());
            EXPECT_FALSE(p.isGlobal());
            EXPECT_TRUE(p.forStream());
            EXPECT_FALSE(p.forPort());
        }

        // port specific flags & invariables
        EXPECT_FALSE(outs1.forInput());
        EXPECT_TRUE(outs1.forOutput());

        EXPECT_TRUE(ins1.forInput());
        EXPECT_FALSE(ins1.forOutput());

        for (const auto &p : { outs1, ins1 }) {
            EXPECT_TRUE((bool)p);
            EXPECT_FALSE(!p);
            EXPECT_EQ(100, p.mNumber);
            EXPECT_EQ(1u, p.stream());
        }
        for (const auto &p : { outs2, bouts2 }) {
            EXPECT_TRUE((bool)p);
            EXPECT_FALSE(!p);

            EXPECT_FALSE(p.forInput());
            EXPECT_TRUE(p.forOutput());
        }
        for (const auto &p : { ins2, bins2 }) {
            EXPECT_TRUE((bool)p);
            EXPECT_FALSE(!p);

            EXPECT_TRUE(p.forInput());
            EXPECT_FALSE(p.forOutput());
        }
        for (const auto &p : { bouts1 } ) {
            EXPECT_FALSE((bool)p);
            EXPECT_TRUE(!p);

            EXPECT_FALSE(p.forInput());
            EXPECT_FALSE(p.forOutput());
            EXPECT_EQ(0, p.mNumber);
        }

        // values
        EXPECT_EQ(100, ins2.mNumber);
        EXPECT_EQ(100, outs2.mNumber);
        EXPECT_EQ(0, bins1.mNumber);
        EXPECT_EQ(0, bins2.mNumber);
        EXPECT_EQ(0, bouts1.mNumber);
        EXPECT_EQ(0, bouts2.mNumber);

        EXPECT_EQ(1u, ins2.stream());
        EXPECT_EQ(1u, outs2.stream());
        EXPECT_EQ(0u, bins1.stream());
        EXPECT_EQ(0u, bins2.stream());
        EXPECT_EQ(0u, bouts1.stream());
        EXPECT_EQ(0u, bouts2.stream());

        EXPECT_TRUE(ins1 != outs1);
        EXPECT_TRUE(ins1 == ins2);
        EXPECT_TRUE(outs1 == outs2);
        EXPECT_TRUE(bins1 == bouts1);
        EXPECT_TRUE(bins2 != bouts2);

        EXPECT_TRUE(ins1 != bins1);
        bins1.mNumber = 100;
        EXPECT_TRUE(ins1 != bins1);
        bins1.setPort(false /* output */);
        EXPECT_TRUE(ins1 != bins1);
        bins1.setStream(1u);
        EXPECT_TRUE(ins1 == bins1);

        EXPECT_TRUE(ins2 != bins2);
        bins2.mNumber = 100;
        EXPECT_TRUE(ins2 != bins2);
        bins2.setStream(1u);
        EXPECT_TRUE(ins2 == bins2);

        bins1.setPort(true /* output */);
        EXPECT_TRUE(outs1 == bins1);

        EXPECT_TRUE(outs1 != bouts1);
        bouts1.mNumber = 100;
        EXPECT_TRUE(outs1 != bouts1);
        bouts1.setPort(true /* output */);
        EXPECT_TRUE(outs1 != bouts1);
        bouts1.setStream(1u);
        EXPECT_TRUE(outs1 == bouts1);

        EXPECT_TRUE(outs2 != bouts2);
        bouts2.mNumber = 100;
        EXPECT_TRUE(outs2 != bouts2);
        bouts2.setStream(1u);
        EXPECT_TRUE(outs2 == bouts2);

        bouts1.setPort(false /* output */);
        EXPECT_TRUE(ins1 == bouts1);

        // index
        EXPECT_EQ(C2Param::Type(ins1.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(ins1.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(ins1.type(), C2NumberStreamTuning::input::typeIndex);

        EXPECT_EQ(C2Param::Type(ins2.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(ins2.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(ins2.type(), C2NumberStreamTuning::input::typeIndex);

        EXPECT_EQ(C2Param::Type(outs1.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(outs1.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(outs1.type(), C2NumberStreamTuning::output::typeIndex);

        EXPECT_EQ(C2Param::Type(outs2.type()).baseIndex(), C2NumberStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(outs2.type()).paramIndex(), kParamIndexNumber);
        EXPECT_EQ(outs2.type(), C2NumberStreamTuning::output::typeIndex);

        C2Param::BaseIndex index = C2NumberStreamTuning::input::typeIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_FALSE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumber);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumber);

        index = C2NumberStreamTuning::output::typeIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_FALSE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumber);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumber);

        C2Param::Type type = C2NumberStreamTuning::input::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_FALSE(type.isFlexible());
        EXPECT_FALSE(type.isGlobal());
        EXPECT_TRUE(type.forInput());
        EXPECT_FALSE(type.forOutput());
        EXPECT_TRUE(type.forStream());
        EXPECT_FALSE(type.forPort());

        type = C2NumberStreamTuning::output::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_FALSE(type.isFlexible());
        EXPECT_FALSE(type.isGlobal());
        EXPECT_FALSE(type.forInput());
        EXPECT_TRUE(type.forOutput());
        EXPECT_TRUE(type.forStream());
        EXPECT_FALSE(type.forPort());

        EXPECT_EQ(C2NumberPortTuning::From(nullptr), nullptr);
        EXPECT_EQ(C2NumberPortTuning::input::From(nullptr), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(nullptr), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&ins1), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&ins2), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&outs1), nullptr);
        EXPECT_EQ(C2NumberTuning::From(&outs2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::From(&ins1), nullptr);
        EXPECT_EQ(C2NumberPortTuning::From(&ins2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::From(&outs1), nullptr);
        EXPECT_EQ(C2NumberPortTuning::From(&outs2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::input::From(&ins1), nullptr);
        EXPECT_EQ(C2NumberPortTuning::input::From(&ins2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::input::From(&outs1), nullptr);
        EXPECT_EQ(C2NumberPortTuning::input::From(&outs2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(&ins1), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(&ins2), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(&outs1), nullptr);
        EXPECT_EQ(C2NumberPortTuning::output::From(&outs2), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::From(&ins1), &ins1);
        EXPECT_EQ(C2NumberStreamTuning::From(&ins2), (C2NumberStreamTuning*)&ins2);
        EXPECT_EQ(C2NumberStreamTuning::From(&outs1), &outs1);
        EXPECT_EQ(C2NumberStreamTuning::From(&outs2), (C2NumberStreamTuning*)&outs2);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&ins1), (C2NumberStreamTuning::input*)&ins1);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&ins2), &ins2);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&outs1), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::input::From(&outs2), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&ins1), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&ins2), nullptr);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&outs1), (C2NumberStreamTuning::output*)&outs1);
        EXPECT_EQ(C2NumberStreamTuning::output::From(&outs2), &outs2);

    }

    {
        uint32_t videoWidth[] = { 12u, C2NumberStreamTuning::output::typeIndex, 100 };
        C2Param *p1 = C2Param::From(videoWidth, sizeof(videoWidth));
        EXPECT_NE(p1, nullptr);
        EXPECT_EQ(12u, p1->size());
        EXPECT_EQ(p1->type(), C2NumberStreamTuning::output::typeIndex);

        p1 = C2Param::From(videoWidth, sizeof(videoWidth) + 2);
        EXPECT_EQ(p1, nullptr);

        p1 = C2Param::From(videoWidth, sizeof(videoWidth) - 2);
        EXPECT_EQ(p1, nullptr);

        p1 = C2Param::From(videoWidth, 3);
        EXPECT_EQ(p1, nullptr);

        p1 = C2Param::From(videoWidth, 0);
        EXPECT_EQ(p1, nullptr);
    }
}

void StaticTestAddBaseIndex() {
    struct nobase {};
    struct base { enum : uint32_t { baseIndex = 1 }; };
    static_assert(C2AddBaseIndex<nobase, 2>::baseIndex == 2, "should be 2");
    static_assert(C2AddBaseIndex<base, 1>::baseIndex == 1, "should be 1");
}

class TestFlexHelper {
    struct _Flex {
        int32_t a;
        char b[];
        _Flex() {}
        FLEX(_Flex, b);
    };

    struct _BoFlex {
        _Flex a;
        _BoFlex() {}
        FLEX(_BoFlex, a);
    };

    struct _NonFlex {
    };


    static void StaticTest() {
        static_assert(std::is_same<_C2FlexHelper<char>::flexType, void>::value, "should be void");
        static_assert(std::is_same<_C2FlexHelper<char[]>::flexType, char>::value, "should be char");
        static_assert(std::is_same<_C2FlexHelper<_Flex>::flexType, char>::value, "should be char");

        static_assert(std::is_same<_C2FlexHelper<_BoFlex>::flexType, char>::value, "should be void");

        static_assert(_C2Flexible<_Flex>::value, "should be flexible");
        static_assert(!_C2Flexible<_NonFlex>::value, "should not be flexible");
    }
};

TEST_F(C2ParamTest, FlexParamOpsTest) {
//    const C2NumbersStruct str{100};
    C2NumbersStruct bstr;
    {
//        EXPECT_EQ(100, str->m.mNumbers[0]);
        (void)&bstr.mNumbers[0];

        C2Param::BaseIndex index = C2NumbersStruct::baseIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_TRUE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumbers | C2Param::BaseIndex::_kFlexibleFlag);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumbers);
    }

    std::unique_ptr<C2NumbersTuning> tun_ = C2NumbersTuning::alloc_unique(1);
    tun_->m.mNumbers[0] = 100;
    std::unique_ptr<const C2NumbersTuning> tun = std::move(tun_);
    std::shared_ptr<C2NumbersTuning> btun = C2NumbersTuning::alloc_shared(1);

    {
        // flags & invariables
        const C2NumbersTuning *T[] = { tun.get(), btun.get() };
        for (const auto p : T) {
            EXPECT_TRUE((bool)(*p));
            EXPECT_FALSE(!(*p));
            EXPECT_EQ(12u, p->size());

            EXPECT_FALSE(p->isVendor());
            EXPECT_TRUE(p->isFlexible());
            EXPECT_TRUE(p->isGlobal());
            EXPECT_FALSE(p->forInput());
            EXPECT_FALSE(p->forOutput());
            EXPECT_FALSE(p->forStream());
            EXPECT_FALSE(p->forPort());
        }

        // value
        EXPECT_EQ(100, tun->m.mNumbers[0]);
        EXPECT_EQ(0, btun->m.mNumbers[0]);
        EXPECT_FALSE(*tun == *btun);
        EXPECT_FALSE(tun->operator==(*btun));
        EXPECT_TRUE(*tun != *btun);
        EXPECT_TRUE(tun->operator!=(*btun));
        btun->m.mNumbers[0] = 100;
        EXPECT_EQ(*tun, *btun);

        // index
        EXPECT_EQ(C2Param::Type(tun->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(tun->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(tun->type(), C2NumbersTuning::typeIndex);
        EXPECT_EQ(tun->stream(), ~0u);

        C2Param::BaseIndex index = C2NumbersTuning::baseIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_TRUE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumbers | C2Param::BaseIndex::_kFlexibleFlag);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumbers);

        C2Param::Type type = C2NumbersTuning::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_TRUE(type.isFlexible());
        EXPECT_TRUE(type.isGlobal());
        EXPECT_FALSE(type.forInput());
        EXPECT_FALSE(type.forOutput());
        EXPECT_FALSE(type.forStream());
        EXPECT_FALSE(type.forPort());

        EXPECT_EQ(C2NumbersTuning::From(nullptr), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(tun.get()), tun.get());
        EXPECT_EQ(C2NumbersPortTuning::From(tun.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::input::From(tun.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(tun.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::From(tun.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::input::From(tun.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::output::From(tun.get()), nullptr);
    }

    std::unique_ptr<C2NumbersPortTuning> outp1_(C2NumbersPortTuning::alloc_unique(1, true)),
            inp1_ = C2NumbersPortTuning::alloc_unique(1, false);
    outp1_->m.mNumbers[0] = 100;
    inp1_->m.mNumbers[0] = 100;
    std::unique_ptr<const C2NumbersPortTuning> outp1 = std::move(outp1_);
    std::unique_ptr<const C2NumbersPortTuning> inp1 = std::move(inp1_);
    std::shared_ptr<C2NumbersPortTuning> boutp1(C2NumbersPortTuning::alloc_shared(1)),
            binp1 = C2NumbersPortTuning::alloc_shared(1),
            binp3 = C2NumbersPortTuning::alloc_shared(1, false);
    binp3->m.mNumbers[0] = 100;
    std::unique_ptr<C2NumbersPortTuning::input> inp2_(C2NumbersPortTuning::input::alloc_unique(1));
    inp2_->m.mNumbers[0] = 100;
    std::unique_ptr<const C2NumbersPortTuning::input> inp2 = std::move(inp2_);
    std::shared_ptr<C2NumbersPortTuning::input> binp2(C2NumbersPortTuning::input::alloc_shared(1));
    std::unique_ptr<C2NumbersPortTuning::output> outp2_(C2NumbersPortTuning::output::alloc_unique(1));
    outp2_->m.mNumbers[0] = 100;
    std::unique_ptr<const C2NumbersPortTuning::output> outp2 = std::move(outp2_);
    std::shared_ptr<C2NumbersPortTuning::output> boutp2(C2NumbersPortTuning::output::alloc_shared(1));

    {
        static_assert(canCallSetPort(*binp3), "should be able to");
        static_assert(canCallSetPort(*binp1), "should be able to");
        static_assert(!canCallSetPort(*inp1), "should not be able to (const)");
        static_assert(!canCallSetPort(*inp2), "should not be able to (const & type)");
        static_assert(!canCallSetPort(*binp2), "should not be able to (type)");

        // flags & invariables
        const C2NumbersPortTuning *P[] = { outp1.get(), inp1.get(), boutp1.get() };
        for (const auto p : P) {
            EXPECT_EQ(12u, p->size());
            EXPECT_FALSE(p->isVendor());
            EXPECT_TRUE(p->isFlexible());
            EXPECT_FALSE(p->isGlobal());
            EXPECT_FALSE(p->forStream());
            EXPECT_TRUE(p->forPort());
        }
        const C2NumbersPortTuning::input *PI[] = { inp2.get(), binp2.get() };
        for (const auto p : PI) {
            EXPECT_EQ(12u, p->size());
            EXPECT_FALSE(p->isVendor());
            EXPECT_TRUE(p->isFlexible());
            EXPECT_FALSE(p->isGlobal());
            EXPECT_FALSE(p->forStream());
            EXPECT_TRUE(p->forPort());
        }
        const C2NumbersPortTuning::output *PO[] = { outp2.get(), boutp2.get() };
        for (const auto p : PO) {
            EXPECT_EQ(12u, p->size());
            EXPECT_FALSE(p->isVendor());
            EXPECT_TRUE(p->isFlexible());
            EXPECT_FALSE(p->isGlobal());
            EXPECT_FALSE(p->forStream());
            EXPECT_TRUE(p->forPort());
        }

        // port specific flags & invariables
        EXPECT_FALSE(outp1->forInput());
        EXPECT_TRUE(outp1->forOutput());

        EXPECT_TRUE(inp1->forInput());
        EXPECT_FALSE(inp1->forOutput());

        const C2NumbersPortTuning *P2[] = { outp1.get(), inp1.get() };
        for (const auto p : P2) {
            EXPECT_TRUE((bool)(*p));
            EXPECT_FALSE(!(*p));
            EXPECT_EQ(100, p->m.mNumbers[0]);
        }
        for (const auto p : PO) {
            EXPECT_TRUE((bool)(*p));
            EXPECT_FALSE(!(*p));

            EXPECT_FALSE(p->forInput());
            EXPECT_TRUE(p->forOutput());
        }
        for (const auto p : PI) {
            EXPECT_TRUE((bool)(*p));
            EXPECT_FALSE(!(*p));

            EXPECT_TRUE(p->forInput());
            EXPECT_FALSE(p->forOutput());
        }
        const C2NumbersPortTuning *P3[] = { boutp1.get() };
        for (const auto p : P3) {
            EXPECT_FALSE((bool)(*p));
            EXPECT_TRUE(!(*p));

            EXPECT_FALSE(p->forInput());
            EXPECT_FALSE(p->forOutput());
            EXPECT_EQ(0, p->m.mNumbers[0]);
        }

        // values
        EXPECT_EQ(100, inp2->m.mNumbers[0]);
        EXPECT_EQ(100, outp2->m.mNumbers[0]);
        EXPECT_EQ(0, binp1->m.mNumbers[0]);
        EXPECT_EQ(0, binp2->m.mNumbers[0]);
        EXPECT_EQ(0, boutp1->m.mNumbers[0]);
        EXPECT_EQ(0, boutp2->m.mNumbers[0]);

        EXPECT_TRUE(*inp1 != *outp1);
        EXPECT_TRUE(*inp1 == *inp2);
        EXPECT_TRUE(*outp1 == *outp2);
        EXPECT_TRUE(*binp1 == *boutp1);
        EXPECT_TRUE(*binp2 != *boutp2);

        EXPECT_TRUE(*inp1 != *binp1);
        binp1->m.mNumbers[0] = 100;
        EXPECT_TRUE(*inp1 != *binp1);
        binp1->setPort(false /* output */);
        EXPECT_TRUE((bool)*binp1);
        EXPECT_FALSE(!*binp1);
        EXPECT_TRUE(*inp1 == *binp1);

        EXPECT_TRUE(*inp2 != *binp2);
        binp2->m.mNumbers[0] = 100;
        EXPECT_TRUE(*inp2 == *binp2);

        binp1->setPort(true /* output */);
        EXPECT_TRUE(*outp1 == *binp1);

        EXPECT_TRUE(*outp1 != *boutp1);
        boutp1->m.mNumbers[0] = 100;
        EXPECT_TRUE(*outp1 != *boutp1);
        boutp1->setPort(true /* output */);
        EXPECT_TRUE((bool)*boutp1);
        EXPECT_FALSE(!*boutp1);
        EXPECT_TRUE(*outp1 == *boutp1);

        EXPECT_TRUE(*outp2 != *boutp2);
        boutp2->m.mNumbers[0] = 100;
        EXPECT_TRUE(*outp2 == *boutp2);

        boutp1->setPort(false /* output */);
        EXPECT_TRUE(*inp1 == *boutp1);

        // index
        EXPECT_EQ(C2Param::Type(inp1->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(inp1->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(inp1->type(), C2NumbersPortTuning::input::typeIndex);
        EXPECT_EQ(inp1->stream(), ~0u);

        EXPECT_EQ(C2Param::Type(inp2->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(inp2->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(inp2->type(), C2NumbersPortTuning::input::typeIndex);
        EXPECT_EQ(inp2->stream(), ~0u);

        EXPECT_EQ(C2Param::Type(outp1->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(outp1->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(outp1->type(), C2NumbersPortTuning::output::typeIndex);
        EXPECT_EQ(outp1->stream(), ~0u);

        EXPECT_EQ(C2Param::Type(outp2->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(outp2->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(outp2->type(), C2NumbersPortTuning::output::typeIndex);
        EXPECT_EQ(outp2->stream(), ~0u);

        C2Param::BaseIndex index = C2NumbersPortTuning::input::typeIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_TRUE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumbers | C2Param::BaseIndex::_kFlexibleFlag);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumbers);

        index = C2NumbersPortTuning::output::typeIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_TRUE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumbers | C2Param::BaseIndex::_kFlexibleFlag);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumbers);

        C2Param::Type type = C2NumbersPortTuning::input::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_TRUE(type.isFlexible());
        EXPECT_FALSE(type.isGlobal());
        EXPECT_TRUE(type.forInput());
        EXPECT_FALSE(type.forOutput());
        EXPECT_FALSE(type.forStream());
        EXPECT_TRUE(type.forPort());

        type = C2NumbersPortTuning::output::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_TRUE(type.isFlexible());
        EXPECT_FALSE(type.isGlobal());
        EXPECT_FALSE(type.forInput());
        EXPECT_TRUE(type.forOutput());
        EXPECT_FALSE(type.forStream());
        EXPECT_TRUE(type.forPort());

        EXPECT_EQ(C2NumbersPortTuning::From(nullptr), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::input::From(nullptr), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(nullptr), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(inp1.get()), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(inp2.get()), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(outp1.get()), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(outp2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::From(inp1.get()), inp1.get());
        EXPECT_EQ(C2NumbersPortTuning::From(inp2.get()), (C2NumbersPortTuning*)inp2.get());
        EXPECT_EQ(C2NumbersPortTuning::From(outp1.get()), outp1.get());
        EXPECT_EQ(C2NumbersPortTuning::From(outp2.get()), (C2NumbersPortTuning*)outp2.get());
        EXPECT_EQ(C2NumbersPortTuning::input::From(inp1.get()), (C2NumbersPortTuning::input*)inp1.get());
        EXPECT_EQ(C2NumbersPortTuning::input::From(inp2.get()), inp2.get());
        EXPECT_EQ(C2NumbersPortTuning::input::From(outp1.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::input::From(outp2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(inp1.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(inp2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(outp1.get()), (C2NumbersPortTuning::output*)outp1.get());
        EXPECT_EQ(C2NumbersPortTuning::output::From(outp2.get()), outp2.get());
        EXPECT_EQ(C2NumbersStreamTuning::From(inp1.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::From(inp2.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::From(outp1.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::From(outp2.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::input::From(inp1.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::input::From(inp2.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::input::From(outp1.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::input::From(outp2.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::output::From(inp1.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::output::From(inp2.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::output::From(outp1.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::output::From(outp2.get()), nullptr);

    }

    std::unique_ptr<C2NumbersStreamTuning> outs1_(C2NumbersStreamTuning::alloc_unique(1, true, 1u));
    outs1_->m.mNumbers[0] = 100;
    std::unique_ptr<const C2NumbersStreamTuning> outs1 = std::move(outs1_);
    std::unique_ptr<C2NumbersStreamTuning> ins1_(C2NumbersStreamTuning::alloc_unique(1, false, 1u));
    ins1_->m.mNumbers[0] = 100;
    std::unique_ptr<const C2NumbersStreamTuning> ins1 = std::move(ins1_);
    std::shared_ptr<C2NumbersStreamTuning> bouts1(C2NumbersStreamTuning::alloc_shared(1));
    std::shared_ptr<C2NumbersStreamTuning> bins1(C2NumbersStreamTuning::alloc_shared(1));
    std::shared_ptr<C2NumbersStreamTuning> bins3(C2NumbersStreamTuning::alloc_shared(1, false, 1u));
    bins3->m.mNumbers[0] = 100;
    std::unique_ptr<C2NumbersStreamTuning::input> ins2_(C2NumbersStreamTuning::input::alloc_unique(1, 1u));
    ins2_->m.mNumbers[0] = 100;
    std::unique_ptr<const C2NumbersStreamTuning::input> ins2 = std::move(ins2_);
    std::shared_ptr<C2NumbersStreamTuning::input> bins2(C2NumbersStreamTuning::input::alloc_shared(1));
    std::unique_ptr<C2NumbersStreamTuning::output> outs2_(C2NumbersStreamTuning::output::alloc_unique(1, 1u));
    outs2_->m.mNumbers[0] = 100;
    std::unique_ptr<const C2NumbersStreamTuning::output> outs2 = std::move(outs2_);
    std::shared_ptr<C2NumbersStreamTuning::output> bouts2(C2NumbersStreamTuning::output::alloc_shared(1));

    {
        static_assert(canCallSetPort(*bins3), "should be able to");
        static_assert(canCallSetPort(*bins1), "should be able to");
        static_assert(!canCallSetPort(*ins1), "should not be able to (const)");
        static_assert(!canCallSetPort(*ins2), "should not be able to (const & type)");
        static_assert(!canCallSetPort(*bins2), "should not be able to (type)");

        // flags & invariables
        const C2NumbersStreamTuning *S[] = { outs1.get(), ins1.get(), bouts1.get() };
        for (const auto p : S) {
            EXPECT_EQ(12u, p->size());
            EXPECT_FALSE(p->isVendor());
            EXPECT_TRUE(p->isFlexible());
            EXPECT_FALSE(p->isGlobal());
            EXPECT_TRUE(p->forStream());
            EXPECT_FALSE(p->forPort());
        }
        const C2NumbersStreamTuning::input *SI[] = { ins2.get(), bins2.get() };
        for (const auto p : SI) {
            EXPECT_EQ(12u, p->size());
            EXPECT_FALSE(p->isVendor());
            EXPECT_TRUE(p->isFlexible());
            EXPECT_FALSE(p->isGlobal());
            EXPECT_TRUE(p->forStream());
            EXPECT_FALSE(p->forPort());
        }
        const C2NumbersStreamTuning::output *SO[] = { outs2.get(), bouts2.get() };
        for (const auto p : SO) {
            EXPECT_EQ(12u, p->size());
            EXPECT_FALSE(p->isVendor());
            EXPECT_TRUE(p->isFlexible());
            EXPECT_FALSE(p->isGlobal());
            EXPECT_TRUE(p->forStream());
            EXPECT_FALSE(p->forPort());
        }

        // port specific flags & invariables
        EXPECT_FALSE(outs1->forInput());
        EXPECT_TRUE(outs1->forOutput());

        EXPECT_TRUE(ins1->forInput());
        EXPECT_FALSE(ins1->forOutput());

        const C2NumbersStreamTuning *S2[] = { outs1.get(), ins1.get() };
        for (const auto p : S2) {
            EXPECT_TRUE((bool)(*p));
            EXPECT_FALSE(!(*p));
            EXPECT_EQ(100, p->m.mNumbers[0]);
            EXPECT_EQ(1u, p->stream());
        }
        for (const auto p : SO) {
            EXPECT_TRUE((bool)(*p));
            EXPECT_FALSE(!(*p));

            EXPECT_FALSE(p->forInput());
            EXPECT_TRUE(p->forOutput());
        }
        for (const auto p : SI) {
            EXPECT_TRUE((bool)(*p));
            EXPECT_FALSE(!(*p));

            EXPECT_TRUE(p->forInput());
            EXPECT_FALSE(p->forOutput());
        }
        const C2NumbersStreamTuning *S3[] = { bouts1.get() };
        for (const auto p : S3) {
            EXPECT_FALSE((bool)(*p));
            EXPECT_TRUE(!(*p));

            EXPECT_FALSE(p->forInput());
            EXPECT_FALSE(p->forOutput());
            EXPECT_EQ(0, p->m.mNumbers[0]);
        }

        // values
        EXPECT_EQ(100, ins2->m.mNumbers[0]);
        EXPECT_EQ(100, outs2->m.mNumbers[0]);
        EXPECT_EQ(0, bins1->m.mNumbers[0]);
        EXPECT_EQ(0, bins2->m.mNumbers[0]);
        EXPECT_EQ(0, bouts1->m.mNumbers[0]);
        EXPECT_EQ(0, bouts2->m.mNumbers[0]);

        EXPECT_EQ(1u, ins2->stream());
        EXPECT_EQ(1u, outs2->stream());
        EXPECT_EQ(0u, bins1->stream());
        EXPECT_EQ(0u, bins2->stream());
        EXPECT_EQ(0u, bouts1->stream());
        EXPECT_EQ(0u, bouts2->stream());

        EXPECT_TRUE(*ins1 != *outs1);
        EXPECT_TRUE(*ins1 == *ins2);
        EXPECT_TRUE(*outs1 == *outs2);
        EXPECT_TRUE(*bins1 == *bouts1);
        EXPECT_TRUE(*bins2 != *bouts2);

        EXPECT_TRUE(*ins1 != *bins1);
        bins1->m.mNumbers[0] = 100;
        EXPECT_TRUE(*ins1 != *bins1);
        bins1->setPort(false /* output */);
        EXPECT_TRUE(*ins1 != *bins1);
        bins1->setStream(1u);
        EXPECT_TRUE(*ins1 == *bins1);

        EXPECT_TRUE(*ins2 != *bins2);
        bins2->m.mNumbers[0] = 100;
        EXPECT_TRUE(*ins2 != *bins2);
        bins2->setStream(1u);
        EXPECT_TRUE(*ins2 == *bins2);

        bins1->setPort(true /* output */);
        EXPECT_TRUE(*outs1 == *bins1);

        EXPECT_TRUE(*outs1 != *bouts1);
        bouts1->m.mNumbers[0] = 100;
        EXPECT_TRUE(*outs1 != *bouts1);
        bouts1->setPort(true /* output */);
        EXPECT_TRUE(*outs1 != *bouts1);
        bouts1->setStream(1u);
        EXPECT_TRUE(*outs1 == *bouts1);

        EXPECT_TRUE(*outs2 != *bouts2);
        bouts2->m.mNumbers[0] = 100;
        EXPECT_TRUE(*outs2 != *bouts2);
        bouts2->setStream(1u);
        EXPECT_TRUE(*outs2 == *bouts2);

        bouts1->setPort(false /* output */);
        EXPECT_TRUE(*ins1 == *bouts1);

        // index
        EXPECT_EQ(C2Param::Type(ins1->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(ins1->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(ins1->type(), C2NumbersStreamTuning::input::typeIndex);

        EXPECT_EQ(C2Param::Type(ins2->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(ins2->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(ins2->type(), C2NumbersStreamTuning::input::typeIndex);

        EXPECT_EQ(C2Param::Type(outs1->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(outs1->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(outs1->type(), C2NumbersStreamTuning::output::typeIndex);

        EXPECT_EQ(C2Param::Type(outs2->type()).baseIndex(), C2NumbersStruct::baseIndex);
        EXPECT_EQ(C2Param::Type(outs2->type()).paramIndex(), kParamIndexNumbers);
        EXPECT_EQ(outs2->type(), C2NumbersStreamTuning::output::typeIndex);

        C2Param::BaseIndex index = C2NumbersStreamTuning::input::typeIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_TRUE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumbers | C2Param::BaseIndex::_kFlexibleFlag);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumbers);

        index = C2NumbersStreamTuning::output::typeIndex;
        EXPECT_FALSE(index.isVendor());
        EXPECT_TRUE(index.isFlexible());
        EXPECT_EQ(index.baseIndex(), kParamIndexNumbers | C2Param::BaseIndex::_kFlexibleFlag);
        EXPECT_EQ(index.paramIndex(), kParamIndexNumbers);

        C2Param::Type type = C2NumbersStreamTuning::input::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_TRUE(type.isFlexible());
        EXPECT_FALSE(type.isGlobal());
        EXPECT_TRUE(type.forInput());
        EXPECT_FALSE(type.forOutput());
        EXPECT_TRUE(type.forStream());
        EXPECT_FALSE(type.forPort());

        type = C2NumbersStreamTuning::output::typeIndex;
        EXPECT_FALSE(type.isVendor());
        EXPECT_TRUE(type.isFlexible());
        EXPECT_FALSE(type.isGlobal());
        EXPECT_FALSE(type.forInput());
        EXPECT_TRUE(type.forOutput());
        EXPECT_TRUE(type.forStream());
        EXPECT_FALSE(type.forPort());

        EXPECT_EQ(C2NumbersPortTuning::From(nullptr), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::input::From(nullptr), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(nullptr), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(ins1.get()), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(ins2.get()), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(outs1.get()), nullptr);
        EXPECT_EQ(C2NumbersTuning::From(outs2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::From(ins1.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::From(ins2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::From(outs1.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::From(outs2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::input::From(ins1.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::input::From(ins2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::input::From(outs1.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::input::From(outs2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(ins1.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(ins2.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(outs1.get()), nullptr);
        EXPECT_EQ(C2NumbersPortTuning::output::From(outs2.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::From(ins1.get()), ins1.get());
        EXPECT_EQ(C2NumbersStreamTuning::From(ins2.get()), (C2NumbersStreamTuning*)ins2.get());
        EXPECT_EQ(C2NumbersStreamTuning::From(outs1.get()), outs1.get());
        EXPECT_EQ(C2NumbersStreamTuning::From(outs2.get()), (C2NumbersStreamTuning*)outs2.get());
        EXPECT_EQ(C2NumbersStreamTuning::input::From(ins1.get()), (C2NumbersStreamTuning::input*)ins1.get());
        EXPECT_EQ(C2NumbersStreamTuning::input::From(ins2.get()), ins2.get());
        EXPECT_EQ(C2NumbersStreamTuning::input::From(outs1.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::input::From(outs2.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::output::From(ins1.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::output::From(ins2.get()), nullptr);
        EXPECT_EQ(C2NumbersStreamTuning::output::From(outs1.get()), (C2NumbersStreamTuning::output*)outs1.get());
        EXPECT_EQ(C2NumbersStreamTuning::output::From(outs2.get()), outs2.get());

    }

    {
        C2Int32Value int32Value(INT32_MIN);
        static_assert(std::is_same<decltype(int32Value.mValue), int32_t>::value, "should be int32_t");
        EXPECT_EQ(INT32_MIN, int32Value.mValue);
        std::list<const C2FieldDescriptor> fields = int32Value.fieldList;
        EXPECT_EQ(1u, fields.size());
        EXPECT_EQ(FD::INT32, fields.cbegin()->type());
        EXPECT_EQ(1u, fields.cbegin()->length());
        EXPECT_EQ(C2String("value"), fields.cbegin()->name());
    }

    {
        C2Uint32Value uint32Value(UINT32_MAX);
        static_assert(std::is_same<decltype(uint32Value.mValue), uint32_t>::value, "should be uint32_t");
        EXPECT_EQ(UINT32_MAX, uint32Value.mValue);
        std::list<const C2FieldDescriptor> fields = uint32Value.fieldList;
        EXPECT_EQ(1u, fields.size());
        EXPECT_EQ(FD::UINT32, fields.cbegin()->type());
        EXPECT_EQ(1u, fields.cbegin()->length());
        EXPECT_EQ(C2String("value"), fields.cbegin()->name());
    }

    {
        C2Int64Value int64Value(INT64_MIN);
        static_assert(std::is_same<decltype(int64Value.mValue), int64_t>::value, "should be int64_t");
        EXPECT_EQ(INT64_MIN, int64Value.mValue);
        std::list<const C2FieldDescriptor> fields = int64Value.fieldList;
        EXPECT_EQ(1u, fields.size());
        EXPECT_EQ(FD::INT64, fields.cbegin()->type());
        EXPECT_EQ(1u, fields.cbegin()->length());
        EXPECT_EQ(C2String("value"), fields.cbegin()->name());
    }

    {
        C2Uint64Value uint64Value(UINT64_MAX);
        static_assert(std::is_same<decltype(uint64Value.mValue), uint64_t>::value, "should be uint64_t");
        EXPECT_EQ(UINT64_MAX, uint64Value.mValue);
        std::list<const C2FieldDescriptor> fields = uint64Value.fieldList;
        EXPECT_EQ(1u, fields.size());
        EXPECT_EQ(FD::UINT64, fields.cbegin()->type());
        EXPECT_EQ(1u, fields.cbegin()->length());
        EXPECT_EQ(C2String("value"), fields.cbegin()->name());
    }

    {
        C2FloatValue floatValue(123.4f);
        static_assert(std::is_same<decltype(floatValue.mValue), float>::value, "should be float");
        EXPECT_EQ(123.4f, floatValue.mValue);
        std::list<const C2FieldDescriptor> fields = floatValue.fieldList;
        EXPECT_EQ(1u, fields.size());
        EXPECT_EQ(FD::FLOAT, fields.cbegin()->type());
        EXPECT_EQ(1u, fields.cbegin()->length());
        EXPECT_EQ(C2String("value"), fields.cbegin()->name());
    }

    {
        uint8_t initValue[] = "ABCD";
        typedef C2GlobalParam<C2Setting, C2BlobValue, 0> BlobSetting;
        std::unique_ptr<BlobSetting> blobValue = BlobSetting::alloc_unique(6, C2ConstMemoryBlock<uint8_t>(initValue));
        static_assert(std::is_same<decltype(blobValue->m.mValue), uint8_t[]>::value, "should be uint8_t[]");
        EXPECT_EQ(0, memcmp(blobValue->m.mValue, "ABCD\0", 6));
        EXPECT_EQ(6u, blobValue->flexCount());
        std::list<const C2FieldDescriptor> fields = blobValue->fieldList;
        EXPECT_EQ(1u, fields.size());
        EXPECT_EQ(FD::BLOB, fields.cbegin()->type());
        EXPECT_EQ(0u, fields.cbegin()->length());
        EXPECT_EQ(C2String("value"), fields.cbegin()->name());

        blobValue = BlobSetting::alloc_unique(3, C2ConstMemoryBlock<uint8_t>(initValue));
        EXPECT_EQ(0, memcmp(blobValue->m.mValue, "ABC", 3));
        EXPECT_EQ(3u, blobValue->flexCount());
    }

    {
        constexpr char initValue[] = "ABCD";
        typedef C2GlobalParam<C2Setting, C2StringValue, 0> StringSetting;
        std::unique_ptr<StringSetting> stringValue = StringSetting::alloc_unique(6, C2ConstMemoryBlock<char>(initValue));
        stringValue = StringSetting::alloc_unique(6, initValue);
        static_assert(std::is_same<decltype(stringValue->m.mValue), char[]>::value, "should be char[]");
        EXPECT_EQ(0, memcmp(stringValue->m.mValue, "ABCD\0", 6));
        EXPECT_EQ(6u, stringValue->flexCount());
        std::list<const C2FieldDescriptor> fields = stringValue->fieldList;
        EXPECT_EQ(1u, fields.size());
        EXPECT_EQ(FD::STRING, fields.cbegin()->type());
        EXPECT_EQ(0u, fields.cbegin()->length());
        EXPECT_EQ(C2String("value"), fields.cbegin()->name());

        stringValue = StringSetting::alloc_unique(3, C2ConstMemoryBlock<char>(initValue));
        EXPECT_EQ(0, memcmp(stringValue->m.mValue, "AB", 3));
        EXPECT_EQ(3u, stringValue->flexCount());

        stringValue = StringSetting::alloc_unique(11, "initValue");
        EXPECT_EQ(0, memcmp(stringValue->m.mValue, "initValue\0", 11));
        EXPECT_EQ(11u, stringValue->flexCount());

        stringValue = StringSetting::alloc_unique(initValue);
        EXPECT_EQ(0, memcmp(stringValue->m.mValue, "ABCD", 5));
        EXPECT_EQ(5u, stringValue->flexCount());

        stringValue = StringSetting::alloc_unique({ 'A', 'B', 'C', 'D' });
        EXPECT_EQ(0, memcmp(stringValue->m.mValue, "ABC", 4));
        EXPECT_EQ(4u, stringValue->flexCount());
    }

    {
        uint32_t videoWidth[] = { 12u, C2NumbersStreamTuning::output::typeIndex, 100 };
        C2Param *p1 = C2Param::From(videoWidth, sizeof(videoWidth));
        EXPECT_NE(nullptr, p1);
        EXPECT_EQ(12u, p1->size());
        EXPECT_EQ(C2NumbersStreamTuning::output::typeIndex, p1->type());

        C2NumbersStreamTuning::output *vst = C2NumbersStreamTuning::output::From(p1);
        EXPECT_NE(nullptr, vst);
        if (vst) {
            EXPECT_EQ(1u, vst->flexCount());
            EXPECT_EQ(100, vst->m.mNumbers[0]);
        }

        p1 = C2Param::From(videoWidth, sizeof(videoWidth) + 2);
        EXPECT_EQ(nullptr, p1);

        p1 = C2Param::From(videoWidth, sizeof(videoWidth) - 2);
        EXPECT_EQ(nullptr, p1);

        p1 = C2Param::From(videoWidth, 3);
        EXPECT_EQ(nullptr, p1);

        p1 = C2Param::From(videoWidth, 0);
        EXPECT_EQ(nullptr, p1);
    }

    {
        uint32_t videoWidth[] = { 16u, C2NumbersPortTuning::input::typeIndex, 101, 102 };

        C2Param *p1 = C2Param::From(videoWidth, sizeof(videoWidth));
        EXPECT_NE(nullptr, p1);
        EXPECT_EQ(16u, p1->size());
        EXPECT_EQ(C2NumbersPortTuning::input::typeIndex, p1->type());

        C2NumbersPortTuning::input *vpt = C2NumbersPortTuning::input::From(p1);
        EXPECT_NE(nullptr, vpt);
        if (vpt) {
            EXPECT_EQ(2u, vpt->flexCount());
            EXPECT_EQ(101, vpt->m.mNumbers[0]);
            EXPECT_EQ(102, vpt->m.mNumbers[1]);
        }

        p1 = C2Param::From(videoWidth, sizeof(videoWidth) + 2);
        EXPECT_EQ(nullptr, p1);

        p1 = C2Param::From(videoWidth, sizeof(videoWidth) - 2);
        EXPECT_EQ(nullptr, p1);

        p1 = C2Param::From(videoWidth, 3);
        EXPECT_EQ(nullptr, p1);

        p1 = C2Param::From(videoWidth, 0);
        EXPECT_EQ(nullptr, p1);
    }
}

// ***********************

}

#include <util/C2ParamUtils.h>
#include <C2Config.h>
#include <C2Component.h>
#include <unordered_map>

namespace android {

C2ENUM(
    MetadataType, int32_t,
    kInvalid = -1,
    kNone = 0,
    kGralloc,
    kNativeHandle,
    kANativeWindow,
    kCamera,
)

enum {
    kParamIndexVideoConfig = 0x1234,
};

struct C2VideoConfigStruct {
    int32_t mWidth;
    uint32_t mHeight;
    MetadataType mMetadataType;
    int32_t mSupportedFormats[];

    C2VideoConfigStruct() {}

    DEFINE_AND_DESCRIBE_FLEX_C2STRUCT(VideoConfig, mSupportedFormats)
    C2FIELD(mWidth, "width")
    C2FIELD(mHeight, "height")
    C2FIELD(mMetadataType, "metadata-type")
    C2FIELD(mSupportedFormats, "formats")
};

typedef C2PortParam<C2Tuning, C2VideoConfigStruct> C2VideoConfigPortTuning;

class MyReflector : public C2ParamReflector {
private:
    std::unique_ptr<C2VideoConfigPortTuning::input> inputVideoConfigTuning;
    std::unique_ptr<C2VideoConfigPortTuning::output> outputVideoConfigTuning;

public:
    void describeSupportedValues() {
        C2TypedFieldSupportedValues<int32_t> supportedWidths(16, 1920, 8);
        C2FieldSupportedValues supportedWidths2(16, 1920, 8);


        std::list<C2FieldSupportedValues> supported;
        //supported.emplace_push(inputVideoConfigTuning->mNumber, range(16, 1920, 8));
        //supported.emplace_push(inputVideoConfigTuning->mHeight, range(16, 1088, 8));
        //supported.emplace_push(inputVideoConfigTuning->mMetadataType, all_enums);
        //supported.emplace_push(inputVideoConfigTuning->mSupportedFormats, { 0, 1, 5, 7 });
    }

    virtual std::unique_ptr<android::C2StructDescriptor> describe(C2Param::BaseIndex paramType) {
        switch (paramType.baseIndex()) {
        case C2VideoConfigPortTuning::baseIndex:
            return std::unique_ptr<C2StructDescriptor>(new C2StructDescriptor{
                paramType.baseIndex(),
                C2VideoConfigPortTuning::fieldList,
            });
        }
        return nullptr;
    }
};

class MyComponentInstance : public C2ComponentInterface {
public:
    virtual C2String getName() const {
        /// \todo this seems too specific
        return "sample.interface";
    };

    virtual node_id getId() const {
        /// \todo how are these shared?
        return 0;
    }

    virtual status_t commit_sm(
            const std::vector<C2Param* const> &params,
            std::vector<std::unique_ptr<C2SettingResult>>* const failures) {
        (void)params;
        (void)failures;
        return C2_UNSUPPORTED;
    }

    virtual status_t config_nb(
            const std::vector<C2Param* const> &params,
            std::vector<std::unique_ptr<C2SettingResult>>* const failures) {
        (void)params;
        (void)failures;
        return C2_UNSUPPORTED;
    }

    virtual status_t createTunnel_sm(node_id targetComponent) {
        (void)targetComponent;
        return C2_UNSUPPORTED;
    }

    virtual status_t query_nb(
            const std::vector<C2Param* const> &stackParams,
            const std::vector<C2Param::Index> &heapParamIndices,
            std::vector<std::unique_ptr<C2Param>>* const heapParams) const {
        for (C2Param* const param : stackParams) {
            if (!*param) { // param is already invalid - remember it
                continue;
            }

            // note: this does not handle stream params (should use index...)
            if (!mMyParams.count(param->type())) {
                continue; // not my param
            }

            C2Param & myParam = mMyParams.find(param->type())->second;
            if (myParam.size() != param->size()) { // incorrect size
                param->invalidate();
                continue;
            }

            param->updateFrom(myParam);
        }

        for (const C2Param::Index index : heapParamIndices) {
            if (mMyParams.count(index)) {
                C2Param & myParam = mMyParams.find(index)->second;
                std::unique_ptr<C2Param> paramCopy(C2Param::From(&myParam, myParam.size()));
                heapParams->push_back(std::move(paramCopy));
            }
        }

        return C2_OK;
    }

    std::unordered_map<uint32_t, C2Param &> mMyParams;

    C2ComponentDomainInfo mDomainInfo;

    MyComponentInstance() {
        mMyParams.insert({mDomainInfo.type(), mDomainInfo});
    }

    virtual status_t releaseTunnel_sm(node_id targetComponent) {
        (void)targetComponent;
        return C2_UNSUPPORTED;
    }

    class MyParamReflector : public C2ParamReflector {
        const MyComponentInstance *instance;

    public:
        MyParamReflector(const MyComponentInstance *i) : instance(i) { }

        virtual std::unique_ptr<C2StructDescriptor> describe(C2Param::BaseIndex paramIndex) {
            switch (paramIndex.baseIndex()) {
            case decltype(instance->mDomainInfo)::baseIndex:
            default:
                return std::unique_ptr<C2StructDescriptor>(new C2StructDescriptor{
                    instance->mDomainInfo.type(),
                    decltype(instance->mDomainInfo)::fieldList,
                });
            }
            return nullptr;
        }
    };

    virtual status_t getSupportedValues(
            const std::vector<const C2ParamField> fields,
            std::vector<C2FieldSupportedValues>* const values) const {
        for (const C2ParamField &field : fields) {
            if (field == C2ParamField(&mDomainInfo, &C2ComponentDomainInfo::mValue)) {
                values->push_back(C2FieldSupportedValues(
                    false /* flag */,
                    &mDomainInfo.mValue
                    //,
                    //{(int32_t)C2DomainVideo}
                ));
            }
        }
        return C2_OK;
    }

    virtual std::shared_ptr<C2ParamReflector> getParamReflector() const {
        return std::shared_ptr<C2ParamReflector>(new MyParamReflector(this));
    }

    virtual status_t getSupportedParams(std::vector<std::shared_ptr<C2ParamDescriptor>> * const params) const {
        params->push_back(std::make_shared<C2ParamDescriptor>(
                true /* required */, "_domain", &mDomainInfo));
        return C2_OK;
    }

    status_t getSupportedParams2(std::vector<std::shared_ptr<C2ParamDescriptor>> * const params) {
        params->push_back(std::shared_ptr<C2ParamDescriptor>(
                new C2ParamDescriptor(true /* required */, "_domain", &mDomainInfo)));
        return C2_OK;
    }

};

template<typename E, bool S=std::is_enum<E>::value>
struct getter {
    int32_t get(const C2FieldSupportedValues::Primitive &p, int32_t*) {
        return p.i32;
    }
    int64_t get(const C2FieldSupportedValues::Primitive &p, int64_t*) {
        return p.i64;
    }
    uint32_t get(const C2FieldSupportedValues::Primitive &p, uint32_t*) {
        return p.u32;
    }
    uint64_t get(const C2FieldSupportedValues::Primitive &p, uint64_t*) {
        return p.u64;
    }
    float get(const C2FieldSupportedValues::Primitive &p, float*) {
        return p.fp;
    }
};

template<typename E>
struct getter<E, true> {
     typename std::underlying_type<E>::type get(const C2FieldSupportedValues::Primitive &p, E*) {
         using u=typename std::underlying_type<E>::type;
         return getter<u>().get(p, (u*)0);
     }
};

template<typename T, bool E=std::is_enum<T>::value>
struct lax_underlying_type {
    typedef typename std::underlying_type<T>::type type;
};

template<typename T>
struct lax_underlying_type<T, false> {
    typedef T type;
};

template<typename E>
typename lax_underlying_type<E>::type get(
        const C2FieldSupportedValues::Primitive &p, E*) {
    return getter<E>().get(p, (E*)0);
}

template<typename T>
void dumpFSV(const C2FieldSupportedValues &sv, T*t) {
    using namespace std;
    cout << (std::is_enum<T>::value ? (std::is_signed<typename std::underlying_type<T>::type>::value ? "i" : "u")
             : std::is_integral<T>::value ? std::is_signed<T>::value ? "i" : "u" : "f")
        << (8 * sizeof(T));
    if (sv.type == sv.RANGE) {
        cout << ".range(" << get(sv.range.min, t);
        if (get(sv.range.step, t) != std::is_integral<T>::value) {
            cout << ":" << get(sv.range.step, t);
        }
        if (get(sv.range.nom, t) != 1 || get(sv.range.denom, t) != 1) {
            cout << ":" << get(sv.range.nom, t) << "/" << get(sv.range.denom, t);
        }
        cout << get(sv.range.max, t) << ")";
    }
    if (sv.values.size()) {
        cout << (sv.type == sv.FLAGS ? ".flags(" : ".list(");
        const char *sep = "";
        for (const C2FieldSupportedValues::Primitive &p : sv.values) {
            cout << sep << get(p, t);
            sep = ",";
        }
        cout << ")";
    }
    cout << endl;
}

void dumpType(C2Param::Type type) {
    using namespace std;
    cout << (type.isVendor() ? "Vendor" : "C2");
    if (type.forInput()) {
        cout << "Input";
    } else if (type.forOutput()) {
        cout << "Output";
    } else if (type.forPort() && !type.forStream()) {
        cout << "Port";
    }
    if (type.forStream()) {
        cout << "Stream";
    }

    if (type.isFlexible()) {
        cout << "Flex";
    }

    cout << type.paramIndex();

    switch (type.kind()) {
    case C2Param::INFO: cout << "Info"; break;
    case C2Param::SETTING: cout << "Setting"; break;
    case C2Param::TUNING: cout << "Tuning"; break;
    case C2Param::STRUCT: cout << "Struct"; break;
    default: cout << "Kind" << (int32_t)type.kind(); break;
    }
}

void dumpType(C2Param::BaseIndex type) {
    using namespace std;
    cout << (type.isVendor() ? "Vendor" : "C2");
    if (type.isFlexible()) {
        cout << "Flex";
    }

    cout << type.paramIndex() << "Struct";
}

void dumpType(FD::Type type) {
    using namespace std;
    switch (type) {
    case FD::BLOB: cout << "blob "; break;
    case FD::FLOAT: cout << "float "; break;
    case FD::INT32: cout << "int32_t "; break;
    case FD::INT64: cout << "int64_t "; break;
    case FD::UINT32: cout << "uint32_t "; break;
    case FD::UINT64: cout << "uint64_t "; break;
    case FD::STRING: cout << "char "; break;
    default:
        cout << "struct ";
        dumpType((C2Param::Type)type);
        break;
    }
}

void dumpStruct(const C2StructDescriptor &sd) {
    using namespace std;
    cout << "struct ";
    dumpType(sd.baseIndex());
    cout << " {" << endl;
    //C2FieldDescriptor &f;
    for (const C2FieldDescriptor &f : sd) {
        PrintTo(f, &cout);
        cout << endl;

        if (f.namedValues().size()) {
            cout << ".named(";
            const char *sep = "";
            for (const FD::named_value_type &p : f.namedValues()) {
                cout << sep << p.first << "=";
                switch (f.type()) {
                case C2Value::INT32: cout << get(p.second, (int32_t *)0); break;
                case C2Value::INT64: cout << get(p.second, (int64_t *)0); break;
                case C2Value::UINT32: cout << get(p.second, (uint32_t *)0); break;
                case C2Value::UINT64: cout << get(p.second, (uint64_t *)0); break;
                case C2Value::FLOAT: cout << get(p.second, (float *)0); break;
                default: cout << "???"; break;
                }
                sep = ",";
            }
            cout << ")";
        }
    }

    cout << "};" << endl;
}

void dumpDesc(const C2ParamDescriptor &pd) {
    using namespace std;
    if (pd.isRequired()) {
        cout << "required ";
    }
    if (pd.isPersistent()) {
        cout << "persistent ";
    }
    cout << "struct ";
    dumpType(pd.type());
    cout << " " << pd.name() << ";" << endl;
}

TEST_F(C2ParamTest, ReflectorTest) {
    C2ComponentDomainInfo domainInfo;
    std::shared_ptr<C2ComponentInterface> comp(new MyComponentInstance);
    std::vector<C2FieldSupportedValues> values;

    std::unique_ptr<C2StructDescriptor> desc{
        comp->getParamReflector()->describe(C2ComponentDomainInfo::indexFlags)};
    dumpStruct(*desc);

    EXPECT_EQ(
        C2_OK,
        comp->getSupportedValues(
            { C2ParamField(&domainInfo, &C2ComponentDomainInfo::mValue) },
            &values)
    );

    for (const C2FieldSupportedValues &sv : values) {
        dumpFSV(sv, &domainInfo.mValue);
    }
}

C2ENUM(Enum1, uint32_t,
    Enum1Value1,
    Enum1Value2,
    Enum1Value4 = Enum1Value2 + 2,
);

C2ENUM_CUSTOM_PREFIX(Enum2, uint32_t, "Enum",
    Enum2Value1,
    Enum2Value2,
    Enum2Value4 = Enum1Value2 + 2,
);

C2ENUM_CUSTOM_NAMES(Enum3, uint8_t,
    ({ { "value1", Enum3Value1 },
       { "value2", Enum3Value2 },
       { "value4", Enum3Value4 },
       { "invalid", Invalid } }),
    Enum3Value1,
    Enum3Value2,
    Enum3Value4 = Enum3Value2 + 2,
    Invalid,
);

TEST_F(C2ParamTest, EnumUtilsTest) {
    std::vector<std::pair<C2String, Enum3>> pairs ( { { "value1", Enum3Value1 },
      { "value2", Enum3Value2 },
      { "value4", Enum3Value4 },
      { "invalid", Invalid } });
    Enum3 e3;
    FD::namedValuesFor(e3);
}

TEST_F(C2ParamTest, ParamUtilsTest) {
    // upper case
    EXPECT_EQ("yes", C2ParamUtils::camelCaseToDashed("YES"));
    EXPECT_EQ("no", C2ParamUtils::camelCaseToDashed("NO"));
    EXPECT_EQ("yes-no", C2ParamUtils::camelCaseToDashed("YES_NO"));
    EXPECT_EQ("yes-no", C2ParamUtils::camelCaseToDashed("YES__NO"));
    EXPECT_EQ("a2dp", C2ParamUtils::camelCaseToDashed("A2DP"));
    EXPECT_EQ("mp2-ts", C2ParamUtils::camelCaseToDashed("MP2_TS"));
    EXPECT_EQ("block-2d", C2ParamUtils::camelCaseToDashed("BLOCK_2D"));
    EXPECT_EQ("mpeg-2-ts", C2ParamUtils::camelCaseToDashed("MPEG_2_TS"));
    EXPECT_EQ("_hidden-value", C2ParamUtils::camelCaseToDashed("_HIDDEN_VALUE"));
    EXPECT_EQ("__hidden-value2", C2ParamUtils::camelCaseToDashed("__HIDDEN_VALUE2"));
    EXPECT_EQ("__hidden-value-2", C2ParamUtils::camelCaseToDashed("__HIDDEN_VALUE_2"));

    // camel case
    EXPECT_EQ("yes", C2ParamUtils::camelCaseToDashed("Yes"));
    EXPECT_EQ("no", C2ParamUtils::camelCaseToDashed("No"));
    EXPECT_EQ("yes-no", C2ParamUtils::camelCaseToDashed("YesNo"));
    EXPECT_EQ("yes-no", C2ParamUtils::camelCaseToDashed("Yes_No"));
    EXPECT_EQ("mp2-ts", C2ParamUtils::camelCaseToDashed("MP2Ts"));
    EXPECT_EQ("block-2d", C2ParamUtils::camelCaseToDashed("Block2D"));
    EXPECT_EQ("mpeg-2-ts", C2ParamUtils::camelCaseToDashed("Mpeg2ts"));
    EXPECT_EQ("_hidden-value", C2ParamUtils::camelCaseToDashed("_HiddenValue"));
    EXPECT_EQ("__hidden-value-2", C2ParamUtils::camelCaseToDashed("__HiddenValue2"));

    // mixed case
    EXPECT_EQ("mp2t-s", C2ParamUtils::camelCaseToDashed("MP2T_s"));
    EXPECT_EQ("block-2d", C2ParamUtils::camelCaseToDashed("Block_2D"));
    EXPECT_EQ("block-2-d", C2ParamUtils::camelCaseToDashed("Block2_D"));
    EXPECT_EQ("mpeg-2-ts", C2ParamUtils::camelCaseToDashed("Mpeg_2ts"));
    EXPECT_EQ("mpeg-2-ts", C2ParamUtils::camelCaseToDashed("Mpeg_2_TS"));
    EXPECT_EQ("_hidden-value", C2ParamUtils::camelCaseToDashed("_Hidden__VALUE"));
    EXPECT_EQ("__hidden-value-2", C2ParamUtils::camelCaseToDashed("__HiddenValue_2"));
    EXPECT_EQ("_2", C2ParamUtils::camelCaseToDashed("_2"));
    EXPECT_EQ("__23", C2ParamUtils::camelCaseToDashed("__23"));
}

TEST_F(C2ParamTest, C2ValueTest) {
    C2Value val;
    int32_t i32 = -32;
    int64_t i64 = -64;
    uint32_t u32 = 32;
    uint64_t u64 = 64;
    float fp = 1.5f;

    EXPECT_EQ(C2Value::NO_INIT, val.type());
    EXPECT_EQ(false, val.get(&i32));
    EXPECT_EQ(-32, i32);
    EXPECT_EQ(false, val.get(&i64));
    EXPECT_EQ(-64, i64);
    EXPECT_EQ(false, val.get(&u32));
    EXPECT_EQ(32u, u32);
    EXPECT_EQ(false, val.get(&u64));
    EXPECT_EQ(64u, u64);
    EXPECT_EQ(false, val.get(&fp));
    EXPECT_EQ(1.5f, fp);

    val = int32_t(-3216);
    EXPECT_EQ(C2Value::INT32, val.type());
    EXPECT_EQ(true, val.get(&i32));
    EXPECT_EQ(-3216, i32);
    EXPECT_EQ(false, val.get(&i64));
    EXPECT_EQ(-64, i64);
    EXPECT_EQ(false, val.get(&u32));
    EXPECT_EQ(32u, u32);
    EXPECT_EQ(false, val.get(&u64));
    EXPECT_EQ(64u, u64);
    EXPECT_EQ(false, val.get(&fp));
    EXPECT_EQ(1.5f, fp);

    val = uint32_t(3216);
    EXPECT_EQ(C2Value::UINT32, val.type());
    EXPECT_EQ(false, val.get(&i32));
    EXPECT_EQ(-3216, i32);
    EXPECT_EQ(false, val.get(&i64));
    EXPECT_EQ(-64, i64);
    EXPECT_EQ(true, val.get(&u32));
    EXPECT_EQ(3216u, u32);
    EXPECT_EQ(false, val.get(&u64));
    EXPECT_EQ(64u, u64);
    EXPECT_EQ(false, val.get(&fp));
    EXPECT_EQ(1.5f, fp);

    val = int64_t(-6432);
    EXPECT_EQ(C2Value::INT64, val.type());
    EXPECT_EQ(false, val.get(&i32));
    EXPECT_EQ(-3216, i32);
    EXPECT_EQ(true, val.get(&i64));
    EXPECT_EQ(-6432, i64);
    EXPECT_EQ(false, val.get(&u32));
    EXPECT_EQ(3216u, u32);
    EXPECT_EQ(false, val.get(&u64));
    EXPECT_EQ(64u, u64);
    EXPECT_EQ(false, val.get(&fp));
    EXPECT_EQ(1.5f, fp);

    val = uint64_t(6432);
    EXPECT_EQ(C2Value::UINT64, val.type());
    EXPECT_EQ(false, val.get(&i32));
    EXPECT_EQ(-3216, i32);
    EXPECT_EQ(false, val.get(&i64));
    EXPECT_EQ(-6432, i64);
    EXPECT_EQ(false, val.get(&u32));
    EXPECT_EQ(3216u, u32);
    EXPECT_EQ(true, val.get(&u64));
    EXPECT_EQ(6432u, u64);
    EXPECT_EQ(false, val.get(&fp));
    EXPECT_EQ(1.5f, fp);

    val = 15.25f;
    EXPECT_EQ(C2Value::FLOAT, val.type());
    EXPECT_EQ(false, val.get(&i32));
    EXPECT_EQ(-3216, i32);
    EXPECT_EQ(false, val.get(&i64));
    EXPECT_EQ(-6432, i64);
    EXPECT_EQ(false, val.get(&u32));
    EXPECT_EQ(3216u, u32);
    EXPECT_EQ(false, val.get(&u64));
    EXPECT_EQ(6432u, u64);
    EXPECT_EQ(true, val.get(&fp));
    EXPECT_EQ(15.25f, fp);
}

} // namespace android
