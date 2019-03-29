#include "shared.rsh"

#define MAKE_TEST(T) \
void vector_test_##T(T t) {} \
void vector_test_##T##2(T##2 t) {} \
void vector_test_##T##3(T##3 t) {} \
void vector_test_##T##4(T##4 t) {} \

MAKE_TEST(float)
MAKE_TEST(double)
MAKE_TEST(char)
MAKE_TEST(uchar)
MAKE_TEST(short)
MAKE_TEST(ushort)
MAKE_TEST(int)
MAKE_TEST(uint)
MAKE_TEST(long)
MAKE_TEST(ulong)

