package org.remapper.util;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.remapper.dto.EntityInfo;
import org.remapper.dto.EntityType;

import java.util.Arrays;
import java.util.stream.Collectors;

public class EntityUtils {

    public static EntityInfo generateTypeEntity(ITypeBinding typeBinding) {
        EntityInfo entityInfo = new EntityInfo();
        entityInfo.setContainer(typeBinding.getQualifiedName());
        if (typeBinding.isAnnotation())
            entityInfo.setType(EntityType.ANNOTATION_TYPE);
        else if (typeBinding.isInterface())
            entityInfo.setType(EntityType.INTERFACE);
        else if (typeBinding.isEnum())
            entityInfo.setType(EntityType.ENUM);
        else if (typeBinding.isRecord())
            entityInfo.setType(EntityType.RECORD);
        else
            entityInfo.setType(EntityType.CLASS);
        entityInfo.setName(typeBinding.getName());
        return entityInfo;
    }

    public static EntityInfo generateMethodEntity(IMethodBinding methodBinding) {
        EntityInfo entityInfo = new EntityInfo();
        ITypeBinding declaringClass = methodBinding.getDeclaringClass();
        entityInfo.setContainer(declaringClass.getQualifiedName());
        if (methodBinding.isAnnotationMember()) {
            entityInfo.setType(EntityType.ANNOTATION_MEMBER);
        } else {
            entityInfo.setType(EntityType.METHOD);
        }
        entityInfo.setName(methodBinding.getName());
        ITypeBinding[] parameterTypes = methodBinding.getParameterTypes();
        String params = Arrays.stream(parameterTypes).map(ITypeBinding::getName).collect(Collectors.joining(","));
        entityInfo.setParams(params);
        return entityInfo;
    }

    public static EntityInfo generateFieldEntity(IVariableBinding variableBinding) {
        EntityInfo entityInfo = new EntityInfo();
        ITypeBinding declaringClass = variableBinding.getDeclaringClass();
        entityInfo.setContainer(declaringClass.getQualifiedName());
        if (variableBinding.isEnumConstant())
            entityInfo.setType(EntityType.ENUM_CONSTANT);
        else if (variableBinding.isField())
            entityInfo.setType(EntityType.FIELD);
        entityInfo.setName(variableBinding.getName());
        return entityInfo;
    }

    public static EntityInfo generateInitializerEntity(ITypeBinding typeBinding, String name) {
        EntityInfo entityInfo = new EntityInfo();
        entityInfo.setContainer(typeBinding.getQualifiedName());
        entityInfo.setType(EntityType.INITIALIZER);
        entityInfo.setName(name);
        return entityInfo;
    }
}
