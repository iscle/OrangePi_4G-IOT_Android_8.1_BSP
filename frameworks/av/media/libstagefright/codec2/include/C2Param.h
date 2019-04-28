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

#ifndef C2PARAM_H_
#define C2PARAM_H_

#include <C2.h>

#include <stdbool.h>
#include <stdint.h>

#include <algorithm>
#include <list>
#include <string>
#include <type_traits>

#define C2_PACK __attribute__((packed))

namespace android {

/// \addtogroup Parameters
/// @{

/// \defgroup internal Internal helpers.

/*!
 * \file
 * PARAMETERS: SETTINGs, TUNINGs, and INFOs
 * ===
 *
 * These represent miscellaneous control and metadata information and are likely copied into
 * kernel space. Therefore, these are C-like structures designed to carry just a small amount of
 * information. We are using C++ to be able to add constructors, as well as non-virtual and class
 * methods.
 *
 * ==Specification details:
 *
 * Restrictions:
 *   - must be POD struct, e.g. no vtable (no virtual destructor)
 *   - must have the same size in 64-bit and 32-bit mode (no size_t)
 *   - as such, no pointer members
 *
 * Behavior:
 * - Params can be global (not related to input or output), related to input or output,
 *   or related to an input/output stream.
 * - All params are queried/set using a unique param index, which incorporates a potential stream
 *   index and/or port.
 * - Querying (supported) params MUST never fail.
 * - All params MUST have default values.
 * - If some fields have "unsupported" or "invalid" values during setting, this SHOULD be
 *   communicated to the app.
 *   a) Ideally, this should be avoided.  When setting parameters, in general, component should do
 *     "best effort" to apply all settings. It should change "invalid/unsupported" values to the
 *     nearest supported values.
 *   - This is communicated to the client by changing the source values in tune()/
 *     configure().
 *   b) If falling back to a supported value is absolutely impossible, the component SHALL return
 *     an error for the specific setting, but should continue to apply other settings.
 *     TODO: this currently may result in unintended results.
 *
 * **NOTE:** unlike OMX, params are not versioned. Instead, a new struct with new base index
 * SHALL be added as new versions are required.
 *
 * The proper subtype (Setting, Info or Param) is incorporated into the class type. Define structs
 * to define multiple subtyped versions of related parameters.
 *
 * ==Implementation details:
 *
 * - Use macros to define parameters
 * - All parameters must have a default constructor
 *   - This is only used for instantiating the class in source (e.g. will not be used
 *     when building a parameter by the framework from key/value pairs.)
 */

/// \ingroup internal
struct _C2ParamManipulator;

/**
 * Parameter base class.
 */
struct C2Param {
    // param index encompasses the following:
    //
    // - type (setting, tuning, info, struct)
    // - vendor extension flag
    // - flexible parameter flag
    // - direction (global, input, output)
    // - stream flag
    // - stream ID (usually 0)
    //
    // layout:
    //
    //      +------+-----+---+------+--------+----|------+--------------+
    //      | kind | dir | - |stream|streamID|flex|vendor|  base index  |
    //      +------+-----+---+------+--------+----+------+--------------+
    //  bit: 31..30 29.28       25   24 .. 17  16    15   14    ..     0
    //
public:
    /**
     * C2Param kinds, usable as bitmaps.
     */
    enum Kind : uint32_t {
        NONE    = 0,
        STRUCT  = (1 << 0),
        INFO    = (1 << 1),
        SETTING = (1 << 2),
        TUNING  = (1 << 3) | SETTING, // tunings are settings
    };

    /**
     * base index (including the vendor extension bit) is a global index for
     * C2 parameter structs. (e.g. the same indices cannot be reused for different
     * structs for different components).
     */
    struct BaseIndex {
    protected:
        enum : uint32_t {
            kTypeMask      = 0xC0000000,
            kTypeStruct    = 0x00000000,
            kTypeTuning    = 0x40000000,
            kTypeSetting   = 0x80000000,
            kTypeInfo      = 0xC0000000,

            kDirMask       = 0x30000000,
            kDirGlobal     = 0x20000000,
            kDirUndefined  = 0x30000000, // MUST have all bits set
            kDirInput      = 0x00000000,
            kDirOutput     = 0x10000000,

            kStreamFlag    = 0x02000000,
            kStreamIdMask  = 0x01FE0000,
            kStreamIdShift = 17,
            kStreamIdMax   = kStreamIdMask >> kStreamIdShift,
            kStreamMask    = kStreamFlag | kStreamIdMask,

            kFlexibleFlag  = 0x00010000,
            kVendorFlag    = 0x00008000,
            kParamMask     = 0x0000FFFF,
            kBaseMask      = kParamMask | kFlexibleFlag,
        };

    public:
        enum : uint32_t {
            kVendorStart = kVendorFlag, ///< vendor structs SHALL start after this
            _kFlexibleFlag = kFlexibleFlag, // TODO: this is only needed for testing
        };

        /// constructor/conversion from uint32_t
        inline BaseIndex(uint32_t index) : mIndex(index) { }

        // no conversion from uint64_t
        inline BaseIndex(uint64_t index) = delete;

        /// returns true iff this is a vendor extension parameter
        inline bool isVendor() const { return mIndex & kVendorFlag; }

        /// returns true iff this is a flexible parameter (with variable size)
        inline bool isFlexible() const { return mIndex & kFlexibleFlag; }

        /// returns the base type: the index for the underlying struct
        inline unsigned int baseIndex() const { return mIndex & kBaseMask; }

        /// returns the param index for the underlying struct
        inline unsigned int paramIndex() const { return mIndex & kParamMask; }

        DEFINE_FIELD_BASED_COMPARISON_OPERATORS(BaseIndex, mIndex)

    protected:
        uint32_t mIndex;
    };

    /**
     * type encompasses the parameter kind (tuning, setting, info), whether the
     * parameter is global, input or output, and whether it is for a stream.
     */
    struct Type : public BaseIndex {
        /// returns true iff this is a global parameter (not for input nor output)
        inline bool isGlobal() const { return (mIndex & kDirMask) == kDirGlobal; }
        /// returns true iff this is an input or input stream parameter
        inline bool forInput() const { return (mIndex & kDirMask) == kDirInput; }
        /// returns true iff this is an output or output stream parameter
        inline bool forOutput() const { return (mIndex & kDirMask) == kDirOutput; }

        /// returns true iff this is a stream parameter
        inline bool forStream() const { return mIndex & kStreamFlag; }
        /// returns true iff this is a port (input or output) parameter
        inline bool forPort() const   { return !forStream() && !isGlobal(); }

        /// returns the parameter type: the parameter index without the stream ID
        inline uint32_t type() const { return mIndex & (~kStreamIdMask); }

        /// return the kind of this param
        inline Kind kind() const {
            switch (mIndex & kTypeMask) {
                case kTypeStruct: return STRUCT;
                case kTypeInfo: return INFO;
                case kTypeSetting: return SETTING;
                case kTypeTuning: return TUNING;
                default: return NONE; // should not happen
            }
        }

        /// constructor/conversion from uint32_t
        inline Type(uint32_t index) : BaseIndex(index) { }

        // no conversion from uint64_t
        inline Type(uint64_t index) = delete;

    private:
        friend struct C2Param;   // for setPort()
        friend struct C2Tuning;  // for kTypeTuning
        friend struct C2Setting; // for kTypeSetting
        friend struct C2Info;    // for kTypeInfo
        // for kDirGlobal
        template<typename T, typename S, int I, class F> friend struct C2GlobalParam;
        template<typename T, typename S, int I, class F> friend struct C2PortParam;   // for kDir*
        template<typename T, typename S, int I, class F> friend struct C2StreamParam; // for kDir*
        friend struct _C2ParamInspector; // for testing

        /**
         * Sets the port/stream direction.
         * @return true on success, false if could not set direction (e.g. it is global param).
         */
        inline bool setPort(bool output) {
            if (isGlobal()) {
                return false;
            } else {
                mIndex = (mIndex & ~kDirMask) | (output ? kDirOutput : kDirInput);
                return true;
            }
        }
    };

    /**
     * index encompasses all remaining information: basically the stream ID.
     */
    struct Index : public Type {
        /// returns the index as uint32_t
        inline operator uint32_t() const { return mIndex; }

        /// constructor/conversion from uint32_t
        inline Index(uint32_t index) : Type(index) { }

        // no conversion from uint64_t
        inline Index(uint64_t index) = delete;

        /// returns the stream ID or ~0 if not a stream
        inline unsigned stream() const {
            return forStream() ? rawStream() : ~0U;
        }

    private:
        friend struct C2Param;           // for setStream, makeStreamId, isValid
        friend struct _C2ParamInspector; // for testing

        /**
         * @return true if the type is valid, e.g. direction is not undefined AND
         * stream is 0 if not a stream param.
         */
        inline bool isValid() const {
            // there is no Type::isValid (even though some of this check could be
            // performed on types) as this is only used on index...
            return (forStream() ? rawStream() < kStreamIdMax : rawStream() == 0)
                    && (mIndex & kDirMask) != kDirUndefined;
        }

        /// returns the raw stream ID field
        inline unsigned rawStream() const {
            return (mIndex & kStreamIdMask) >> kStreamIdShift;
        }

        /// returns the streamId bitfield for a given |stream|. If stream is invalid,
        /// returns an invalid bitfield.
        inline static uint32_t makeStreamId(unsigned stream) {
            // saturate stream ID (max value is invalid)
            if (stream > kStreamIdMax) {
                stream = kStreamIdMax;
            }
            return (stream << kStreamIdShift) & kStreamIdMask;
        }

        /**
         * Sets the stream index.
         * \return true on success, false if could not set index (e.g. not a stream param).
         */
        inline bool setStream(unsigned stream) {
            if (forStream()) {
                mIndex = (mIndex & ~kStreamIdMask) | makeStreamId(stream);
                return this->stream() < kStreamIdMax;
            }
            return false;
        }
    };

public:
    // public getters for Index methods

    /// returns true iff this is a vendor extension parameter
    inline bool isVendor() const { return _mIndex.isVendor(); }
    /// returns true iff this is a flexible parameter
    inline bool isFlexible() const { return _mIndex.isFlexible(); }
    /// returns true iff this is a global parameter (not for input nor output)
    inline bool isGlobal() const { return _mIndex.isGlobal(); }
    /// returns true iff this is an input or input stream parameter
    inline bool forInput() const { return _mIndex.forInput(); }
    /// returns true iff this is an output or output stream parameter
    inline bool forOutput() const { return _mIndex.forOutput(); }

    /// returns true iff this is a stream parameter
    inline bool forStream() const { return _mIndex.forStream(); }
    /// returns true iff this is a port (input or output) parameter
    inline bool forPort() const   { return _mIndex.forPort(); }

    /// returns the stream ID or ~0 if not a stream
    inline unsigned stream() const { return _mIndex.stream(); }

    /// returns the parameter type: the parameter index without the stream ID
    inline uint32_t type() const { return _mIndex.type(); }

    /// returns the kind of this parameter
    inline Kind kind() const { return _mIndex.kind(); }

    /// returns the size of the parameter or 0 if the parameter is invalid
    inline size_t size() const { return _mSize; }

    /// returns true iff the parameter is valid
    inline operator bool() const { return _mIndex.isValid() && _mSize > 0; }

    /// returns true iff the parameter is invalid
    inline bool operator!() const { return !operator bool(); }

    // equality is done by memcmp (use equals() to prevent any overread)
    inline bool operator==(const C2Param &o) const {
        return equals(o) && memcmp(this, &o, _mSize) == 0;
    }
    inline bool operator!=(const C2Param &o) const { return !operator==(o); }

    /// safe(r) type cast from pointer and size
    inline static C2Param* From(void *addr, size_t len) {
        // _mSize must fit into size
        if (len < sizeof(_mSize) + offsetof(C2Param, _mSize)) {
            return nullptr;
        }
        // _mSize must match length
        C2Param *param = (C2Param*)addr;
        if (param->_mSize != len) {
            return nullptr;
        }
        return param;
    }

#if 0
    template<typename P, class=decltype(C2Param(P()))>
    P *As() { return P::From(this); }
    template<typename P>
    const P *As() const { return const_cast<const P*>(P::From(const_cast<C2Param*>(this))); }
#endif

protected:
    /// sets the stream field. Returns true iff successful.
    inline bool setStream(unsigned stream) {
        return _mIndex.setStream(stream);
    }

    /// sets the port (direction). Returns true iff successful.
    inline bool setPort(bool output) {
        return _mIndex.setPort(output);
    }

public:
    /// invalidate this parameter. There is no recovery from this call; e.g. parameter
    /// cannot be 'corrected' to be valid.
    inline void invalidate() { _mSize = 0; }

    // if other is the same kind of (valid) param as this, copy it into this and return true.
    // otherwise, do not copy anything, and return false.
    inline bool updateFrom(const C2Param &other) {
        if (other._mSize == _mSize && other._mIndex == _mIndex && _mSize > 0) {
            memcpy(this, &other, _mSize);
            return true;
        }
        return false;
    }

protected:
    // returns |o| if it is a null ptr, or if can suitably be a param of given |type| (e.g. has
    // same type (ignoring stream ID), and size). Otherwise, returns null. If |checkDir| is false,
    // allow undefined or different direction (e.g. as constructed from C2PortParam() vs.
    // C2PortParam::input), but still require equivalent type (stream, port or global); otherwise,
    // return null.
    inline static const C2Param* ifSuitable(
            const C2Param* o, size_t size, Type type, size_t flexSize = 0, bool checkDir = true) {
        if (o == nullptr || o->_mSize < size || (flexSize && ((o->_mSize - size) % flexSize))) {
            return nullptr;
        } else if (checkDir) {
            return o->_mIndex.type() == type.mIndex ? o : nullptr;
        } else if (o->_mIndex.isGlobal()) {
            return nullptr;
        } else {
            return ((o->_mIndex.type() ^ type.mIndex) & ~Type::kDirMask) ? nullptr : o;
        }
    }

    /// base constructor
    inline C2Param(uint32_t paramSize, Index paramIndex)
        : _mSize(paramSize),
          _mIndex(paramIndex) {
        if (paramSize > sizeof(C2Param)) {
            memset(this + 1, 0, paramSize - sizeof(C2Param));
        }
    }

    /// base constructor with stream set
    inline C2Param(uint32_t paramSize, Index paramIndex, unsigned stream)
        : _mSize(paramSize),
          _mIndex(paramIndex | Index::makeStreamId(stream)) {
        if (paramSize > sizeof(C2Param)) {
            memset(this + 1, 0, paramSize - sizeof(C2Param));
        }
        if (!forStream()) {
            invalidate();
        }
    }

private:
    friend struct _C2ParamInspector; // for testing

    /// returns the base type: the index for the underlying struct (for testing
    /// as this can be gotten by the baseIndex enum)
    inline uint32_t _baseIndex() const { return _mIndex.baseIndex(); }

    /// returns true iff |o| has the same size and index as this. This performs the
    /// basic check for equality.
    inline bool equals(const C2Param &o) const {
        return _mSize == o._mSize && _mIndex == o._mIndex;
    }

    uint32_t _mSize;
    Index _mIndex;
};

/// \ingroup internal
/// allow C2Params access to private methods, e.g. constructors
#define C2PARAM_MAKE_FRIENDS \
    template<typename U, typename S, int I, class F> friend struct C2GlobalParam; \
    template<typename U, typename S, int I, class F> friend struct C2PortParam; \
    template<typename U, typename S, int I, class F> friend struct C2StreamParam; \

/**
 * Setting base structure for component method signatures. Wrap constructors.
 */
struct C2Setting : public C2Param {
protected:
    template<typename ...Args>
    inline C2Setting(const Args(&... args)) : C2Param(args...) { }
public: // TODO
    enum : uint32_t { indexFlags = Type::kTypeSetting };
};

/**
 * Tuning base structure for component method signatures. Wrap constructors.
 */
struct C2Tuning : public C2Setting {
protected:
    template<typename ...Args>
    inline C2Tuning(const Args(&... args)) : C2Setting(args...) { }
public: // TODO
    enum : uint32_t { indexFlags = Type::kTypeTuning };
};

/**
 * Info base structure for component method signatures. Wrap constructors.
 */
struct C2Info : public C2Param {
protected:
    template<typename ...Args>
    inline C2Info(const Args(&... args)) : C2Param(args...) { }
public: // TODO
    enum : uint32_t { indexFlags = Type::kTypeInfo };
};

/**
 * Structure uniquely specifying a field in an arbitrary structure.
 *
 * \note This structure is used differently in C2FieldDescriptor to
 * identify array fields, such that _mSize is the size of each element. This is
 * because the field descriptor contains the array-length, and we want to keep
 * a relevant element size for variable length arrays.
 */
struct _C2FieldId {
//public:
    /**
     * Constructor used for C2FieldDescriptor that removes the array extent.
     *
     * \param[in] offset pointer to the field in an object at address 0.
     */
    template<typename T, class B=typename std::remove_extent<T>::type>
    inline _C2FieldId(T* offset)
        : // offset is from "0" so will fit on 32-bits
          _mOffset((uint32_t)(uintptr_t)(offset)),
          _mSize(sizeof(B)) { }

    /**
     * Direct constructor from offset and size.
     *
     * \param[in] offset offset of the field.
     * \param[in] size size of the field.
     */
    inline _C2FieldId(size_t offset, size_t size)
        : _mOffset(offset), _mSize(size) {}

    /**
     * Constructor used to identify a field in an object.
     *
     * \param U[type] pointer to the object that contains this field. This is needed in case the
     *        field is in an (inherited) base class, in which case T will be that base class.
     * \param pm[im] member pointer to the field
     */
    template<typename R, typename T, typename U, typename B=typename std::remove_extent<R>::type>
    inline _C2FieldId(U *, R T::* pm)
        : _mOffset((uint32_t)(uintptr_t)(&(((U*)256)->*pm)) - 256u),
          _mSize(sizeof(B)) { }

    /**
     * Constructor used to identify a field in an object.
     *
     * \param U[type] pointer to the object that contains this field
     * \param pm[im] member pointer to the field
     */
    template<typename R, typename T, typename B=typename std::remove_extent<R>::type>
    inline _C2FieldId(R T::* pm)
        : _mOffset((uint32_t)(uintptr_t)(&(((T*)0)->*pm))),
          _mSize(sizeof(B)) { }

    inline bool operator==(const _C2FieldId &other) const {
        return _mOffset == other._mOffset && _mSize == other._mSize;
    }

    inline bool operator<(const _C2FieldId &other) const {
        return _mOffset < other._mOffset ||
            // NOTE: order parent structure before sub field
            (_mOffset == other._mOffset && _mSize > other._mSize);
    }

    DEFINE_OTHER_COMPARISON_OPERATORS(_C2FieldId)

#if 0
    inline uint32_t offset() const { return _mOffset; }
    inline uint32_t size() const { return _mSize; }
#endif

#if defined(FRIEND_TEST)
    friend void PrintTo(const _C2FieldId &d, ::std::ostream*);
#endif

private:
    uint32_t _mOffset; // offset of field
    uint32_t _mSize;   // size of field
};

/**
 * Structure uniquely specifying a field in a configuration
 */
struct C2ParamField {
//public:
    // TODO: fix what this is for T[] (for now size becomes T[1])
    template<typename S, typename T>
    inline C2ParamField(S* param, T* offset)
        : _mIndex(param->index()),
          _mFieldId(offset) {}

    template<typename R, typename T, typename U>
    inline C2ParamField(U *p, R T::* pm) : _mIndex(p->type()), _mFieldId(p, pm) { }

    inline bool operator==(const C2ParamField &other) const {
        return _mIndex == other._mIndex && _mFieldId == other._mFieldId;
    }

    inline bool operator<(const C2ParamField &other) const {
        return _mIndex < other._mIndex ||
            (_mIndex == other._mIndex && _mFieldId < other._mFieldId);
    }

    DEFINE_OTHER_COMPARISON_OPERATORS(C2ParamField)

private:
    C2Param::Index _mIndex;
    _C2FieldId _mFieldId;
};

/**
 * A shared (union) representation of numeric values
 */
class C2Value {
public:
    /// A union of supported primitive types.
    union Primitive {
        int32_t  i32;   ///< int32_t value
        uint32_t u32;   ///< uint32_t value
        int64_t  i64;   ///< int64_t value
        uint64_t u64;   ///< uint64_t value
        float    fp;    ///< float value

        // constructors - implicit
        Primitive(int32_t value)  : i32(value) { }
        Primitive(uint32_t value) : u32(value) { }
        Primitive(int64_t value)  : i64(value) { }
        Primitive(uint64_t value) : u64(value) { }
        Primitive(float value)    : fp(value)  { }

        Primitive() : u64(0) { }

    private:
        friend class C2Value;
        template<typename T> const T &ref() const;
    };

    enum Type {
        NO_INIT,
        INT32,
        UINT32,
        INT64,
        UINT64,
        FLOAT,
    };

    template<typename T> static constexpr Type typeFor();

    // constructors - implicit
    template<typename T>
    C2Value(T value)  : mType(typeFor<T>()),  mValue(value) { }

    C2Value() : mType(NO_INIT) { }

    inline Type type() const { return mType; }

    template<typename T>
    inline bool get(T *value) const {
        if (mType == typeFor<T>()) {
            *value = mValue.ref<T>();
            return true;
        }
        return false;
    }

private:
    Type mType;
    Primitive mValue;
};

template<> const int32_t &C2Value::Primitive::ref<int32_t>() const { return i32; }
template<> const int64_t &C2Value::Primitive::ref<int64_t>() const { return i64; }
template<> const uint32_t &C2Value::Primitive::ref<uint32_t>() const { return u32; }
template<> const uint64_t &C2Value::Primitive::ref<uint64_t>() const { return u64; }
template<> const float &C2Value::Primitive::ref<float>() const { return fp; }

template<> constexpr C2Value::Type C2Value::typeFor<int32_t>() { return INT32; }
template<> constexpr C2Value::Type C2Value::typeFor<int64_t>() { return INT64; }
template<> constexpr C2Value::Type C2Value::typeFor<uint32_t>() { return UINT32; }
template<> constexpr C2Value::Type C2Value::typeFor<uint64_t>() { return UINT64; }
template<> constexpr C2Value::Type C2Value::typeFor<float>() { return FLOAT; }

/**
 * field descriptor. A field is uniquely defined by an index into a parameter.
 * (Note: Stream-id is not captured as a field.)
 *
 * Ordering of fields is by offset. In case of structures, it is depth first,
 * with a structure taking an index just before and in addition to its members.
 */
struct C2FieldDescriptor {
//public:
    /** field types and flags
     * \note: only 32-bit and 64-bit fields are supported (e.g. no boolean, as that
     * is represented using INT32).
     */
    enum Type : uint32_t {
        // primitive types
        INT32   = C2Value::INT32,  ///< 32-bit signed integer
        UINT32  = C2Value::UINT32, ///< 32-bit unsigned integer
        INT64   = C2Value::INT64,  ///< 64-bit signed integer
        UINT64  = C2Value::UINT64, ///< 64-bit signed integer
        FLOAT   = C2Value::FLOAT,  ///< 32-bit floating point

        // array types
        STRING = 0x100, ///< fixed-size string (POD)
        BLOB,           ///< blob. Blobs have no sub-elements and can be thought of as byte arrays;
                        ///< however, bytes cannot be individually addressed by clients.

        // complex types
        STRUCT_FLAG = 0x10000, ///< structs. Marked with this flag in addition to their baseIndex.
    };

    typedef std::pair<C2String, C2Value::Primitive> named_value_type;
    typedef std::vector<const named_value_type> named_values_type;
    //typedef std::pair<std::vector<C2String>, std::vector<C2Value::Primitive>> named_values_type;

    /**
     * Template specialization that returns the named values for a type.
     *
     * \todo hide from client.
     *
     * \return a vector of name-value pairs.
     */
    template<typename B>
    static named_values_type namedValuesFor(const B &);

    inline C2FieldDescriptor(uint32_t type, uint32_t length, C2StringLiteral name, size_t offset, size_t size)
        : _mType((Type)type), _mLength(length), _mName(name), _mFieldId(offset, size) { }

    template<typename T, class B=typename std::remove_extent<T>::type>
    inline C2FieldDescriptor(const T* offset, const char *name)
        : _mType(this->getType((B*)nullptr)),
          _mLength(std::is_array<T>::value ? std::extent<T>::value : 1),
          _mName(name),
          _mNamedValues(namedValuesFor(*(B*)0)),
          _mFieldId(offset) {}

/*
    template<typename T, typename B=typename std::remove_extent<T>::type>
    inline C2FieldDescriptor<T, B, false>(T* offset, const char *name)
        : _mType(this->getType((B*)nullptr)),
          _mLength(std::is_array<T>::value ? std::extent<T>::value : 1),
          _mName(name),
          _mFieldId(offset) {}
*/

    /// \deprecated
    template<typename T, typename S, class B=typename std::remove_extent<T>::type>
    constexpr inline C2FieldDescriptor(S*, T S::* field, const char *name)
        : _mType(this->getType((B*)nullptr)),
          _mLength(std::is_array<T>::value ? std::extent<T>::value : 1),
          _mName(name),
          _mFieldId(&(((S*)0)->*field)) {}

    /// returns the type of this field
    inline Type type() const { return _mType; }
    /// returns the length of the field in case it is an array. Returns 0 for
    /// T[] arrays, returns 1 for T[1] arrays as well as if the field is not an array.
    inline size_t length() const { return _mLength; }
    /// returns the name of the field
    inline C2StringLiteral name() const { return _mName; }

    const named_values_type &namedValues() const { return _mNamedValues; }

#if defined(FRIEND_TEST)
    friend void PrintTo(const C2FieldDescriptor &, ::std::ostream*);
    friend bool operator==(const C2FieldDescriptor &, const C2FieldDescriptor &);
    FRIEND_TEST(C2ParamTest_ParamFieldList, VerifyStruct);
#endif

private:
    const Type _mType;
    const uint32_t _mLength; // the last member can be arbitrary length if it is T[] array,
                       // extending to the end of the parameter (this is marked with
                       // 0). T[0]-s are not fields.
    const C2StringLiteral _mName;
    const named_values_type _mNamedValues;

    const _C2FieldId _mFieldId;   // field identifier (offset and size)

    // NOTE: We do not capture default value(s) here as that may depend on the component.
    // NOTE: We also do not capture bestEffort, as 1) this should be true for most fields,
    // 2) this is at parameter granularity.

    // type resolution
    inline static Type getType(int32_t*)  { return INT32; }
    inline static Type getType(uint32_t*) { return UINT32; }
    inline static Type getType(int64_t*)  { return INT64; }
    inline static Type getType(uint64_t*) { return UINT64; }
    inline static Type getType(float*)    { return FLOAT; }
    inline static Type getType(char*)     { return STRING; }
    inline static Type getType(uint8_t*)  { return BLOB; }

    template<typename T,
             class=typename std::enable_if<std::is_enum<T>::value>::type>
    inline static Type getType(T*) {
        typename std::underlying_type<T>::type underlying(0);
        return getType(&underlying);
    }

    // verify C2Struct by having a fieldList and a baseIndex.
    template<typename T,
             class=decltype(T::baseIndex + 1), class=decltype(T::fieldList)>
    inline static Type getType(T*) {
        static_assert(!std::is_base_of<C2Param, T>::value, "cannot use C2Params as fields");
        return (Type)(T::baseIndex | STRUCT_FLAG);
    }
};

#define DEFINE_NO_NAMED_VALUES_FOR(type) \
template<> inline C2FieldDescriptor::named_values_type C2FieldDescriptor::namedValuesFor(const type &) { \
    return named_values_type(); \
}

// We cannot subtype constructor for enumerated types so insted define no named values for
// non-enumerated integral types.
DEFINE_NO_NAMED_VALUES_FOR(int32_t)
DEFINE_NO_NAMED_VALUES_FOR(uint32_t)
DEFINE_NO_NAMED_VALUES_FOR(int64_t)
DEFINE_NO_NAMED_VALUES_FOR(uint64_t)
DEFINE_NO_NAMED_VALUES_FOR(uint8_t)
DEFINE_NO_NAMED_VALUES_FOR(char)
DEFINE_NO_NAMED_VALUES_FOR(float)

/**
 * Describes the fields of a structure.
 */
struct C2StructDescriptor {
public:
    /// Returns the parameter type
    inline C2Param::BaseIndex baseIndex() const { return _mType.baseIndex(); }

    // Returns the number of fields in this param (not counting any recursive fields).
    // Must be at least 1 for valid params.
    inline size_t numFields() const { return _mFields.size(); }

    // Returns the list of immediate fields (not counting any recursive fields).
    typedef std::vector<const C2FieldDescriptor>::const_iterator field_iterator;
    inline field_iterator cbegin() const { return _mFields.cbegin(); }
    inline field_iterator cend() const { return _mFields.cend(); }

    // only supplying const iterator - but these are needed for range based loops
    inline field_iterator begin() const { return _mFields.cbegin(); }
    inline field_iterator end() const { return _mFields.cend(); }

    template<typename T>
    inline C2StructDescriptor(T*)
        : C2StructDescriptor(T::baseIndex, T::fieldList) { }

    inline C2StructDescriptor(
            C2Param::BaseIndex type,
            std::initializer_list<const C2FieldDescriptor> fields)
        : _mType(type), _mFields(fields) { }

private:
    const C2Param::BaseIndex _mType;
    const std::vector<const C2FieldDescriptor> _mFields;
};

/**
 * Describes parameters for a component.
 */
struct C2ParamDescriptor {
public:
    /**
     * Returns whether setting this param is required to configure this component.
     * This can only be true for builtin params for platform-defined components (e.g. video and
     * audio encoders/decoders, video/audio filters).
     * For vendor-defined components, it can be true even for vendor-defined params,
     * but it is not recommended, in case the component becomes platform-defined.
     */
    inline bool isRequired() const { return _mIsRequired; }

    /**
     * Returns whether this parameter is persistent. This is always true for C2Tuning and C2Setting,
     * but may be false for C2Info. If true, this parameter persists across frames and applies to
     * the current and subsequent frames. If false, this C2Info parameter only applies to the
     * current frame and is not assumed to have the same value (or even be present) on subsequent
     * frames, unless it is specified for those frames.
     */
    inline bool isPersistent() const { return _mIsPersistent; }

    /// Returns the name of this param.
    /// This defaults to the underlying C2Struct's name, but could be altered for a component.
    inline C2String name() const { return _mName; }

    /// Returns the parameter type
    /// \todo fix this
    inline C2Param::Type type() const { return _mType; }

    template<typename T>
    inline C2ParamDescriptor(bool isRequired, C2StringLiteral name, const T*)
        : _mIsRequired(isRequired),
          _mIsPersistent(true),
          _mName(name),
          _mType(T::typeIndex) { }

    inline C2ParamDescriptor(
            bool isRequired, C2StringLiteral name, C2Param::Type type)
        : _mIsRequired(isRequired),
          _mIsPersistent(true),
          _mName(name),
          _mType(type) { }

private:
    const bool _mIsRequired;
    const bool _mIsPersistent;
    const C2String _mName;
    const C2Param::Type _mType;
};

/// \ingroup internal
/// Define a structure without baseIndex.
#define DEFINE_C2STRUCT_NO_BASE(name) \
public: \
    typedef C2##name##Struct _type; /**< type name shorthand */ \
    const static std::initializer_list<const C2FieldDescriptor> fieldList; /**< structure fields */

/// Define a structure with matching baseIndex.
#define DEFINE_C2STRUCT(name) \
public: \
    enum : uint32_t { baseIndex = kParamIndex##name }; \
    DEFINE_C2STRUCT_NO_BASE(name)

/// Define a flexible structure with matching baseIndex.
#define DEFINE_FLEX_C2STRUCT(name, flexMember) \
public: \
    FLEX(C2##name##Struct, flexMember) \
    enum : uint32_t { baseIndex = kParamIndex##name | C2Param::BaseIndex::_kFlexibleFlag }; \
    DEFINE_C2STRUCT_NO_BASE(name)

/// \ingroup internal
/// Describe a structure of a templated structure.
#define DESCRIBE_TEMPLATED_C2STRUCT(strukt, list) \
    template<> \
    const std::initializer_list<const C2FieldDescriptor> strukt::fieldList = list;

/// \deprecated
/// Describe the fields of a structure using an initializer list.
#define DESCRIBE_C2STRUCT(name, list) \
    const std::initializer_list<const C2FieldDescriptor> C2##name##Struct::fieldList = list;

/**
 * Describe a field of a structure.
 * These must be in order.
 *
 * There are two ways to use this macro:
 *
 *  ~~~~~~~~~~~~~ (.cpp)
 *  struct C2VideoWidthStruct {
 *      int32_t mWidth;
 *      C2VideoWidthStruct() {} // optional default constructor
 *      C2VideoWidthStruct(int32_t _width) : mWidth(_width) {}
 *
 *      DEFINE_AND_DESCRIBE_C2STRUCT(VideoWidth)
 *      C2FIELD(mWidth, "width")
 *  };
 *  ~~~~~~~~~~~~~
 *
 *  ~~~~~~~~~~~~~ (.cpp)
 *  struct C2VideoWidthStruct {
 *      int32_t mWidth;
 *      C2VideoWidthStruct() = default; // optional default constructor
 *      C2VideoWidthStruct(int32_t _width) : mWidth(_width) {}
 *
 *      DEFINE_C2STRUCT(VideoWidth)
 *  } C2_PACK;
 *
 *  DESCRIBE_C2STRUCT(VideoWidth, {
 *      C2FIELD(mWidth, "width")
 *  })
 *  ~~~~~~~~~~~~~
 *
 *  For flexible structures (those ending in T[]), use the flexible macros:
 *
 *  ~~~~~~~~~~~~~ (.cpp)
 *  struct C2VideoFlexWidthsStruct {
 *      int32_t mWidths[];
 *      C2VideoFlexWidthsStruct(); // must have a default constructor
 *
 *  private:
 *      // may have private constructors taking number of widths as the first argument
 *      // This is used by the C2Param factory methods, e.g.
 *      //   C2VideoFlexWidthsGlobalParam::alloc_unique(size_t, int32_t);
 *      C2VideoFlexWidthsStruct(size_t flexCount, int32_t value) {
 *          for (size_t i = 0; i < flexCount; ++i) {
 *              mWidths[i] = value;
 *          }
 *      }
 *
 *      // If the last argument is T[N] or std::initializer_list<T>, the flexCount will
 *      // be automatically calculated and passed by the C2Param factory methods, e.g.
 *      //   int widths[] = { 1, 2, 3 };
 *      //   C2VideoFlexWidthsGlobalParam::alloc_unique(widths);
 *      template<unsigned N>
 *      C2VideoFlexWidthsStruct(size_t flexCount, const int32_t(&init)[N]) {
 *          for (size_t i = 0; i < flexCount; ++i) {
 *              mWidths[i] = init[i];
 *          }
 *      }
 *
 *      DEFINE_AND_DESCRIBE_FLEX_C2STRUCT(VideoFlexWidths, mWidths)
 *      C2FIELD(mWidths, "widths")
 *  };
 *  ~~~~~~~~~~~~~
 *
 *  ~~~~~~~~~~~~~ (.cpp)
 *  struct C2VideoFlexWidthsStruct {
 *      int32_t mWidths[];
 *      C2VideoFlexWidthsStruct(); // must have a default constructor
 *
 *      DEFINE_FLEX_C2STRUCT(VideoFlexWidths, mWidths)
 *  } C2_PACK;
 *
 *  DESCRIBE_C2STRUCT(VideoFlexWidths, {
 *      C2FIELD(mWidths, "widths")
 *  })
 *  ~~~~~~~~~~~~~
 *
 */
#define C2FIELD(member, name) \
  C2FieldDescriptor(&((_type*)(nullptr))->member, name),

/// \deprecated
#define C2SOLE_FIELD(member, name) \
  C2FieldDescriptor(&_type::member, name, 0)

/// Define a structure with matching baseIndex and start describing its fields.
/// This must be at the end of the structure definition.
#define DEFINE_AND_DESCRIBE_C2STRUCT(name) \
    DEFINE_C2STRUCT(name) }  C2_PACK; \
    const std::initializer_list<const C2FieldDescriptor> C2##name##Struct::fieldList = {

/// Define a flexible structure with matching baseIndex and start describing its fields.
/// This must be at the end of the structure definition.
#define DEFINE_AND_DESCRIBE_FLEX_C2STRUCT(name, flexMember) \
    DEFINE_FLEX_C2STRUCT(name, flexMember) } C2_PACK; \
    const std::initializer_list<const C2FieldDescriptor> C2##name##Struct::fieldList = {

/**
 * Parameter reflector class.
 *
 * This class centralizes the description of parameter structures. This can be shared
 * by multiple components as describing a parameter does not imply support of that
 * parameter. However, each supported parameter and any dependent structures within
 * must be described by the parameter reflector provided by a component.
 */
class C2ParamReflector {
public:
    /**
     *  Describes a parameter structure.
     *
     *  \param[in] paramIndex the base index of the parameter structure
     *
     *  \return the description of the parameter structure
     *  \retval nullptr if the parameter is not supported by this reflector
     *
     *  This methods shall not block and return immediately.
     *
     *  \note this class does not take a set of indices because we would then prefer
     *  to also return any dependent structures, and we don't want this logic to be
     *  repeated in each reflector. Alternately, this could just return a map of all
     *  descriptions, but we want to conserve memory if client only wants the description
     *  of a few indices.
     */
    virtual std::unique_ptr<C2StructDescriptor> describe(C2Param::BaseIndex paramIndex) = 0;

protected:
    virtual ~C2ParamReflector() = default;
};

/**
 * A useable supported values for a field.
 *
 * This can be either a range or a set of values. The range can be linear or geometric with a
 * clear minimum and maximum value, and can have an optional step size or geometric ratio. Values
 * can optionally represent flags.
 *
 * \note Do not use flags to represent bitfields. Use individual values or separate fields instead.
 */
template<typename T>
struct C2TypedFieldSupportedValues {
//public:
    enum Type {
        RANGE,      ///< a numeric range that can be continuous or discrete
        VALUES,     ///< a list of values
        FLAGS       ///< a list of flags that can be OR-ed
    };

    Type type;

    struct {
        T min;
        T max;
        T step;
        T nom;
        T denom;
    } range;
    std::vector<T> values;

    C2TypedFieldSupportedValues(T min, T max, T step = T(std::is_floating_point<T>::value ? 0 : 1))
        : type(RANGE),
          range{min, max, step, (T)1, (T)1} { }

    C2TypedFieldSupportedValues(T min, T max, T nom, T den) :
        type(RANGE),
        range{min, max, (T)0, nom, den} { }

    C2TypedFieldSupportedValues(bool flags, std::initializer_list<T> list) :
        type(flags ? FLAGS : VALUES),
        values(list) {}
};

/**
 * Generic supported values for a field.
 *
 * This can be either a range or a set of values. The range can be linear or geometric with a
 * clear minimum and maximum value, and can have an optional step size or geometric ratio. Values
 * can optionally represent flags.
 *
 * \note Do not use flags to represent bitfields. Use individual values or separate fields instead.
 */
struct C2FieldSupportedValues {
//public:
    enum Type {
        RANGE,      ///< a numeric range that can be continuous or discrete
        VALUES,     ///< a list of values
        FLAGS       ///< a list of flags that can be OR-ed
    };

    Type type;

    typedef C2Value::Primitive Primitive;

    struct {
        Primitive min;
        Primitive max;
        Primitive step;
        Primitive nom;
        Primitive denom;
    } range;
    std::vector<Primitive> values;

    template<typename T>
    C2FieldSupportedValues(T min, T max, T step = T(std::is_floating_point<T>::value ? 0 : 1))
        : type(RANGE),
          range{min, max, step, (T)1, (T)1} { }

    template<typename T>
    C2FieldSupportedValues(T min, T max, T nom, T den) :
        type(RANGE),
        range{min, max, (T)0, nom, den} { }

    template<typename T>
    C2FieldSupportedValues(bool flags, std::initializer_list<T> list)
        : type(flags ? FLAGS : VALUES),
          range{(T)0, (T)0, (T)0, (T)0, (T)0} {
        for(T value : list) {
            values.emplace_back(value);
        }
    }

    template<typename T, typename E=decltype(C2FieldDescriptor::namedValuesFor(*(T*)0))>
    C2FieldSupportedValues(bool flags, const T*)
        : type(flags ? FLAGS : VALUES),
          range{(T)0, (T)0, (T)0, (T)0, (T)0} {
              C2FieldDescriptor::named_values_type named = C2FieldDescriptor::namedValuesFor(*(T*)0);
        for (const C2FieldDescriptor::named_value_type &item : named) {
            values.emplace_back(item.second);
        }
    }
};

/// @}

}  // namespace android

#endif  // C2PARAM_H_
