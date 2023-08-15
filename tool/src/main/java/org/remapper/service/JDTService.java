package org.remapper.service;

import org.eclipse.jdt.core.dom.ASTNode;
import org.remapper.dto.ChildNode;
import org.remapper.dto.RootNode;

import java.util.List;

public interface JDTService {

    RootNode parseFileDNT(String filePath, String fileContent);

    List<ChildNode> getDescendants(ASTNode node);
}
