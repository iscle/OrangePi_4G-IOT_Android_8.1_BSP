#include "shared.rsh"

rs_allocation aInt;

void write_i(int value, uint32_t x) {
    rsSetElementAt_int(aInt, value, x);
}

void __attribute__((kernel)) write_k(int unused) {
    rsSetElementAt_int(aInt, 1, 1);
}

// OOB tests on rsAllocationCopy[12]DRange
rs_allocation aIn1D;
rs_allocation aOut1D;
rs_allocation aIn2D;
rs_allocation aOut2D;

int dstXOff = 0;
int srcXOff = 0;
int yOff = 0;
int xCount = 0;
int yCount = 0;
int srcMip = 0;  // Only used in 1D tests
int dstMip = 0;  // Ditto.

void test1D() {
    rsAllocationCopy1DRange(aOut1D, dstXOff, dstMip, xCount,
		    aIn1D, srcXOff, srcMip);
}

void test2D() {
    rsAllocationCopy2DRange(aOut2D, dstXOff, yOff, 0, 0, xCount, yCount,
		    aIn2D, srcXOff, yOff, 0, 0);
}
