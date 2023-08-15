package org.remapper.util;

import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Objects;

public class MethodUtils {

    public static boolean isGetter(MethodDeclaration methodDeclaration, String field) {
        Block body = methodDeclaration.getBody();
        if (body != null) {
            List<Statement> statements = body.statements();
            List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
            if (statements.size() == 1 && parameters.size() == 0) {
                Statement statement = statements.get(0);
                return StringUtils.equals(statement.toString(), "return " + field + ";") ||
                        StringUtils.equals(statement.toString(), "return this." + field + ";");
            }
        }
        return false;
    }

    public static boolean isSetter(MethodDeclaration methodDeclaration, String field) {
        Block body = methodDeclaration.getBody();
        List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
        if (body != null && parameters.size() == 1) {
            List<Statement> statements = body.statements();
            if (statements.size() == 1 && parameters.size() == 1) {
                Statement statement = statements.get(0);
                return StringUtils.equals(statement.toString(), field + "=" + parameters.get(0).getName().getFullyQualifiedName() + ";") ||
                        StringUtils.equals(statement.toString(), "this." + field + "=" + parameters.get(0).getName().getFullyQualifiedName() + ";");
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
            }
            return index == oldMethodLines.length;
        }
        return false;
    }
}
