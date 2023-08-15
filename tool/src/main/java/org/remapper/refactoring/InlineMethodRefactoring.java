package org.remapper.refactoring;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.refactoringminer.api.RefactoringType;
import org.remapper.dto.LocationInfo;

import java.util.ArrayList;
import java.util.List;

public class InlineMethodRefactoring extends Refactoring {

    public InlineMethodRefactoring(LocationInfo inlinedSide, LocationInfo rightSide) {
        super(RefactoringType.INLINE_OPERATION, inlinedSide, rightSide);
    }

    public void setDisplayName(MethodDeclaration deletedDeclaration, MethodDeclaration rightDeclaration, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("Inline Method\t");
        int methodModifiers = deletedDeclaration.getModifiers();
        boolean isInterfaceMethod = false;
        if (deletedDeclaration.getParent() instanceof TypeDeclaration) {
            TypeDeclaration parent = (TypeDeclaration) deletedDeclaration.getParent();
            isInterfaceMethod = parent.isInterface();
        }
        if ((methodModifiers & Modifier.PUBLIC) != 0)
            sb.append("public").append(" ");
        else if ((methodModifiers & Modifier.PROTECTED) != 0)
            sb.append("protected").append(" ");
        else if ((methodModifiers & Modifier.PRIVATE) != 0)
            sb.append("private").append(" ");
        else if (isInterfaceMethod)
            sb.append("public").append(" ");
        else
            sb.append("package").append(" ");
        if ((methodModifiers & Modifier.ABSTRACT) != 0)
            sb.append("abstract").append(" ");
        sb.append(deletedDeclaration.getName().getFullyQualifiedName()).append("(");
        List<SingleVariableDeclaration> parameters = deletedDeclaration.parameters();
        List<String> list = new ArrayList<>();
        for (SingleVariableDeclaration parameter : parameters) {
            if (parameter.isVarargs()) {
                list.add(parameter.getName().getFullyQualifiedName() + " " + parameter.getType().toString() + "...");
            } else {
                list.add(parameter.getName().getFullyQualifiedName() + " " + parameter.getType().toString());
            }
        }
        sb.append(String.join(", ", list));
        sb.append(")");
        if (deletedDeclaration.getReturnType2() != null)
            sb.append(" : ").append(deletedDeclaration.getReturnType2().toString());
        sb.append(" inlined to ");
        int methodModifiers2 = rightDeclaration.getModifiers();
        if (rightDeclaration.getParent() instanceof TypeDeclaration) {
            TypeDeclaration parent = (TypeDeclaration) rightDeclaration.getParent();
            isInterfaceMethod = parent.isInterface();
        }
        if ((methodModifiers2 & Modifier.PUBLIC) != 0)
            sb.append("public").append(" ");
        else if ((methodModifiers2 & Modifier.PROTECTED) != 0)
            sb.append("protected").append(" ");
        else if ((methodModifiers2 & Modifier.PRIVATE) != 0)
            sb.append("private").append(" ");
        else if (isInterfaceMethod)
            sb.append("public").append(" ");
        else
            sb.append("package").append(" ");
        if ((methodModifiers2 & Modifier.ABSTRACT) != 0)
            sb.append("abstract").append(" ");
        sb.append(rightDeclaration.getName().getFullyQualifiedName()).append("(");
        List<SingleVariableDeclaration> parameters2 = rightDeclaration.parameters();
        List<String> list2 = new ArrayList<>();
        for (SingleVariableDeclaration parameter : parameters2) {
            if (parameter.isVarargs()) {
                list2.add(parameter.getName().getFullyQualifiedName() + " " + parameter.getType().toString() + "...");
            } else {
                list2.add(parameter.getName().getFullyQualifiedName() + " " + parameter.getType().toString());
            }
        }
        sb.append(String.join(", ", list2));
        sb.append(")");
        if (rightDeclaration.getReturnType2() != null)
            sb.append(" : ").append(rightDeclaration.getReturnType2().toString());
        sb.append(" in class ");
        sb.append(className);
        super.setDisplayName(sb.toString());
    }
}
