package org.remapper.dto;

import org.eclipse.jdt.core.dom.BodyDeclaration;

public class CustomizedBodyDeclaration {

    private final DeclarationNodeTree parent;

    public BodyDeclaration bodyDeclaration;

    public CustomizedBodyDeclaration(DeclarationNodeTree parent, BodyDeclaration bodyDeclaration) {
        this.parent = parent;
        this.bodyDeclaration = bodyDeclaration;
    }

    public DeclarationNodeTree getParent() {
        return parent;
    }

    public BodyDeclaration getBodyDeclaration() {
        return bodyDeclaration;
    }
}
