package org.remapper.dto;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.ArrayList;
import java.util.List;

public class InternalNode extends DeclarationNodeTree {

    private List<DeclarationNodeTree> descendants;

    public InternalNode(CompilationUnit cu, String filePath, ASTNode node) {
        super(cu, filePath, node);
        super.setRoot(false);
        super.setLeaf(false);
    }

    public void addDescendants(List<DeclarationNodeTree> nodes) {
        descendants = new ArrayList<>();
        descendants.addAll(nodes);
    }

    public List<DeclarationNodeTree> getDescendants() {
        if (descendants == null) {
            descendants = getChildren();
            return descendants;
        }
        return descendants;
    }
}
