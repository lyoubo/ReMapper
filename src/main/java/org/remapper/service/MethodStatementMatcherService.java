package org.remapper.service;

import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.remapper.dto.*;
import org.remapper.util.DiceFunction;
import org.remapper.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class MethodStatementMatcherService {

    public void matchStatements(MethodNode methodBefore, MethodNode methodAfter, MatchPair originalPair,
                                List<Pair<String, String>> replacementsBefore, List<Pair<String, String>> replacementsCurrent) {
        MatchPair matchPair = new MatchPair();
        Map<EntityType, List<Pair<String, String>>> replacements = getReplacements(originalPair);
        matchControls(matchPair, methodBefore, methodAfter);
        matchBlocks(matchPair, methodBefore, methodAfter);
        matchOperations(matchPair, methodBefore, methodAfter);
        matchByEntityReplacement(matchPair, methodBefore, methodAfter, replacements);
        matchByVariableReplacement(matchPair, methodBefore, methodAfter, replacementsBefore, replacementsCurrent);

        iterativeMatching(matchPair, methodBefore, methodAfter, replacements);

        matchPair.addDeletedStatements(methodBefore.getUnmatchedStatements());
        matchPair.addAddedStatements(methodAfter.getUnmatchedStatements());

        additionalMatchByDice(matchPair);
        additionalMatchByChildren(matchPair);
        additionalMatchByContext(matchPair);

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
                if (!node1.isMatched() && !node2.isMatched() && node1.getType() == node2.getType() &&
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
                if (!node1.isMatched() && !node2.isMatched() && node1.getType() == node2.getType() &&
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
            if (!control1.isMatched() && !control2.isMatched()) {
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
                if (!node1.isMatched() && !node2.isMatched() &&
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
                if (!node1.isMatched() && !node2.isMatched() && StringUtils.equals(node1.getStatement().toString(), node2.getStatement().toString()) &&
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
                if (!node1.isMatched() && !node2.isMatched() && StringUtils.equals(node1.getStatement().toString(), node2.getStatement().toString())) {
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
            if (!node1.isMatched() && !node2.isMatched()) {
                matchPair.addMatchedStatement(node1, node2);
                node1.setMatched();
                node2.setMatched();
                setChildrenMatched(matchPair, node1.getChildren(), node2.getChildren());
            }
        }
    }

    private void setChildrenMatched(MatchPair matchPair, List<StatementNodeTree> children1, List<StatementNodeTree> children2) {
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
    }

    private void matchOperations(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter) {
        List<StatementNodeTree> allOperationsBefore = methodBefore.getAllOperations();
        List<StatementNodeTree> allOperationsAfter = methodAfter.getAllOperations();
        /**
         * 1. n1.text = n2.text ^ n1.depth = n2.depth
         */
        Map<StatementNodeTree, StatementNodeTree> temp1 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allOperationsBefore) {
            for (StatementNodeTree node2 : allOperationsAfter) {
                if (!node1.isMatched() && !node2.isMatched() && isTheSameExpression(node1, node2) &&
                        node1.getDepth() == node2.getDepth()) {
                    processOperationMap(matchPair, temp1, node1, node2);
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
                if (!node1.isMatched() && !node2.isMatched() && isTheSameExpression(node1, node2)) {
                    processOperationMap(matchPair, temp2, node1, node2);
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
                if (!node1.isMatched() && !node2.isMatched() && node1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT &&
                        node2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                    VariableDeclarationStatement variableDeclaration1 = (VariableDeclarationStatement) node1.getStatement();
                    VariableDeclarationStatement variableDeclaration2 = (VariableDeclarationStatement) node2.getStatement();
                    if (variableDeclaration1.getType().toString().equals(variableDeclaration2.getType().toString())) {
                        VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) variableDeclaration1.fragments().get(0);
                        VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) variableDeclaration2.fragments().get(0);
                        if (fragment1.getName().getIdentifier().equals(fragment2.getName().getIdentifier()))
                            processOperationMap(matchPair, temp3, node1, node2);
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
            if (!node1.isMatched() && !node2.isMatched()) {
                matchPair.addMatchedStatement(node1, node2);
                node1.setMatched();
                node2.setMatched();
            }
        }
    }

    private void matchByEntityReplacement(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter, Map<EntityType, List<Pair<String, String>>> replacements) {
        Map<StatementNodeTree, StatementNodeTree> temp1 = new LinkedHashMap<>();
        List<StatementNodeTree> allControlsBefore = methodBefore.getAllControls();
        List<StatementNodeTree> allControlsAfter = methodAfter.getAllControls();
        for (StatementNodeTree node1 : allControlsBefore) {
            for (StatementNodeTree node2 : allControlsAfter) {
                if (!node1.isMatched() && !node2.isMatched() && equalsWithReplacements(node1.getExpression(), node2.getExpression(), replacements)) {
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
        List<StatementNodeTree> allOperationsBefore = methodBefore.getAllOperations();
        List<StatementNodeTree> allOperationsAfter = methodAfter.getAllOperations();
        for (StatementNodeTree node1 : allOperationsBefore) {
            for (StatementNodeTree node2 : allOperationsAfter) {
                if (!node1.isMatched() && !node2.isMatched() && equalsWithReplacements(node1.getExpression(), node2.getExpression(), replacements))
                    processOperationMap(matchPair, temp2, node1, node2);
            }
        }
        stmtMap2SetOfMatchedStatements(matchPair, temp2);
    }

    private boolean equalsWithReplacements(String statement1, String statement2, Map<EntityType, List<Pair<String, String>>> replacements) {
        String temp = statement1;
        List<Pair<String, String>> list = new ArrayList<>();
        if (replacements.get(EntityType.CLASS) != null)
            list.addAll(replacements.get(EntityType.CLASS));
        if (replacements.get(EntityType.INTERFACE) != null)
            list.addAll(replacements.get(EntityType.INTERFACE));
        if (replacements.get(EntityType.ENUM) != null)
            list.addAll(replacements.get(EntityType.ENUM));
        if (replacements.get(EntityType.FIELD) != null)
            list.addAll(replacements.get(EntityType.FIELD));
        if (replacements.get(EntityType.METHOD) != null)
            list.addAll(replacements.get(EntityType.METHOD));
        if (replacements.get(EntityType.ENUM_CONSTANT) != null)
            list.addAll(replacements.get(EntityType.ENUM_CONSTANT));
        NormalizedLevenshtein nl = new NormalizedLevenshtein();
        double distance = nl.distance(statement1, statement2);
        for (Pair<String, String> pair : list) {
            String oldEntity = pair.getLeft();
            String newEntity = pair.getRight();
            String replaced = temp.replace(oldEntity, newEntity);
            double compared = nl.distance(replaced, statement2);
            if (compared == 0.0)
                return true;
            if (compared < distance) {
                temp = replaced;
                distance = compared;
            }
        }
        return false;
    }

    private void matchByVariableReplacement(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter,
                                            List<Pair<String, String>> replacementsBefore, List<Pair<String, String>> replacementsCurrent) {
        List<StatementNodeTree> allControlsBefore = methodBefore.getAllControls();
        List<StatementNodeTree> allControlsAfter = methodAfter.getAllControls();
        List<StatementNodeTree> allOperationsBefore = methodBefore.getAllOperations();
        List<StatementNodeTree> allOperationsAfter = methodAfter.getAllOperations();
        List<StatementNodeTree> allVariablesBefore = allOperationsBefore.stream().filter(node -> node.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT).collect(Collectors.toList());
        List<StatementNodeTree> allVariablesAfter = allOperationsAfter.stream().filter(node -> node.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT).collect(Collectors.toList());
        List<Pair<String, String>> replacements1 = new ArrayList<>(replacementsBefore);
        for (StatementNodeTree variable : allVariablesBefore) {
            VariableDeclarationStatement statement = (VariableDeclarationStatement) variable.getStatement();
            List<VariableDeclarationFragment> fragments = statement.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                if (fragment.getInitializer() != null)
                    replacements1.add(Pair.of(fragment.getName().getIdentifier(), fragment.getInitializer().toString()));
            }
        }
        List<Pair<String, String>> replacements2 = new ArrayList<>(replacementsCurrent);
        for (StatementNodeTree variable : allVariablesAfter) {
            VariableDeclarationStatement statement = (VariableDeclarationStatement) variable.getStatement();
            List<VariableDeclarationFragment> fragments = statement.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                if (fragment.getInitializer() != null)
                    replacements2.add(Pair.of(fragment.getName().getIdentifier(), fragment.getInitializer().toString()));
            }
        }
        Map<StatementNodeTree, StatementNodeTree> temp1 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allControlsBefore) {
            for (StatementNodeTree node2 : allControlsAfter) {
                if (!node1.isMatched() && !node2.isMatched() && equalsWithReplacements(node1.getExpression(), node2.getExpression(), replacements1, replacements2)) {
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
                if (!node1.isMatched() && !node2.isMatched() && equalsWithReplacements(node1.getExpression(), node2.getExpression(), replacements1, replacements2))
                    processOperationMap(matchPair, temp2, node1, node2);
            }
        }
        stmtMap2SetOfMatchedStatements(matchPair, temp2);
    }

    private boolean equalsWithReplacements(String statement1, String statement2, List<Pair<String, String>> replacements1, List<Pair<String, String>> replacements2) {
        String replaced1 = statement1;
        String replaced2 = statement2;
        for (Pair<String, String> pair : replacements1) {
            String name = pair.getLeft();
            String initializer = pair.getRight();
            if (statement1.contains(name)) {
                replaced1 = replaced1.replace(name, initializer);
            }
        }
        for (Pair<String, String> pair : replacements2) {
            String name = pair.getLeft();
            String initializer = pair.getRight();
            if (statement2.contains(name)) {
                replaced2 = replaced2.replace(name, initializer);
            }
        }
        return replaced1.equals(statement2) || statement1.equals(replaced2) || replaced1.equals(replaced2);
    }

    private void processOperationMap(MatchPair matchPair, Map<StatementNodeTree, StatementNodeTree> temp, StatementNodeTree node1, StatementNodeTree node2) {
        if (temp.containsKey(node1) || temp.containsValue(node2)) {
            StatementNodeTree candidateValue = temp.get(node1);
            StatementNodeTree candidateKey = getKeyByValue(temp, node2);
            double previousValue = candidateValue == null ? 0.0 : DiceFunction.calculateContextSimilarity(matchPair, node1, candidateValue);
            double previousKey = candidateKey == null ? 0.0 : DiceFunction.calculateContextSimilarity(matchPair, candidateKey, node2);
            double current = DiceFunction.calculateContextSimilarity(matchPair, node1, node2);
            if (current > previousValue && current > previousKey) {
                temp.remove(node1, candidateValue);
                temp.remove(candidateKey, node2);
                temp.put(node1, node2);
            }
        } else
            temp.put(node1, node2);
    }

    private void iterativeMatching(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter, Map<EntityType, List<Pair<String, String>>> replacements) {
        Set<Pair<StatementNodeTree, StatementNodeTree>> candidateStatements = matchByDiceCoefficient(matchPair, methodBefore, methodAfter, replacements);
        matchPair.setCandidateStatements(candidateStatements);
        for (int i = 0; i < 5; i++) {
            Set<Pair<StatementNodeTree, StatementNodeTree>> temp = matchByDiceCoefficient(matchPair, methodBefore, methodAfter, replacements);
            if (matchPair.getCandidateStatements().size() == temp.size() && matchPair.getCandidateStatements().equals(temp)) {
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

    private Set<Pair<StatementNodeTree, StatementNodeTree>> matchByDiceCoefficient(MatchPair matchPair, MethodNode methodBefore,
                                                                                   MethodNode methodAfter, Map<EntityType, List<Pair<String, String>>> replacements) {
        Set<Pair<StatementNodeTree, StatementNodeTree>> temp = new HashSet<>();
        List<StatementNodeTree> allOperationsBefore = methodBefore.getAllOperations();
        List<StatementNodeTree> allOperationsAfter = methodAfter.getAllOperations();
        /**
         * 1. sim(n1.text, n2.text) +sim(n1.context, n2.context) > 1.0 ^ n1 instance operation
         */
        Map<StatementNodeTree, StatementNodeTree> temp1 = new HashMap<>();
        for (StatementNodeTree node1 : allOperationsBefore) {
            for (StatementNodeTree node2 : allOperationsAfter) {
                if (!node1.isMatched() && !node2.isMatched()) {
                    if (!typeCompatible(node1, node2))
                        continue;
                    double sim = DiceFunction.calculateSimilarity(matchPair, node1, node2);
                    if (sim < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(matchPair, temp1, node1, node2);
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
                if (!node1.isMatched() && !node2.isMatched()) {
                    if (!typeCompatible(node1, node2))
                        continue;
                    if (DiceFunction.calculateSimilarity(matchPair, node1, node2) < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(matchPair, temp2, node1, node2);
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
                if (!node1.isMatched() && !node2.isMatched()) {
                    if (!typeCompatible(node1, node2))
                        continue;
                    if (DiceFunction.calculateSimilarity(matchPair, node1, node2) < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(matchPair, temp3, node1, node2);
                }
            }
        }
        for (StatementNodeTree control1 : temp3.keySet()) {
            StatementNodeTree control2 = temp3.get(control1);
            temp.add(Pair.of(control1, control2));
        }
        return temp;
    }

    private boolean typeCompatible(StatementNodeTree node1, StatementNodeTree node2) {
        if (node1 instanceof BlockNode && node2 instanceof BlockNode)
            return node1.getBlockType() == node2.getBlockType() || (node1.getBlockType() == BlockType.IF_BLOCK && node2.getBlockType() == BlockType.ELSE_BLOCK) ||
                    (node1.getBlockType() == BlockType.ELSE_BLOCK && node2.getBlockType() == BlockType.IF_BLOCK);
        else
            return node1.getType() == node2.getType() ||
                    ((node1.getType() == StatementType.LAMBDA_EXPRESSION_BODY || node1.getType() == StatementType.EXPRESSION_STATEMENT) &&
                            (node2.getType() == StatementType.LAMBDA_EXPRESSION_BODY || node2.getType() == StatementType.EXPRESSION_STATEMENT)) ||
                    ((node1.getType() == StatementType.FOR_STATEMENT || node1.getType() == StatementType.ENHANCED_FOR_STATEMENT ||
                            node1.getType() == StatementType.WHILE_STATEMENT || node1.getType() == StatementType.DO_STATEMENT) &&
                            (node2.getType() == StatementType.FOR_STATEMENT || node2.getType() == StatementType.ENHANCED_FOR_STATEMENT ||
                                    node2.getType() == StatementType.WHILE_STATEMENT || node2.getType() == StatementType.DO_STATEMENT));
    }

    private void processStatementMap(MatchPair matchPair, Map<StatementNodeTree, StatementNodeTree> temp, StatementNodeTree node1, StatementNodeTree node2) {
        if (temp.containsKey(node1) || temp.containsValue(node2)) {
            StatementNodeTree candidateValue = temp.get(node1);
            StatementNodeTree candidateKey = getKeyByValue(temp, node2);
            double previousValue = candidateValue == null ? 0.0 : DiceFunction.calculateSimilarity(matchPair, node1, candidateValue);
            double previousKey = candidateKey == null ? 0.0 : DiceFunction.calculateSimilarity(matchPair, candidateKey, node2);
            double current = DiceFunction.calculateSimilarity(matchPair, node1, node2);
            if (current > previousValue && current > previousKey) {
                temp.remove(node1, candidateValue);
                temp.remove(candidateKey, node2);
                temp.put(node1, node2);
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

    private void additionalMatchByDice(MatchPair matchPair) {
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        Map<StatementNodeTree, StatementNodeTree> temp = new HashMap<>();
        for (StatementNodeTree node1 : deletedStatements) {
            for (StatementNodeTree node2 : addedStatements) {
                if (typeCompatible(node1, node2) && node1 instanceof OperationNode && node2 instanceof OperationNode) {
                    double sim = DiceFunction.calculateDiceSimilarity(node1, node2);
                    if (sim < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(matchPair, temp, node1, node2);
                }
            }
        }
        replacedMap2SetOfMatchedStatements(matchPair.getMatchedStatements(), deletedStatements, addedStatements, temp);
    }

    private void additionalMatchByChildren(MatchPair matchPair) {
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        Map<StatementNodeTree, StatementNodeTree> temp = new HashMap<>();
        for (StatementNodeTree node1 : deletedStatements) {
            for (StatementNodeTree node2 : addedStatements) {
                if (typeCompatible(node1, node2) && node1 instanceof BlockNode && node2 instanceof BlockNode) {
                    if (DiceFunction.isMatchedGreaterThanAnyOne(matchPair, node1, node2)) {
                        StatementNodeTree parent1 = node1.getParent();
                        StatementNodeTree parent2 = node2.getParent();
                        if (typeCompatible(parent1, parent2)) {
                            processStatementMap(matchPair, temp, node1, node2);
                            processStatementMap(matchPair, temp, parent1, parent2);
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

    private void additionalMatchByContext(MatchPair matchPair) {
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        Map<StatementNodeTree, StatementNodeTree> temp = new HashMap<>();
        for (StatementNodeTree node1 : deletedStatements) {
            for (StatementNodeTree node2 : addedStatements) {
                if (typeCompatible(node1, node2)) {
                    double sim = DiceFunction.calculateSimilarity(matchPair, node1, node2);
                    if (sim < DiceFunction.minSimilarity)
                        continue;
                    processStatementMap(matchPair, temp, node1, node2);
                }
            }
        }
        Set<Pair<StatementNodeTree, StatementNodeTree>> matchedStatements = matchPair.getMatchedStatements();
        replacedMap2SetOfMatchedStatements(matchedStatements, deletedStatements, addedStatements, temp);
    }
}
