package org.remapper.dto;

import java.util.Objects;

public class ChildNode {

    private int label;
    private String value;

    public int getLabel() {
        return label;
    }

    public void setLabel(int label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChildNode childNode = (ChildNode) o;
        return label == childNode.label && value.equals(childNode.value);
    }

    public boolean equalsIgnoreSimpleName(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChildNode childNode = (ChildNode) o;
        if (label == 42 && childNode.label == 42) return true;
        return label == childNode.label && value.equals(childNode.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, value);
    }
}
