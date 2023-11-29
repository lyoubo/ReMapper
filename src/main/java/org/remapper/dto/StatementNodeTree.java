package org.remapper.dto;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class StatementNodeTree {

    private int depth;
    private StatementType type;
    private StatementNodeTree parent;
    private List<StatementNodeTree> children;
    private List<StatementNodeTree> descendants;
    private MethodNode root;
    private ASTNode statement;
    private String expression;
    private BlockType blockType;
    private String blockExpression;
    private boolean isDuplicated;
    private boolean isMatched;
    private int position;
    private StatementInfo entity;
    private LocationInfo location;

    public StatementNodeTree(CompilationUnit cu, String filePath, ASTNode node) {
        location = new LocationInfo(cu, filePath, node);
        children = new ArrayList<>();
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public StatementType getType() {
        return type;
    }

    public void setType(StatementType type) {
        this.type = type;
    }

    public StatementNodeTree getParent() {
        return parent;
    }

    public void setParent(StatementNodeTree parent) {
        this.parent = parent;
    }

    public List<StatementNodeTree> getChildren() {
        return children;
    }

    public void addChild(StatementNodeTree child) {
        children.add(child);
        children.sort(Comparator.comparingInt(a -> a.position));
    }

    public List<StatementNodeTree> getDescendants() {
        descendants = new ArrayList<>();
        depthFirstSearch(descendants, getChildren());
        return descendants;
    }

    private void depthFirstSearch(List<StatementNodeTree> list, List<StatementNodeTree> children) {
        for (StatementNodeTree child : children) {
            list.add(child);
            depthFirstSearch(list, child.getChildren());
        }
    }

    public MethodNode getRoot() {
        if (root == null) {
            StatementNodeTree root = this;
            while (!(root instanceof MethodNode)) {
                root = root.parent;
            }
            this.root = (MethodNode) root;
        }
        return root;
    }

    public ASTNode getStatement() {
        return statement;
    }

    public void setStatement(ASTNode statement) {
        this.statement = statement;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public BlockType getBlockType() {
        return blockType;
    }

    public void setBlockType(BlockType blockType) {
        this.blockType = blockType;
    }

    public String getBlockExpression() {
        return blockExpression;
    }

    public void setBlockExpression(String blockExpression) {
        this.blockExpression = blockExpression;
    }

    public boolean isMatched() {
        return isMatched && !isDuplicated;
    }

    public void setDuplicated() {
        isDuplicated = true;
    }

    public void setMatched() {
        isMatched = true;
    }

    public boolean hasChildren() {
        return children.size() > 0;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public StatementInfo getEntity() {
        if (entity != null) {
            return entity;
        } else {
            entity = new StatementInfo();
            StatementNodeTree parent = this.parent;
            while (!(parent instanceof MethodNode)) {
                parent = parent.parent;
            }
            entity.setMethod(parent.getExpression());
            entity.setExpression(expression);
            entity.setType(type);
            entity.setLocation(location);
        }
        return entity;
    }

    public LocationInfo getLocation() {
        return location;
    }
}
