package org.remapper.dto;

import java.util.Objects;

public class StatementInfo {

    private String method;
    private StatementType type;
    private String expression;
    private LocationInfo locationInfo;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public StatementType getType() {
        return type;
    }

    public void setType(StatementType type) {
        this.type = type;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public LocationInfo getLocationInfo() {
        return locationInfo;
    }

    public void setLocationInfo(LocationInfo locationInfo) {
        this.locationInfo = locationInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatementInfo that = (StatementInfo) o;
        return method.equals(that.method) && type == that.type && expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, type, expression);
    }

    @Override
    public String toString() {
        return "StatementInfo{" +
                "container='" + method + '\'' +
                ", type=" + type +
                ", name='" + expression + '\'' +
                ", startLine=" + locationInfo.getStartLine() +
                ", endLine=" + locationInfo.getEndLine() +
                '}';
    }
}
