#ifndef ANDROID_HARDWARE_TESTS_POINTER_V1_0_GRAPH_H
#define ANDROID_HARDWARE_TESTS_POINTER_V1_0_GRAPH_H

#include <android/hardware/tests/pointer/1.0/IGraph.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>
namespace android {
namespace hardware {
namespace tests {
namespace pointer {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::pointer::V1_0::IGraph;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Graph : public IGraph {
    // Methods from ::android::hardware::tests::pointer::V1_0::IGraph follow.
    Return<void> passANode(const IGraph::Node& n)  override;
    Return<void> passAGraph(const IGraph::Graph& g)  override;
    Return<void> passTwoGraphs(::android::hardware::tests::pointer::V1_0::IGraph::Graph const* g1, ::android::hardware::tests::pointer::V1_0::IGraph::Graph const* g2)  override;
    Return<void> giveAGraph(giveAGraph_cb _hidl_cb)  override;
    Return<void> passAGamma(const IGraph::Gamma& c)  override;
    Return<void> passASimpleRef(::android::hardware::tests::pointer::V1_0::IGraph::Alpha const* a)  override;
    Return<void> passASimpleRefS(::android::hardware::tests::pointer::V1_0::IGraph::Theta const* s)  override;
    Return<void> giveASimpleRef(giveASimpleRef_cb _hidl_cb)  override;
    Return<int32_t> getErrors()  override;
private:
    std::vector<std::string> errors;

};

extern "C" IGraph* HIDL_FETCH_IGraph(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace pointer
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_POINTER_V1_0_GRAPH_H
