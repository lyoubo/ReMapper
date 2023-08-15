package org.remapper.dto;

import java.util.Objects;

public class EntityInfo {

    private String container;
    private EntityType type;
    private String name;
    private String params;
    private String filePath;  // resolve the issue of multiple modules with the same class name in the project
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

    public String getParams() {
        return params;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public LocationInfo getLocationInfo() {
        return location;
    }

    public void setLocationInfo(LocationInfo locationInfo) {
        this.location = locationInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityInfo that = (EntityInfo) o;
        return container.equals(that.container) && type == that.type && name.equals(that.name) && Objects.equals(params, that.params) && Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(container, type, name, params, filePath);
    }

    @Override
    public String toString() {
        return "EntityInfo{" +
                "container='" + container + '\'' +
                ", label=" + type +
                ", name='" + name + '\'' +
                ", startLine=" + location.getStartLine() +
                ", endLine=" + location.getEndLine() +
                '}';
    }
}
