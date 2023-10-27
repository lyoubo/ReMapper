package org.remapper.dto;

public enum BlockType {

    IF("If"),
    ELSE("Else"),
    TRY("Try"),
    CATCH("Catch Clause"),
    FINALLY("Finally"),
    FOR("For"),
    ENHANCED("Enhanced For"),
    WHILE("While"),
    DO("Do"),
    CASE("Case"),
    LAMBDA("Lambda"),
    ANONYMOUS("Anonymous"),
    BLOCK("Block"),
    OTHER("Other");

    private final String name;

    BlockType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
