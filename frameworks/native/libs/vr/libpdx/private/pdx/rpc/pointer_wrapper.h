#ifndef ANDROID_PDX_RPC_POINTER_WRAPPER_H_
#define ANDROID_PDX_RPC_POINTER_WRAPPER_H_

namespace android {
namespace pdx {
namespace rpc {

// Wrapper class for pointers to any serializable type. This class is used by
// serialization/deserialization to handle pointers to objects that are to be
// serialized or deserialized.
template <typename T>
class PointerWrapper {
 public:
  using BaseType = T;

  PointerWrapper(T* pointer) : pointer_(pointer) {}
  PointerWrapper(const PointerWrapper&) = default;
  PointerWrapper(PointerWrapper&&) = default;
  PointerWrapper& operator=(const PointerWrapper&) = default;
  PointerWrapper& operator=(PointerWrapper&&) = default;

  T& Dereference() { return *pointer_; }
  const T& Dereference() const { return *pointer_; }

 private:
  T* pointer_;
};

template <typename T>
PointerWrapper<T> WrapPointer(T* pointer) {
  return PointerWrapper<T>(pointer);
}

}  // namespace rpc
}  // namespace pdx
}  // namespace android

#endif  // ANDROID_PDX_RPC_POINTER_WRAPPER_H_
