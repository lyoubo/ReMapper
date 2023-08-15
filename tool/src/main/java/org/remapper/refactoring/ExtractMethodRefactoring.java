package org.remapper.refactoring;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.refactoringminer.api.RefactoringType;
import org.remapper.dto.LocationInfo;

import java.util.ArrayList;
import java.util.List;

public class ExtractMethodRefactoring extends Refactoring {

    public ExtractMethodRefactoring(LocationInfo extractedSide, LocationInfo leftSide) {
        super(RefactoringType.EXTRACT_OPERATION, extractedSide, leftSide);
    }

    public void setDisplayName(MethodDeclaration addedDeclaration, MethodDeclaration leftDeclaration, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract Method\t");
        int methodModifiers = addedDeclaration.getModifiers();
        boolean isInterfaceMethod = false;
        if (addedDeclaration.getParent() instanceof TypeDeclaration) {
            TypeDeclaration parent = (TypeDeclaration) addedDeclaration.getParent();
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
        sb.append(addedDeclaration.getName().getFullyQualifiedName()).append("(");
        List<SingleVariableDeclaration> parameters = addedDeclaration.parameters();
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
        if (addedDeclaration.getReturnType2() != null)
            sb.append(" : ").append(addedDeclaration.getReturnType2().toString());
        sb.append(" extracted from ");
        int methodModifiers2 = leftDeclaration.getModifiers();
        if (leftDeclaration.getParent() instanceof TypeDeclaration) {
            TypeDeclaration parent = (TypeDeclaration) leftDeclaration.getParent();
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
        sb.append(leftDeclaration.getName().getFullyQualifiedName()).append("(");
        List<SingleVariableDeclaration> parameters2 = leftDeclaration.parameters();
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
        if (leftDeclaration.getReturnType2() != null)
            sb.append(" : ").append(leftDeclaration.getReturnType2().toString());
        sb.append(" in class ");
        sb.append(className);
        super.setDisplayName(sb.toString());
    }
}
