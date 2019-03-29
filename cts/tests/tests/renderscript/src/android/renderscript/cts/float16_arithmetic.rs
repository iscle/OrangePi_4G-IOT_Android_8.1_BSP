#pragma version(1)
#pragma rs java_package_name(android.renderscript.cts)

rs_allocation gInput;

half RS_KERNEL add(int x, int y) {
    half a = rsGetElementAt_half(gInput, x);
    half b = rsGetElementAt_half(gInput, y);
    return a + b;
}

half RS_KERNEL sub(int x, int y) {
    half a = rsGetElementAt_half(gInput, x);
    half b = rsGetElementAt_half(gInput, y);
    return a - b;
}

half RS_KERNEL mul(int x, int y) {
    half a = rsGetElementAt_half(gInput, x);
    half b = rsGetElementAt_half(gInput, y);
    return a * b;
}

half RS_KERNEL div(int x, int y) {
    half a = rsGetElementAt_half(gInput, x);
    half b = rsGetElementAt_half(gInput, y);
    return a / b;
}

union Bit16Type {
    half h;
    unsigned short s;
};

unsigned short __attribute__((kernel)) bitcast(half h) {
    union Bit16Type u = {h};
    return u.s;
}
