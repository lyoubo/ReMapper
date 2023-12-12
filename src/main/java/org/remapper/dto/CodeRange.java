package org.remapper.dto;

public class CodeRange {

    private String filePath;
    private int startLine;
    private int endLine;
    private int startColumn;
    private int endColumn;
    private LocationInfo.CodeElementType codeElementType;
    private String description;
    private String codeElement;

    public CodeRange(String filePath, int startLine, int endLine, int startColumn, int endColumn, LocationInfo.CodeElementType codeElementType) {
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
        this.codeElementType = codeElementType;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public int getStartLine() {
        return this.startLine;
    }

    public int getEndLine() {
        return this.endLine;
    }

    public int getStartColumn() {
        return this.startColumn;
    }

    public int getEndColumn() {
        return this.endColumn;
    }

    public String getDescription() {
        return this.description;
    }

    public CodeRange setDescription(String description) {
        this.description = description;
        return this;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append("\n");
        this.encodeStringProperty(sb, "filePath", this.filePath, false);
        this.encodeIntProperty(sb, "startLine", this.startLine, false);
        this.encodeIntProperty(sb, "endLine", this.endLine, false);
        this.encodeIntProperty(sb, "startColumn", this.startColumn, false);
        this.encodeIntProperty(sb, "endColumn", this.endColumn, false);
        encodeStringProperty(sb, "codeElementType", codeElementType.name(), false);
        this.encodeStringProperty(sb, "description", this.description, false);
        encodeStringProperty(sb, "codeElement", codeElement, true);
        sb.append("}");
        return sb.toString();
    }

    private void encodeStringProperty(StringBuilder sb, String propertyName, String value, boolean last) {
        if (value != null) {
            sb.append("\t").append("\t").append("\"" + propertyName + "\": \"" + value + "\"");
        } else {
            sb.append("\t").append("\t").append("\"" + propertyName + "\": " + value);
        }

        this.insertNewLine(sb, last);
    }

    private void encodeIntProperty(StringBuilder sb, String propertyName, int value, boolean last) {
        sb.append("\t").append("\t").append("\"" + propertyName + "\": " + value);
        this.insertNewLine(sb, last);
    }

    private void insertNewLine(StringBuilder sb, boolean last) {
        if (last) {
            sb.append("\n");
        } else {
            sb.append(",").append("\n");
        }
    }


    public LocationInfo.CodeElementType getCodeElementType() {
        return codeElementType;
    }

    public String getCodeElement() {
        return codeElement;
    }

    public CodeRange setCodeElement(String codeElement) {
        this.codeElement = codeElement;
        return this;
    }
}
