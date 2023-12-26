package org.remapper.dto;

public enum StatementType {

    METHOD_DECLARATION("Method Declaration"),
    ASSERT_STATEMENT("Assert Statement"),
    BLOCK("Block"),
    BREAK_STATEMENT("Break Statement"),
    CONSTRUCTOR_INVOCATION("Constructor Invocation"),
    CONTINUE_STATEMENT("Continue Statement"),
    DO_STATEMENT("Do Statement"),
    EMPTY_STATEMENT("Empty Statement"),
    ENHANCED_FOR_STATEMENT("Enhanced For Statement"),
    EXPRESSION_STATEMENT("Expression Statement"),
    FOR_STATEMENT("For Statement"),
    IF_STATEMENT("If Statement"),
    LABELED_STATEMENT("Labeled Statement"),
    RETURN_STATEMENT("Return Statement"),
    SUPER_CONSTRUCTOR_INVOCATION("Super Constructor Invocation"),
    SWITCH_CASE("Switch Case"),
    SWITCH_STATEMENT("Switch Statement"),
    SYNCHRONIZED_STATEMENT("Synchronized Statement"),
    THROW_STATEMENT("Throw Statement"),
    TRY_STATEMENT("Try Statement"),
    TYPE_DECLARATION_STATEMENT("Type Declaration Statement"),
    VARIABLE_DECLARATION_STATEMENT("Variable Declaration Statement"),
    WHILE_STATEMENT("While Statement"),
    CATCH_CLAUSE("Catch Clause"),
    YIELD_STATEMENT("Yield Statement"),
    LAMBDA_EXPRESSION_BODY("Lambda Expression Body");

    private final String name;

    StatementType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
