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

/** \file
 * Templates used to declare parameters.
 */
#ifndef C2PARAM_DEF_H_
#define C2PARAM_DEF_H_

#include <type_traits>

#include <C2Param.h>

namespace android {

/// \addtogroup Parameters
/// @{

/* ======================== UTILITY TEMPLATES FOR PARAMETER DEFINITIONS ======================== */

/// \addtogroup internal
/// @{

/// Helper class that checks if a type has equality and inequality operators.
struct C2_HIDE _C2Comparable_impl
{
    template<typename S, typename=decltype(S() == S())>
    static std::true_type __testEQ(int);
    template<typename>
    static std::false_type __testEQ(...);

    template<typename S, typename=decltype(S() != S())>
    static std::true_type __testNE(int);
    template<typename>
    static std::false_type __testNE(...);
};

/**
 * Helper template that returns if a type has equality and inequality operators.
 *
 * Use as _C2Comparable<typename S>::value.
 */
template<typename S>
struct C2_HIDE _C2Comparable
    : public std::integral_constant<bool, decltype(_C2Comparable_impl::__testEQ<S>(0))::value
                        || decltype(_C2Comparable_impl::__testNE<S>(0))::value> {
};

///  Helper class that checks if a type has a baseIndex constant.
struct C2_HIDE _C2BaseIndexHelper_impl
{
    template<typename S, int=S::baseIndex>
    static std::true_type __testBaseIndex(int);
    template<typename>
    static std::false_type __testBaseIndex(...);
};

/// Helper template that verifies a type's baseIndex and creates it if the type does not have one.
template<typename S, int BaseIndex,
        bool HasBase=decltype(_C2BaseIndexHelper_impl::__testBaseIndex<S>(0))::value>
struct C2_HIDE C2BaseIndexOverride {
    // TODO: what if we allow structs without baseIndex?
    static_assert(BaseIndex == S::baseIndex, "baseIndex differs from structure");
};

/// Specialization for types without a baseIndex.
template<typename S, int BaseIndex>
struct C2_HIDE C2BaseIndexOverride<S, BaseIndex, false> {
public:
    enum : uint32_t {
        baseIndex = BaseIndex, ///< baseIndex override.
    };
};

/// Helper template that adds a baseIndex to a type if it does not have one.
template<typename S, int BaseIndex>
struct C2_HIDE C2AddBaseIndex : public S, public C2BaseIndexOverride<S, BaseIndex> {};

/**
 * \brief Helper class to check struct requirements for parameters.
 *
 * Features:
 *  - verify default constructor, no virtual methods, and no equality operators.
 *  - expose typeIndex, and non-flex flexSize.
 */
template<typename S, int BaseIndex, unsigned TypeIndex>
struct C2_HIDE C2StructCheck {
    static_assert(
            std::is_default_constructible<S>::value, "C2 structure must have default constructor");
    static_assert(!std::is_polymorphic<S>::value, "C2 structure must not have virtual methods");
    static_assert(!_C2Comparable<S>::value, "C2 structure must not have operator== or !=");

public:
    enum : uint32_t {
        typeIndex = BaseIndex | TypeIndex
    };

protected:
    enum : uint32_t {
        flexSize = 0, // TODO: is this still needed? this may be confusing.
    };
};

/// Helper class that checks if a type has an integer flexSize member.
struct C2_HIDE _C2Flexible_impl {
    /// specialization for types that have a flexSize member
    template<typename S, unsigned=S::flexSize>
    static std::true_type __testFlexSize(int);
    template<typename>
    static std::false_type __testFlexSize(...);
};

/// Helper template that returns if a type has an integer flexSize member.
template<typename S>
struct C2_HIDE _C2Flexible
    : public std::integral_constant<bool, decltype(_C2Flexible_impl::__testFlexSize<S>(0))::value> {
};

/// Macro to test if a type is flexible (has a flexSize member).
#define IF_FLEXIBLE(S) ENABLE_IF(_C2Flexible<S>::value)
/// Shorthand for std::enable_if
#define ENABLE_IF(cond) typename std::enable_if<cond>::type

/// Helper template that exposes the flexible subtype of a struct.
template<typename S, typename E=void>
struct C2_HIDE _C2FlexHelper {
    typedef void flexType;
    enum : uint32_t { flexSize = 0 };
};

/// Specialization for flexible types.
template<typename S>
struct C2_HIDE _C2FlexHelper<S,
        typename std::enable_if<!std::is_void<typename S::flexMemberType>::value>::type> {
    typedef typename _C2FlexHelper<typename S::flexMemberType>::flexType flexType;
    enum : uint32_t { flexSize = _C2FlexHelper<typename S::flexMemberType>::flexSize };
};

/// Specialization for flex arrays.
template<typename S>
struct C2_HIDE _C2FlexHelper<S[],
        typename std::enable_if<std::is_void<typename _C2FlexHelper<S>::flexType>::value>::type> {
    typedef S flexType;
    enum : uint32_t { flexSize = sizeof(S) };
};

/**
 * \brief Helper class to check flexible struct requirements and add common operations.
 *
 * Features:
 *  - expose baseIndex and fieldList (this is normally inherited from the struct, but flexible
 *    structs cannot be base classes and thus inherited from)
 *  - disable copy assignment and construction (TODO: this is already done in the FLEX macro for the
 *    flexible struct, so may not be needed here)
 */
template<typename S, int BaseIndex, unsigned TypeIndex>
struct C2_HIDE C2FlexStructCheck : public C2StructCheck<S, BaseIndex, TypeIndex> {
public:
    enum : uint32_t {
        /// \hideinitializer
        baseIndex = BaseIndex | C2Param::BaseIndex::_kFlexibleFlag, ///< flexible struct base-index
    };

    const static std::initializer_list<const C2FieldDescriptor> fieldList; // TODO assign here

    // default constructor needed because of the disabled copy constructor
    inline C2FlexStructCheck() = default;

protected:
    // cannot copy flexible params
    C2FlexStructCheck(const C2FlexStructCheck<S, BaseIndex, TypeIndex> &) = delete;
    C2FlexStructCheck& operator= (const C2FlexStructCheck<S, BaseIndex, TypeIndex> &) = delete;

    // constants used for helper methods
    enum : uint32_t {
        /// \hideinitializer
        flexSize = _C2FlexHelper<S>::flexSize, ///< size of flexible type
        /// \hideinitializer
        maxSize = (uint32_t)std::min((size_t)UINT32_MAX, SIZE_MAX), // TODO: is this always u32 max?
        /// \hideinitializer
        baseSize = sizeof(S) + sizeof(C2Param), ///< size of the base param
    };

    /// returns the allocated size of this param with flexCount, or 0 if it would overflow.
    inline static size_t calcSize(size_t flexCount, size_t size = baseSize) {
        if (flexCount <= (maxSize - size) / S::flexSize) {
            return size + S::flexSize * flexCount;
        }
        return 0;
    }

    /// dynamic new operator usable for params of type S
    inline void* operator new(size_t size, size_t flexCount) noexcept {
        // TODO: assert(size == baseSize);
        size = calcSize(flexCount, size);
        if (size > 0) {
            return ::operator new(size);
        }
        return nullptr;
    }
};

// TODO: this probably does not work.
/// Expose fieldList from subClass;
template<typename S, int BaseIndex, unsigned TypeIndex>
const std::initializer_list<const C2FieldDescriptor> C2FlexStructCheck<S, BaseIndex, TypeIndex>::fieldList = S::fieldList;

/// Define From() cast operators for params.
#define DEFINE_CAST_OPERATORS(_type) \
    inline static _type* From(C2Param *other) { \
        return (_type*)C2Param::ifSuitable( \
                other, sizeof(_type),_type::typeIndex, _type::flexSize, \
                (_type::typeIndex & T::Index::kDirUndefined) != T::Index::kDirUndefined); \
    } \
    inline static const _type* From(const C2Param *other) { \
        return const_cast<const _type*>(From(const_cast<C2Param *>(other))); \
    } \
    inline static _type* From(std::nullptr_t) { return nullptr; } \

/**
 * Define flexible allocators (alloc_shared or alloc_unique) for flexible params.
 *  - P::alloc_xyz(flexCount, args...): allocate for given flex-count.
 *  - P::alloc_xyz(args..., T[]): allocate for size of (and with) init array.
 *  - P::alloc_xyz(T[]): allocate for size of (and with) init array with no other args.
 *  - P::alloc_xyz(args..., std::initializer_list<T>): allocate for size of (and with) initializer
 *    list.
 */
#define DEFINE_FLEXIBLE_ALLOC(_type, S, ptr) \
    template<typename ...Args> \
    inline static std::ptr##_ptr<_type> alloc_##ptr(size_t flexCount, const Args(&... args)) { \
        return std::ptr##_ptr<_type>(new(flexCount) _type(flexCount, args...)); \
    } \
    /* NOTE: unfortunately this is not supported by clang yet */ \
    template<typename ...Args, typename U=typename S::flexType, unsigned N> \
    inline static std::ptr##_ptr<_type> alloc_##ptr(const Args(&... args), const U(&init)[N]) { \
        return std::ptr##_ptr<_type>(new(N) _type(N, args..., init)); \
    } \
    /* so for now, specialize for no args */ \
    template<typename U=typename S::flexType, unsigned N> \
    inline static std::ptr##_ptr<_type> alloc_##ptr(const U(&init)[N]) { \
        return std::ptr##_ptr<_type>(new(N) _type(N, init)); \
    } \
    template<typename ...Args, typename U=typename S::flexType> \
    inline static std::ptr##_ptr<_type> alloc_##ptr( \
            const Args(&... args), const std::initializer_list<U> &init) { \
        return std::ptr##_ptr<_type>(new(init.size()) _type(init.size(), args..., init)); \
    } \

/**
 * Define flexible methods alloc_shared, alloc_unique and flexCount.
 */
#define DEFINE_FLEXIBLE_METHODS(_type, S) \
    DEFINE_FLEXIBLE_ALLOC(_type, S, shared) \
    DEFINE_FLEXIBLE_ALLOC(_type, S, unique) \
    inline size_t flexCount() const { \
        static_assert(sizeof(_type) == _type::baseSize, "incorrect baseSize"); \
        size_t sz = this->size(); \
        if (sz >= sizeof(_type)) { \
            return (sz - sizeof(_type)) / _type::flexSize; \
        } \
        return 0; \
    } \

/// Mark flexible member variable and make structure flexible.
#define FLEX(cls, m) \
    C2_DO_NOT_COPY(cls) \
private: \
    C2PARAM_MAKE_FRIENDS \
    /* default constructor with flexCount */ \
    inline cls(size_t) : cls() {} \
    /** \if 0 */ \
    template<typename, typename> friend struct _C2FlexHelper; \
    typedef decltype(m) flexMemberType; \
public: \
    /* constexpr static flexMemberType cls::* flexMember = &cls::m; */ \
    typedef typename _C2FlexHelper<flexMemberType>::flexType flexType; \
    static_assert(\
            !std::is_void<flexType>::value, \
            "member is not flexible, or a flexible array of a flexible type"); \
    enum : uint32_t { flexSize = _C2FlexHelper<flexMemberType>::flexSize }; \
    /** \endif */ \

/// @}

/**
 * Global-parameter template.
 *
 * Base template to define a global setting/tuning or info based on a structure and
 * an optional BaseIndex. Global parameters are not tied to a port (input or output).
 *
 * Parameters wrap structures by prepending a (parameter) header. The fields of the wrapped
 * structure can be accessed directly, and constructors and potential public methods are also
 * wrapped.
 *
 * \tparam T param type C2Setting, C2Tuning or C2Info
 * \tparam S wrapped structure
 * \tparam BaseIndex optional base-index override. Must be specified for common/reused structures.
 */
template<typename T, typename S, int BaseIndex=S::baseIndex, class Flex=void>
struct C2_HIDE C2GlobalParam : public T, public S, public C2BaseIndexOverride<S, BaseIndex>,
        public C2StructCheck<S, BaseIndex, T::indexFlags | T::Type::kDirGlobal> {
private:
    typedef C2GlobalParam<T, S, BaseIndex> _type;

public:
    /// Wrapper around base structure's constructor.
    template<typename ...Args>
    inline C2GlobalParam(const Args(&... args)) : T(sizeof(_type), _type::typeIndex), S(args...) { }

    DEFINE_CAST_OPERATORS(_type)
};

/**
 * Global-parameter template for flexible structures.
 *
 * Base template to define a global setting/tuning or info based on a flexible structure and
 * an optional BaseIndex. Global parameters are not tied to a port (input or output).
 *
 * \tparam T param type C2Setting, C2Tuning or C2Info
 * \tparam S wrapped flexible structure
 * \tparam BaseIndex optional base-index override. Must be specified for common/reused structures.
 *
 * Parameters wrap structures by prepending a (parameter) header. The fields and methods of flexible
 * structures can be accessed via the m member variable; however, the constructors of the structure
 * are wrapped directly. (This is because flexible types cannot be subclassed.)
 */
template<typename T, typename S, int BaseIndex>
struct C2_HIDE C2GlobalParam<T, S, BaseIndex, IF_FLEXIBLE(S)>
    : public T, public C2FlexStructCheck<S, BaseIndex, T::indexFlags | T::Type::kDirGlobal> {
private:
    typedef C2GlobalParam<T, S, BaseIndex> _type;

    /// Wrapper around base structure's constructor.
    template<typename ...Args>
    inline C2GlobalParam(size_t flexCount, const Args(&... args))
        : T(_type::calcSize(flexCount), _type::typeIndex), m(flexCount, args...) { }

public:
    S m; ///< wrapped flexible structure

    DEFINE_FLEXIBLE_METHODS(_type, S)
    DEFINE_CAST_OPERATORS(_type)
};

/**
 * Port-parameter template.
 *
 * Base template to define a port setting/tuning or info based on a structure and
 * an optional BaseIndex. Port parameters are tied to a port (input or output), but not to a
 * specific stream.
 *
 * \tparam T param type C2Setting, C2Tuning or C2Info
 * \tparam S wrapped structure
 * \tparam BaseIndex optional base-index override. Must be specified for common/reused structures.
 *
 * Parameters wrap structures by prepending a (parameter) header. The fields of the wrapped
 * structure can be accessed directly, and constructors and potential public methods are also
 * wrapped.
 *
 * There are 3 flavors of port parameters: unspecified, input and output. Parameters with
 * unspecified port expose a setPort method, and add an initial port parameter to the constructor.
 */
template<typename T, typename S, int BaseIndex=S::baseIndex, class Flex=void>
struct C2_HIDE C2PortParam : public T, public S, public C2BaseIndexOverride<S, BaseIndex>,
        private C2StructCheck<S, BaseIndex, T::indexFlags | T::Index::kDirUndefined> {
private:
    typedef C2PortParam<T, S, BaseIndex> _type;

public:
    /// Default constructor.
    inline C2PortParam() : T(sizeof(_type), _type::typeIndex) { }
    template<typename ...Args>
    /// Wrapper around base structure's constructor while specifying port/direction.
    inline C2PortParam(bool _output, const Args(&... args))
        : T(sizeof(_type), _output ? output::typeIndex : input::typeIndex), S(args...) { }
    /// Set port/direction.
    inline void setPort(bool output) { C2Param::setPort(output); }

    DEFINE_CAST_OPERATORS(_type)

    /// Specialization for an input port parameter.
    struct input : public T, public S, public C2BaseIndexOverride<S, BaseIndex>,
            public C2StructCheck<S, BaseIndex, T::indexFlags | T::Index::kDirInput> {
        /// Wrapper around base structure's constructor.
        template<typename ...Args>
        inline input(const Args(&... args)) : T(sizeof(_type), input::typeIndex), S(args...) { }

        DEFINE_CAST_OPERATORS(input)

    };

    /// Specialization for an output port parameter.
    struct output : public T, public S, public C2BaseIndexOverride<S, BaseIndex>,
            public C2StructCheck<S, BaseIndex, T::indexFlags | T::Index::kDirOutput> {
        /// Wrapper around base structure's constructor.
        template<typename ...Args>
        inline output(const Args(&... args)) : T(sizeof(_type), output::typeIndex), S(args...) { }

        DEFINE_CAST_OPERATORS(output)
    };
};

/**
 * Port-parameter template for flexible structures.
 *
 * Base template to define a port setting/tuning or info based on a flexible structure and
 * an optional BaseIndex. Port parameters are tied to a port (input or output), but not to a
 * specific stream.
 *
 * \tparam T param type C2Setting, C2Tuning or C2Info
 * \tparam S wrapped flexible structure
 * \tparam BaseIndex optional base-index override. Must be specified for common/reused structures.
 *
 * Parameters wrap structures by prepending a (parameter) header. The fields and methods of flexible
 * structures can be accessed via the m member variable; however, the constructors of the structure
 * are wrapped directly. (This is because flexible types cannot be subclassed.)
 *
 * There are 3 flavors of port parameters: unspecified, input and output. Parameters with
 * unspecified port expose a setPort method, and add an initial port parameter to the constructor.
 */
template<typename T, typename S, int BaseIndex>
struct C2_HIDE C2PortParam<T, S, BaseIndex, IF_FLEXIBLE(S)>
    : public T, public C2FlexStructCheck<S, BaseIndex, T::indexFlags | T::Type::kDirUndefined> {
private:
    typedef C2PortParam<T, S, BaseIndex> _type;

    /// Default constructor for basic allocation: new(flexCount) P.
    inline C2PortParam(size_t flexCount) : T(_type::calcSize(flexCount), _type::typeIndex) { }
    template<typename ...Args>
    /// Wrapper around base structure's constructor while also specifying port/direction.
    inline C2PortParam(size_t flexCount, bool _output, const Args(&... args))
        : T(_type::calcSize(flexCount), _output ? output::typeIndex : input::typeIndex),
          m(flexCount, args...) { }

public:
    /// Set port/direction.
    inline void setPort(bool output) { C2Param::setPort(output); }

    S m; ///< wrapped flexible structure

    DEFINE_FLEXIBLE_METHODS(_type, S)
    DEFINE_CAST_OPERATORS(_type)

    /// Specialization for an input port parameter.
    struct input : public T, public C2BaseIndexOverride<S, BaseIndex>,
            public C2FlexStructCheck<S, BaseIndex, T::indexFlags | T::Index::kDirInput> {
    private:
        /// Wrapper around base structure's constructor while also specifying port/direction.
        template<typename ...Args>
        inline input(size_t flexCount, const Args(&... args))
            : T(_type::calcSize(flexCount), input::typeIndex), m(flexCount, args...) { }

    public:
        S m; ///< wrapped flexible structure

        DEFINE_FLEXIBLE_METHODS(input, S)
        DEFINE_CAST_OPERATORS(input)
    };

    /// Specialization for an output port parameter.
    struct output : public T, public C2BaseIndexOverride<S, BaseIndex>,
            public C2FlexStructCheck<S, BaseIndex, T::indexFlags | T::Index::kDirOutput> {
    private:
        /// Wrapper around base structure's constructor while also specifying port/direction.
        template<typename ...Args>
        inline output(size_t flexCount, const Args(&... args))
            : T(_type::calcSize(flexCount), output::typeIndex), m(flexCount, args...) { }

    public:
        S m; ///< wrapped flexible structure

        DEFINE_FLEXIBLE_METHODS(output, S)
        DEFINE_CAST_OPERATORS(output)
    };
};

/**
 * Stream-parameter template.
 *
 * Base template to define a stream setting/tuning or info based on a structure and
 * an optional BaseIndex. Stream parameters are tied to a specific stream on a port (input or
 * output).
 *
 * \tparam T param type C2Setting, C2Tuning or C2Info
 * \tparam S wrapped structure
 * \tparam BaseIndex optional base-index override. Must be specified for common/reused structures.
 *
 * Parameters wrap structures by prepending a (parameter) header. The fields of the wrapped
 * structure can be accessed directly, and constructors and potential public methods are also
 * wrapped.
 *
 * There are 3 flavors of stream parameters: unspecified port, input and output. All of these expose
 * a setStream method and an extra initial streamID parameter for the constructor. Moreover,
 * parameters with unspecified port expose a setPort method, and add an additional initial port
 * parameter to the constructor.
 */
template<typename T, typename S, int BaseIndex=S::baseIndex, class Flex=void>
struct C2_HIDE C2StreamParam : public T, public S, public C2BaseIndexOverride<S, BaseIndex>,
        private C2StructCheck<S, BaseIndex,
                T::indexFlags | T::Index::kStreamFlag | T::Index::kDirUndefined> {
private:
    typedef C2StreamParam<T, S, BaseIndex> _type;

public:
    /// Default constructor. Port/direction and stream-ID is undefined.
    inline C2StreamParam() : T(sizeof(_type), _type::typeIndex) { }
    /// Wrapper around base structure's constructor while also specifying port/direction and
    /// stream-ID.
    template<typename ...Args>
    inline C2StreamParam(bool _output, unsigned stream, const Args(&... args))
        : T(sizeof(_type), _output ? output::typeIndex : input::typeIndex, stream),
          S(args...) { }
    /// Set port/direction.
    inline void setPort(bool output) { C2Param::setPort(output); }
    /// Set stream-id. \retval true if the stream-id was successfully set.
    inline bool setStream(unsigned stream) { return C2Param::setStream(stream); }

    DEFINE_CAST_OPERATORS(_type)

    /// Specialization for an input stream parameter.
    struct input : public T, public S, public C2BaseIndexOverride<S, BaseIndex>,
            public C2StructCheck<S, BaseIndex,
                    T::indexFlags | T::Index::kStreamFlag | T::Type::kDirInput> {
        /// Default constructor. Stream-ID is undefined.
        inline input() : T(sizeof(_type), input::typeIndex) { }
        /// Wrapper around base structure's constructor while also specifying stream-ID.
        template<typename ...Args>
        inline input(unsigned stream, const Args(&... args))
            : T(sizeof(_type), input::typeIndex, stream), S(args...) { }
        /// Set stream-id. \retval true if the stream-id was successfully set.
        inline bool setStream(unsigned stream) { return C2Param::setStream(stream); }

        DEFINE_CAST_OPERATORS(input)
    };

    /// Specialization for an output stream parameter.
    struct output : public T, public S, public C2BaseIndexOverride<S, BaseIndex>,
            public C2StructCheck<S, BaseIndex,
                    T::indexFlags | T::Index::kStreamFlag | T::Type::kDirOutput> {
        /// Default constructor. Stream-ID is undefined.
        inline output() : T(sizeof(_type), output::typeIndex) { }
        /// Wrapper around base structure's constructor while also specifying stream-ID.
        template<typename ...Args>
        inline output(unsigned stream, const Args(&... args))
            : T(sizeof(_type), output::typeIndex, stream), S(args...) { }
        /// Set stream-id. \retval true if the stream-id was successfully set.
        inline bool setStream(unsigned stream) { return C2Param::setStream(stream); }

        DEFINE_CAST_OPERATORS(output)
    };
};

/**
 * Stream-parameter template for flexible structures.
 *
 * Base template to define a stream setting/tuning or info based on a flexible structure and
 * an optional BaseIndex. Stream parameters are tied to a specific stream on a port (input or
 * output).
 *
 * \tparam T param type C2Setting, C2Tuning or C2Info
 * \tparam S wrapped flexible structure
 * \tparam BaseIndex optional base-index override. Must be specified for common/reused structures.
 *
 * Parameters wrap structures by prepending a (parameter) header. The fields and methods of flexible
 * structures can be accessed via the m member variable; however, the constructors of the structure
 * are wrapped directly. (This is because flexible types cannot be subclassed.)
 *
 * There are 3 flavors of stream parameters: unspecified port, input and output. All of these expose
 * a setStream method and an extra initial streamID parameter for the constructor. Moreover,
 * parameters with unspecified port expose a setPort method, and add an additional initial port
 * parameter to the constructor.
 */
template<typename T, typename S, int BaseIndex>
struct C2_HIDE C2StreamParam<T, S, BaseIndex, IF_FLEXIBLE(S)>
    : public T, public C2BaseIndexOverride<S, BaseIndex>,
      private C2FlexStructCheck<S, BaseIndex,
              T::indexFlags | T::Index::kStreamFlag | T::Index::kDirUndefined> {
private:
    typedef C2StreamParam<T, S> _type;
    /// Default constructor. Port/direction and stream-ID is undefined.
    inline C2StreamParam(size_t flexCount) : T(_type::calcSize(flexCount), _type::typeIndex, 0u) { }
    /// Wrapper around base structure's constructor while also specifying port/direction and
    /// stream-ID.
    template<typename ...Args>
    inline C2StreamParam(size_t flexCount, bool _output, unsigned stream, const Args(&... args))
        : T(_type::calcSize(flexCount), _output ? output::typeIndex : input::typeIndex, stream),
          m(flexCount, args...) { }

public:
    S m; ///< wrapped flexible structure

    /// Set port/direction.
    inline void setPort(bool output) { C2Param::setPort(output); }
    /// Set stream-id. \retval true if the stream-id was successfully set.
    inline bool setStream(unsigned stream) { return C2Param::setStream(stream); }

    DEFINE_FLEXIBLE_METHODS(_type, S)
    DEFINE_CAST_OPERATORS(_type)

    /// Specialization for an input stream parameter.
    struct input : public T, public C2BaseIndexOverride<S, BaseIndex>,
            public C2FlexStructCheck<S, BaseIndex,
                    T::indexFlags | T::Index::kStreamFlag | T::Type::kDirInput> {
    private:
        /// Default constructor. Stream-ID is undefined.
        inline input(size_t flexCount) : T(_type::calcSize(flexCount), input::typeIndex) { }
        /// Wrapper around base structure's constructor while also specifying stream-ID.
        template<typename ...Args>
        inline input(size_t flexCount, unsigned stream, const Args(&... args))
            : T(_type::calcSize(flexCount), input::typeIndex, stream), m(flexCount, args...) { }

    public:
        S m; ///< wrapped flexible structure

        /// Set stream-id. \retval true if the stream-id was successfully set.
        inline bool setStream(unsigned stream) { return C2Param::setStream(stream); }

        DEFINE_FLEXIBLE_METHODS(input, S)
        DEFINE_CAST_OPERATORS(input)
    };

    /// Specialization for an output stream parameter.
    struct output : public T, public C2BaseIndexOverride<S, BaseIndex>,
            public C2FlexStructCheck<S, BaseIndex,
                    T::indexFlags | T::Index::kStreamFlag | T::Type::kDirOutput> {
    private:
        /// Default constructor. Stream-ID is undefined.
        inline output(size_t flexCount) : T(_type::calcSize(flexCount), output::typeIndex) { }
        /// Wrapper around base structure's constructor while also specifying stream-ID.
        template<typename ...Args>
        inline output(size_t flexCount, unsigned stream, const Args(&... args))
            : T(_type::calcSize(flexCount), output::typeIndex, stream), m(flexCount, args...) { }

    public:
        S m; ///< wrapped flexible structure

        /// Set stream-id. \retval true if the stream-id was successfully set.
        inline bool setStream(unsigned stream) { return C2Param::setStream(stream); }

        DEFINE_FLEXIBLE_METHODS(output, S)
        DEFINE_CAST_OPERATORS(output)
    };
};

/* ======================== SIMPLE VALUE PARAMETERS ======================== */

/**
 * \ingroup internal
 * A structure template encapsulating a single element with default constructors and no base-index.
 */
template<typename T>
struct C2SimpleValueStruct {
    T mValue; ///< simple value of the structure
    // Default constructor.
    inline C2SimpleValueStruct() = default;
    // Constructor with an initial value.
    inline C2SimpleValueStruct(T value) : mValue(value) {}
    DEFINE_C2STRUCT_NO_BASE(SimpleValue)
};

// TODO: move this and next to some generic place
/**
 * Interface to a block of (mapped) memory containing an array of some type (T).
 */
template<typename T>
struct C2MemoryBlock {
    /// \returns the number of elements in this block.
    virtual size_t size() const = 0;
    /// \returns a const pointer to the start of this block. Care must be taken to not read outside
    /// the block.
    virtual const T *data() const = 0; // TODO: should this be friend access only in some C2Memory module?
    /// \returns a pointer to the start of this block. Care must be taken to not read or write
    /// outside the block.
    inline T *data() { return const_cast<T*>(data()); }
protected:
    // TODO: for now it should never be deleted as C2MemoryBlock
    virtual ~C2MemoryBlock() = default;
};

/**
 * Interface to a block of memory containing a constant (constexpr) array of some type (T).
 */
template<typename T>
struct C2ConstMemoryBlock : public C2MemoryBlock<T> {
    virtual const T * data() const { return mData; }
    virtual size_t size() const { return mSize; }

    /// Constructor.
    template<unsigned N>
    inline constexpr C2ConstMemoryBlock(const T(&init)[N]) : mData(init), mSize(N) {}

private:
    const T *mData;
    const size_t mSize;
};

/// \addtogroup internal
/// @{

/// Helper class to initialize flexible arrays with various initalizers.
struct _C2ValueArrayHelper {
    // char[]-s are used as null terminated strings, so the last element is never inited.

    /// Initialize a flexible array using a constexpr memory block.
    template<typename T>
    static void init(T(&array)[], size_t arrayLen, const C2MemoryBlock<T> &block) {
        // reserve last element for terminal 0 for strings
        if (arrayLen && std::is_same<T, char>::value) {
            --arrayLen;
        }
        if (block.data()) {
            memcpy(array, block.data(), std::min(arrayLen, block.size()) * sizeof(T));
        }
    }

    /// Initialize a flexible array using an initializer list.
    template<typename T>
    static void init(T(&array)[], size_t arrayLen, const std::initializer_list<T> &init) {
        size_t ix = 0;
        // reserve last element for terminal 0 for strings
        if (arrayLen && std::is_same<T, char>::value) {
            --arrayLen;
        }
        for (const T &item : init) {
            if (ix == arrayLen) {
                break;
            }
            array[ix++] = item;
        }
    }

    /// Initialize a flexible array using another flexible array.
    template<typename T, unsigned N>
    static void init(T(&array)[], size_t arrayLen, const T(&str)[N]) {
        // reserve last element for terminal 0 for strings
        if (arrayLen && std::is_same<T, char>::value) {
            --arrayLen;
        }
        if (arrayLen) {
            strncpy(array, str, std::min(arrayLen, (size_t)N));
        }
    }
};

/**
 * Specialization for a flexible blob and string arrays. A structure template encapsulating a single
 * flexible array member with default flexible constructors and no base-index. This type cannot be
 * constructed on its own as it's size is 0.
 *
 * \internal This is different from C2SimpleArrayStruct<T[]> simply because its member has the name
 * as mValue to reflect this is a single value.
 */
template<typename T>
struct C2SimpleValueStruct<T[]> {
    static_assert(std::is_same<T, char>::value || std::is_same<T, uint8_t>::value,
                  "C2SimpleValueStruct<T[]> is only for BLOB or STRING");
    T mValue[];

    inline C2SimpleValueStruct() = default;
    DEFINE_C2STRUCT_NO_BASE(SimpleValue)
    FLEX(C2SimpleValueStruct, mValue)

private:
    inline C2SimpleValueStruct(size_t flexCount, const C2MemoryBlock<T> &block) {
        _C2ValueArrayHelper::init(mValue, flexCount, block);
    }

    inline C2SimpleValueStruct(size_t flexCount, const std::initializer_list<T> &init) {
        _C2ValueArrayHelper::init(mValue, flexCount, init);
    }

    template<unsigned N>
    inline C2SimpleValueStruct(size_t flexCount, const T(&init)[N]) {
        _C2ValueArrayHelper::init(mValue, flexCount, init);
    }
};

/// @}

/**
 * A structure template encapsulating a single flexible array element of a specific type (T) with
 * default constructors and no base-index. This type cannot be constructed on its own as it's size
 * is 0. Instead, it is meant to be used as a parameter, e.g.
 *
 *   typedef C2StreamParam<C2Info, C2SimpleArrayStruct<C2MyFancyStruct>,
 *           kParamIndexMyFancyArrayStreamParam> C2MyFancyArrayStreamInfo;
 */
template<typename T>
struct C2SimpleArrayStruct {
    static_assert(!std::is_same<T, char>::value && !std::is_same<T, uint8_t>::value,
                  "use C2SimpleValueStruct<T[]> is for BLOB or STRING");

    T mValues[]; ///< array member
    /// Default constructor
    inline C2SimpleArrayStruct() = default;
    DEFINE_C2STRUCT_NO_BASE(SimpleArray)
    FLEX(C2SimpleArrayStruct, mValues)

private:
    /// Construct from a C2MemoryBlock.
    /// Used only by the flexible parameter allocators (alloc_unique & alloc_shared).
    inline C2SimpleArrayStruct(size_t flexCount, const C2MemoryBlock<T> &block) {
        _C2ValueArrayHelper::init(mValues, flexCount, block);
    }

    /// Construct from an initializer list.
    /// Used only by the flexible parameter allocators (alloc_unique & alloc_shared).
    inline C2SimpleArrayStruct(size_t flexCount, const std::initializer_list<T> &init) {
        _C2ValueArrayHelper::init(mValues, flexCount, init);
    }

    /// Construct from another flexible array.
    /// Used only by the flexible parameter allocators (alloc_unique & alloc_shared).
    template<unsigned N>
    inline C2SimpleArrayStruct(size_t flexCount, const T(&init)[N]) {
        _C2ValueArrayHelper::init(mValues, flexCount, init);
    }
};

/**
 * \addtogroup simplevalue Simple value and array structures.
 * @{
 *
 * Simple value structures.
 *
 * Structures containing a single simple value. These can be reused to easily define simple
 * parameters of various types:
 *
 *   typedef C2PortParam<C2Tuning, C2Int32Value, kParamIndexMyIntegerPortParam>
 *           C2MyIntegerPortParamTuning;
 *
 * They contain a single member (mValue or mValues) that is described as "value" or "values".
 */
/// A 32-bit signed integer parameter in mValue, described as "value"
typedef C2SimpleValueStruct<int32_t> C2Int32Value;
/// A 32-bit signed integer array parameter in mValues, described as "values"
typedef C2SimpleArrayStruct<int32_t> C2Int32Array;
/// A 32-bit unsigned integer parameter in mValue, described as "value"
typedef C2SimpleValueStruct<uint32_t> C2Uint32Value;
/// A 32-bit unsigned integer array parameter in mValues, described as "values"
typedef C2SimpleArrayStruct<uint32_t> C2Uint32Array;
/// A 64-bit signed integer parameter in mValue, described as "value"
typedef C2SimpleValueStruct<int64_t> C2Int64Value;
/// A 64-bit signed integer array parameter in mValues, described as "values"
typedef C2SimpleArrayStruct<int64_t> C2Int64Array;
/// A 64-bit unsigned integer parameter in mValue, described as "value"
typedef C2SimpleValueStruct<uint64_t> C2Uint64Value;
/// A 64-bit unsigned integer array parameter in mValues, described as "values"
typedef C2SimpleArrayStruct<uint64_t> C2Uint64Array;
/// A float parameter in mValue, described as "value"
typedef C2SimpleValueStruct<float> C2FloatValue;
/// A float array parameter in mValues, described as "values"
typedef C2SimpleArrayStruct<float> C2FloatArray;
/// A blob flexible parameter in mValue, described as "value"
typedef C2SimpleValueStruct<uint8_t[]> C2BlobValue;
/// A string flexible parameter in mValue, described as "value"
typedef C2SimpleValueStruct<char[]> C2StringValue;

#if 1
template<typename T>
const std::initializer_list<const C2FieldDescriptor> C2SimpleValueStruct<T>::fieldList = { C2FIELD(mValue, "value") };
template<typename T>
const std::initializer_list<const C2FieldDescriptor> C2SimpleValueStruct<T[]>::fieldList = { C2FIELD(mValue, "value") };
template<typename T>
const std::initializer_list<const C2FieldDescriptor> C2SimpleArrayStruct<T>::fieldList = { C2FIELD(mValues, "values") };
#else
// This seem to be able to be handled by the template above
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleValueStruct<int32_t>, { C2FIELD(mValue, "value") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleValueStruct<uint32_t>, { C2FIELD(mValue, "value") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleValueStruct<int64_t>, { C2FIELD(mValue, "value") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleValueStruct<uint64_t>, { C2FIELD(mValue, "value") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleValueStruct<float>, { C2FIELD(mValue, "value") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleValueStruct<uint8_t[]>, { C2FIELD(mValue, "value") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleValueStruct<char[]>, { C2FIELD(mValue, "value") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleArrayStruct<int32_t>, { C2FIELD(mValues, "values") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleArrayStruct<uint32_t>, { C2FIELD(mValues, "values") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleArrayStruct<int64_t>, { C2FIELD(mValues, "values") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleArrayStruct<uint64_t>, { C2FIELD(mValues, "values") });
DESCRIBE_TEMPLATED_C2STRUCT(C2SimpleArrayStruct<float>, { C2FIELD(mValues, "values") });
#endif

/// @}

/// @}

}  // namespace android

#endif  // C2PARAM_DEF_H_
