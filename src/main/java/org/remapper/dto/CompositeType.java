package org.remapper.dto;

public class CompositeType extends UMLType {
    private UMLType leftType;
    private LeafType rightType;

    public CompositeType(UMLType leftType, LeafType rightType) {
        this.leftType = leftType;
        this.rightType = rightType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CompositeType other = (CompositeType) obj;
        if (leftType == null) {
            if (other.leftType != null)
                return false;
        } else if (!leftType.equals(other.leftType))
            return false;
        if (rightType == null) {
            if (other.rightType != null)
                return false;
        } else if (!rightType.equals(other.rightType))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return leftType.toString() + "." + rightType.toString();
    }

    @Override
    public String toQualifiedString() {
        return leftType.toQualifiedString() + "." + rightType.toQualifiedString();
    }

    @Override
    public String getClassType() {
        return rightType.getClassType();
    }
}