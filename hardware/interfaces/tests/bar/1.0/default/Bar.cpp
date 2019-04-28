
#define LOG_TAG "hidl_test"

#include "Bar.h"

#include <inttypes.h>

#include <log/log.h>

namespace android {
namespace hardware {
namespace tests {
namespace bar {
namespace V1_0 {
namespace implementation {

Bar::Bar() {
    mFoo = IFoo::getService("", true);
}

// Methods from ::android::hardware::tests::foo::V1_0::IFoo follow.
Return<void> Bar::doThis(float param) {
    return mFoo->doThis(param);
}

Return<int32_t> Bar::doThatAndReturnSomething(
        int64_t param) {
    return mFoo->doThatAndReturnSomething(param);
}

Return<double> Bar::doQuiteABit(
        int32_t a,
        int64_t b,
        float c,
        double d) {
    return mFoo->doQuiteABit(a, b, c, d);
}

Return<void> Bar::doSomethingElse(
        const hidl_array<int32_t, 15> &param, doSomethingElse_cb _cb) {
    return mFoo->doSomethingElse(param, _cb);
}

Return<void> Bar::doStuffAndReturnAString(
        doStuffAndReturnAString_cb _cb) {
    return mFoo->doStuffAndReturnAString(_cb);
}

Return<void> Bar::mapThisVector(
        const hidl_vec<int32_t> &param, mapThisVector_cb _cb) {
    return mFoo->mapThisVector(param, _cb);
}

Return<void> Bar::callMe(
        const sp<IFooCallback> &cb) {
    return mFoo->callMe(cb);
}

Return<Bar::SomeEnum> Bar::useAnEnum(SomeEnum param) {
    return mFoo->useAnEnum(param);
}

Return<void> Bar::haveAGooberVec(const hidl_vec<Goober>& param) {
    return mFoo->haveAGooberVec(param);
}

Return<void> Bar::haveAGoober(const Goober &g) {
    return mFoo->haveAGoober(g);
}

Return<void> Bar::haveAGooberArray(const hidl_array<Goober, 20> &lots) {
    return mFoo->haveAGooberArray(lots);
}

Return<void> Bar::haveATypeFromAnotherFile(const Abc &def) {
    return mFoo->haveATypeFromAnotherFile(def);
}

Return<void> Bar::haveSomeStrings(
        const hidl_array<hidl_string, 3> &array,
        haveSomeStrings_cb _cb) {
    return mFoo->haveSomeStrings(array, _cb);
}

Return<void> Bar::haveAStringVec(
        const hidl_vec<hidl_string> &vector,
        haveAStringVec_cb _cb) {
    return mFoo->haveAStringVec(vector, _cb);
}

Return<void> Bar::transposeMe(
        const hidl_array<float, 3, 5> &in, transposeMe_cb _cb) {
    return mFoo->transposeMe(in, _cb);
}

Return<void> Bar::callingDrWho(
        const MultiDimensional &in, callingDrWho_cb _hidl_cb) {
    return mFoo->callingDrWho(in, _hidl_cb);
}

Return<void> Bar::transpose(const StringMatrix5x3 &in, transpose_cb _hidl_cb) {
    return mFoo->transpose(in, _hidl_cb);
}

Return<void> Bar::transpose2(
        const hidl_array<hidl_string, 5, 3> &in, transpose2_cb _hidl_cb) {
    return mFoo->transpose2(in, _hidl_cb);
}

Return<void> Bar::sendVec(
        const hidl_vec<uint8_t> &data, sendVec_cb _hidl_cb) {
    return mFoo->sendVec(data, _hidl_cb);
}

Return<void> Bar::sendVecVec(sendVecVec_cb _hidl_cb) {
    return mFoo->sendVecVec(_hidl_cb);
}

Return<void> Bar::haveAVectorOfInterfaces(
        const hidl_vec<sp<ISimple> > &in,
        haveAVectorOfInterfaces_cb _hidl_cb) {
    _hidl_cb(in);

    return Void();
}

Return<void> Bar::haveAVectorOfGenericInterfaces(
        const hidl_vec<sp<android::hidl::base::V1_0::IBase> > &in,
        haveAVectorOfGenericInterfaces_cb _hidl_cb) {
    _hidl_cb(in);

    return Void();
}

Return<void> Bar::createMyHandle(createMyHandle_cb _hidl_cb) {
    return mFoo->createMyHandle(_hidl_cb);
}

Return<void> Bar::createHandles(uint32_t size, createHandles_cb _hidl_cb) {
    return mFoo->createHandles(size, _hidl_cb);
}

Return<void> Bar::closeHandles() {
    return mFoo->closeHandles();
}

Return<void> Bar::echoNullInterface(const sp<IFooCallback> &cb, echoNullInterface_cb _hidl_cb) {
    return mFoo->echoNullInterface(cb, _hidl_cb);
}

// Methods from ::android::hardware::tests::bar::V1_0::IBar follow.
Return<void> Bar::thisIsNew()  {
    ALOGI("SERVER(Bar) thisIsNew");

    return Void();
}

Return<void> Bar::expectNullHandle(const hidl_handle& h, const Abc& xyz, expectNullHandle_cb _hidl_cb) {
    ALOGI("SERVER(Bar) h = %p, xyz.z = %p", h.getNativeHandle(), xyz.z.getNativeHandle());
    _hidl_cb(h == nullptr, xyz.z == nullptr);
    return Void();
}

Return<void> Bar::takeAMask(BitField bf, uint8_t first, const MyMask& second, uint8_t third,
            takeAMask_cb _hidl_cb) {
    _hidl_cb(bf, bf | first, second.value & bf, (bf | bf) & third);
    return Void();
}

Return<sp<ISimple>> Bar::haveAInterface(const sp<ISimple> &in) {
    return in;
}


IBar* HIDL_FETCH_IBar(const char* /* name */) {
    return new Bar();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace bar
}  // namespace tests
}  // namespace hardware
}  // namespace android
