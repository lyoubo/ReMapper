package org.remapper.util;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.Map;

public class ASTParserUtils {

    private static final String DEFAULT_JAVA_CORE_VERSION = JavaCore.VERSION_14;

    public static ASTParser getASTParser(String[] sourcepathEntries, String[] encodings) {
        return setAttributes(sourcepathEntries, encodings);
    }

    public static ASTParser getASTParser() {
        return setAttributes(null, null);
    }

    public static ASTParser getFastParser() {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, DEFAULT_JAVA_CORE_VERSION);
        options.put(JavaCore.COMPILER_SOURCE, DEFAULT_JAVA_CORE_VERSION);
        options.put(JavaCore.COMPILER_COMPLIANCE, DEFAULT_JAVA_CORE_VERSION);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(false);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        return parser;
    }

    public static ASTParser getBodyParser() {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, DEFAULT_JAVA_CORE_VERSION);
        options.put(JavaCore.COMPILER_SOURCE, DEFAULT_JAVA_CORE_VERSION);
        options.put(JavaCore.COMPILER_COMPLIANCE, DEFAULT_JAVA_CORE_VERSION);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(false);
        parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
        parser.setStatementsRecovery(true);
        return parser;
    }

    private static ASTParser setAttributes(String[] sourcepathEntries, String[] encodings) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, DEFAULT_JAVA_CORE_VERSION);
        options.put(JavaCore.COMPILER_SOURCE, DEFAULT_JAVA_CORE_VERSION);
        options.put(JavaCore.COMPILER_COMPLIANCE, DEFAULT_JAVA_CORE_VERSION);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(true);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setEnvironment(null, sourcepathEntries, encodings, true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setUnitName("unitName");
        return parser;
    }

    public static CompilationUnit getCompilationUnit(String javaCoreVersion, ASTParser parser, char[] charArray) {
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, javaCoreVersion);
        options.put(JavaCore.COMPILER_SOURCE, javaCoreVersion);
        options.put(JavaCore.COMPILER_COMPLIANCE, javaCoreVersion);
        parser.setCompilerOptions(options);
        parser.setResolveBindings(false);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setSource(charArray);
        return (CompilationUnit) parser.createAST(null);
    }
}
