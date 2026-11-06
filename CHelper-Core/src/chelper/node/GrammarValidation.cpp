#include <chelper/node/GrammarValidation.h>
#include <chelper/util/HashContainer.h>

namespace CHelper::Node {
    void validateGrammar(const NodeJsonElement &grammar) {
        const auto &nodes = grammar.nodes.nodes;
        DenseMap<const NodeBase *, size_t, std::hash<const NodeBase *>> indexes;
        DenseSet<std::string_view> ids;
        indexes.reserve(nodes.size());
        ids.reserve(nodes.size());
        for (size_t i = 0; i < nodes.size(); ++i) {
            const auto &node = nodes[i];
            if (!node.data) throw std::runtime_error("grammar contains a null node");
            indexes.emplace(node.data, i);
            const auto &serializable = *static_cast<const NodeSerializable *>(node.data);
            if (serializable.id && !ids.insert(*serializable.id).second) {
                throw std::runtime_error("duplicate grammar node id: " + std::string(*serializable.id));
            }
            if (node.nodeTypeId == NodeTypeId::AND && static_cast<const NodeAnd *>(node.data)->childNodes.empty()) {
                throw std::runtime_error("AND grammar node must have at least one child");
            }
            if (node.nodeTypeId == NodeTypeId::OR && static_cast<const NodeOr *>(node.data)->childNodes.empty()) {
                throw std::runtime_error("OR grammar node must have at least one child");
            }
        }

        // Least fixed point: a recursive reference alone cannot match empty input.
        std::vector<bool> nullable(nodes.size(), false);
        const auto isNullable = [&](const NodeWithType &node) {
            const auto it = indexes.find(node.data);
            return it != indexes.end() && nullable[it->second];
        };
        // 用连续边表代替每个节点各自分配的小 vector。
        constexpr size_t noEdge = std::numeric_limits<size_t>::max();
        struct Edge {
            size_t node, next;
        };
        std::vector<size_t> parentHeads(nodes.size(), noEdge);
        std::vector<Edge> parents;
        parents.reserve(nodes.size() * 2);
        std::vector<size_t> remaining(nodes.size(), 0);
        std::vector<size_t> ready;
        ready.reserve(nodes.size());
        const auto markNullable = [&](size_t index) {
            if (!nullable[index]) {
                nullable[index] = true;
                ready.push_back(index);
            }
        };
        for (size_t i = 0; i < nodes.size(); ++i) {
            const auto &node = nodes[i];
            const auto addDependencies = [&](const auto &children) {
                remaining[i] = children.size();
                for (const auto &child: children) {
                    if (const auto it = indexes.find(child.data); it != indexes.end()) {
                        parents.push_back({i, parentHeads[it->second]});
                        parentHeads[it->second] = parents.size() - 1;
                    }
                }
            };
            switch (node.nodeTypeId) {
                case NodeTypeId::AND:
                    addDependencies(static_cast<const NodeAnd *>(node.data)->childNodes);
                    break;
                case NodeTypeId::OR:
                    addDependencies(static_cast<const NodeOr *>(node.data)->childNodes);
                    break;
                case NodeTypeId::OPTIONAL:
                    markNullable(i);
                    break;
                case NodeTypeId::STRING:
                    if (static_cast<const NodeString *>(node.data)->allowMissingString) markNullable(i);
                    break;
                case NodeTypeId::NORMAL_ID:
                    if (static_cast<const NodeNormalId *>(node.data)->allowMissingID) markNullable(i);
                    break;
                default:
                    break;
            }
        }
        // Propagate each edge once instead of repeatedly scanning a long chain.
        for (size_t next = 0; next < ready.size(); ++next) {
            for (size_t edge = parentHeads[ready[next]]; edge != noEdge; edge = parents[edge].next) {
                const size_t parent = parents[edge].node;
                if (nodes[parent].nodeTypeId == NodeTypeId::OR || --remaining[parent] == 0) markNullable(parent);
            }
        }

        std::vector<size_t> edgeHeads(nodes.size(), noEdge);
        std::vector<Edge> edges;
        edges.reserve(nodes.size() * 2);
        for (size_t i = 0; i < nodes.size(); ++i) {
            const auto add = [&](const NodeWithType &target) {
                if (!target.data) throw std::runtime_error("grammar has an unbound reference");
                if (const auto it = indexes.find(target.data); it != indexes.end()) {
                    edges.push_back({it->second, edgeHeads[i]});
                    edgeHeads[i] = edges.size() - 1;
                }
            };
            const auto &node = nodes[i];
            if (node.nodeTypeId == NodeTypeId::OR) {
                for (const auto &child: static_cast<const NodeOr *>(node.data)->childNodes) add(child);
            } else if (node.nodeTypeId == NodeTypeId::AND) {
                for (const auto &child: static_cast<const NodeAnd *>(node.data)->childNodes) {
                    add(child);
                    if (!isNullable(child)) break;
                }
            } else if (node.nodeTypeId == NodeTypeId::OPTIONAL) {
                add(static_cast<const NodeOptional *>(node.data)->optionalNode);
            } else if (node.nodeTypeId == NodeTypeId::LIST) {
                const auto &list = *static_cast<const NodeList *>(node.data);
                add(list.nodeLeft);
                if (isNullable(list.nodeLeft)) {
                    add(list.nodeElement);
                    add(list.nodeRight);
                    if (isNullable(list.nodeElement)) add(list.nodeSeparator);
                }
            }
        }
        // Iterative DFS also bounds the validator's own stack usage.
        std::vector<uint8_t> color(nodes.size(), 0);
        std::vector<std::pair<size_t, size_t>> stack;
        stack.reserve(nodes.size());
        for (size_t root = 0; root < nodes.size(); ++root) {
            if (color[root]) continue;
            color[root] = 1;
            stack.emplace_back(root, edgeHeads[root]);
            while (!stack.empty()) {
                auto &[index, next] = stack.back();
                if (next == noEdge) {
                    color[index] = 2;
                    stack.pop_back();
                    continue;
                }
                const auto target = edges[next].node;
                next = edges[next].next;
                if (color[target] == 1) throw std::runtime_error("grammar recursion does not consume input");
                if (color[target] == 0) {
                    color[target] = 1;
                    stack.emplace_back(target, edgeHeads[target]);
                }
            }
        }
    }
}// namespace CHelper::Node
