#ifndef ANDROID_HIDL_TEST_POINTER_HELPER_H
#define ANDROID_HIDL_TEST_POINTER_HELPER_H

#include <android/hardware/tests/pointer/1.0/IGraph.h>

using ::android::hardware::tests::pointer::V1_0::IGraph;

namespace android {

void simpleGraph(IGraph::Graph& g);
bool isSimpleGraph(const IGraph::Graph &g);
void logSimpleGraph(const char *prefix, const IGraph::Graph& g);

} // namespace android
#endif // ANDROID_HIDL_TEST_POINTER_HELPER_H
