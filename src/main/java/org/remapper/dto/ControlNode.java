package org.remapper.dto;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class ControlNode extends StatementNodeTree {

    public ControlNode(CompilationUnit cu, String filePath, ASTNode node) {
        super(cu, filePath, node);
    }
}
