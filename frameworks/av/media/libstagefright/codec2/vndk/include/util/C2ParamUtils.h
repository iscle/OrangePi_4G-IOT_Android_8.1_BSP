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

#ifndef C2UTILS_PARAM_UTILS_H_
#define C2UTILS_PARAM_UTILS_H_

#include <C2Param.h>
#include <util/_C2MacroUtils.h>

#include <iostream>

/** \file
 * Utilities for parameter handling to be used by Codec2 implementations.
 */

namespace android {

/// \cond INTERNAL

/* ---------------------------- UTILITIES FOR ENUMERATION REFLECTION ---------------------------- */

/**
 * Utility class that allows ignoring enum value assignment (e.g. both '(_C2EnumConst)kValue = x'
 * and '(_C2EnumConst)kValue' will eval to kValue.
 */
template<typename T>
class _C2EnumConst {
public:
    // implicit conversion from T
    inline _C2EnumConst(T value) : _mValue(value) {}
    // implicit conversion to T
    inline operator T() { return _mValue; }
    // implicit conversion to C2Value::Primitive
    inline operator C2Value::Primitive() { return (T)_mValue; }
    // ignore assignment and return T here to avoid implicit conversion to T later
    inline T &operator =(T value __unused) { return _mValue; }
private:
    T _mValue;
};

/// mapper to get name of enum
/// \note this will contain any initialization, which we will remove when converting to lower-case
#define _C2_GET_ENUM_NAME(x, y) #x
/// mapper to get value of enum
#define _C2_GET_ENUM_VALUE(x, type) (_C2EnumConst<type>)x

/// \endcond

#define DEFINE_C2_ENUM_VALUE_AUTO_HELPER(name, type, prefix, ...) \
template<> C2FieldDescriptor::named_values_type C2FieldDescriptor::namedValuesFor(const name &r __unused) { \
    return C2ParamUtils::sanitizeEnumValues( \
            std::vector<C2Value::Primitive> { _C2_MAP(_C2_GET_ENUM_VALUE, type, __VA_ARGS__) }, \
            { _C2_MAP(_C2_GET_ENUM_NAME, type, __VA_ARGS__) }, \
            prefix); \
}

#define DEFINE_C2_ENUM_VALUE_CUSTOM_HELPER(name, type, names, ...) \
template<> C2FieldDescriptor::named_values_type C2FieldDescriptor::namedValuesFor(const name &r __unused) { \
    return C2ParamUtils::customEnumValues( \
            std::vector<std::pair<C2StringLiteral, name>> names); \
}


class C2ParamUtils {
private:
    static size_t countLeadingUnderscores(C2StringLiteral a) {
        size_t i = 0;
        while (a[i] == '_') {
            ++i;
        }
        return i;
    }

    static size_t countMatching(C2StringLiteral a, const C2String &b) {
        for (size_t i = 0; i < b.size(); ++i) {
            if (!a[i] || a[i] != b[i]) {
                return i;
            }
        }
        return b.size();
    }

    // ABCDef => abc-def
    // ABCD2ef => abcd2-ef // 0
    // ABCD2Ef => ancd2-ef // -1
    // AbcDef => abc-def // -1
    // Abc2Def => abc-2def
    // Abc2def => abc-2-def
    // _Yo => _yo
    // _yo => _yo
    // C2_yo => c2-yo
    // C2__yo => c2-yo

    static C2String camelCaseToDashed(C2String name) {
        enum {
            kNone = '.',
            kLower = 'a',
            kUpper = 'A',
            kDigit = '1',
            kDash = '-',
            kUnderscore = '_',
        } type = kNone;
        size_t word_start = 0;
        for (size_t ix = 0; ix < name.size(); ++ix) {
            /* std::cout << name.substr(0, word_start) << "|"
                    << name.substr(word_start, ix - word_start) << "["
                    << name.substr(ix, 1) << "]" << name.substr(ix + 1)
                    << ": " << (char)type << std::endl; */
            if (isupper(name[ix])) {
                if (type == kLower) {
                    name.insert(ix++, 1, '-');
                    word_start = ix;
                }
                name[ix] = tolower(name[ix]);
                type = kUpper;
            } else if (islower(name[ix])) {
                if (type == kDigit && ix > 0) {
                    name.insert(ix++, 1, '-');
                    word_start = ix;
                } else if (type == kUpper && ix > word_start + 1) {
                    name.insert(ix++ - 1, 1, '-');
                    word_start = ix - 1;
                }
                type = kLower;
            } else if (isdigit(name[ix])) {
                if (type == kLower) {
                    name.insert(ix++, 1, '-');
                    word_start = ix;
                }
                type = kDigit;
            } else if (name[ix] == '_') {
                if (type == kDash) {
                    name.erase(ix--, 1);
                } else if (type != kNone && type != kUnderscore) {
                    name[ix] = '-';
                    type = kDash;
                    word_start = ix + 1;
                } else {
                    type = kUnderscore;
                    word_start = ix + 1;
                }
            } else {
                name.resize(ix);
            }
        }
        // std::cout << "=> " << name << std::endl;
        return name;
    }

    static std::vector<C2String> sanitizeEnumValueNames(
            const std::vector<C2StringLiteral> names,
            C2StringLiteral _prefix = NULL) {
        std::vector<C2String> sanitizedNames;
        C2String prefix;
        size_t extraUnderscores = 0;
        bool first = true;
        if (_prefix) {
            extraUnderscores = countLeadingUnderscores(_prefix);
            prefix = _prefix + extraUnderscores;
            first = false;
            // std::cout << "prefix:" << prefix << ", underscores:" << extraUnderscores << std::endl;
        }

        // calculate prefix and minimum leading underscores
        for (C2StringLiteral s : names) {
            // std::cout << s << std::endl;
            size_t underscores = countLeadingUnderscores(s);
            if (first) {
                extraUnderscores = underscores;
                prefix = s + underscores;
                first = false;
            } else {
                size_t matching = countMatching(
                    s + underscores,
                    prefix);
                prefix.resize(matching);
                extraUnderscores = std::min(underscores, extraUnderscores);
            }
            // std::cout << "prefix:" << prefix << ", underscores:" << extraUnderscores << std::endl;
            if (prefix.size() == 0 && extraUnderscores == 0) {
                break;
            }
        }

        // we swallow the first underscore after upper case prefixes
        bool upperCasePrefix = true;
        for (size_t i = 0; i < prefix.size(); ++i) {
            if (islower(prefix[i])) {
                upperCasePrefix = false;
                break;
            }
        }

        for (C2StringLiteral s : names) {
            size_t underscores = countLeadingUnderscores(s);
            C2String sanitized = C2String(s, underscores - extraUnderscores);
            sanitized.append(s + prefix.size() + underscores +
                        (upperCasePrefix && s[prefix.size() + underscores] == '_'));
            sanitizedNames.push_back(camelCaseToDashed(sanitized));
        }

        for (C2String s : sanitizedNames) {
            std::cout << s << std::endl;
        }

        return sanitizedNames;
    }

    friend class C2ParamTest_ParamUtilsTest_Test;

public:
    static std::vector<C2String> getEnumValuesFromString(C2StringLiteral value) {
        std::vector<C2String> foundNames;
        size_t pos = 0, len = strlen(value);
        do {
            size_t endPos = strcspn(value + pos, " ,=") + pos;
            if (endPos > pos) {
                foundNames.emplace_back(value + pos, endPos - pos);
            }
            if (value[endPos] && value[endPos] != ',') {
                endPos += strcspn(value + endPos, ",");
            }
            pos = strspn(value + endPos, " ,") + endPos;
        } while (pos < len);
        return foundNames;
    }

    template<typename T>
    static C2FieldDescriptor::named_values_type sanitizeEnumValues(
            std::vector<T> values,
            std::vector<C2StringLiteral> names,
            C2StringLiteral prefix = NULL) {
        C2FieldDescriptor::named_values_type namedValues;
        std::vector<C2String> sanitizedNames = sanitizeEnumValueNames(names, prefix);
        for (size_t i = 0; i < values.size() && i < sanitizedNames.size(); ++i) {
            namedValues.emplace_back(sanitizedNames[i], values[i]);
        }
        return namedValues;
    }

    template<typename E>
    static C2FieldDescriptor::named_values_type customEnumValues(
            std::vector<std::pair<C2StringLiteral, E>> items) {
        C2FieldDescriptor::named_values_type namedValues;
        for (auto &item : items) {
            namedValues.emplace_back(item.first, item.second);
        }
        return namedValues;
    }
};

/* ---------------------------- UTILITIES FOR PARAMETER REFLECTION ---------------------------- */

/* ======================== UTILITY TEMPLATES FOR PARAMETER REFLECTION ======================== */

#if 1
template<typename... Params>
class C2_HIDE _C2Tuple { };

C2_HIDE
void addC2Params(std::list<const C2FieldDescriptor> &, _C2Tuple<> *) {
}

template<typename T, typename... Params>
C2_HIDE
void addC2Params(std::list<const C2FieldDescriptor> &fields, _C2Tuple<T, Params...> *)
{
    //C2Param::index_t index = T::baseIndex;
    //(void)index;
    fields.insert(fields.end(), T::fieldList);
    addC2Params(fields, (_C2Tuple<Params...> *)nullptr);
}

template<typename... Params>
C2_HIDE
std::list<const C2FieldDescriptor> describeC2Params() {
    std::list<const C2FieldDescriptor> fields;
    addC2Params(fields, (_C2Tuple<Params...> *)nullptr);
    return fields;
}

#endif

/* ---------------------------- UTILITIES FOR ENUMERATION REFLECTION ---------------------------- */

}  // namespace android

#endif  // C2UTILS_PARAM_UTILS_H_

