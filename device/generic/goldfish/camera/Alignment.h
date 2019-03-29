#ifndef HW_EMULATOR_CAMERA_ALIGNMENT_H
#define HW_EMULATOR_CAMERA_ALIGNMENT_H

namespace android {

// Align |value| to the next larger value that is divisible by |alignment|
// |alignment| has to be a power of 2.
inline int align(int value, int alignment) {
    return (value + alignment - 1) & (~(alignment - 1));
}

}  // namespace android

#endif  // HW_EMULATOR_CAMERA_ALIGNMENT_H
