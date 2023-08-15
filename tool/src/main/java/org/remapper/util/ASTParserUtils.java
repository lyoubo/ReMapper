package org.remapper.util;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;

import java.util.Map;

public class ASTParserUtils {

    public static ASTParser getASTParser(String[] sourcepathEntries, String[] encodings) {
        return setAttributes(sourcepathEntries, encodings);
    }

    public static ASTParser getASTParser() {
        return setAttributes(null, null);
    }

    public static ASTParser getFastParser() {
        ASTParser parser = ASTParser.newParser(AST.JLS19);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
        parser.setCompilerOptions(options);
        return parser;
    }

    private static ASTParser setAttributes(String[] sourcepathEntries, String[] encodings) {
        ASTParser parser = ASTParser.newParser(AST.JLS19);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        // TODO: JavaCore.Version 应该让用户自行设置
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(true);
        parser.setEnvironment(null, sourcepathEntries, encodings, true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setUnitName("unitName");
        return parser;
    }
}
