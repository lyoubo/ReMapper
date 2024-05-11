package org.remapper.dto;

import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.core.dom.NaiveASTFlattener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class UMLType implements Serializable {
    private int arrayDimension;
    private boolean parameterized;
    private List<UMLType> typeArguments = new ArrayList<>();
    protected List<Annotation> annotations = new ArrayList<>();

    public int getArrayDimension() {
        return this.arrayDimension;
    }

    public List<UMLType> getTypeArguments() {
        return typeArguments;
    }

    protected String typeArgumentsToString() {
        StringBuilder sb = new StringBuilder();
        if (typeArguments.isEmpty()) {
            if (parameterized) {
                sb.append("<>");
            }
        } else {
            sb.append("<");
            for (int i = 0; i < typeArguments.size(); i++) {
                sb.append(typeArguments.get(i).toQualifiedString());
                if (i < typeArguments.size() - 1)
                    sb.append(",");
            }
            sb.append(">");
        }
        return sb.toString();
    }

    protected String typeArgumentsAndArrayDimensionToString() {
        StringBuilder sb = new StringBuilder();
        if (isParameterized())
            sb.append(typeArgumentsToString());
        for (int i = 0; i < getArrayDimension(); i++) {
            for (int j = 0; j < annotations.size(); j++) {
                if (i == j) {
                    sb.append(" " + annotations.get(j) + " ");
                }
            }
            sb.append("[]");
        }
        return sb.toString();
    }

    private boolean equalTypeArguments(UMLType type) {
        String thisTypeArguments = this.typeArgumentsToString();
        String otherTypeArguments = type.typeArgumentsToString();
        if ((thisTypeArguments.equals("<?>") && otherTypeArguments.startsWith("<? ")) ||
                (thisTypeArguments.startsWith("<? ") && otherTypeArguments.equals("<?>"))) {
            return true;
        }
        if ((thisTypeArguments.equals("<Object>") && otherTypeArguments.contains("<Object>")) ||
                (otherTypeArguments.equals("<Object>") && thisTypeArguments.contains("<Object>"))) {
            return true;
        }
        if (this.typeArguments.size() != type.typeArguments.size()) {
            return false;
        }
        for (int i = 0; i < this.typeArguments.size(); i++) {
            UMLType thisComponent = this.typeArguments.get(i);
            UMLType otherComponent = type.typeArguments.get(i);
            if (!thisComponent.equals(otherComponent)) {
                return false;
            }
        }
        return true;
    }

    public boolean equalTypeArgumentsAndArrayDimension(UMLType typeObject) {
        if (!this.isParameterized() && !typeObject.isParameterized())
            return this.arrayDimension == typeObject.arrayDimension;
        else if (this.isParameterized() && typeObject.isParameterized())
            return equalTypeArguments(typeObject) && this.arrayDimension == typeObject.arrayDimension;
        return false;
    }

    public boolean equalTypeArgumentsAndArrayDimensionForSubType(UMLType typeObject) {
        if (!this.isParameterized() && !typeObject.isParameterized())
            return this.arrayDimension == typeObject.arrayDimension;
        else if (this.isParameterized() && typeObject.isParameterized())
            return equalTypeArguments(typeObject) && this.arrayDimension == typeObject.arrayDimension;
        else if (this.isParameterized() && this.typeArgumentsToString().equals("<?>") && !typeObject.isParameterized())
            return this.arrayDimension == typeObject.arrayDimension;
        else if (!this.isParameterized() && typeObject.isParameterized() && typeObject.typeArgumentsToString().equals("<?>"))
            return this.arrayDimension == typeObject.arrayDimension;
        return false;
    }

    public boolean containsTypeArgument(String type) {
        for (UMLType typeArgument : typeArguments) {
            if (typeArgument.toString().equals(type)) {
                return true;
            }
        }
        return false;
    }

    public boolean isParameterized() {
        return typeArguments.size() > 0 || parameterized;
    }

    public abstract boolean equals(Object o);

    public abstract String toString();

    public abstract String toQualifiedString();

    public abstract String getClassType();

    public boolean equalsQualified(UMLType type) {
        if (this.getClass() == type.getClass()) {
            return this.equals(type);
        }
        return false;
    }

    public boolean equalsWithSubType(UMLType type) {
        if (this.getClass() == type.getClass()) {
            return this.equals(type);
        }
        return false;
    }

    public boolean equalClassType(UMLType type) {
        if (this.getClass() == type.getClass()) {
            return this.equals(type);
        }
        return false;
    }

    public boolean compatibleTypes(UMLType type) {
        if (this.getClass() == type.getClass()) {
            return this.equals(type);
        }
        return false;
    }

    public static LeafType extractTypeObject(String qualifiedName) {
        int arrayDimension = 0;
        boolean parameterized = false;
        List<UMLType> typeArgumentDecomposition = new ArrayList<UMLType>();
        if (qualifiedName.endsWith("[]")) {
            while (qualifiedName.endsWith("[]")) {
                qualifiedName = qualifiedName.substring(0, qualifiedName.lastIndexOf("[]"));
                arrayDimension++;
            }
        }
        if (qualifiedName.contains("<") && qualifiedName.contains(">") &&
                !closingTagBeforeOpeningTag(qualifiedName.substring(qualifiedName.indexOf("<") + 1, qualifiedName.lastIndexOf(">")))) {
            String typeArguments = qualifiedName.substring(qualifiedName.indexOf("<") + 1, qualifiedName.lastIndexOf(">"));
            parameterized = true;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < typeArguments.length(); i++) {
                char charAt = typeArguments.charAt(i);
                if (charAt != ',') {
                    sb.append(charAt);
                } else {
                    if (sb.length() > 0 && equalOpeningClosingTags(sb.toString())) {
                        typeArgumentDecomposition.add(extractTypeObject(sb.toString()));
                        sb = new StringBuilder();
                    } else {
                        sb.append(charAt);
                    }
                }
            }
            if (sb.length() > 0) {
                typeArgumentDecomposition.add(extractTypeObject(sb.toString()));
            }
            qualifiedName = qualifiedName.substring(0, qualifiedName.indexOf("<"));
        }
        UMLType typeObject = new LeafType(qualifiedName);
        typeObject.arrayDimension = arrayDimension;
        typeObject.typeArguments = typeArgumentDecomposition;
        typeObject.parameterized = parameterized;
        return (LeafType) typeObject;
    }

    private static boolean closingTagBeforeOpeningTag(String typeArguments) {
        int indexOfOpeningTag = typeArguments.indexOf("<");
        int indexOfClosingTag = typeArguments.lastIndexOf(">");
        return indexOfClosingTag < indexOfOpeningTag;
    }

    private static boolean equalOpeningClosingTags(String typeArguments) {
        int openingTags = 0;
        int closingTags = 0;
        for (int i = 0; i < typeArguments.length(); i++) {
            if (typeArguments.charAt(i) == '>') {
                openingTags++;
            } else if (typeArguments.charAt(i) == '<') {
                closingTags++;
            }
        }
        return openingTags == closingTags;
    }

    public static UMLType extractTypeObject(Type type) {
        if (type.isPrimitiveType() || type.isSimpleType()) {
            LeafType leafType = extractTypeObject(stringify(type));
            AnnotatableType annotatableType = (AnnotatableType)type;
            List<Annotation> annotations = annotatableType.annotations();
            for(Annotation annotation : annotations) {
                leafType.annotations.add(annotation);
            }
            return leafType;
        } else if (type instanceof QualifiedType) {
            QualifiedType qualified = (QualifiedType) type;
            UMLType leftType = extractTypeObject(qualified.getQualifier());
            LeafType rightType = extractTypeObject(qualified.getName().getFullyQualifiedName());
            AnnotatableType annotatableType = (AnnotatableType)qualified;
            List<Annotation> annotations = annotatableType.annotations();
            for(Annotation annotation : annotations) {
                rightType.annotations.add(annotation);
            }
            return new CompositeType(leftType, rightType);
        } else if (type instanceof NameQualifiedType) {
            NameQualifiedType nameQualified = (NameQualifiedType) type;
            LeafType leftType = extractTypeObject(nameQualified.getQualifier().getFullyQualifiedName());
            LeafType rightType = extractTypeObject(nameQualified.getName().getFullyQualifiedName());
            AnnotatableType annotatableType = (AnnotatableType)nameQualified;
            List<Annotation> annotations = annotatableType.annotations();
            for(Annotation annotation : annotations) {
                rightType.annotations.add(annotation);
            }
            return new CompositeType(leftType, rightType);
        } else if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            org.remapper.dto.WildcardType myWildcardType = null;
            if (wildcard.getBound() != null) {
                UMLType bound = extractTypeObject(wildcard.getBound());
                myWildcardType = new org.remapper.dto.WildcardType(bound, wildcard.isUpperBound());
            } else {
                myWildcardType = new org.remapper.dto.WildcardType(null, false);
            }
            AnnotatableType annotatableType = (AnnotatableType)wildcard;
            List<Annotation> annotations = annotatableType.annotations();
            for(Annotation annotation : annotations) {
                myWildcardType.annotations.add(annotation);
            }
            return myWildcardType;
        } else if (type instanceof ArrayType) {
            ArrayType array = (ArrayType) type;
            UMLType arrayType = extractTypeObject(array.getElementType());
            for(Object dim : array.dimensions()) {
                Dimension dimension = (Dimension)dim;
                List<Annotation> annotations = dimension.annotations();
                for(Annotation annotation : annotations) {
                    arrayType.annotations.add(annotation);
                }
            }
            arrayType.arrayDimension = array.getDimensions();
            return arrayType;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            UMLType container = extractTypeObject(parameterized.getType());
            container.parameterized = true;
            List<Type> typeArguments = parameterized.typeArguments();
            for (Type argument : typeArguments) {
                container.typeArguments.add(extractTypeObject(argument));
            }
            return container;
        } else if (type instanceof UnionType) {
            UnionType union = (UnionType) type;
            List<Type> types = union.types();
            List<UMLType> umlTypes = new ArrayList<UMLType>();
            for (Type unionType : types) {
                umlTypes.add(extractTypeObject(unionType));
            }
            return new ListCompositeType(umlTypes, ListCompositeType.Kind.UNION);
        } else if (type instanceof IntersectionType) {
            IntersectionType intersection = (IntersectionType) type;
            List<Type> types = intersection.types();
            List<UMLType> umlTypes = new ArrayList<UMLType>();
            for (Type unionType : types) {
                umlTypes.add(extractTypeObject(unionType));
            }
            return new ListCompositeType(umlTypes, ListCompositeType.Kind.INTERSECTION);
        }
        return null;
    }

    public static String stringify(ASTNode node) {
        ASTFlattener printer = new ASTFlattener();
        node.accept(printer);
        return printer.getResult();
    }
}

class ASTFlattener extends NaiveASTFlattener {
    @Override
    public boolean visit(InfixExpression node) {
        node.getLeftOperand().accept(this);
        this.buffer.append(' ');  // for cases like x= i - -1; or x= i++ + ++i;
        this.buffer.append(node.getOperator().toString());
        this.buffer.append(' ');
        node.getRightOperand().accept(this);
        final List extendedOperands = node.extendedOperands();
        if (extendedOperands.size() != 0) {
            for (Iterator it = extendedOperands.iterator(); it.hasNext(); ) {
                this.buffer.append(' ');
                this.buffer.append(node.getOperator().toString()).append(' ');
                Expression e = (Expression) it.next();
                e.accept(this);
            }
        }
        return false;
    }
}