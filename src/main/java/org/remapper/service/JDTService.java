package org.remapper.service;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.remapper.dto.ChildNode;
import org.remapper.dto.MethodNode;
import org.remapper.dto.RootNode;

import java.util.List;

public interface JDTService {

    RootNode parseFileDNT(String filePath, String fileContent);

    MethodNode parseMethodSNT(String filePath, MethodDeclaration methodDeclaration);

    MethodNode parseMethodSNT(String filePath, Initializer initializer);

    List<ChildNode> getDescendants(ASTNode node);
}
