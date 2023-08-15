package org.remapper.dto;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.Objects;

public class LocationInfo {

    private String filePath;
    private int startOffset;
    private int endOffset;
    private int length;
    private int startLine;
    private int startColumn;
    private int endLine;
    private int endColumn;

    public LocationInfo() {
    }

    public LocationInfo(CompilationUnit cu, String filePath, ASTNode node) {
        this.filePath = filePath;
        this.startOffset = node.getStartPosition();
        this.length = node.getLength();
        this.endOffset = startOffset + length;

        //lines are 1-based
        this.startLine = cu.getLineNumber(startOffset);
        this.endLine = cu.getLineNumber(endOffset);
        if (this.endLine == -1) {
            this.endLine = cu.getLineNumber(endOffset - 1);
        }
        //columns are 0-based
        this.startColumn = cu.getColumnNumber(startOffset);
        //convert to 1-based
        if (this.startColumn > 0) {
            this.startColumn += 1;
        }
        this.endColumn = cu.getColumnNumber(endOffset);
        if (this.endColumn == -1) {
            this.endColumn = cu.getColumnNumber(endOffset - 1);
        }
        //convert to 1-based
        if (this.endColumn > 0) {
            this.endColumn += 1;
        }
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public void setStartColumn(int startColumn) {
        this.startColumn = startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationInfo that = (LocationInfo) o;
        return startLine == that.startLine && endLine == that.endLine && filePath.equals(that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, startLine, endLine);
    }

    public String toString() {
        return "line range:" + startLine + "-" + endLine;
    }
}
