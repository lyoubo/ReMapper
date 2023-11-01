package org.remapper.service;

import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.remapper.dto.*;
import org.remapper.util.DiceFunction;
import org.remapper.util.MethodUtils;
import org.remapper.util.StringUtils;

import java.util.*;

public class MethodStatementMatcherService {

    public void matchStatements(MethodNode methodBefore, MethodNode methodAfter, MatchPair matchPair) {
        matchControls(matchPair, methodBefore, methodAfter);
        matchBlocks(matchPair, methodBefore, methodAfter);
        matchOperations(matchPair, methodBefore, methodAfter);
        iterativeMatching(matchPair, methodBefore, methodAfter);

        matchPair.addDeletedStatements(methodBefore.getUnmatchedNodes());
        matchPair.addAddedStatements(methodAfter.getUnmatchedNodes());

        repairMatching(matchPair);
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
        double similarity = 0.0;
        if (node1 == null || node2 == null) return similarity;
        NormalizedLevenshtein nl = new NormalizedLevenshtein();
        similarity += (1 - nl.distance(node1.getStatement().toString(), node2.getStatement().toString()));
        return similarity;
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
        sntMap2SetOfMatchedStatements(matchPair, temp1);
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
        sntMap2SetOfMatchedStatements(matchPair, temp2);
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
        if (node1.getBlockType() == BlockType.IF && node2.getBlockType() == BlockType.ELSE)
            weight += 1.0;
        if (node1.getBlockType() == BlockType.ELSE && node2.getBlockType() == BlockType.IF)
            weight += 1.0;
        weight += (1 - nl.distance(node1.getBlockExpression(), node2.getBlockExpression()));
        return weight;
    }

    private void sntMap2SetOfMatchedStatements(MatchPair matchPair, Map<StatementNodeTree, StatementNodeTree> temp) {
        for (StatementNodeTree snt1 : temp.keySet()) {
            StatementNodeTree snt2 = temp.get(snt1);
            if (!snt1.isMatched() && !snt2.isMatched()) {
                matchPair.addMatchedStatement(snt1, snt2);
                snt1.setMatched();
                snt2.setMatched();
                setChildrenMatched(matchPair, snt1.getChildren(), snt2.getChildren());
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
        List<StatementNodeTree> allStatementsBefore = methodBefore.getAllOperations();
        List<StatementNodeTree> allStatementsAfter = methodAfter.getAllOperations();
        /**
         * 1. n1.text = n2.text ^ n1.depth = n2.depth
         */
        Map<StatementNodeTree, StatementNodeTree> temp1 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allStatementsBefore) {
            for (StatementNodeTree node2 : allStatementsAfter) {
                if (!node1.isMatched() && !node2.isMatched() && StringUtils.equals(node1.getStatement().toString(), node2.getStatement().toString()) &&
                        node1.getDepth() == node2.getDepth()) {
                    processOperationMap(matchPair, temp1, node1, node2);
                }
            }
        }
        sntMap2SetOfMatchedStatements(matchPair, temp1);
        /**
         * 2. n1.text = n2.text
         */
        Map<StatementNodeTree, StatementNodeTree> temp2 = new LinkedHashMap<>();
        for (StatementNodeTree node1 : allStatementsBefore) {
            for (StatementNodeTree node2 : allStatementsAfter) {
                if (!node1.isMatched() && !node2.isMatched() && StringUtils.equals(node1.getStatement().toString(), node2.getStatement().toString())) {
                    processOperationMap(matchPair, temp2, node1, node2);
                }
            }
        }
        sntMap2SetOfMatchedStatements(matchPair, temp2);
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

    private void iterativeMatching(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter) {
        Set<Pair<StatementNodeTree, StatementNodeTree>> candidateStatements = matchByDiceCoefficient(matchPair, methodBefore, methodAfter);
        matchPair.setCandidateStatements(candidateStatements);
        for (int i = 0; i < 5; i++) {
            Set<Pair<StatementNodeTree, StatementNodeTree>> temp = matchByDiceCoefficient(matchPair, methodBefore, methodAfter);
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

    private Set<Pair<StatementNodeTree, StatementNodeTree>> matchByDiceCoefficient(MatchPair matchPair, MethodNode methodBefore, MethodNode methodAfter) {
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
                    if (DiceFunction.calculateSimilarity(matchPair, node1, node2) < DiceFunction.minSimilarity)
                        continue;
                    if (node1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT && node2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                        VariableDeclarationStatement statement1 = (VariableDeclarationStatement) node1.getStatement();
                        VariableDeclarationStatement statement2 = (VariableDeclarationStatement) node2.getStatement();
                        VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) statement1.fragments().get(0);
                        VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) statement2.fragments().get(0);
                        if (DiceFunction.calculateDiceSimilarity(node1, node2) >= 0.2 ||
                                statement1.getType().toString().equals(statement2.getType().toString()) ||
                                fragment1.getName().getIdentifier().equals(fragment2.getName().getIdentifier())) {
                            processStatementMap(matchPair, temp1, node1, node2);
                        }
                        continue;
                    }
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
        return node1.getType() == node2.getType() ||
                ((node1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT || node1.getType() == StatementType.EXPRESSION_STATEMENT ||
                        node1.getType() == StatementType.RETURN_STATEMENT) &&
                        (node2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT || node2.getType() == StatementType.EXPRESSION_STATEMENT ||
                                node2.getType() == StatementType.RETURN_STATEMENT)) ||
                (node1.getBlockType() == BlockType.IF && node2.getBlockType() == BlockType.ELSE) ||
                (node1.getBlockType() == BlockType.ELSE && node2.getBlockType() == BlockType.IF) ||
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

    private void repairMatching(MatchPair matchPair) {
        Set<Pair<StatementNodeTree, StatementNodeTree>> matchedStatements = matchPair.getMatchedStatements();
        Set<Pair<StatementNodeTree, StatementNodeTree>> oldPairs = new HashSet<>();
        Set<Pair<StatementNodeTree, StatementNodeTree>> newPairs = new HashSet<>();
        Set<StatementNodeTree> replacedDeleted = new HashSet<>();
        Set<StatementNodeTree> addedDeleted = new HashSet<>();
        Set<StatementNodeTree> replacedAdded = new HashSet<>();
        Set<StatementNodeTree> addedAdded = new HashSet<>();
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        for (Pair<StatementNodeTree, StatementNodeTree> pair : matchedStatements) {
            StatementNodeTree left = pair.getLeft();
            StatementNodeTree right = pair.getRight();
            for (StatementNodeTree added : addedStatements) {
                if (((left.getDepth() == 1 && added.getDepth() == 1) || (matchedStatements.contains(Pair.of(left.getParent(), added.getParent())))) &&
                        left.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT &&
                        added.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                    VariableDeclarationStatement leftStatement = (VariableDeclarationStatement) left.getStatement();
                    VariableDeclarationStatement addedStatement = (VariableDeclarationStatement) added.getStatement();
                    VariableDeclarationFragment leftFragment = (VariableDeclarationFragment) leftStatement.fragments().get(0);
                    VariableDeclarationFragment addedFragment = (VariableDeclarationFragment) addedStatement.fragments().get(0);
                    if (leftFragment.getName().getIdentifier().equals(addedFragment.getName().getIdentifier())) {
                        oldPairs.add(pair);
                        newPairs.add(Pair.of(left, added));
                        replacedAdded.add(added);
                        addedAdded.add(right);
                    }
                }
            }
            for (StatementNodeTree deleted : deletedStatements) {
                if (((deleted.getDepth() == 1 && right.getDepth() == 1) || matchedStatements.contains(Pair.of(deleted.getParent(), right.getParent()))) &&
                        deleted.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT &&
                        right.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                    VariableDeclarationStatement deletedStatement = (VariableDeclarationStatement) deleted.getStatement();
                    VariableDeclarationStatement rightStatement = (VariableDeclarationStatement) right.getStatement();
                    VariableDeclarationFragment deletedFragment = (VariableDeclarationFragment) deletedStatement.fragments().get(0);
                    VariableDeclarationFragment rightFragment = (VariableDeclarationFragment) rightStatement.fragments().get(0);
                    if (deletedFragment.getName().getIdentifier().equals(rightFragment.getName().getIdentifier())) {
                        oldPairs.add(pair);
                        newPairs.add(Pair.of(deleted, right));
                        replacedDeleted.add(deleted);
                        addedDeleted.add(left);
                    }
                }
            }
        }
        deletedStatements.removeAll(replacedDeleted);
        deletedStatements.addAll(addedDeleted);
        addedStatements.removeAll(replacedAdded);
        addedStatements.addAll(addedAdded);
        for (StatementNodeTree deleted : deletedStatements) {
            for (StatementNodeTree added : addedStatements) {
                if (((deleted.getDepth() == 1 && added.getDepth() == 1) || matchedStatements.contains(Pair.of(deleted.getParent(), added.getParent()))) &&
                        deleted.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT &&
                        added.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                    VariableDeclarationStatement deletedStatement = (VariableDeclarationStatement) deleted.getStatement();
                    VariableDeclarationFragment deletedFragment = (VariableDeclarationFragment) deletedStatement.fragments().get(0);
                    VariableDeclarationStatement addedStatement = (VariableDeclarationStatement) added.getStatement();
                    VariableDeclarationFragment addedFragment = (VariableDeclarationFragment) addedStatement.fragments().get(0);
                    if (deletedStatement.getType().toString().equals(addedStatement.getType().toString()) &&
                            deletedFragment.getName().getIdentifier().equals(addedFragment.getName().getIdentifier())) {
                        newPairs.add(Pair.of(deleted, added));
                        replacedDeleted.add(deleted);
                        replacedAdded.add(added);
                    }
                }
                if (deleted.getDepth() == 1 && added.getDepth() == 1 &&
                        deleted.getParent().getChildren().size() == 1 && added.getParent().getChildren().size() == 1) {
                    double sim = DiceFunction.calculateDiceSimilarity(deleted, added);
                    if (sim >= 0.5) {
                        newPairs.add(Pair.of(deleted, added));
                        replacedDeleted.add(deleted);
                        replacedAdded.add(added);
                    }
                }
                if ((deleted.getType() == StatementType.FOR_STATEMENT || deleted.getType() == StatementType.ENHANCED_FOR_STATEMENT ||
                        deleted.getType() == StatementType.WHILE_STATEMENT || deleted.getType() == StatementType.DO_STATEMENT) &&
                        MethodUtils.isStreamAPI(added.getStatement())) {
                    double sim = DiceFunction.calculateSimilarity(matchPair, deleted, added);
                    if (sim >= DiceFunction.minSimilarity) {
                        newPairs.add(Pair.of(deleted, added));
                        replacedDeleted.add(deleted);
                        replacedAdded.add(added);
                    }
                }
            }
        }
        matchedStatements.removeAll(oldPairs);
        matchedStatements.addAll(newPairs);
        deletedStatements.removeAll(replacedDeleted);
        addedStatements.removeAll(replacedAdded);
    }
}
