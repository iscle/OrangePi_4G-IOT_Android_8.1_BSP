#ifndef ANDROID_HARDWARE_TESTS_POINTER_V1_0_POINTER_H
#define ANDROID_HARDWARE_TESTS_POINTER_V1_0_POINTER_H

#include <android/hardware/tests/pointer/1.0/IPointer.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>

// TODO move to Pointer.cpp so that I won't have weird macros in headers
#define PUSH_ERROR_IF(__cond__) if(__cond__) { errors.push_back(std::to_string(__LINE__) + ": " + #__cond__); }

namespace android {
namespace hardware {
namespace tests {
namespace pointer {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::pointer::V1_0::IPointer;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Pointer : public IPointer {
private:
    std::vector<std::string> errors;
public:
    Return<int32_t> getErrors() override;
    Return<void> foo1(const IPointer::Sam& s, IPointer::Sam const* s_ptr) override {
        PUSH_ERROR_IF(!(&s == s_ptr));
        return Void();
    }
    Return<void> foo2(const IPointer::Sam& s, const IPointer::Ada& a) override {
        PUSH_ERROR_IF(!(&s == a.s_ptr));
        return Void();
    }
    Return<void> foo3(const IPointer::Sam& s, const IPointer::Ada& a, const IPointer::Bob& b) override {
        PUSH_ERROR_IF(!(&a == b.a_ptr && a.s_ptr == b.s_ptr && a.s_ptr == &s));
        return Void();
    }
    Return<void> foo4(IPointer::Sam const* s_ptr) override {
        PUSH_ERROR_IF(!(s_ptr->data == 500));
        return Void();
    }
    Return<void> foo5(const IPointer::Ada& a, const IPointer::Bob& b) override {
        PUSH_ERROR_IF(!(a.s_ptr == b.s_ptr && b.a_ptr == &a));
        return Void();
    }
    Return<void> foo6(IPointer::Ada const* a_ptr) override {
        PUSH_ERROR_IF(!(a_ptr->s_ptr->data == 500));
        return Void();
    }
    Return<void> foo7(IPointer::Ada const* a_ptr, IPointer::Bob const* b_ptr) override {
        PUSH_ERROR_IF(!(a_ptr->s_ptr == b_ptr->s_ptr && a_ptr == b_ptr->a_ptr && a_ptr->s_ptr->data == 500));
        return Void();
    }
    Return<void> foo8(const IPointer::Dom& d) override {
        const IPointer::Cin& c = d.c;
        PUSH_ERROR_IF(&c.a != c.b_ptr->a_ptr);
        PUSH_ERROR_IF(c.a.s_ptr != c.b_ptr->s_ptr);
        PUSH_ERROR_IF(c.a.s_ptr->data != 500);
        return Void();
    }
    Return<void> foo9(::android::hardware::hidl_string const* str_ref) override {
        PUSH_ERROR_IF(!(strcmp(str_ref->c_str(), "meowmeowmeow") == 0));
        return Void();
    }
    Return<void> foo10(const ::android::hardware::hidl_vec<IPointer::Sam const*>& s_ptr_vec) override {
        PUSH_ERROR_IF(s_ptr_vec[0]->data != 500);
        if(s_ptr_vec.size() != 5) {
            errors.push_back("foo10: s_ptr_vec.size() != 5");
            return Void();
        }
        for(size_t i = 0; i < s_ptr_vec.size(); i++)
            PUSH_ERROR_IF(s_ptr_vec[0] != s_ptr_vec[i]);
        return Void();
    }
    Return<void> foo11(::android::hardware::hidl_vec<IPointer::Sam> const* s_vec_ptr) override {
        if(s_vec_ptr->size() != 5) {
            errors.push_back("foo11: s_vec_ptr->size() != 5");
            return Void();
        }
        for(size_t i = 0; i < 5; i++)
            PUSH_ERROR_IF((*s_vec_ptr)[i].data != 500);
        return Void();
    }
    Return<void> foo12(hidl_array<IPointer::Sam, 5> const* s_array_ref) override {
        for(size_t i = 0; i < 5; ++i)
            PUSH_ERROR_IF((*s_array_ref)[i].data != 500);
        return Void();
    }
    Return<void> foo13(const hidl_array<IPointer::Sam const*, 5>& s_ref_array) override {
        PUSH_ERROR_IF(s_ref_array[0]->data != 500)
        for(size_t i = 0; i < 5; i++)
            PUSH_ERROR_IF(s_ref_array[i] != s_ref_array[0])
        return Void();
    }
    Return<void> foo14(IPointer::Sam const* const* const* s_3ptr) override {
        PUSH_ERROR_IF(!((***s_3ptr).data == 500))
        return Void();
    }
    Return<void> foo15(int32_t const* const* const* i_3ptr) override {
        PUSH_ERROR_IF(!((***i_3ptr) == 500))
        return Void();
    }

    Return<void> foo16(const IPointer::Ptr& p) override {
        PUSH_ERROR_IF((*p.array_ptr)[0].s_ptr->data != 500);
        for(size_t i = 0; i < 5; i++) PUSH_ERROR_IF((*p.array_ptr)[i].s_ptr != (*p.array_ptr)[0].s_ptr);
        PUSH_ERROR_IF(*(p.int_ptr) != 500);
        for(size_t i = 0; i < 5; i++) PUSH_ERROR_IF((*p.int_array_ptr)[i] != 500);
        for(size_t i = 0; i < 5; i++) PUSH_ERROR_IF(p.int_ptr_array[i] != p.int_ptr);
        PUSH_ERROR_IF(p.a_ptr_vec.size() != 5);
        PUSH_ERROR_IF(p.a_ptr_vec[0]->s_ptr->data != 500);
        for(size_t i = 0; i < 5; i++) PUSH_ERROR_IF(p.a_ptr_vec[i]->s_ptr != p.a_ptr_vec[0]->s_ptr);
        PUSH_ERROR_IF(strcmp(p.str_ref->c_str(), "meowmeowmeow") != 0);
        PUSH_ERROR_IF(p.a_vec_ptr->size() != 5);
        PUSH_ERROR_IF((*p.a_vec_ptr)[0].s_ptr->data != 500);
        for(size_t i = 0; i < 5; i++) PUSH_ERROR_IF((*p.a_vec_ptr)[i].s_ptr != (*p.a_vec_ptr)[0].s_ptr);
        return Void();
    };
    Return<void> foo17(IPointer::Ptr const* p) override {
        return foo16(*p);
    };
    Return<void> foo18(hidl_string const* str_ref, hidl_string const* str_ref2, const hidl_string& str) override {
        PUSH_ERROR_IF(&str != str_ref);
        PUSH_ERROR_IF(str_ref != str_ref2);
        PUSH_ERROR_IF(strcmp(str.c_str(), "meowmeowmeow") != 0)
        return Void();
    };
    Return<void> foo19(
                hidl_vec<IPointer::Ada> const* a_vec_ref,
                const hidl_vec<IPointer::Ada>& a_vec,
                hidl_vec<IPointer::Ada> const* a_vec_ref2) {
        PUSH_ERROR_IF(&a_vec != a_vec_ref);
        PUSH_ERROR_IF(a_vec_ref2 != a_vec_ref);
        PUSH_ERROR_IF(a_vec.size() != 5);
        PUSH_ERROR_IF(a_vec[0].s_ptr->data != 500);
        for(size_t i = 0; i < 5; i++)
            PUSH_ERROR_IF(a_vec[i].s_ptr != a_vec[0].s_ptr);
        return Void();
    };

    Return<void> foo20(const hidl_vec<IPointer::Sam const*>&) override {
        return Void();
    }
    Return<void> foo21(hidl_array<IPointer::Ada, 1, 2, 3> const* a_array_ptr) override {
        const hidl_array<IPointer::Ada, 1, 2, 3>& a_array = *a_array_ptr;
        PUSH_ERROR_IF(a_array[0][0][0].s_ptr->data != 500);
        for(size_t i = 0; i < 1; i++)
            for(size_t j = 0; j < 2; j++)
                for(size_t k = 0; k < 3; k++)
                    PUSH_ERROR_IF(a_array[i][j][k].s_ptr != a_array[0][0][0].s_ptr);
        return Void();
    }
    Return<void> foo22(const hidl_array<IPointer::Ada const*, 1, 2, 3>& a_ptr_array) override {
        PUSH_ERROR_IF(a_ptr_array[0][0][0]->s_ptr->data != 500);
        for(size_t i = 0; i < 1; i++)
            for(size_t j = 0; j < 2; j++)
                for(size_t k = 0; k < 3; k++)
                    PUSH_ERROR_IF(a_ptr_array[i][j][k] != a_ptr_array[0][0][0]);
        return Void();
    }

    IPointer::Sam *s;
    IPointer::Ada *a;
    IPointer::Bob *b;
    IPointer::Cin *c;
    IPointer::Dom *d;

    IPointer::Ptr p;
    hidl_array<IPointer::Ada, 5> a_array;
    int32_t someInt;
    hidl_array<int32_t, 5> someIntArray;
    hidl_string str;
    hidl_vec<IPointer::Ada> a_vec;
    Pointer() {
        d = new IPointer::Dom();
        s = new IPointer::Sam();
        b = new IPointer::Bob();
        c = &d->c;
        a = &c->a;
        b->s_ptr = a->s_ptr = s;
        b->a_ptr = a;
        c->b_ptr = b;
        s->data = 500;

        someInt = 500;
        for(size_t i = 0; i < 5; i++) someIntArray[i] = 500;

        for(size_t i = 0; i < 5; i++) a_array[i] = *a;

        for(size_t i = 0; i < 5; i++) p.ptr_array[i] = a;
        p.array_ptr = &a_array;
        p.int_ptr = &someInt;
        p.int_array_ptr = &someIntArray;
        for(size_t i = 0; i < 5; i++) p.int_ptr_array[i] = &someInt;
        p.a_ptr_vec.resize(5);
        for(size_t i = 0; i < 5; i++) p.a_ptr_vec[i] = a;
        str = "meowmeowmeow";
        p.str_ref = &str;
        a_vec.resize(5);
        for(size_t i = 0; i < 5; i++) a_vec[i].s_ptr = s;
        p.a_vec_ptr = &a_vec;
    }
    ~Pointer() {
        delete d; delete s; delete b;
    }
    Return<void> bar1(bar1_cb _cb) override {
        _cb(*s, s);
        return Void();
    }
    Return<void> bar2(bar2_cb _cb) override {
        _cb(*s, *a);
        return Void();
    }
    Return<void> bar3(bar3_cb _cb) override {
        _cb(*s, *a, *b);
        return Void();
    }
    Return<void> bar4(bar4_cb _cb) override {
        _cb(s);
        return Void();
    }
    Return<void> bar5(bar5_cb _cb) override {
        _cb(*a, *b);
        return Void();
    }
    Return<void> bar6(bar6_cb _cb) override {
        _cb(a);
        return Void();
    }
    Return<void> bar7(bar7_cb _cb) override {
        _cb(a, b);
        return Void();
    }
    Return<void> bar8(bar8_cb _cb) override {
        _cb(*d);
        return Void();
    }
    Return<void> bar9(bar9_cb _cb) override {
        _cb(&str);
        return Void();
    }
    Return<void> bar10(bar10_cb _cb) override {
        hidl_vec<const IPointer::Sam *> v; v.resize(5);
        for(size_t i = 0; i < 5; i++) v[i] = s;
        _cb(v);
        return Void();
    }
    Return<void> bar11(bar11_cb _cb) override {
        hidl_vec<IPointer::Sam> v; v.resize(5);
        for(size_t i = 0; i < 5; i++) v[i].data = 500;
            _cb(&v);
        return Void();
    }
    Return<void> bar12(bar12_cb _cb) override {
        hidl_array<IPointer::Sam, 5> array;
        for(size_t i = 0; i < 5; i++) array[i] = *s;
        _cb(&array);
        return Void();
    }
    Return<void> bar13(bar13_cb _cb) override {
        hidl_array<const IPointer::Sam *, 5> array;
        for(size_t i = 0; i < 5; i++) array[i] = s;
        _cb(array);
        return Void();
    }
    Return<void> bar14(bar14_cb _cb) override {
        IPointer::Sam const* p1 = s;
        IPointer::Sam const* const* p2 = &p1;
        _cb(&p2);
        return Void();
    }
    Return<void> bar15(bar15_cb _cb) override {
        int32_t const* p1 = &someInt;
        int32_t const* const* p2 = &p1;
        _cb(&p2);
        return Void();
    }
    Return<void> bar16(bar16_cb _cb) override {
        _cb(p);
        return Void();
    }
    Return<void> bar17(bar17_cb _cb) override {
        _cb(&p);
        return Void();
    }
    Return<void> bar18(bar18_cb _cb) override {
        _cb(&str, &str, str);
        return Void();
    }
    Return<void> bar19(bar19_cb _cb) override {
        _cb(&a_vec, a_vec, &a_vec);
        return Void();
    }
    Return<void> bar20(bar20_cb _cb) override {
        // 1026 == PARCEL_REF_CAP + 2.
        // 1026 means 1 writeBuffer and 1025 writeReferences. 1025 > PARCEL_REF_CAP.
        hidl_vec<const IPointer::Sam *> v; v.resize(1026);
        for(size_t i = 0; i < 1026; i++) v[i] = s;
        _cb(v);
        return Void();
    }
    Return<void> bar21(bar21_cb _cb) override {
        hidl_array<IPointer::Ada, 1, 2, 3> a_array;
        for(size_t i = 0; i < 1; i++)
            for(size_t j = 0; j < 2; j++)
                for(size_t k = 0; k < 3; k++)
                    a_array[i][j][k] = *a;
        _cb(&a_array);
        return Void();
    }
    Return<void> bar22(bar22_cb _cb) override {
        hidl_array<const IPointer::Ada *, 1, 2, 3> a_ptr_array;
        for(size_t i = 0; i < 1; i++)
            for(size_t j = 0; j < 2; j++)
                for(size_t k = 0; k < 3; k++)
                    a_ptr_array[i][j][k] = a;
        _cb(a_ptr_array);
        return Void();
    }
};

extern "C" IPointer* HIDL_FETCH_IPointer(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace pointer
}  // namespace tests
}  // namespace hardware
}  // namespace android

#undef PUSH_ERROR_IF

#endif  // ANDROID_HARDWARE_TESTS_POINTER_V1_0_POINTER_H
