package org.remapper.dto;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class LeafNode extends DeclarationNodeTree {

    public LeafNode(CompilationUnit cu, String filePath, ASTNode node) {
        super(cu, filePath, node);
        super.setRoot(false);
        super.setLeaf(true);
    }
}
