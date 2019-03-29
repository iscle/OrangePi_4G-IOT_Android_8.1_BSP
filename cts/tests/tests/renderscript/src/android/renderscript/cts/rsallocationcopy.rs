#include "shared.rsh"

rs_allocation aIn1D;
rs_allocation aOut1D;
rs_allocation aIn2D;
rs_allocation aOut2D;

int xOff = 0;
int yOff = 0;
int xCount = 0;
int yCount = 0;

void test1D() {
    rsAllocationCopy1DRange(aOut1D, xOff, 0, xCount, aIn1D, xOff, 0);
}

void test2D() {
    rsAllocationCopy2DRange(aOut2D, xOff, yOff, 0, 0, xCount, yCount, aIn2D, xOff, yOff, 0, 0);
}
