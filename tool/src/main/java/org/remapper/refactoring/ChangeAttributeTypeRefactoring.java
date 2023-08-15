package org.remapper.refactoring;

import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.refactoringminer.api.RefactoringType;
import org.remapper.dto.LocationInfo;

public class ChangeAttributeTypeRefactoring extends Refactoring {

    public ChangeAttributeTypeRefactoring(LocationInfo leftSide, LocationInfo rightSide) {
        super(RefactoringType.CHANGE_ATTRIBUTE_TYPE, leftSide, rightSide);
    }

    public void setDisplayName(FieldDeclaration leftDeclaration, FieldDeclaration rightDeclaration, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("Change Attribute Type\t");
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) leftDeclaration.fragments().get(0);
        sb.append(fragment.getName().getFullyQualifiedName()).append(" : ");
        sb.append(leftDeclaration.getType().toString()).append(" to ");
        VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) rightDeclaration.fragments().get(0);
        sb.append(fragment2.getName().getFullyQualifiedName()).append(" : ");
        sb.append(rightDeclaration.getType().toString()).append(" in class ");
        sb.append(className);
        super.setDisplayName(sb.toString());
    }
}
