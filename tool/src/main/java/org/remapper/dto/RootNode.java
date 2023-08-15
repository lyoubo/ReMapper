package org.remapper.dto;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RootNode extends DeclarationNodeTree {

    private List<DeclarationNodeTree> allNodes;

    public RootNode(CompilationUnit cu, String filePath, ASTNode node) {
        super(cu, filePath, node);
        super.setHeight(0);
        super.setType(EntityType.COMPILATION_UNIT);
        super.setNamespace("");
        super.setName("root");
        super.setRoot(true);
        super.setLeaf(false);
    }

    /**
     * All nodes after pruning
     */
    public List<DeclarationNodeTree> getAllNodes() {
        if (allNodes == null) {
            allNodes = new ArrayList<>();
            depthFirstSearch(allNodes, getChildren());
        }
        return allNodes;
    }

    public List<LeafNode> getLeafNodes() {
        return getAllNodes().stream().filter(node -> node.isLeaf() && !node.isMatched()).
                map(node -> (LeafNode) node).collect(Collectors.toList());
    }

    public List<InternalNode> getInternalNodes() {
        return getAllNodes().stream().filter(node -> !node.isLeaf() && !node.isRoot() && !node.isMatched()).
                map(node -> (InternalNode) node).collect(Collectors.toList());
    }

    public List<DeclarationNodeTree> getUnmatchedNodes() {
        return getAllNodes().stream().filter(node -> !node.isMatched()).collect(Collectors.toList());
    }

    private void depthFirstSearch(List<DeclarationNodeTree> list, List<DeclarationNodeTree> children) {
        for (DeclarationNodeTree child : children) {
            depthFirstSearch(list, child.getChildren());
            list.add(child);
        }
    }
}
