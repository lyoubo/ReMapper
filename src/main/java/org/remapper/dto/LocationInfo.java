package org.remapper.dto;

import org.eclipse.jdt.core.dom.*;

import java.util.Objects;

public class LocationInfo {

    private String filePath;
    private int startOffset;
    private int endOffset;
    private int length;
    private int startLine;
    private int startColumn;
    private int endLine;
    private int endColumn;
    private CodeElementType codeElementType;


    public LocationInfo(CompilationUnit cu, String filePath, ASTNode node) {
        this.filePath = filePath;
        this.startOffset = node.getStartPosition();
        this.length = node.getLength();
        this.endOffset = startOffset + length;

        //lines are 1-based
        this.startLine = cu.getLineNumber(startOffset);
        this.endLine = cu.getLineNumber(endOffset);
        if (this.endLine == -1) {
            this.endLine = cu.getLineNumber(endOffset - 1);
        }
        //columns are 0-based
        this.startColumn = cu.getColumnNumber(startOffset);
        //convert to 1-based
        if (this.startColumn > 0) {
            this.startColumn += 1;
        }
        this.endColumn = cu.getColumnNumber(endOffset);
        if (this.endColumn == -1) {
            this.endColumn = cu.getColumnNumber(endOffset - 1);
        }
        //convert to 1-based
        if (this.endColumn > 0) {
            this.endColumn += 1;
        }
        this.codeElementType = getCodeElementType(node);
    }

    private CodeElementType getCodeElementType(ASTNode node) {
        if (node instanceof TypeDeclaration)
            return CodeElementType.TYPE_DECLARATION;
        if (node instanceof MethodDeclaration)
            return CodeElementType.METHOD_DECLARATION;
        if (node instanceof FieldDeclaration)
            return CodeElementType.FIELD_DECLARATION;
        if (node instanceof EnumConstantDeclaration)
            return CodeElementType.ENUM_CONSTANT_DECLARATION;
        if (node instanceof AnnotationTypeDeclaration)
            return CodeElementType.ANNOTATION_TYPE;
        if (node instanceof AnnotationTypeMemberDeclaration)
            return CodeElementType.ANNOTATION_MEMBER;
        if (node instanceof Initializer)
            return CodeElementType.INITIALIZER;
        if (node instanceof AssertStatement)
            return CodeElementType.ASSERT_STATEMENT;
        if (node instanceof Block) {
            if (node.getParent() instanceof TryStatement) {
                TryStatement statement = (TryStatement) node.getParent();
                if (node == statement.getFinally())
                    return CodeElementType.FINALLY_BLOCK;
            }
            return CodeElementType.BLOCK;
        }
        if (node instanceof BreakStatement)
            return CodeElementType.BREAK_STATEMENT;
        if (node instanceof ConstructorInvocation)
            return CodeElementType.CONSTRUCTOR_INVOCATION;
        if (node instanceof ContinueStatement)
            return CodeElementType.CONTINUE_STATEMENT;
        if (node instanceof DoStatement)
            return CodeElementType.DO_STATEMENT;
        if (node instanceof EmptyStatement)
            return CodeElementType.EMPTY_STATEMENT;
        if (node instanceof EnhancedForStatement)
            return CodeElementType.ENHANCED_FOR_STATEMENT;
        if (node instanceof ExpressionStatement)
            return CodeElementType.EXPRESSION_STATEMENT;
        if (node instanceof ForStatement)
            return CodeElementType.FOR_STATEMENT;
        if (node instanceof IfStatement)
            return CodeElementType.IF_STATEMENT;
        if (node instanceof LabeledStatement)
            return CodeElementType.LABELED_STATEMENT;
        if (node instanceof ReturnStatement)
            return CodeElementType.RETURN_STATEMENT;
        if (node instanceof SuperConstructorInvocation)
            return CodeElementType.SUPER_CONSTRUCTOR_INVOCATION;
        if (node instanceof SwitchCase)
            return CodeElementType.SWITCH_CASE;
        if (node instanceof SwitchStatement)
            return CodeElementType.SWITCH_STATEMENT;
        if (node instanceof SynchronizedStatement)
            return CodeElementType.SYNCHRONIZED_STATEMENT;
        if (node instanceof ThrowStatement)
            return CodeElementType.THROW_STATEMENT;
        if (node instanceof TryStatement)
            return CodeElementType.TRY_STATEMENT;
        if (node instanceof TypeDeclarationStatement)
            return CodeElementType.TYPE_DECLARATION_STATEMENT;
        if (node instanceof VariableDeclarationStatement)
            return CodeElementType.VARIABLE_DECLARATION_STATEMENT;
        if (node instanceof WhileStatement)
            return CodeElementType.WHILE_STATEMENT;
        if (node instanceof CatchClause)
            return CodeElementType.CATCH_CLAUSE;
        if (node instanceof Annotation)
            return CodeElementType.ANNOTATION;
        if (node instanceof Type)
            return CodeElementType.TYPE;
        if (node instanceof SingleVariableDeclaration)
            return CodeElementType.SINGLE_VARIABLE_DECLARATION;
        if (node instanceof VariableDeclarationFragment) {
            ASTNode parent = node.getParent();
            if (parent instanceof FieldDeclaration)
                return CodeElementType.FIELD_DECLARATION;
            if (parent instanceof VariableDeclarationStatement)
                return CodeElementType.VARIABLE_DECLARATION_STATEMENT;
            if (parent instanceof VariableDeclarationExpression)
                return CodeElementType.VARIABLE_DECLARATION_EXPRESSION;
        }
        return null;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public void setStartColumn(int startColumn) {
        this.startColumn = startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }

    public CodeElementType getCodeElementType() {
        return codeElementType;
    }

    public CodeRange codeRange() {
        return new CodeRange(this.getFilePath(), this.getStartLine(), this.getEndLine(), this.getStartColumn(), this.getEndColumn(), this.getCodeElementType());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationInfo that = (LocationInfo) o;
        return startLine == that.startLine && endLine == that.endLine && filePath.equals(that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, startLine, endLine);
    }

    public String toString() {
        return "line range:" + startLine + "-" + endLine;
    }

    public enum CodeElementType {
        TYPE_DECLARATION,
        ANNOTATION_TYPE,
        ANNOTATION_MEMBER,
        IMPORT_DECLARATION,
        METHOD_DECLARATION,
        FIELD_DECLARATION,
        SINGLE_VARIABLE_DECLARATION,
        VARIABLE_DECLARATION_STATEMENT,
        VARIABLE_DECLARATION_EXPRESSION,
        VARIABLE_DECLARATION_INITIALIZER,
        ANONYMOUS_CLASS_DECLARATION,
        LAMBDA_EXPRESSION,
        LAMBDA_EXPRESSION_BODY,
        CLASS_INSTANCE_CREATION,
        ARRAY_CREATION,
        METHOD_INVOCATION,
        SUPER_METHOD_INVOCATION,
        TERNARY_OPERATOR,
        TERNARY_OPERATOR_CONDITION,
        TERNARY_OPERATOR_THEN_EXPRESSION,
        TERNARY_OPERATOR_ELSE_EXPRESSION,
        LABELED_STATEMENT,
        FOR_STATEMENT("for"),
        FOR_STATEMENT_CONDITION,
        FOR_STATEMENT_INITIALIZER,
        FOR_STATEMENT_UPDATER,
        ENHANCED_FOR_STATEMENT("for"),
        ENHANCED_FOR_STATEMENT_PARAMETER_NAME,
        ENHANCED_FOR_STATEMENT_EXPRESSION,
        WHILE_STATEMENT("while"),
        WHILE_STATEMENT_CONDITION,
        IF_STATEMENT("if"),
        IF_STATEMENT_CONDITION,
        DO_STATEMENT("do"),
        DO_STATEMENT_CONDITION,
        SWITCH_STATEMENT("switch"),
        SWITCH_STATEMENT_CONDITION,
        SYNCHRONIZED_STATEMENT("synchronized"),
        SYNCHRONIZED_STATEMENT_EXPRESSION,
        TRY_STATEMENT("try"),
        TRY_STATEMENT_RESOURCE,
        CATCH_CLAUSE("catch"),
        CATCH_CLAUSE_EXCEPTION_NAME,
        EXPRESSION_STATEMENT,
        SWITCH_CASE,
        ASSERT_STATEMENT,
        RETURN_STATEMENT,
        THROW_STATEMENT,
        CONSTRUCTOR_INVOCATION,
        SUPER_CONSTRUCTOR_INVOCATION,
        BREAK_STATEMENT,
        CONTINUE_STATEMENT,
        EMPTY_STATEMENT,
        BLOCK("{"),
        FINALLY_BLOCK("finally"),
        TYPE,
        LIST_OF_STATEMENTS,
        TYPE_DECLARATION_STATEMENT,
        ANNOTATION,
        MODIFIER,
        SINGLE_MEMBER_ANNOTATION_VALUE,
        NORMAL_ANNOTATION_MEMBER_VALUE_PAIR,
        ENUM_CONSTANT_DECLARATION,
        JAVADOC,
        LINE_COMMENT,
        BLOCK_COMMENT,
        LAMBDA_EXPRESSION_PARAMETER,
        METHOD_REFERENCE,
        CREATION_REFERENCE,
        INITIALIZER,
        TYPE_PARAMETER,
        //expressions
        STRING_LITERAL, CHAR_LITERAL, ARRAY_ACCESS, PREFIX_EXPRESSION, POSTFIX_EXPRESSION, INFIX_EXPRESSION, THIS_EXPRESSION, NUMBER_LITERAL, NULL_LITERAL, BOOLEAN_LITERAL, TYPE_LITERAL, FIELD_ACCESS, SIMPLE_NAME, EXPRESSION, QUALIFIED_NAME, CAST_EXPRESSION, PARENTHESIZED_EXPRESSION;

        private String name;

        private CodeElementType() {

        }

        private CodeElementType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public CodeElementType setName(String name) {
            this.name = name;
            return this;
        }
    }
}
