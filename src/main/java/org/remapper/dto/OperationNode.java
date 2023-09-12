package org.remapper.dto;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class OperationNode extends StatementNodeTree {

    public OperationNode(CompilationUnit cu, String filePath, ASTNode node) {
        super(cu, filePath, node);
    }
}
