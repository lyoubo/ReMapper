package org.remapper.visitor;

import org.eclipse.jdt.core.dom.*;
import org.remapper.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class NodeDeclarationVisitor extends ASTVisitor {

    private final List<TypeDeclaration> typeDeclarations;
    private final List<EnumDeclaration> enumDeclarations;
    private final List<AnnotationTypeDeclaration> annotationTypeDeclarations;
    private final List<RecordDeclaration> recordDeclarations;
    private final List<Initializer> initializers;
    private final List<FieldDeclaration> fieldDeclarations;
    private final List<MethodDeclaration> methodDeclarations;
    private final List<AnnotationTypeMemberDeclaration> annotationMemberDeclarations;
    private final List<EnumConstantDeclaration> enumConstantDeclarations;

    public NodeDeclarationVisitor() {
        typeDeclarations = new ArrayList<>();
        enumDeclarations = new ArrayList<>();
        annotationTypeDeclarations = new ArrayList<>();
        recordDeclarations = new ArrayList<>();
        initializers = new ArrayList<>();
        fieldDeclarations = new ArrayList<>();
        methodDeclarations = new ArrayList<>();
        annotationMemberDeclarations = new ArrayList<>();
        enumConstantDeclarations = new ArrayList<>();
    }

    public List<ASTNode> getASTNodes() {
        List<ASTNode> astNodes = new ArrayList<>();
        astNodes.addAll(typeDeclarations);
        astNodes.addAll(enumDeclarations);
        astNodes.addAll(annotationTypeDeclarations);
        astNodes.addAll(recordDeclarations);
        astNodes.addAll(initializers);
        astNodes.addAll(fieldDeclarations);
        astNodes.addAll(methodDeclarations);
        astNodes.addAll(annotationMemberDeclarations);
        astNodes.addAll(enumConstantDeclarations);
        return astNodes;
    }

    public List<TypeDeclaration> getTypeDeclarations() {
        return typeDeclarations;
    }

    public List<EnumDeclaration> getEnumDeclarations() {
        return enumDeclarations;
    }

    public List<AnnotationTypeDeclaration> getAnnotationTypeDeclarations() {
        return annotationTypeDeclarations;
    }

    public List<RecordDeclaration> getRecordDeclarations() {
        return recordDeclarations;
    }

    public List<Initializer> getInitializers() {
        return initializers;
    }

    public List<FieldDeclaration> getFieldDeclarations() {
        return fieldDeclarations;
    }

    public List<MethodDeclaration> getMethodDeclarations() {
        return methodDeclarations;
    }

    public List<AnnotationTypeMemberDeclaration> getAnnotationMemberDeclarations() {
        return annotationMemberDeclarations;
    }

    public List<EnumConstantDeclaration> getEnumConstantDeclarations() {
        return enumConstantDeclarations;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        ASTNode parent = node.getParent();
        if (parent instanceof AnonymousClassDeclaration || parent instanceof Initializer) return true;
        ITypeBinding typeBinding = node.resolveBinding();
        if (typeBinding == null || StringUtils.isEmpty(typeBinding.getQualifiedName())) return true;
        typeDeclarations.add(node);
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        ASTNode parent = node.getParent();
        if (parent instanceof AnonymousClassDeclaration || parent instanceof Initializer) return true;
        ITypeBinding typeBinding = node.resolveBinding();
        if (typeBinding == null || StringUtils.isEmpty(typeBinding.getQualifiedName())) return true;
        enumDeclarations.add(node);
        return true;
    }

    @Override
    public boolean visit(RecordDeclaration node) {
        ASTNode parent = node.getParent();
        if (parent instanceof AnonymousClassDeclaration || parent instanceof Initializer) return true;
        ITypeBinding typeBinding = node.resolveBinding();
        if (typeBinding == null || StringUtils.isEmpty(typeBinding.getQualifiedName())) return true;
        recordDeclarations.add(node);
        return true;
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        ITypeBinding typeBinding = node.resolveBinding();
        if (typeBinding == null) return true;
        annotationTypeDeclarations.add(node);
        return true;
    }

    @Override
    public boolean visit(Initializer node) {
        ASTNode parent = node.getParent();
        if (parent instanceof AbstractTypeDeclaration) {
            ITypeBinding typeBinding = ((AbstractTypeDeclaration) parent).resolveBinding();
            if (typeBinding == null) return true;
            initializers.add(node);
        }
        return false;
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        ASTNode parent = node.getParent();
        if (parent instanceof AbstractTypeDeclaration) {
            List<VariableDeclarationFragment> fragments = node.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                IVariableBinding variableBinding = fragment.resolveBinding();
                if (variableBinding == null) return true;
                ITypeBinding declaringClass = variableBinding.getDeclaringClass();
                if (declaringClass == null) return true;
            }
            fieldDeclarations.add(node);
        }
        return false;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        ASTNode parent = node.getParent();
        if (parent instanceof AbstractTypeDeclaration) {
            IMethodBinding methodBinding = node.resolveBinding();
            if (methodBinding == null) return true;
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            if (declaringClass == null) return true;
            methodDeclarations.add(node);
        }
        return false;
    }

    @Override
    public boolean visit(AnnotationTypeMemberDeclaration node) {
        ASTNode parent = node.getParent();
        if (parent instanceof AnnotationTypeDeclaration) {
            IMethodBinding methodBinding = node.resolveBinding();
            if (methodBinding == null) return true;
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            if (declaringClass == null) return true;
            annotationMemberDeclarations.add(node);
        }
        return false;
    }

    @Override
    public boolean visit(EnumConstantDeclaration node) {
        ASTNode parent = node.getParent();
        if (parent instanceof EnumDeclaration) {
            IVariableBinding variableBinding = node.resolveVariable();
            if (variableBinding == null) return true;
            ITypeBinding declaringClass = variableBinding.getDeclaringClass();
            if (declaringClass == null) return true;
            enumConstantDeclarations.add(node);
        }
        return false;
    }
}
