package org.remapper.dto;

import java.util.Objects;

public class EntityComparator {

    private String container;
    private EntityType type;
    private String name;
    private LocationInfo location;

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public EntityType getType() {
        return type;
    }

    public void setType(EntityType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocationInfo getLocationInfo() {
        return location;
    }

    public void setLocationInfo(LocationInfo location) {
        this.location = location;
    }

    public void setType(String type) {
        switch (type) {
            case "Compilation Unit":
                this.type = EntityType.COMPILATION_UNIT;
                break;
            case "Class":
                this.type = EntityType.CLASS;
                break;
            case "Interface":
                this.type = EntityType.INTERFACE;
                break;
            case "Enum":
                this.type = EntityType.ENUM;
                break;
            case "Record":
                this.type = EntityType.RECORD;
                break;
            case "Annotation Type":
                this.type = EntityType.ANNOTATION_TYPE;
                break;
            case "Initializer":
                this.type = EntityType.INITIALIZER;
                break;
            case "Field":
                this.type = EntityType.FIELD;
                break;
            case "Method":
                this.type = EntityType.METHOD;
                break;
            case "Annotation Member":
                this.type = EntityType.ANNOTATION_MEMBER;
                break;
            case "Enum Constant":
                this.type = EntityType.ENUM_CONSTANT;
                break;
            case "Package":
                this.type = EntityType.PACKAGE;
                break;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityComparator that = (EntityComparator) o;
        return container.equals(that.container) && type == that.type && name.equals(that.name) && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(container, type, name, location);
    }

    @Override
    public String toString() {
        return "EntityComparator{" +
                "container='" + container + '\'' +
                ", label=" + type +
                ", name='" + name + '\'' +
                ", startLine='" + location.getStartLine() + '\'' +
                ", endLine='" + location.getEndLine() + '\'' +
                '}';
    }
}
