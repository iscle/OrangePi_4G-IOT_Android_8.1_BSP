#pragma version(1)
#pragma rs java_package_name(android.renderscript.cts)

const int* pointer;
rs_allocation alloc_in;
rs_allocation alloc_out;

int __attribute__((kernel)) copy(int in) {
    return in;
}

void start() {
    alloc_in = rsGetAllocation(pointer);
    rsForEach(copy, alloc_in, alloc_out);
}
