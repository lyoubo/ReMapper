package org.remapper.dto;

public enum BlockType {

    IF_BLOCK("If Block"),
    ELSE_BLOCK("Else Block"),
    TRY_BLOCK("Try Block"),
    CATCH_BLOCK("Catch Clause"),
    FINALLY_BLOCK("Finally Clause"),
    FOR_BLOCK("For Block"),
    ENHANCED_FOR_BLOCK("Enhanced For Block"),
    WHILE_BLOCK("While Block"),
    DO_BLOCK("Do Block"),
    CASE_BLOCK("Case Block"),
    LAMBDA_BLOCK("Lambda Block"),
    ANONYMOUS_CLASS_BlOCK("Anonymous Class Block"),
    BLOCK("Block");

    private final String name;

    BlockType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
