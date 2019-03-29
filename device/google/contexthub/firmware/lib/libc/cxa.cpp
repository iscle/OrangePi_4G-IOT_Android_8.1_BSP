#include <cstdlib>
#include <cstdint>
#include <cxxabi.h>

namespace __cxxabiv1
{
// 3.2.6 Pure Virtual Function API
extern "C" void __cxa_pure_virtual ()
{
    while(true);
}

// 3.2.7 Deleted Virtual Function API
extern "C" void __cxa_deleted_virtual ()
{
    while(true);
}

// 3.3.2 One-time Construction API
// NOTE: Implementation does not support threads; no locking involved

extern "C" int
__cxa_guard_acquire(__guard *_guard)
{
    uint8_t *guard = reinterpret_cast<uint8_t*>(_guard);
    return guard[0] ? 0 : 1;
}

extern "C" void
__cxa_guard_release(__guard *_guard)
{
    uint8_t *guard = reinterpret_cast<uint8_t*>(_guard);
    guard[0] = 1;
}

extern "C" void
__cxa_guard_abort(__guard *_guard)
{
    uint8_t *guard = reinterpret_cast<uint8_t*>(_guard);
    guard[0] = 0;
}

} // namespace __cxxabiv1
