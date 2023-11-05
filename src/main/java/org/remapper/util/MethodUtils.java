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
                    List<String> variables = new ArrayList<>();
                    statement.accept(new ASTVisitor() {
                        @Override
                        public boolean visit(VariableDeclarationFragment node) {
                            variables.add(node.getName().getIdentifier());
                            return true;
                        }
                    });
                    boolean parameterUsed = false;
                    for (SingleVariableDeclaration parameter : parameters) {
                        for (String variable : variables) {
                            if (variable.equals(parameter.getName().getIdentifier())) {
                                parameterUsed = true;
                                break;
                            }
                        }
                    }
                    for (String variable : variables) {
                        if (statement.toString().equals("return " + variable + ";\n") && (parameters.size() == 0 || !parameterUsed)) {
                            return true;
                        } else if (statement.toString().equals("return " + variable + ".keySet()" + ";\n") && (parameters.size() == 0 || !parameterUsed)) {
                            return true;
                        } else if (statement.toString().equals("return " + variable + ".values()" + ";\n") && (parameters.size() == 0 || !parameterUsed)) {
                            return true;
                        }
                    }
                    String name = methodDeclaration.getName().getIdentifier();
                    Type returnType = methodDeclaration.getReturnType2();
                    if ((name.startsWith("is")) && (parameters.size() == 0) &&
                            returnType != null && returnType.toString().equals("boolean")) {
                        return true;
                    }
                    if ((name.startsWith("has")) && (!parameterUsed) &&
                            returnType != null && returnType.toString().equals("boolean")) {
                        return true;
                    }
                    if (statement.toString().equals("return null;\n")) {
                        return true;
                    }
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
                List<String> variables = new ArrayList<>();
                statement.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(VariableDeclarationFragment node) {
                        variables.add(node.getName().getIdentifier());
                        return true;
                    }
                });
                for (String variable : variables) {
                    if (statement.toString().equals(variable + "=" + parameters.get(0).getName().getIdentifier() + ";\n")) {
                        return true;
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
            if (list.size() > 0)
                return true;
        }
        return false;
    }

    private static boolean streamAPIName(String name) {
        return name.equals("stream") || name.equals("filter") || name.equals("forEach") || name.equals("collect") || name.equals("map") || name.equals("removeIf");
    }
}
