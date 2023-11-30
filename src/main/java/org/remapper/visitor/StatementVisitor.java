package org.remapper.visitor;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

public class StatementVisitor extends ASTVisitor {

    private final List<ASTNode> statements;

    public StatementVisitor() {
        this.statements = new ArrayList<>();
    }

    public List<ASTNode> getStatements() {
        return statements;
    }

    @Override
    public boolean visit(AssertStatement node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(Block node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(BreakStatement node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(DoStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(EmptyStatement node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(ForStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(IfStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(LabeledStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        statements.add(node);
        Expression expression = node.getExpression();
        if (expression instanceof SwitchExpression)
            return true;
        else
            return false;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(SwitchCase node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(SwitchStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(SynchronizedStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(ThrowStatement node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(TryStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        statements.add(node);
        return false;
    }

    @Override
    public boolean visit(WhileStatement node) {
        statements.add(node);
        return true;
    }

    @Override
    public boolean visit(CatchClause node) {
        statements.add(node);
        return true;
    }
}
