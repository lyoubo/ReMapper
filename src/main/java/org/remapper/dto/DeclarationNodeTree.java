package org.remapper.dto;

import org.eclipse.jdt.core.dom.*;
import org.remapper.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public abstract class DeclarationNodeTree implements LocationInfoProvider{

    private int height;
    private EntityType type;
    private String namespace;
    private String name;
    private DeclarationNodeTree parent;
    private List<DeclarationNodeTree> children;
    private ASTNode declaration;
    private boolean isRoot;
    private boolean isLeaf;
    private boolean isMatched;
    private String filePath;
    private EntityInfo entity;
    private MethodNode methodNode;
    private LocationInfo locationInfo;
    private List<EntityInfo> dependencies;

    public DeclarationNodeTree() {
    }

    public DeclarationNodeTree(CompilationUnit cu, String filePath, ASTNode node) {
        locationInfo = new LocationInfo(cu, filePath, node);
        children = new ArrayList<>();
        dependencies = new ArrayList<>();
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public EntityType getType() {
        return type;
    }

    public void setType(EntityType type) {
        this.type = type;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DeclarationNodeTree getParent() {
        return parent;
    }

    public void setParent(DeclarationNodeTree parent) {
        this.parent = parent;
    }

    public ASTNode getDeclaration() {
        return declaration;
    }

    public void setDeclaration(ASTNode declaration) {
        this.declaration = declaration;
    }

    public List<DeclarationNodeTree> getChildren() {
        return children;
    }

    public void addChild(DeclarationNodeTree child) {
        children.add(child);
    }

    public boolean isRoot() {
        return isRoot;
    }

    public void setRoot(boolean root) {
        this.isRoot = root;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public void setLeaf(boolean leaf) {
        this.isLeaf = leaf;
    }

    public boolean hasChildren() {
        return children.size() > 0;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isMatched() {
        return isMatched;
    }

    public void setMatched() {
        this.isMatched = true;
    }

    public EntityInfo getEntity() {
        if (entity != null) {
            return entity;
        }
        if (type == EntityType.METHOD) {
            entity = new EntityInfo();
            entity.setContainer(namespace);
            entity.setType(type);
            entity.setName(name);
            IMethodBinding methodBinding = ((MethodDeclaration) declaration).resolveBinding();
            if (methodBinding != null) {
                ITypeBinding[] parameterTypes = methodBinding.getParameterTypes();
                String params = Arrays.stream(parameterTypes).map(ITypeBinding::getName).collect(Collectors.joining(","));
                entity.setParams(params);
            } else {
                List<SingleVariableDeclaration> parameters = ((MethodDeclaration) declaration).parameters();
                StringJoiner joiner = new StringJoiner(",");
                for (SingleVariableDeclaration param : parameters) {
                    Type type = param.getType();
                    String typeName = type.toString();
                    String s = param.isVarargs() ? typeName + "[]" : typeName;
                    joiner.add(s);
                }
                String params = joiner.toString();
                entity.setParams(params);
            }
            entity.setLocationInfo(locationInfo);
        } else if (type == EntityType.CLASS || type == EntityType.INTERFACE || type == EntityType.ENUM ||
                type == EntityType.ANNOTATION_TYPE || type == EntityType.RECORD) {
            entity = new EntityInfo();
            entity.setContainer(namespace.equals("") ? name : namespace + "." + name);
            entity.setType(type);
            entity.setName(name);
            entity.setLocationInfo(locationInfo);
        } else {
            entity = new EntityInfo();
            entity.setContainer(namespace);
            entity.setType(type);
            entity.setName(name);
            entity.setLocationInfo(locationInfo);
        }
        return entity;
    }

    public List<EntityInfo> getDependencies() {
        return dependencies;
    }

    public void addDependencies(List<EntityInfo> dependencies) {
        this.dependencies.addAll(dependencies);
    }

    public boolean equals(DeclarationNodeTree other) {
        return this.type == other.type &&
                StringUtils.equals(this.namespace, other.namespace) &&
                StringUtils.equals(this.name, other.name);
    }

    public boolean equals(DeclarationNodeTree other, String filePath, String renamedFilePath) {
        String originalName = filePath.substring(filePath.lastIndexOf("/") + 1, filePath.lastIndexOf(".java"));
        String originalPackage = filePath.substring(0, filePath.lastIndexOf("/")).replace("/", ".");
        while (originalPackage.contains(".")) {
            if (this.namespace.startsWith(originalPackage))
                break;
            else {
                originalPackage = originalPackage.substring(originalPackage.indexOf(".") + 1);
            }
        }
        String renamedName = renamedFilePath.substring(renamedFilePath.lastIndexOf("/") + 1, renamedFilePath.lastIndexOf(".java"));
        String renamedPackage = renamedFilePath.substring(0, renamedFilePath.lastIndexOf("/")).replace("/", ".");
        while (renamedPackage.contains(".")) {
            if (other.namespace.startsWith(renamedPackage))
                break;
            else {
                renamedPackage = renamedPackage.substring(renamedPackage.indexOf(".") + 1);
            }
        }
        return this.type == other.type &&
                StringUtils.equals(this.namespace.replace(originalPackage, renamedPackage).replace(originalName, renamedName), other.namespace) &&
                StringUtils.equals(this.name.replace(originalName, renamedName), other.name);
    }

    public MethodNode getMethodNode() {
        return methodNode;
    }

    public void setMethodNode(MethodNode methodNode) {
        this.methodNode = methodNode;
    }

    public LocationInfo getLocationInfo() {
        return this.locationInfo;
    }

    public CodeRange codeRange() {
        return this.locationInfo.codeRange();
    }
}
