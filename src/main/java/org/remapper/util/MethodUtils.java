package org.remapper.util;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MethodUtils {

    public static boolean isGetter(MethodDeclaration methodDeclaration) {
        Block body = methodDeclaration.getBody();
        if (body != null) {
            List<Statement> statements = body.statements();
            List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
            if (statements.size() == 1) {
                Statement statement = statements.get(0);
                if (statement.toString().startsWith("return ")) {
                    ASTNode parent = methodDeclaration.getParent();
                    if (parent instanceof TypeDeclaration) {
                        TypeDeclaration typeDeclaration = (TypeDeclaration) parent;
                        FieldDeclaration[] fields = typeDeclaration.getFields();
                        for (FieldDeclaration fieldDeclaration : fields) {
                            List<VariableDeclarationFragment> fragments = fieldDeclaration.fragments();
                            for (VariableDeclarationFragment fragment : fragments) {
                                if (statement.toString().equals("return " + fragment.getName().getIdentifier() + ";\n") && (parameters.size() == 0)) {
                                    return true;
                                } else if (statement.toString().equals("return " + fragment.getName().getIdentifier() + ".keySet()" + ";\n") && (parameters.size() == 0)) {
                                    return true;
                                } else if (statement.toString().equals("return " + fragment.getName().getIdentifier() + ".values()" + ";\n") && (parameters.size() == 0)) {
                                    return true;
                                }
                            }
                        }
                    }
                    /*String name = methodDeclaration.getName().getIdentifier();
                    Type returnType = methodDeclaration.getReturnType2();
                    if ((name.startsWith("is") || name.startsWith("has")) && (parameters.size() == 0) &&
                            returnType != null && returnType.toString().equals("boolean")) {
                        return true;
                    }
                    if (statement.toString().equals("return null;\n")) {
                        return true;
                    }*/
                }
            }
        }
        return false;
    }

    public static boolean isSetter(MethodDeclaration methodDeclaration) {
        Block body = methodDeclaration.getBody();
        List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
        if (body != null && parameters.size() == 1) {
            List<Statement> statements = body.statements();
            if (statements.size() == 1) {
                Statement statement = statements.get(0);
                ASTNode parent = methodDeclaration.getParent();
                if (parent instanceof TypeDeclaration) {
                    TypeDeclaration typeDeclaration = (TypeDeclaration) parent;
                    FieldDeclaration[] fields = typeDeclaration.getFields();
                    for (FieldDeclaration fieldDeclaration : fields) {
                        List<VariableDeclarationFragment> fragments = fieldDeclaration.fragments();
                        for (VariableDeclarationFragment fragment : fragments) {
                            if (statement.toString().equals(fragment.getName().getIdentifier() + "=" + parameters.get(0).getName().getIdentifier() + ";\n") ||
                                    statement.toString().equals("this." + fragment.getName().getIdentifier() + "=" + parameters.get(0).getName().getIdentifier() + ";\n")) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isNewFunction(ASTNode oldFragment, ASTNode newFragment) {
        if (oldFragment instanceof MethodDeclaration && newFragment instanceof MethodDeclaration) {
            if (((MethodDeclaration) oldFragment).getBody() == null || ((MethodDeclaration) newFragment).getBody() == null)
                return false;
            String[] oldMethodLines = ((MethodDeclaration) oldFragment).getBody().toString().split("\n");
            String[] newMethodLines = ((MethodDeclaration) newFragment).getBody().toString().split("\n");
            int index = 0;
            for (String newMethodLine : newMethodLines) {
                if (Objects.equals(oldMethodLines[index], newMethodLine))
                    index++;
                if (index == oldMethodLines.length)
                    return true;
            }
            return index == oldMethodLines.length;
        }
        return false;
    }

    public static boolean isStreamAPI(ASTNode statement) {
        if (statement.toString().contains(" -> ") || statement.toString().contains("::")) {
            List<Expression> list = new ArrayList<>();
            statement.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation node) {
                    if (streamAPIName(node.getName().getFullyQualifiedName()))
                        list.add(node);
                    return true;
                }

                @Override
                public boolean visit(SuperMethodInvocation node) {
                    if (streamAPIName(node.getName().getFullyQualifiedName()))
                        list.add(node);
                    return true;
                }

                @Override
                public boolean visit(ExpressionMethodReference node) {
                    if (streamAPIName(node.getName().getFullyQualifiedName()))
                        list.add(node);
                    return true;
                }

                @Override
                public boolean visit(SuperMethodReference node) {
                    if (streamAPIName(node.getName().getFullyQualifiedName()))
                        list.add(node);
                    return true;
                }

                @Override
                public boolean visit(TypeMethodReference node) {
                    if (streamAPIName(node.getName().getFullyQualifiedName()))
                        list.add(node);
                    return true;
                }
            });
            if (!list.isEmpty())
                return true;
        }
        return false;
    }

    private static boolean streamAPIName(String name) {
        return name.equals("stream") || name.equals("filter") || name.equals("forEach") || name.equals("collect") || name.equals("map") || name.equals("removeIf");
    }
}
