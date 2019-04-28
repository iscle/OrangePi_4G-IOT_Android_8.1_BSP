#include "FooHelper.h"

namespace android {

std::string to_string(const IFoo::StringMatrix5x3 &M) {
    return to_string(M.s);
}

std::string to_string(const IFoo::StringMatrix3x5 &M) {
    return to_string(M.s);
}

std::string to_string(const hidl_string &s) {
    return std::string("'") + s.c_str() + "'";
}

std::string QuuxToString(const IFoo::Quux &val) {
    std::string s;

    s = "Quux(first='";
    s += val.first.c_str();
    s += "', last='";
    s += val.last.c_str();
    s += "')";

    return s;
}

std::string MultiDimensionalToString(const IFoo::MultiDimensional &val) {
    std::string s;

    s += "MultiDimensional(";

    s += "quuxMatrix=[";

    size_t k = 0;
    for (size_t i = 0; i < 5; ++i) {
        if (i > 0) {
            s += ", ";
        }

        s += "[";
        for (size_t j = 0; j < 3; ++j, ++k) {
            if (j > 0) {
                s += ", ";
            }

            s += QuuxToString(val.quuxMatrix[i][j]);
        }
    }
    s += "]";

    s += ")";

    return s;
}
} // namespace android
