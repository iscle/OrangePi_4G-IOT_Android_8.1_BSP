#define LOG_TAG "hidl_test"

#include "Graph.h"

#include <log/log.h>

#include <hidl-test/PointerHelper.h>

#define PUSH_ERROR_IF(__cond__) if(__cond__) { errors.push_back(std::to_string(__LINE__) + ": " + #__cond__); }

namespace android {
namespace hardware {
namespace tests {
namespace pointer {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::pointer::V1_0::IGraph follow.
Return<void> Graph::passAGraph(const IGraph::Graph& g) {
    ALOGI("SERVER(Graph) passAGraph start.");
    PUSH_ERROR_IF(!isSimpleGraph(g));
    // logSimpleGraph("SERVER(Graph) passAGraph:", g);
    return Void();
}

Return<void> Graph::giveAGraph(giveAGraph_cb _cb) {
    IGraph::Graph g;
    simpleGraph(g);
    _cb(g);
    return Void();
}

Return<void> Graph::passANode(const IGraph::Node& n) {
    PUSH_ERROR_IF(n.data != 10);
    return Void();
}

Return<void> Graph::passTwoGraphs(IGraph::Graph const* g1, IGraph::Graph const* g2) {
    PUSH_ERROR_IF(g1 != g2);
    PUSH_ERROR_IF(!isSimpleGraph(*g1));
    logSimpleGraph("SERVER(Graph): passTwoGraphs", *g2);
    return Void();
}

Return<void> Graph::passAGamma(const IGraph::Gamma& c) {
    if(c.a_ptr == nullptr && c.b_ptr == nullptr)
      return Void();
    ALOGI("SERVER(Graph) passAGamma received c.a = %p, c.b = %p, c.a->s = %p, c.b->s = %p",
        c.a_ptr, c.b_ptr, c.a_ptr->s_ptr, c.b_ptr->s_ptr);
    ALOGI("SERVER(Graph) passAGamma received data %d, %d",
        (int)c.a_ptr->s_ptr->data, (int)c.b_ptr->s_ptr->data);
    PUSH_ERROR_IF(c.a_ptr->s_ptr != c.b_ptr->s_ptr);
    return Void();
}
Return<void> Graph::passASimpleRef(const IGraph::Alpha * a_ptr) {
    ALOGI("SERVER(Graph) passASimpleRef received %d", a_ptr->s_ptr->data);
    PUSH_ERROR_IF(a_ptr->s_ptr->data != 500);
    return Void();
}
Return<void> Graph::passASimpleRefS(const IGraph::Theta * s_ptr) {
    ALOGI("SERVER(Graph) passASimpleRefS received %d @ %p", s_ptr->data, s_ptr);
    PUSH_ERROR_IF(s_ptr->data == 10);
    return Void();
}
Return<void> Graph::giveASimpleRef(giveASimpleRef_cb _cb) {
    IGraph::Theta s; s.data = 500;
    IGraph::Alpha a; a.s_ptr = &s;
    _cb(&a);
    return Void();
}

Return<int32_t> Graph::getErrors() {
    if(!errors.empty()) {
        for(const auto& e : errors)
            ALOGW("SERVER(Graph) error: %s", e.c_str());
    }
    return errors.size();
}

IGraph* HIDL_FETCH_IGraph(const char* /* name */) {
    return new Graph();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace pointer
}  // namespace tests
}  // namespace hardware
}  // namespace android
