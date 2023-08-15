package org.remapper.refactoring;

import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.refactoringminer.api.RefactoringType;
import org.remapper.dto.LocationInfo;

public class RenameAttributeRefactoring extends Refactoring {

    public RenameAttributeRefactoring(LocationInfo leftSide, LocationInfo rightSide) {
        super(RefactoringType.RENAME_ATTRIBUTE, leftSide, rightSide);
    }

    public void setDisplayName(FieldDeclaration leftDeclaration, FieldDeclaration rightDeclaration, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rename Attribute\t");
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) leftDeclaration.fragments().get(0);
        sb.append(fragment.getName().getFullyQualifiedName()).append(" : ");
        sb.append(leftDeclaration.getType().toString()).append(" to ");
        VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) rightDeclaration.fragments().get(0);
        sb.append(fragment2.getName().getFullyQualifiedName()).append(" : ");
        sb.append(rightDeclaration.getType().toString()).append(" in class ");
        sb.append(className);
        super.setDisplayName(sb.toString());
    }

    public void setDisplayName(EnumConstantDeclaration leftDeclaration, EnumConstantDeclaration rightDeclaration, String leftEnum, String rightEnum, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rename Attribute\t");
        sb.append(leftDeclaration.getName().getFullyQualifiedName()).append(" : ");
        sb.append(leftEnum).append(" to ");
        sb.append(rightDeclaration.getName().getFullyQualifiedName()).append(" : ");
        sb.append(rightEnum).append(" in class ");
        sb.append(className);
        super.setDisplayName(sb.toString());
    }
}
