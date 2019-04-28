#define LOG_TAG "hidl_test"

#include <log/log.h>

#include "PointerHelper.h"

namespace android {

void simpleGraph(IGraph::Graph& g) {
    g.nodes.resize(2);
    g.edges.resize(1);
    g.nodes[0].data = 10;
    g.nodes[1].data = 20;
    g.edges[0].left = &g.nodes[0];
    g.edges[0].right = &g.nodes[1];
}

bool isSimpleGraph(const IGraph::Graph &g) {
    if(g.nodes.size() != 2) return false;
    if(g.edges.size() != 1) return false;
    if(g.nodes[0].data != 10) return false;
    if(g.nodes[1].data != 20) return false;
    if(g.edges[0].left != &g.nodes[0]) return false;
    if(g.edges[0].right != &g.nodes[1]) return false;
    return true;
}

void logSimpleGraph(const char *prefix, const IGraph::Graph& g) {
    ALOGI("%s Graph %p, %d nodes, %d edges", prefix, &g, (int)g.nodes.size(), (int)g.edges.size());
    std::ostringstream os;
    for(size_t i = 0; i < g.nodes.size(); i++)
      os << &g.nodes[i] << " = " << g.nodes[i].data << ", ";
    ALOGI("%s Nodes: [%s]", prefix, os.str().c_str());
    os.str("");
    os.clear();
    for(size_t i = 0; i < g.edges.size(); i++)
      os << g.edges[i].left << " -> " << g.edges[i].right << ", ";
    ALOGI("%s Edges: [%s]", prefix, os.str().c_str());
}
} // namespace android
