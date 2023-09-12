package org.remapper.dto;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.remapper.service.JDTService;

import java.util.ArrayList;
import java.util.List;

public class LeafNode extends DeclarationNodeTree {

    private List<ChildNode> descendants;

    public LeafNode(CompilationUnit cu, String filePath, ASTNode node) {
        super(cu, filePath, node);
        super.setRoot(false);
        super.setLeaf(true);
    }

    public List<ChildNode> getDescendants(JDTService jdtService) {
        if (descendants == null) {
            descendants = jdtService.getDescendants(getDeclaration());
            return descendants;
        }
        return descendants;
    }

    public List<ChildNode> getDescendantsInBody(JDTService jdtService) {
        MethodDeclaration declaration = (MethodDeclaration) getDeclaration();
        Block body = declaration.getBody();
        return body == null ? new ArrayList<>() : jdtService.getDescendants(body);
    }
}
