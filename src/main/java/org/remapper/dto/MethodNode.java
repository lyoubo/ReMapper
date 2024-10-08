package org.remapper.dto;

import org.eclipse.jdt.core.dom.*;
import org.remapper.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MethodNode extends StatementNodeTree {

    private List<StatementNodeTree> allBlocks;
    private List<StatementNodeTree> allOperations;
    private List<StatementNodeTree> allControls;
    private List<StatementNodeTree> unmatchedStatements;
    private List<StatementNodeTree> matchedStatements;
    private List<StatementNodeTree> duplicatedStatements;
    private DeclarationNodeTree methodEntity;

    public MethodNode(CompilationUnit cu, String filePath, MethodDeclaration method) {
        super(cu, filePath, method);
        super.setMatched();
        super.setDepth(0);
        super.setType(StatementType.METHOD_DECLARATION);
        super.setExpression(getMethodDeclaration(method));
        allBlocks = new ArrayList<>();
        allOperations = new ArrayList<>();
        allControls = new ArrayList<>();
    }

    public MethodNode(CompilationUnit cu, String filePath, Initializer method) {
        super(cu, filePath, method);
        super.setMatched();
        super.setDepth(0);
        super.setType(StatementType.METHOD_DECLARATION);
        super.setExpression("initializer");
        allBlocks = new ArrayList<>();
        allOperations = new ArrayList<>();
        allControls = new ArrayList<>();
    }

    public String getMethodDeclaration(MethodDeclaration methodDeclaration) {
        StringBuilder sb = new StringBuilder();
        int methodModifiers = methodDeclaration.getModifiers();
        boolean isInterfaceMethod = false;
        if (methodDeclaration.getParent() instanceof TypeDeclaration) {
            TypeDeclaration parent = (TypeDeclaration) methodDeclaration.getParent();
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
        sb.append(methodDeclaration.getName().getIdentifier());
        sb.append("(");
        List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
        List<String> list = new ArrayList<>();
        for (SingleVariableDeclaration parameter : parameters) {
            if (parameter.isVarargs()) {
                list.add(parameter.getName().getFullyQualifiedName() + " " + StringUtils.type2String(parameter.getType()) + "...");
            } else {
                list.add(parameter.getName().getFullyQualifiedName() + " " + StringUtils.type2String(parameter.getType()));
            }
        }
        sb.append(String.join(", ", list));
        sb.append(")");
        if (methodDeclaration.getReturnType2() != null) {
            sb.append(" : ");
            sb.append(methodDeclaration.getReturnType2().toString());
        }
        return sb.toString();
    }

    public void addBlock(StatementNodeTree block) {
        allBlocks.add(block);
        allBlocks.sort(Comparator.comparingInt(StatementNodeTree::getPosition));
    }

    public void addBlocks(int index, List<StatementNodeTree> blocks) {
        if (index == -1)
            allBlocks.addAll(blocks);
        else
            allBlocks.addAll(index, blocks);
    }

    public void addOperation(StatementNodeTree operation) {
        allOperations.add(operation);
        allOperations.sort(Comparator.comparingInt(StatementNodeTree::getPosition));
    }

    public void addOperations(int index, List<StatementNodeTree> operations) {
        if (index == -1)
            allOperations.addAll(operations);
        else
            allOperations.addAll(index, operations);
    }

    public void addControl(StatementNodeTree control) {
        allControls.add(control);
        allControls.sort(Comparator.comparingInt(StatementNodeTree::getPosition));
    }

    public void addControls(int index, List<StatementNodeTree> controls) {
        if (index == -1)
            allControls.addAll(controls);
        else
            allControls.addAll(index, controls);
    }

    public List<StatementNodeTree> getAllBlocks() {
        return allBlocks;
    }

    public List<StatementNodeTree> getAllOperations() {
        return allOperations;
    }

    public List<StatementNodeTree> getAllControls() {
        return allControls;
    }

    public List<StatementNodeTree> getUnmatchedStatements() {
        unmatchedStatements = new ArrayList<>();
        depthFirstSearch(unmatchedStatements, getChildren(), false);
        return unmatchedStatements;
    }

    public List<StatementNodeTree> getMatchedStatements() {
        matchedStatements = new ArrayList<>();
        depthFirstSearch(matchedStatements, getChildren(), true);
        return matchedStatements;
    }

    public List<StatementNodeTree> getDuplicatedStatements() {
        duplicatedStatements = new ArrayList<>();
        depthFirstSearch(duplicatedStatements, getChildren());
        return duplicatedStatements;
    }

    private void depthFirstSearch(List<StatementNodeTree> list, List<StatementNodeTree> children, boolean matched) {
        for (StatementNodeTree child : children) {
            if (child.isMatched() == matched) {
                if (list.contains(child)) continue;
                list.add(child);
            }
            depthFirstSearch(list, child.getChildren(), matched);
        }
    }

    private void depthFirstSearch(List<StatementNodeTree> list, List<StatementNodeTree> children) {
        for (StatementNodeTree child : children) {
            if (child.isDuplicated() == true) {
                if (list.contains(child)) continue;
                list.add(child);
            }
            depthFirstSearch(list, child.getChildren());
        }
    }

    public void setDuplicated() {
        setDuplicated(this.getChildren());
    }

    private void setDuplicated(List<StatementNodeTree> children) {
        for (StatementNodeTree child : children) {
            if (!child.isMatched())
                child.setDuplicated();
            setDuplicated(child.getChildren());
        }
    }

    public void addDeletedStatements(MatchPair matchPair, List<StatementNodeTree> children) {
        for (StatementNodeTree child : children) {
            if (!child.isMatched())
                matchPair.addDeletedStatement(child);
            addDeletedStatements(matchPair, child.getChildren());
        }
    }

    public void addAddedStatements(MatchPair matchPair, List<StatementNodeTree> children) {
        for (StatementNodeTree child : children) {
            if (!child.isMatched())
                matchPair.addAddedStatement(child);
            addAddedStatements(matchPair, child.getChildren());
        }
    }

    public DeclarationNodeTree getMethodEntity() {
        return methodEntity;
    }

    public void setMethodEntity(DeclarationNodeTree methodEntity) {
        this.methodEntity = methodEntity;
    }
}
