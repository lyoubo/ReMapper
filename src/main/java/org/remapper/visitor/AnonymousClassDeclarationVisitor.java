package org.remapper.visitor;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;

import java.util.LinkedHashSet;
import java.util.Set;

public class AnonymousClassDeclarationVisitor extends ASTVisitor {

    private final Set<AnonymousClassDeclaration> anonymousClassDeclarations = new LinkedHashSet<AnonymousClassDeclaration>();

    public Set<AnonymousClassDeclaration> getAnonymousClassDeclarations() {
        return anonymousClassDeclarations;
    }

    @Override
    public boolean visit(AnonymousClassDeclaration node) {
        anonymousClassDeclarations.add(node);
        return super.visit(node);
    }
}
