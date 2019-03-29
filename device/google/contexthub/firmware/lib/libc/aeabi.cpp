/*
 * Copyright (C) 2016 The Android Open Source Project
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

/*
 * THis file is based on the template provided in the reference doc:
 * http://infocenter.arm.com/help/topic/com.arm.doc.ihi0041e/IHI0041E_cppabi.pdf
 *
 * ARM reference implementation is not changed, except as follows:
 *
 * 1 Function prototypes added to avoid compiler warning "no previous function declaration".
 *   Since all of those are internal functions presumably exclusively used by the compiler,
 *   I decided not to provide header file for those and embed function prototypes in the source.
 *
 * 2 Methods calling into __cxa_*() primitives;
 *   I decided to not implement such __cxa_*() primitives, and code the functionality directly in aeabi.
 *   this works because the toolchain we use is generating calls to aeabi, and not generic calls.
 *   Decision was made to simplify the solution, because generic code must take care of more corner
 *   cases than necessary in ARM case.
 *   strictly speaking, aeabi.cpp is ARM-specific and should be annotated as such.
 *   This is easy to do in Android.mk build, but not that easy with original Makefile build.
 *   For now, I'm going to ignore both the missing ARM-specific annotation, and missing
 *   certain __cxa_*() calls; I'll deal with both when it comes to that (i.e. when we actually
 *   have an offending use case).
 *
 * 3 __aeabi_atexit() was originally calling __cxa_atexit(); I changed that to do-nothing stub;
 *   this is because dynamic registration of destructors as per standard would require one to reserve
 *   sizeof(uintptr_t) * 32 bytes (i.e. 128 bytes on ARM Cortex M4) to comply with standard,
 *   and on top of that, be able to register variable amount of destructor methods (which may be of any size).
 *   possible solution would be to reserve extra space for BSS as boot time, and release extra space
 *   after init is done, however this does not solve entire problem, only global part of it, since
 *   local static registration may happen at any time.
 *   Another possible solution is to provide size of destructor allocation heap at build time;
 *   I reserved a field for that in application data segment for future use.
 */

#include <cstddef>
#include <cstdint>
#include <cxxabi.h>

using namespace __cxxabiv1;

namespace __aeabiv1 {

using ::std::size_t;

// Note: Only the __aeabi_* names are exported.
// array_cookie, cookie_size, cookie_of, etc. are presented for exposition only.
// They are not expected to be available to users, but implementers may find them useful.
struct array_cookie {
    size_t element_size; // element_size != 0
    size_t element_count;
};
// The struct array_cookie fields and the arguments element_size and element_count
// are ordered for convenient use of LDRD/STRD on architecture 5TE and above.
const size_t cookie_size = sizeof(array_cookie);

// cookie_of() takes a pointer to the user array and returns a reference to the cookie.
inline array_cookie& cookie_of(void* user_array)
{
    return reinterpret_cast<array_cookie*>(user_array)[-1];
}

// element_size_of() takes a pointer to the user array and returns a reference to the
// element_size field of the cookie.
inline size_t& element_size_of(void* user_array)
{
    return cookie_of(user_array).element_size;
}

// element_count_of() takes a pointer to the user array and returns a reference to the
// element_count field of the cookie.
inline size_t& element_count_of(void* user_array)
{
    return cookie_of(user_array).element_count;
}

// user_array_of() takes a pointer to the cookie and returns a pointer to the user array.
inline void* user_array_of(array_cookie* cookie_address)
{
    return cookie_address + 1;
}

extern "C" void* __aeabi_vec_ctor_nocookie_nodtor(void* user_array,
                                                  void* (*constructor)(void*),
                                                  size_t element_size, size_t element_count);
extern "C" void* __aeabi_vec_ctor_cookie_nodtor(array_cookie* cookie,
                                                void*(*constructor)(void*),
                                                size_t element_size, size_t element_count);
extern "C" void* __aeabi_vec_cctor_nocookie_nodtor(void* user_array_dest,
                                                   void* user_array_src,
                                                   size_t element_size, size_t element_count,
                                                   void* (*copy_constructor)(void*, void*));
extern "C" void* __aeabi_vec_new_cookie_noctor(size_t element_size, size_t element_count);
extern "C" int __aeabi_atexit(void* object, void (*destroyer)(void*), void* dso_handle);
extern "C" void __aeabi_vec_delete3_nodtor(void* user_array, void (*dealloc)(void*, size_t));
extern "C" void __aeabi_vec_delete3(void* user_array, void* (*destructor)(void*),
                                    void (*dealloc)(void*, size_t));
extern "C" void __aeabi_vec_delete(void* user_array, void* (*destructor)(void*));
extern "C" void* __aeabi_vec_dtor_cookie(void* user_array, void* (*destructor)(void*));
extern "C" void* __aeabi_vec_dtor(void* user_array,
                                  void* (*destructor)(void*),
                                  size_t element_size, size_t element_count);
extern "C" void* __aeabi_vec_new_cookie(size_t element_size, size_t element_count,
                                        void* (*constructor)(void*),
                                        void* (*destructor)(void*));
extern "C" void* __aeabi_vec_new_cookie_nodtor(size_t element_size,
                                               size_t element_count,
                                               void* (*constructor)(void*));
extern "C" void* __aeabi_vec_new_nocookie(size_t element_size, size_t element_count,
                                          void* (*constructor)(void*));

extern "C" void* __aeabi_vec_ctor_nocookie_nodtor(void* user_array,
                                       void* (*constructor)(void*),
                                       size_t element_size, size_t element_count)
{
    if (constructor != nullptr) {
        uintptr_t addr = reinterpret_cast<uintptr_t>(user_array);
        for (size_t i = 0; i < element_count; ++i, addr += element_size) {
            constructor(reinterpret_cast<void*>(addr));
        }
    }
    return user_array;
}

// __aeabi_vec_ctor_cookie_nodtor is like __aeabi_vec_ctor_nocookie_nodtor but sets
// cookie fields and returns user_array. The parameters are arranged to make STRD
// usable. Does nothing and returns NULL if cookie is NULL.
extern "C" void* __aeabi_vec_ctor_cookie_nodtor(array_cookie* cookie,
                                                void*(*constructor)(void*),
                                                size_t element_size, size_t element_count)
{
    if (cookie == nullptr) {
        return nullptr;
    } else {
        cookie->element_size = element_size;
        cookie->element_count = element_count;
        return __aeabi_vec_ctor_nocookie_nodtor(user_array_of(cookie), constructor,
                                                element_size, element_count);
    }
}

extern "C" void* __aeabi_vec_cctor_nocookie_nodtor(void* user_array_dest,
                                                   void* user_array_src,
                                                   size_t element_size, size_t element_count,
                                                   void* (*copy_constructor)(void*, void*))
{
    if (copy_constructor != nullptr) {
        uintptr_t src_addr = reinterpret_cast<uintptr_t>(user_array_src);
        uintptr_t dest_addr = reinterpret_cast<uintptr_t>(user_array_dest);
        for (size_t i = 0; i < element_count; ++i, src_addr += element_size, dest_addr += element_size) {
            copy_constructor(reinterpret_cast<void*>(dest_addr), reinterpret_cast<void*>(src_addr));
        }
    }
    return user_array_dest;
}

extern "C" void* __aeabi_vec_new_cookie_noctor(size_t element_size, size_t element_count)
{
    array_cookie* cookie = reinterpret_cast<array_cookie*>(
            ::operator new[](element_count * element_size + cookie_size)
    );
    cookie->element_size = element_size;
    cookie->element_count = element_count;
    return user_array_of(cookie);
}

extern "C" void* __aeabi_vec_new_nocookie(size_t element_size, size_t element_count,
                                          void* (*constructor)(void*))
{
    return __aeabi_vec_ctor_nocookie_nodtor(::operator new[](element_count * element_size),
            constructor, element_size, element_count);
}

extern "C" void* __aeabi_vec_new_cookie_nodtor(size_t element_size,
                                               size_t element_count,
                                               void* (*constructor)(void*))
{
    array_cookie* cookie = reinterpret_cast<array_cookie*>(
            ::operator new[](element_count * element_size + cookie_size)
    );
    return __aeabi_vec_ctor_cookie_nodtor(cookie, constructor, element_size, element_count);
}

extern "C" void* __aeabi_vec_new_cookie(size_t element_size, size_t element_count,
                                        void* (*constructor)(void*),
                                        void* (*destructor)(void*))
{
    return __aeabi_vec_new_cookie_nodtor(element_size, element_count, constructor);
}

// __aeabi_vec_dtor is like __cxa_vec_dtor but has its parameters reordered and returns
// a pointer to the cookie (assuming user_array has one).
// Unlike __cxa_vec_dtor, destructor must not be NULL.
// user_array must not be NULL.
extern "C" void* __aeabi_vec_dtor(void* user_array,
                                  void* (*destructor)(void*),
                                  size_t element_size, size_t element_count)
{
    uintptr_t addr = reinterpret_cast<uintptr_t>(user_array);
    for (size_t i = 0; i < element_count; ++i, addr += element_size) {
        destructor(reinterpret_cast<void*>(addr));
    }
    return &cookie_of(user_array);
}

// __aeabi_vec_dtor_cookie is only used on arrays that have cookies.
// __aeabi_vec_dtor is like __cxa_vec_dtor but returns a pointer to the cookie.
// That is, it takes a pointer to the user array, calls the given destructor on
// each element (from highest index down to zero) and returns a pointer to the cookie.
// Does nothing and returns NULL if cookie is NULL.
// Unlike __cxa_vec_dtor, destructor must not be NULL.
// Exceptions are handled as in __cxa_vec_dtor.
// __aeabi_vec_dtor_cookie must not change the element count in the cookie.
// (But it may corrupt the element size if desired.)
extern "C" void* __aeabi_vec_dtor_cookie(void* user_array, void* (*destructor)(void*))
{
    return user_array == nullptr ? nullptr :
                         __aeabi_vec_dtor(user_array, destructor,
                                          element_size_of(user_array),
                                          element_count_of(user_array));
}

extern "C" void __aeabi_vec_delete(void* user_array, void* (*destructor)(void*))
{
    ::operator delete[](__aeabi_vec_dtor_cookie(user_array, destructor));
}

extern "C" void __aeabi_vec_delete3(void* user_array, void* (*destructor)(void*), void (*dealloc)(void*, size_t))
{
    if (user_array != NULL) {
        size_t size = element_size_of(user_array) * element_count_of(user_array) + cookie_size;
        void *array_cookie = __aeabi_vec_dtor_cookie(user_array, destructor);
        dealloc(array_cookie, size);
    }
}

extern "C" void __aeabi_vec_delete3_nodtor(void* user_array, void (*dealloc)(void*, size_t))
{
    if (user_array != NULL) {
        size_t size = element_size_of(user_array) * element_count_of(user_array) + cookie_size;
        (*dealloc)(&cookie_of(user_array), size);
    }
}

extern "C" int __aeabi_atexit(void* object, void (*destroyer)(void*), void* dso_handle)
{
    return 0;
}

} // namespace __aeabiv1
