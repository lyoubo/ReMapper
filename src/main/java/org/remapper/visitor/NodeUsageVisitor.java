package org.remapper.visitor;

import org.eclipse.jdt.core.dom.*;
import org.remapper.dto.EntityInfo;
import org.remapper.util.EntityUtils;

import java.util.ArrayList;
import java.util.List;

public class NodeUsageVisitor extends ASTVisitor {

    private final List<EntityInfo> entityUsages;

    public NodeUsageVisitor() {
        entityUsages = new ArrayList<>();
    }

    public List<EntityInfo> getEntityUsages() {
        return entityUsages;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        return visit(node.resolveMethodBinding());
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        return visit(node.resolveConstructorBinding());
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        return visit(node.resolveConstructorBinding());
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        return visit(node.resolveMethodBinding());
    }

    @Override
    public boolean visit(ExpressionMethodReference node) {
        return visit(node.resolveMethodBinding());
    }

    @Override
    public boolean visit(TypeMethodReference node) {
        return visit(node.resolveMethodBinding());
    }

    @Override
    public boolean visit(SuperMethodReference node) {
        return visit(node.resolveMethodBinding());
    }

    @Override
    public boolean visit(CreationReference node) {
        return visit(node.resolveMethodBinding());
    }

    @Override
    public boolean visit(MemberValuePair node) {
        IMemberValuePairBinding iMemberValuePairBinding = node.resolveMemberValuePairBinding();
        if (iMemberValuePairBinding == null) return false;
        IMethodBinding methodBinding = iMemberValuePairBinding.getMethodBinding();
        return visit(methodBinding);
    }

    private boolean visit(IMethodBinding methodBinding) {
        if (methodBinding == null) return true;
        IMethodBinding methodDeclaration = methodBinding.getMethodDeclaration();
        if (methodDeclaration == null) return true;
        ITypeBinding declaringClass = methodDeclaration.getDeclaringClass();
        if (declaringClass == null || !declaringClass.isFromSource()) return true;
        EntityInfo methodEntity = EntityUtils.generateMethodEntity(methodDeclaration);
        entityUsages.add(methodEntity);
        return true;
    }

    @Override
    public boolean visit(MarkerAnnotation node) {
        return visit(node.resolveTypeBinding());
    }

    @Override
    public boolean visit(SingleMemberAnnotation node) {
        return visit(node.resolveTypeBinding());
    }

    @Override
    public boolean visit(NormalAnnotation node) {
        return visit(node.resolveTypeBinding());
    }

    @Override
    public boolean visit(SimpleType node) {
        return visit(node.resolveBinding());
    }

    private boolean visit(ITypeBinding typeBinding) {
        if (typeBinding == null) return true;
        ITypeBinding typeDeclaration = typeBinding.getTypeDeclaration();
        if (typeDeclaration == null || !typeDeclaration.isFromSource()) return true;
        EntityInfo typeEntity = EntityUtils.generateTypeEntity(typeDeclaration);
        entityUsages.add(typeEntity);
        return true;
    }

    @Override
    public boolean visit(FieldAccess node) {
        return visit(node.resolveFieldBinding());
    }

    @Override
    public boolean visit(SuperFieldAccess node) {
        return visit(node.resolveFieldBinding());
    }

    @Override
    public boolean visit(QualifiedName node) {
        IBinding iBinding = node.resolveBinding();
        if (iBinding instanceof IVariableBinding) {
            IVariableBinding variableBinding = (IVariableBinding) iBinding;
            if (variableBinding.isField() || variableBinding.isEnumConstant())
                return visit(variableBinding);
        }
        return true;
    }

    @Override
    public boolean visit(SimpleName node) {
        ASTNode parent = node.getParent();
        if (parent instanceof FieldAccess || parent instanceof SuperFieldAccess || parent instanceof QualifiedName)
            return false;
        IBinding iBinding = node.resolveBinding();
        if (iBinding instanceof IVariableBinding) {
            IVariableBinding variableBinding = (IVariableBinding) iBinding;
            if (variableBinding.isField() || variableBinding.isEnumConstant())
                return visit(variableBinding);
        }
        return false;
    }

    private boolean visit(IVariableBinding variableBinding) {
        if (variableBinding == null) return true;
        IVariableBinding variableDeclaration = variableBinding.getVariableDeclaration();
        if (variableDeclaration == null) return true;
        ITypeBinding declaringClass = variableDeclaration.getDeclaringClass();
        if (declaringClass == null || !declaringClass.isFromSource()) return true;
        EntityInfo fieldEntity = EntityUtils.generateFieldEntity(variableDeclaration);
        entityUsages.add(fieldEntity);
        return true;
    }
}
