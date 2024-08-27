package org.remapper.service;

import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import org.remapper.dto.*;
import org.remapper.util.DiceFunction;
import org.remapper.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class MethodStatementMatcherService {

    public void matchStatements(MethodNode methodBefore, MethodNode methodAfter, MatchPair originalPair,
                                List<Pair<String, String>> replacementsBefore, List<Pair<String, String>> replacementsCurrent) {
        MatchPair matchPair = new MatchPair();
        matchControls(matchPair, methodBefore, methodAfter);
        matchBlocks(matchPair, methodBefore, methodAfter);
        matchOperations(originalPair, matchPair, methodBefore, methodAfter);
        Map<EntityType, List<Pair<String, String>>> entityReplacements = getReplacements(originalPair);
        matchByVariableReplacement(matchPair, methodBefore, methodAfter, originalPair, replacementsBefore, replacementsCurrent, entityReplacements);
        iterativeMatching(originalPair, matchPair, methodBefore, methodAfter);

        List<StatementNodeTree> unmatchedStatementsBefore = methodBefore.getUnmatchedStatements();
        for (StatementNodeTree unmatchedStatement : unmatchedStatementsBefore) {
            if (unmatchedStatement.isRefactored())
                continue;
            matchPair.addDeletedStatement(unmatchedStatement);
        }
        List<StatementNodeTree> duplicatedStatementsBefore = methodBefore.getDuplicatedStatements();
        Set<StatementNodeTree> matchedStatementsLeft = matchPair.getMatchedStatementsLeft();
        for (StatementNodeTree duplicatedStatement : duplicatedStatementsBefore) {
            if (!matchedStatementsLeft.contains(duplicatedStatement))
                matchPair.addDeletedStatement(duplicatedStatement);
        }
        List<StatementNodeTree> duplicatedStatementsAfter = methodAfter.getDuplicatedStatements();
        Set<StatementNodeTree> matchedStatementsRight = matchPair.getMatchedStatementsRight();
        for (StatementNodeTree duplicatedStatement : duplicatedStatementsAfter) {
            if (!matchedStatementsRight.contains(duplicatedStatement))
                matchPair.addAddedStatement(duplicatedStatement);
        }
        List<StatementNodeTree> unmatchedStatementsAfter = methodAfter.getUnmatchedStatements();
        for (StatementNodeTree unmatchedStatement : unmatchedStatementsAfter) {
            if (unmatchedStatement.isRefactored())
                continue;
            matchPair.addAddedStatement(unmatchedStatement);
        }

        additionalMatchByDice(originalPair, matchPair);
        additionalMatchByChildren(originalPair, matchPair);
        additionalMatchByContext(originalPair, matchPair, entityReplacements, replacementsBefore, replacementsCurrent);
        additionalMatchByReturn(matchPair);

        Set<Pair<StatementNodeTree, StatementNodeTree>> matchedStatements = matchPair.getMatchedStatements();
        for (Pair<StatementNodeTree, StatementNodeTree> matchedStatement : matchedStatements) {
            originalPair.addMatchedStatement(matchedStatement.getLeft(), matchedStatement.getRight());
            matchedStatement.getLeft().setMatched();
            matchedStatement.getRight().setMatched();
        }
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        for (StatementNodeTree deletedStatement : deletedStatements) {
            originalPair.addDeletedStatement(deletedStatement);
        }
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        for (StatementNodeTree addedStatement : addedStatements) {
            originalPair.addAddedStatement(addedStatement);
        }
    }

    private Map<EntityType, List<Pair<String, String>>> getReplacements(MatchPair originalPair) {
        Map<EntityType, List<Pair<String, String>>> replacements = new LinkedHashMap<>();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = originalPair.getMatchedEntities();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
            DeclarationNodeTree dntBefore = pair.getLeft();
            DeclarationNodeTree dntCurrent = pair.getRight();
            if (dntBefore.getType() == EntityType.CLASS && dntCurrent.getType() == EntityType.CLASS) {
                if (!dntBefore.getName().equals(dntCurrent.getName())) {
                    if (replacements.containsKey(EntityType.CLASS)) {
                        List<Pair<String, String>> list = replacements.get(EntityType.CLASS);
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                    } else {
                        List<Pair<String, String>> list = new ArrayList<>();
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                        replacements.put(EntityType.CLASS, list);
                    }
                }
            } else if (dntBefore.getType() == EntityType.INTERFACE && dntCurrent.getType() == EntityType.INTERFACE) {
                if (!dntBefore.getName().equals(dntCurrent.getName())) {
                    if (replacements.containsKey(EntityType.INTERFACE)) {
                        List<Pair<String, String>> list = replacements.get(EntityType.INTERFACE);
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                    } else {
                        List<Pair<String, String>> list = new ArrayList<>();
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                        replacements.put(EntityType.INTERFACE, list);
                    }
                }
            } else if (dntBefore.getType() == EntityType.ENUM && dntCurrent.getType() == EntityType.ENUM) {
                if (!dntBefore.getName().equals(dntCurrent.getName())) {
                    if (replacements.containsKey(EntityType.ENUM)) {
                        List<Pair<String, String>> list = replacements.get(EntityType.ENUM);
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                    } else {
                        List<Pair<String, String>> list = new ArrayList<>();
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                        replacements.put(EntityType.ENUM, list);
                    }
                }
            } else if (dntBefore.getType() == EntityType.FIELD && dntCurrent.getType() == EntityType.FIELD) {
                if (!dntBefore.getName().equals(dntCurrent.getName())) {
                    if (replacements.containsKey(EntityType.FIELD)) {
                        List<Pair<String, String>> list = replacements.get(EntityType.FIELD);
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                    } else {
                        List<Pair<String, String>> list = new ArrayList<>();
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                        replacements.put(EntityType.FIELD, list);
                    }
                }
            } else if (dntBefore.getType() == EntityType.METHOD && dntCurrent.getType() == EntityType.METHOD) {
                if (!dntBefore.getName().equals(dntCurrent.getName())) {
                    if (replacements.containsKey(EntityType.METHOD)) {
                        List<Pair<String, String>> list = replacements.get(EntityType.METHOD);
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                    } else {
                        List<Pair<String, String>> list = new ArrayList<>();
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                        replacements.put(EntityType.METHOD, list);
                    }
                }
            } else if (dntBefore.getType() == EntityType.ENUM_CONSTANT && dntCurrent.getType() == EntityType.ENUM_CONSTANT) {
                if (!dntBefore.getName().equals(dntCurrent.getName())) {
                    if (replacements.containsKey(EntityType.ENUM_CONSTANT)) {
                        List<Pair<String, String>> list = replacements.get(EntityType.ENUM_CONSTANT);
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                    } else {
                        List<Pair<String, String>> list = new ArrayList<>();
                        Pair<String, String> replacement = Pair.of(dntBefore.getName(), dntCurrent.getName());
                        list.add(replacement);
                        replacements.put(EntityType.ENUM_CONSTANT, list);
                    }
                }
            }
        }
        return replacements;
    }

    private void matchControls(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter) {
        List<StatementNodeTree> allControlsBefore = methodBefore.getAllControls();
        List<StatementNodeTree> allControlsAfter = methodAfter.getAllControls();
        /**
         * 1. n1.type = n2.type ^ node1.expression = node2.expression ^ n1.depth = n2.depth
         */
        Map<StatementNodeTree, StatementNodeTree> temp1 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allControlsBefore) {
            for (StatementNodeTree node2 : allControlsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() && node1.getType() == node2.getType() &&
                        node1.getExpression().equals(node2.getExpression()) && node1.getDepth() == node2.getDepth()) {
                    processControlMap(temp1, node1, node2);
                }
            }
        }
        controlMap2SetOfMatchedStatements(matchPair, temp1);
        /**
         * 2. n1.type = n2.type ^ node1.expression = node2.expression
         */
        Map<StatementNodeTree, StatementNodeTree> temp2 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allControlsBefore) {
            for (StatementNodeTree node2 : allControlsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() && node1.getType() == node2.getType() &&
                        node1.getExpression().equals(node2.getExpression())) {
                    processControlMap(temp2, node1, node2);
                }
            }
        }
        controlMap2SetOfMatchedStatements(matchPair, temp2);
    }

    private void processControlMap(Map<StatementNodeTree, StatementNodeTree> temp, StatementNodeTree node1, StatementNodeTree node2) {
        if (temp.containsKey(node1) || temp.containsValue(node2)) {
            StatementNodeTree candidateValue = temp.get(node1);
            StatementNodeTree candidateKey = getKeyByValue(temp, node2);
            double previousValue = calculateTextSimilarity(node1, candidateValue);
            double previousKey = calculateTextSimilarity(candidateKey, node2);
            double current = calculateTextSimilarity(node1, node2);
            if (current > previousValue && current > previousKey) {
                temp.remove(node1, candidateValue);
                temp.remove(candidateKey, node2);
                temp.put(node1, node2);
            }
        } else
            temp.put(node1, node2);
    }

    private double calculateTextSimilarity(StatementNodeTree node1, StatementNodeTree node2) {
        double sim = 0.0;
        if (node1 == null || node2 == null) return sim;
        NormalizedLevenshtein nl = new NormalizedLevenshtein();
        sim += (1 - nl.distance(node1.getStatement().toString(), node2.getStatement().toString()));
        return sim;
    }

    private void controlMap2SetOfMatchedStatements(MatchPair matchPair, Map<StatementNodeTree, StatementNodeTree> temp) {
        for (StatementNodeTree control1 : temp.keySet()) {
            StatementNodeTree control2 = temp.get(control1);
            if (!control1.isMatchedOver() && !control2.isMatchedOver()) {
                matchPair.addMatchedStatement(control1, control2);
                control1.setMatched();
                control2.setMatched();
                if (control1.hasChildren() && control2.hasChildren())
                    matchSubStatements(matchPair, control1.getChildren(), control2.getChildren());
            }
        }
    }

    private void matchSubStatements(MatchPair matchPair, List<StatementNodeTree> childrenBefore, List<StatementNodeTree> childrenAfter) {
        for (StatementNodeTree node1 : childrenBefore) {
            for (StatementNodeTree node2 : childrenAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() &&
                        StringUtils.equals(node1.getStatement().toString(), node2.getStatement().toString())) {
                    matchPair.addMatchedStatement(node1, node2);
                    node1.setMatched();
                    node2.setMatched();
                    if (node1.hasChildren() && node2.hasChildren()) {
                        matchSubStatements(matchPair, node1.getChildren(), node2.getChildren());
                    }
                    break;
                }
            }
        }
    }

    private void matchBlocks(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter) {
        List<StatementNodeTree> allBlocksBefore = methodBefore.getAllBlocks();
        List<StatementNodeTree> allBlocksAfter = methodAfter.getAllBlocks();
        /**
         * 1. n1.text = n2.text ^ n1.depth = n2.depth
         */
        Map<StatementNodeTree, StatementNodeTree> temp1 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allBlocksBefore) {
            for (StatementNodeTree node2 : allBlocksAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() && StringUtils.equals(node1.getStatement().toString(), node2.getStatement().toString()) &&
                        node1.getDepth() == node2.getDepth()) {
                    processBlockMap(temp1, node1, node2);
                }
            }
        }
        blockMap2SetOfMatchedStatements(matchPair, temp1);
        /**
         * 2. n1.text = n2.text
         */
        Map<StatementNodeTree, StatementNodeTree> temp2 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allBlocksBefore) {
            for (StatementNodeTree node2 : allBlocksAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() && StringUtils.equals(node1.getStatement().toString(), node2.getStatement().toString())) {
                    processBlockMap(temp2, node1, node2);
                }
            }
        }
        blockMap2SetOfMatchedStatements(matchPair, temp2);
    }

    private void processBlockMap(Map<StatementNodeTree, StatementNodeTree> temp, StatementNodeTree node1, StatementNodeTree node2) {
        if (temp.containsKey(node1) || temp.containsValue(node2)) {
            StatementNodeTree candidateValue = temp.get(node1);
            StatementNodeTree candidateKey = getKeyByValue(temp, node2);
            double previousValue = calculateWeight(node1, candidateValue);
            double previousKey = calculateWeight(candidateKey, node2);
            double current = calculateWeight(node1, node2);
            if (current > previousValue && current > previousKey) {
                temp.remove(node1, candidateValue);
                temp.remove(candidateKey, node2);
                temp.put(node1, node2);
            }
        } else
            temp.put(node1, node2);
    }

    private double calculateWeight(StatementNodeTree node1, StatementNodeTree node2) {
        double weight = 0.0;
        if (node1 == null || node2 == null) return weight;
        NormalizedLevenshtein nl = new NormalizedLevenshtein();
        if (node1.getBlockType() == node2.getBlockType())
            weight += 1.0;
        if (node1.getBlockType() == BlockType.IF_BLOCK && node2.getBlockType() == BlockType.ELSE_BLOCK)
            weight += 1.0;
        if (node1.getBlockType() == BlockType.ELSE_BLOCK && node2.getBlockType() == BlockType.IF_BLOCK)
            weight += 1.0;
        weight += (1 - nl.distance(node1.getBlockExpression(), node2.getBlockExpression()));
        return weight;
    }

    private void blockMap2SetOfMatchedStatements(MatchPair matchPair, Map<StatementNodeTree, StatementNodeTree> temp) {
        for (StatementNodeTree node1 : temp.keySet()) {
            StatementNodeTree node2 = temp.get(node1);
            if (!node1.isMatchedOver() && !node2.isMatchedOver()) {
                matchPair.addMatchedStatement(node1, node2);
                node1.setMatched();
                node2.setMatched();
                setChildrenMatched(matchPair, node1.getChildren(), node2.getChildren());
            }
        }
    }

    private void setChildrenMatched(MatchPair matchPair, List<StatementNodeTree> children1, List<StatementNodeTree> children2) {
        if (children1.size() == children2.size()) {
            for (int i = 0; i < children1.size(); i++) {
                StatementNodeTree child1 = children1.get(i);
                StatementNodeTree child2 = children2.get(i);
                matchPair.addMatchedStatement(child1, child2);
                child1.setMatched();
                child2.setMatched();
                if (child1.hasChildren() && child2.hasChildren()) {
                    setChildrenMatched(matchPair, child1.getChildren(), child2.getChildren());
                }
            }
        } else {
            matchSubStatements(matchPair, children1, children2);
        }
    }

    private void matchOperations(MatchPair originalPair, MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter) {
        List<StatementNodeTree> allOperationsBefore = methodBefore.getAllOperations();
        List<StatementNodeTree> allOperationsAfter = methodAfter.getAllOperations();
        /**
         * 1. n1.text = n2.text ^ n1.depth = n2.depth
         */
        Map<StatementNodeTree, StatementNodeTree> temp1 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allOperationsBefore) {
            for (StatementNodeTree node2 : allOperationsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() && isTheSameExpression(node1, node2) &&
                        node1.getDepth() == node2.getDepth()) {
                    processOperationMap(originalPair, matchPair, temp1, node1, node2);
                }
            }
        }
        stmtMap2SetOfMatchedStatements(matchPair, temp1);
        /**
         * 2. n1.text = n2.text
         */
        Map<StatementNodeTree, StatementNodeTree> temp2 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allOperationsBefore) {
            for (StatementNodeTree node2 : allOperationsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() && isTheSameExpression(node1, node2)) {
                    processOperationMap(originalPair, matchPair, temp2, node1, node2);
                }
            }
        }
        stmtMap2SetOfMatchedStatements(matchPair, temp2);
        /**
         * 3. n1.variableDeclaration = n2.variableDeclaration
         */
        Map<StatementNodeTree, StatementNodeTree> temp3 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allOperationsBefore) {
            for (StatementNodeTree node2 : allOperationsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() && node1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT &&
                        node2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                    VariableDeclarationStatement variableDeclaration1 = (VariableDeclarationStatement) node1.getStatement();
                    VariableDeclarationStatement variableDeclaration2 = (VariableDeclarationStatement) node2.getStatement();
                    if (StringUtils.type2String(variableDeclaration1.getType()).equals(StringUtils.type2String(variableDeclaration2.getType()))) {
                        VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) variableDeclaration1.fragments().get(0);
                        VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) variableDeclaration2.fragments().get(0);
                        if (fragment1.getName().getIdentifier().equals(fragment2.getName().getIdentifier()))
                            processOperationMap(originalPair, matchPair, temp3, node1, node2);
                    }
                }
            }
        }
        stmtMap2SetOfMatchedStatements(matchPair, temp3);
    }

    private boolean isTheSameExpression(StatementNodeTree node1, StatementNodeTree node2) {
        if (node1.getType() == StatementType.EXPRESSION_STATEMENT && node2.getType() == StatementType.LAMBDA_EXPRESSION_BODY) {
            String expression1 = node1.getExpression();
            String expression2 = node2.getExpression();
            return StringUtils.equals(expression1, expression2) || StringUtils.equals(expression1, expression2 + ";\n");
        }
        if (node1.getType() == StatementType.LAMBDA_EXPRESSION_BODY && node2.getType() == StatementType.EXPRESSION_STATEMENT) {
            String expression1 = node1.getExpression();
            String expression2 = node2.getExpression();
            return StringUtils.equals(expression1, expression2) || StringUtils.equals(expression1 + ";\n", expression2);
        }
        return false;
    }

    private void stmtMap2SetOfMatchedStatements(MatchPair matchPair, Map<StatementNodeTree, StatementNodeTree> temp) {
        for (StatementNodeTree node1 : temp.keySet()) {
            StatementNodeTree node2 = temp.get(node1);
            if (!node1.isMatchedOver() && !node2.isMatchedOver()) {
                matchPair.addMatchedStatement(node1, node2);
                node1.setMatched();
                node2.setMatched();
            }
        }
    }

    private void matchByVariableReplacement(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter, MatchPair originalPair,
                                            List<Pair<String, String>> replacementsBefore, List<Pair<String, String>> replacementsCurrent,
                                            Map<EntityType, List<Pair<String, String>>> entityReplacements) {
        List<StatementNodeTree> allControlsBefore = methodBefore.getAllControls();
        List<StatementNodeTree> allControlsAfter = methodAfter.getAllControls();
        List<StatementNodeTree> allOperationsBefore = methodBefore.getAllOperations();
        List<StatementNodeTree> allOperationsAfter = methodAfter.getAllOperations();
        List<StatementNodeTree> allVariablesBefore = allOperationsBefore.stream().filter(
                node -> node.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT && !node.isMatched()).collect(Collectors.toList());
        List<StatementNodeTree> allVariablesAfter = allOperationsAfter.stream().filter(
                node -> node.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT && !node.isMatched()).collect(Collectors.toList());
        Map<StatementNodeTree, StatementNodeTree> temp1 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allControlsBefore) {
            for (StatementNodeTree node2 : allControlsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() &&
                        equalsWithReplacements(matchPair, methodBefore, methodAfter, node1, node2, entityReplacements,
                                replacementsBefore, replacementsCurrent, allVariablesBefore, allVariablesAfter)) {
                    processControlMap(temp1, node1, node2);
                    List<StatementNodeTree> children1 = node1.getChildren();
                    List<StatementNodeTree> children2 = node2.getChildren();
                    if (children1.size() == 1 && children2.size() == 1) {
                        StatementNodeTree child1 = children1.get(0);
                        StatementNodeTree child2 = children2.get(0);
                        if (child1 instanceof BlockNode && child2 instanceof BlockNode)
                            processBlockMap(temp1, child1, child2);
                    }
                }
            }
        }
        stmtMap2SetOfMatchedStatements(matchPair, temp1);
        Map<StatementNodeTree, StatementNodeTree> temp2 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allOperationsBefore) {
            for (StatementNodeTree node2 : allOperationsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver() &&
                        equalsWithReplacements(matchPair, methodBefore, methodAfter, node1, node2, entityReplacements,
                                replacementsBefore, replacementsCurrent, allVariablesBefore, allVariablesAfter))
                    processOperationMap(originalPair, matchPair, temp2, node1, node2);
            }
        }
        stmtMap2SetOfMatchedStatements(matchPair, temp2);
    }

    private boolean equalsWithReplacements(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter,
                                           StatementNodeTree node1, StatementNodeTree node2,
                                           Map<EntityType, List<Pair<String, String>>> entityReplacements,
                                           List<Pair<String, String>> replacementsBefore, List<Pair<String, String>> replacementsCurrent,
                                           List<StatementNodeTree> allVariablesBefore, List<StatementNodeTree> allVariablesAfter) {
        String statement1 = node1.getExpression();
        String statement2 = node2.getExpression();
        if (node1.getType() == StatementType.RETURN_STATEMENT && node2.getType() != StatementType.RETURN_STATEMENT &&
                statement1.startsWith("return ")) {
            MethodNode root = node1.getRoot();
            if (root != methodBefore)
                statement1 = statement1.substring("return ".length());
        }
        if (node2.getType() == StatementType.RETURN_STATEMENT && node1.getType() != StatementType.RETURN_STATEMENT &&
                statement2.startsWith("return ")) {
            MethodNode root = node2.getRoot();
            if (root != methodAfter)
                statement2 = statement2.substring("return ".length());
        }
        List<String> astNodes1 = new ArrayList<>();
        List<String> astNodes2 = new ArrayList<>();
        node1.getStatement().accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                astNodes1.add(node.getIdentifier());
                return true;
            }
        });
        node2.getStatement().accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                astNodes2.add(node.getIdentifier());
                return true;
            }
        });
        List<Pair<String, String>> parameterReplacements = new ArrayList<>();
        if (!methodBefore.getExpression().equals("initializer") && !methodAfter.getExpression().equals("initializer")) {
            MethodDeclaration declaration1 = (MethodDeclaration) methodBefore.getMethodEntity().getDeclaration();
            MethodDeclaration declaration2 = (MethodDeclaration) methodAfter.getMethodEntity().getDeclaration();
            List<SingleVariableDeclaration> parameters1 = declaration1.parameters();
            List<SingleVariableDeclaration> parameters2 = declaration2.parameters();
            if (parameters1.size() == parameters2.size()) {
                for (int i = 0; i < parameters1.size(); i++) {
                    SingleVariableDeclaration parameter1 = parameters1.get(i);
                    SingleVariableDeclaration parameter2 = parameters2.get(i);
                    Type type1 = parameter1.getType();
                    Type type2 = parameter2.getType();
                    String name1 = parameter1.getName().getIdentifier();
                    String name2 = parameter2.getName().getIdentifier();
                    if (StringUtils.equals(type1.toString(), type2.toString()) &&
                            !StringUtils.equals(name1, name2)) {
                        parameterReplacements.add(Pair.of(name1, name2));
                    }
                }
            }
        }
        return equalsWithReplacements(matchPair, statement1, statement2, astNodes1, astNodes2, entityReplacements, parameterReplacements,
                replacementsBefore, replacementsCurrent, allVariablesBefore, allVariablesAfter);
    }

    private boolean equalsWithReplacements(MatchPair matchPair, String statement1, String statement2,
                                           List<String> astNodes1, List<String> astNodes2,
                                           Map<EntityType, List<Pair<String, String>>> entityReplacements,
                                           List<Pair<String, String>> parameterReplacements,
                                           List<Pair<String, String>> replacementsBefore, List<Pair<String, String>> replacementsCurrent,
                                           List<StatementNodeTree> allVariablesBefore, List<StatementNodeTree> allVariablesAfter) {
        String replaced1 = statement1;
        String replaced2 = statement2;
        List<Pair<String, String>> list = new ArrayList<>();
        if (entityReplacements.get(EntityType.CLASS) != null)
            list.addAll(entityReplacements.get(EntityType.CLASS));
        if (entityReplacements.get(EntityType.INTERFACE) != null)
            list.addAll(entityReplacements.get(EntityType.INTERFACE));
        if (entityReplacements.get(EntityType.ENUM) != null)
            list.addAll(entityReplacements.get(EntityType.ENUM));
        if (entityReplacements.get(EntityType.FIELD) != null)
            list.addAll(entityReplacements.get(EntityType.FIELD));
        if (entityReplacements.get(EntityType.METHOD) != null)
            list.addAll(entityReplacements.get(EntityType.METHOD));
        if (entityReplacements.get(EntityType.ENUM_CONSTANT) != null)
            list.addAll(entityReplacements.get(EntityType.ENUM_CONSTANT));
        NormalizedLevenshtein nl = new NormalizedLevenshtein();
        double distance = nl.distance(statement1, statement2);
        for (Pair<String, String> pair : list) {
            String oldEntityName = pair.getLeft();
            String newEntityName = pair.getRight();
            if (astNodes1.contains(oldEntityName)) {
                String replaced = replaced1.replace(oldEntityName, newEntityName);
                double compared = nl.distance(replaced, replaced2);
                if (compared == 0.0)
                    return true;
                if (compared < distance) {
                    replaced1 = replaced;
                    distance = compared;
                }
            }
        }
        for (Pair<String, String> pair : replacementsBefore) {
            String name = pair.getLeft();
            String initializer = pair.getRight();
            if (astNodes1.contains(name)) {
                if (replaced1.equals("this." + name + "=" + name + ";\n")) {
                    String replaced = "this." + name + "=" + initializer + ";\n";
                    double compared = nl.distance(replaced, replaced2);
                    if (compared == 0.0)
                        return true;
                    if (compared < distance) {
                        replaced1 = replaced;
                        distance = compared;
                    }
                } else {
                    String replaced = replaceLast(replaced1, name, initializer);
                    double compared = nl.distance(replaced, replaced2);
                    if (compared == 0.0)
                        return true;
                    if (compared < distance) {
                        replaced1 = replaced;
                        distance = compared;
                    }
                }
            }
        }
        for (StatementNodeTree variable : allVariablesBefore) {
            VariableDeclarationStatement statement = (VariableDeclarationStatement) variable.getStatement();
            List<VariableDeclarationFragment> fragments = statement.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                if (fragment.getInitializer() != null) {
                    String name = fragment.getName().getIdentifier();
                    String initializer = fragment.getInitializer().toString();
                    if (replaced1.contains(name + "="))
                        continue;
                    if (astNodes1.contains(name)) {
                        String replaced = replaced1.replace(name, initializer);
                        double compared = nl.distance(replaced, replaced2);
                        if (compared == 0.0) {
                            variable.setRefactored();
                            if (!variable.isMatched())
                                matchPair.addDeletedStatement(variable);
                            return true;
                        }
                        if (compared < distance) {
                            replaced1 = replaced;
                            distance = compared;
                        }
                    }
                }
            }
        }
        for (Pair<String, String> pair : replacementsCurrent) {
            String name = pair.getLeft();
            String initializer = pair.getRight();
            if (astNodes2.contains(name)) {
                if (replaced2.equals("this." + name + "=" + name + ";\n")) {
                    String replaced = "this." + name + "=" + initializer + ";\n";
                    double compared = nl.distance(replaced1, replaced);
                    if (compared == 0.0)
                        return true;
                    if (compared < distance) {
                        replaced2 = replaced;
                        distance = compared;
                    }
                } else {
                    String replaced = replaceLast(replaced2, name, initializer);
                    double compared = nl.distance(replaced1, replaced);
                    if (compared == 0.0)
                        return true;
                    if (compared < distance) {
                        replaced2 = replaced;
                        distance = compared;
                    }
                }
            }
        }
        for (StatementNodeTree variable : allVariablesAfter) {
            VariableDeclarationStatement statement = (VariableDeclarationStatement) variable.getStatement();
            List<VariableDeclarationFragment> fragments = statement.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                if (fragment.getInitializer() != null) {
                    String name = fragment.getName().getIdentifier();
                    String initializer = fragment.getInitializer().toString();
                    if (replaced2.contains(name + "="))
                        continue;
                    if (astNodes2.contains(name)) {
                        String replaced = replaced2.replace(name, initializer);
                        double compared = nl.distance(replaced1, replaced);
                        if (compared == 0.0) {
                            variable.setRefactored();
                            if (!variable.isMatched())
                                matchPair.addAddedStatement(variable);
                            return true;
                        }
                        if (compared < distance) {
                            replaced2 = replaced;
                            distance = compared;
                        }
                    }
                }
            }
        }
        for (Pair<String, String> pair : parameterReplacements) {
            String oldParameter = pair.getLeft();
            String newParameter = pair.getRight();
            if (astNodes1.contains(oldParameter)) {
                String replaced = replaced1.replace(oldParameter, newParameter);
                double compared = nl.distance(replaced, replaced2);
                if (compared == 0.0)
                    return true;
                if (compared < distance) {
                    replaced1 = replaced;
                    distance = compared;
                }
            }
        }
        for (StatementNodeTree variable1 : allVariablesBefore) {
            if (variable1.isRefactored() || variable1.isMatched())
                continue;
            for (StatementNodeTree variable2 : allVariablesAfter) {
                if (variable2.isRefactored() || variable2.isMatched())
                    continue;
                VariableDeclarationStatement variableDeclaration1 = (VariableDeclarationStatement) variable1.getStatement();
                VariableDeclarationStatement variableDeclaration2 = (VariableDeclarationStatement) variable2.getStatement();
                List<VariableDeclarationFragment> fragments1 = variableDeclaration1.fragments();
                List<VariableDeclarationFragment> fragments2 = variableDeclaration2.fragments();
                for (VariableDeclarationFragment fragment1 : fragments1) {
                    for (VariableDeclarationFragment fragment2 : fragments2) {
                        String name1 = fragment1.getName().getIdentifier();
                        String name2 = fragment2.getName().getIdentifier();
                        Expression initializer1 = fragment1.getInitializer();
                        Expression initializer2 = fragment2.getInitializer();
                        if (initializer1 == null || initializer2 == null)
                            continue;
                        if (!initializer1.toString().equals(initializer2.toString()))
                            continue;
                        if (!astNodes1.contains(name1) || !astNodes2.contains(name2))
                            continue;
                        if (StringUtils.equals(replaced1.replace(name1 + ".", initializer1.toString() + "."),
                                replaced2.replace(name2 + ".", initializer2.toString() + "."))) {
                            variable1.setMatched();
                            variable2.setMatched();
                            matchPair.addMatchedStatement(variable1, variable2);
                            return true;
                        }
                    }
                }
            }
        }
        return replaced1.equals(statement2) || statement1.equals(replaced2) || replaced1.equals(replaced2);
    }

    private String replaceLast(String originalString, String searchString, String replacementString) {
        int lastIndex = originalString.lastIndexOf(searchString);
        if (lastIndex != -1) {
            String newString = originalString.substring(0, lastIndex) + replacementString +
                    originalString.substring(lastIndex + searchString.length());
            return newString;
        }
        return originalString;
    }

    private void processOperationMap(MatchPair originalPair, MatchPair matchPair, Map<StatementNodeTree, StatementNodeTree> temp, StatementNodeTree node1, StatementNodeTree node2) {
        if (temp.containsKey(node1) || temp.containsValue(node2)) {
            StatementNodeTree candidateValue = temp.get(node1);
            StatementNodeTree candidateKey = getKeyByValue(temp, node2);
            double previousValue = candidateValue == null ? 0.0 : DiceFunction.calculateContextSimilarity(originalPair, matchPair, node1, candidateValue);
            double previousKey = candidateKey == null ? 0.0 : DiceFunction.calculateContextSimilarity(originalPair, matchPair, candidateKey, node2);
            double current = DiceFunction.calculateContextSimilarity(originalPair, matchPair, node1, node2);
            if (current > previousValue && current > previousKey) {
                temp.remove(node1, candidateValue);
                temp.remove(candidateKey, node2);
                temp.put(node1, node2);
            }
        } else
            temp.put(node1, node2);
    }

    private void iterativeMatching(MatchPair originalPair, MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter) {
        Set<Pair<StatementNodeTree, StatementNodeTree>> candidateStatements = matchByDiceCoefficient(originalPair, matchPair, methodBefore, methodAfter);
        matchPair.setCandidateStatements(candidateStatements);
        for (int i = 0; i < 5; i++) {
            Set<Pair<StatementNodeTree, StatementNodeTree>> temp = matchByDiceCoefficient(originalPair, matchPair, methodBefore, methodAfter);
            if (matchPair.getCandidateStatements().size() == temp.size() && matchPair.getCandidateStatements().equals(temp)) {
//                System.out.println("At the " + (i + 1) + "th iteration, the candidate set of statement mapping does not change.");
                break;
            }
            matchPair.setCandidateStatements(temp);
        }
        for (Pair<StatementNodeTree, StatementNodeTree> pair : matchPair.getCandidateStatements()) {
            matchPair.getMatchedStatements().add(pair);
            pair.getLeft().setMatched();
            pair.getRight().setMatched();
        }
        matchPair.getCandidateStatements().clear();
    }

    private Set<Pair<StatementNodeTree, StatementNodeTree>> matchByDiceCoefficient(MatchPair originalPair, MatchPair matchPair,
                                                                                   MethodNode methodBefore, MethodNode methodAfter) {
        Set<Pair<StatementNodeTree, StatementNodeTree>> temp = new HashSet<>();
        List<StatementNodeTree> allOperationsBefore = methodBefore.getAllOperations();
        List<StatementNodeTree> allOperationsAfter = methodAfter.getAllOperations();
        /**
         * 1. sim(n1.text, n2.text) +sim(n1.context, n2.context) > 1.0 ^ n1 instance operation
         */
        Map<StatementNodeTree, StatementNodeTree> temp1 = new HashMap<>();
        for (StatementNodeTree node1 : allOperationsBefore) {
            for (StatementNodeTree node2 : allOperationsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver()) {
                    if (!typeCompatible(originalPair, node1, node2))
                        continue;
                    double sim = DiceFunction.calculateSimilarity(originalPair, matchPair, node1, node2);
                    if (sim < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(originalPair, matchPair, temp1, node1, node2);
                }
            }
        }
        for (StatementNodeTree operation1 : temp1.keySet()) {
            StatementNodeTree operation2 = temp1.get(operation1);
            temp.add(Pair.of(operation1, operation2));
        }
        /**
         * 2. sim(n1.text, n2.text) +sim(n1.context, n2.context) > 1.0 ^ n1 instance block
         */
        Map<StatementNodeTree, StatementNodeTree> temp2 = new HashMap<>();
        List<StatementNodeTree> allBlocksBefore = methodBefore.getAllBlocks();
        List<StatementNodeTree> allBlocksAfter = methodAfter.getAllBlocks();
        for (StatementNodeTree node1 : allBlocksBefore) {
            for (StatementNodeTree node2 : allBlocksAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver()) {
                    if (!typeCompatible(originalPair, node1, node2))
                        continue;
                    if (DiceFunction.calculateSimilarity(originalPair, matchPair, node1, node2) < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(originalPair, matchPair, temp2, node1, node2);
                }
            }
        }
        for (StatementNodeTree block1 : temp2.keySet()) {
            StatementNodeTree block2 = temp2.get(block1);
            temp.add(Pair.of(block1, block2));
        }
        /**
         * 3. sim(n1.text, n2.text) +sim(n1.context, n2.context) > 1.0 ^ n1 instance control
         */
        Map<StatementNodeTree, StatementNodeTree> temp3 = new HashMap<>();
        List<StatementNodeTree> allControlsBefore = methodBefore.getAllControls();
        List<StatementNodeTree> allControlsAfter = methodAfter.getAllControls();
        for (StatementNodeTree node1 : allControlsBefore) {
            for (StatementNodeTree node2 : allControlsAfter) {
                if (!node1.isMatchedOver() && !node2.isMatchedOver()) {
                    if (!typeCompatible(originalPair, node1, node2))
                        continue;
                    if (DiceFunction.calculateSimilarity(originalPair, matchPair, node1, node2) < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(originalPair, matchPair, temp3, node1, node2);
                }
            }
        }
        for (StatementNodeTree control1 : temp3.keySet()) {
            StatementNodeTree control2 = temp3.get(control1);
            temp.add(Pair.of(control1, control2));
        }
        return temp;
    }

    private boolean typeCompatible(MatchPair matchPair, StatementNodeTree node1, StatementNodeTree node2) {
        if (node1 instanceof BlockNode && node2 instanceof BlockNode)
            return node1.getBlockType() == node2.getBlockType() || (node1.getBlockType() == BlockType.IF_BLOCK && node2.getBlockType() == BlockType.ELSE_BLOCK) ||
                    (node1.getBlockType() == BlockType.ELSE_BLOCK && node2.getBlockType() == BlockType.IF_BLOCK);
        else {
            boolean isSameType = node1.getType() == node2.getType();
            boolean isLoopType = (node1.getType() == StatementType.FOR_STATEMENT || node1.getType() == StatementType.ENHANCED_FOR_STATEMENT ||
                    node1.getType() == StatementType.WHILE_STATEMENT || node1.getType() == StatementType.DO_STATEMENT) &&
                    (node2.getType() == StatementType.FOR_STATEMENT || node2.getType() == StatementType.ENHANCED_FOR_STATEMENT ||
                            node2.getType() == StatementType.WHILE_STATEMENT || node2.getType() == StatementType.DO_STATEMENT);
            boolean isLambdaType = (node1.getType() == StatementType.LAMBDA_EXPRESSION_BODY || node1.getType() == StatementType.EXPRESSION_STATEMENT) &&
                    (node2.getType() == StatementType.LAMBDA_EXPRESSION_BODY || node2.getType() == StatementType.EXPRESSION_STATEMENT);
            boolean isExtractedOrInlinedType = false;
            if ((node1.getType() == StatementType.RETURN_STATEMENT && node2.getType() != StatementType.RETURN_STATEMENT) ||
                    (node1.getType() != StatementType.RETURN_STATEMENT && node2.getType() == StatementType.RETURN_STATEMENT)) {
                DeclarationNodeTree entity1 = node1.getRoot().getMethodEntity();
                DeclarationNodeTree entity2 = node2.getRoot().getMethodEntity();
                if (!matchPair.getMatchedEntities().contains(Pair.of(entity1, entity2)))
                    isExtractedOrInlinedType = true;
            }
            boolean isSameExpression = false;
            if (node1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT && node2.getType() == StatementType.EXPRESSION_STATEMENT) {
                VariableDeclarationStatement statement1 = (VariableDeclarationStatement) node1.getStatement();
                ExpressionStatement statement2 = (ExpressionStatement) node2.getStatement();
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) statement1.fragments().get(0);
                if (fragment.getInitializer() != null && statement2.getExpression() != null &&
                        fragment.getInitializer().toString().equals(statement2.getExpression().toString()))
                    isSameExpression = true;
            }
            if (node1.getType() == StatementType.EXPRESSION_STATEMENT && node2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                ExpressionStatement statement1 = (ExpressionStatement) node1.getStatement();
                VariableDeclarationStatement statement2 = (VariableDeclarationStatement) node2.getStatement();
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) statement2.fragments().get(0);
                if (fragment.getInitializer() != null && statement1.getExpression() != null &&
                        fragment.getInitializer().toString().equals(statement1.getExpression().toString()))
                    isSameExpression = true;
            }
            return isSameType || isLoopType || isLambdaType || isExtractedOrInlinedType || isSameExpression;
        }
    }

    private void processStatementMap(MatchPair originalPair, MatchPair matchPair, Map<StatementNodeTree, StatementNodeTree> temp, StatementNodeTree node1, StatementNodeTree node2) {
        if (temp.containsKey(node1) || temp.containsValue(node2)) {
            StatementNodeTree candidateValue = temp.get(node1);
            StatementNodeTree candidateKey = getKeyByValue(temp, node2);
            double previousValue = candidateValue == null ? 0.0 : DiceFunction.calculateSimilarity(originalPair, matchPair, node1, candidateValue);
            double previousKey = candidateKey == null ? 0.0 : DiceFunction.calculateSimilarity(originalPair, matchPair, candidateKey, node2);
            double current = DiceFunction.calculateSimilarity(originalPair, matchPair, node1, node2);
            if (current > previousValue && current > previousKey) {
                temp.remove(node1, candidateValue);
                temp.remove(candidateKey, node2);
                temp.put(node1, node2);
            }
            if (current == previousValue || current == previousKey) {
                NormalizedLevenshtein levenshtein = new NormalizedLevenshtein();
                double distance1 = candidateValue == null ? 1.0 : levenshtein.distance(node1.getExpression(), candidateValue.getExpression());
                double distance2 = candidateKey == null ? 1.0 : levenshtein.distance(candidateKey.getExpression(), node2.getExpression());
                double distance3 = levenshtein.distance(node1.getExpression(), node2.getExpression());
                if (distance3 < distance2 && distance3 < distance1) {
                    temp.remove(node1, candidateValue);
                    temp.remove(candidateKey, node2);
                    temp.put(node1, node2);
                }
            }
        } else
            temp.put(node1, node2);
    }

    public <K, V> K getKeyByValue(Map<K, V> map, V value) {
        return map.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), value))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void additionalMatchByDice(MatchPair originalPair, MatchPair matchPair) {
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        Map<StatementNodeTree, StatementNodeTree> temp = new HashMap<>();
        for (StatementNodeTree node1 : deletedStatements) {
            for (StatementNodeTree node2 : addedStatements) {
                if (typeCompatible(originalPair, node1, node2) && node1 instanceof OperationNode && node2 instanceof OperationNode &&
                        !node1.isRefactored() && !node2.isRefactored()) {
                    double sim = DiceFunction.calculateDiceSimilarity(node1, node2);
                    if (sim < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(originalPair, matchPair, temp, node1, node2);
                }
            }
        }
        replacedMap2SetOfMatchedStatements(matchPair.getMatchedStatements(), deletedStatements, addedStatements, temp);
    }

    private void additionalMatchByChildren(MatchPair originalPair, MatchPair matchPair) {
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        Map<StatementNodeTree, StatementNodeTree> temp = new HashMap<>();
        for (StatementNodeTree node1 : deletedStatements) {
            for (StatementNodeTree node2 : addedStatements) {
                if (typeCompatible(originalPair, node1, node2) && node1 instanceof BlockNode && node2 instanceof BlockNode) {
                    if (DiceFunction.isMatchedGreaterThanAnyOne(matchPair, node1, node2)) {
                        StatementNodeTree parent1 = node1.getParent();
                        StatementNodeTree parent2 = node2.getParent();
                        if (typeCompatible(originalPair, parent1, parent2) && parent1 instanceof ControlNode && parent2 instanceof ControlNode) {
                            processStatementMap(originalPair, matchPair, temp, node1, node2);
                            processStatementMap(originalPair, matchPair, temp, parent1, parent2);
                        }
                    }
                }
            }
        }
        replacedMap2SetOfMatchedStatements(matchPair.getMatchedStatements(), deletedStatements, addedStatements, temp);
    }

    private void replacedMap2SetOfMatchedStatements(Set<Pair<StatementNodeTree, StatementNodeTree>> matchedStatements,
                                                    Set<StatementNodeTree> deletedStatements, Set<StatementNodeTree> addedStatements,
                                                    Map<StatementNodeTree, StatementNodeTree> temp) {
        for (StatementNodeTree node1 : temp.keySet()) {
            StatementNodeTree node2 = temp.get(node1);
            node1.setMatched();
            node2.setMatched();
            matchedStatements.add(Pair.of(node1, node2));
            deletedStatements.remove(node1);
            addedStatements.remove(node2);
        }
    }

    private void additionalMatchByContext(MatchPair originalPair, MatchPair matchPair, Map<EntityType, List<Pair<String, String>>> entityReplacements,
                                          List<Pair<String, String>> replacementsBefore, List<Pair<String, String>> replacementsCurrent) {
        Set<Pair<StatementNodeTree, StatementNodeTree>> matchedStatements = matchPair.getMatchedStatements();
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        Map<StatementNodeTree, StatementNodeTree> temp = new HashMap<>();
        for (StatementNodeTree node1 : deletedStatements) {
            for (StatementNodeTree node2 : addedStatements) {
                if (node1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT &&
                        node2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                    double ctxSim = DiceFunction.calculateContextSimilarity(originalPair, matchPair, node1, node2);
                    if (ctxSim < 0.25)
                        continue;
                    VariableDeclarationStatement variableDeclaration1 = (VariableDeclarationStatement) node1.getStatement();
                    VariableDeclarationStatement variableDeclaration2 = (VariableDeclarationStatement) node2.getStatement();
                    VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) variableDeclaration1.fragments().get(0);
                    VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) variableDeclaration2.fragments().get(0);
                    String variableName1 = fragment1.getName().getIdentifier();
                    String variableName2 = fragment2.getName().getIdentifier();
                    Type type1 = variableDeclaration1.getType();
                    Type type2 = variableDeclaration2.getType();
                    Expression initializer1 = fragment1.getInitializer();
                    Expression initializer2 = fragment2.getInitializer();
                    if (initializer1 != null && initializer2 != null && initializer1.toString().equals(initializer2.toString()))
                        processStatementMap(originalPair, matchPair, temp, node1, node2);
                    for (Pair<String, String> replacement : replacementsBefore) {
                        String left = replacement.getLeft();
                        String right = replacement.getRight();
                        if (initializer1 != null && initializer2 != null && initializer1.toString().replace(left, right).equals(initializer2.toString())) {
                            processStatementMap(originalPair, matchPair, temp, node1, node2);
                            break;
                        }
                    }
                    for (Pair<String, String> replacement : replacementsCurrent) {
                        String left = replacement.getLeft();
                        String right = replacement.getRight();
                        if (initializer1 != null && initializer2 != null && initializer1.toString().equals(initializer2.toString().replace(left, right))) {
                            processStatementMap(originalPair, matchPair, temp, node1, node2);
                            break;
                        }
                    }
                    if (initializer1 instanceof ClassInstanceCreation && initializer2 instanceof ClassInstanceCreation) {
                        ClassInstanceCreation creation1 = (ClassInstanceCreation) initializer1;
                        ClassInstanceCreation creation2 = (ClassInstanceCreation) initializer2;
                        String classType1 = creation1.getType().toString();
                        String classType2 = creation2.getType().toString();
                        if (entityReplacements.get(EntityType.CLASS) != null && entityReplacements.get(EntityType.CLASS).contains(Pair.of(classType1, classType2)))
                            processStatementMap(originalPair, matchPair, temp, node1, node2);
                    }
                    if (!node1.isRefactored() && !node2.isRefactored()) {
                        double txtSim = DiceFunction.calculateDiceSimilarity(node1, node2);
                        if (txtSim < 0.15 && !StringUtils.equals(type1.toString(), type2.toString()) &&
                                !StringUtils.equals(fragment1.getName().getIdentifier(), fragment2.getName().getIdentifier()))
                            continue;
                        for (Pair<StatementNodeTree, StatementNodeTree> pair : matchedStatements) {
                            StatementNodeTree left = pair.getLeft();
                            StatementNodeTree right = pair.getRight();
                            List<String> astNodes1 = new ArrayList<>();
                            List<String> astNodes2 = new ArrayList<>();
                            left.getStatement().accept(new ASTVisitor() {
                                @Override
                                public boolean visit(SimpleName node) {
                                    astNodes1.add(node.getIdentifier());
                                    return true;
                                }
                            });
                            right.getStatement().accept(new ASTVisitor() {
                                @Override
                                public boolean visit(SimpleName node) {
                                    astNodes2.add(node.getIdentifier());
                                    return true;
                                }
                            });
                            if (astNodes1.contains(variableName1) && astNodes2.contains(variableName2))
                                processStatementMap(originalPair, matchPair, temp, node1, node2);
                        }
                    }
                }
            }
        }
        replacedMap2SetOfMatchedStatements(matchedStatements, deletedStatements, addedStatements, temp);
    }

    private void additionalMatchByReturn(MatchPair matchPair) {
        Set<Pair<StatementNodeTree, StatementNodeTree>> matchedStatements = matchPair.getMatchedStatements();
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        List<StatementNodeTree> returnStatements1 = new ArrayList<>();
        List<StatementNodeTree> returnStatements2 = new ArrayList<>();
        for (StatementNodeTree node : deletedStatements) {
            if (node.getType() == StatementType.RETURN_STATEMENT)
                returnStatements1.add(node);
        }
        for (StatementNodeTree node : addedStatements) {
            if (node.getType() == StatementType.RETURN_STATEMENT)
                returnStatements2.add(node);
        }
        if (returnStatements1.size() == 1 && returnStatements2.size() == 1) {
            StatementNodeTree returnStatement1 = returnStatements1.get(0);
            StatementNodeTree returnStatement2 = returnStatements2.get(0);
            if (returnStatement1.getDepth() == 1 && returnStatement2.getDepth() == 1) {
                returnStatement1.setMatched();
                returnStatement2.setMatched();
                matchedStatements.add(Pair.of(returnStatement1, returnStatement2));
                deletedStatements.remove(returnStatement1);
                addedStatements.remove(returnStatement2);
            }
        }
    }
}
