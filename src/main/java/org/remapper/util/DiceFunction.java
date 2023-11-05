package org.remapper.util;

import info.debatty.java.stringsimilarity.NGram;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import org.remapper.dto.*;
import org.remapper.service.JDTService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiceFunction {

    public static double minDice = 0.5;

    public static double minSimilarity = 1.0;

    public static double calculateDice(LeafNode leafBefore, LeafNode leafCurrent) {
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = leafBefore.getDescendants(jdtService);
        List<ChildNode> list2 = leafCurrent.getDescendants(jdtService);
        int intersection = 0;
        int union = list1.size() + list2.size();
        Set<Integer> matched = new HashSet<>();
        for (ChildNode childBefore : list1) {
            for (int i = 0; i < list2.size(); i++) {
                if (matched.contains(i)) continue;
                ChildNode childCurrent = list2.get(i);
                if (childBefore.equals(childCurrent)) {
                    intersection++;
                    matched.add(i);
                    break;
                }
            }
        }
        return union == 0 ? 0 : 2.0 * intersection / union;
    }

    public static double calculateDice(MatchPair matchPair, InternalNode internalBefore, InternalNode internalCurrent) {
        List<DeclarationNodeTree> list1 = internalBefore.getDescendants();
        List<DeclarationNodeTree> list2 = internalCurrent.getDescendants();
        int intersection = 0;
        int union = list1.size() + list2.size();
        for (DeclarationNodeTree leafBefore : list1) {
            for (DeclarationNodeTree leafCurrent : list2) {
                if (matchPair.getUnchangedEntities().contains(Pair.of(leafBefore, leafCurrent)) ||
                        matchPair.getMatchedEntities().contains(Pair.of(leafBefore, leafCurrent)) ||
                        matchPair.getCandidateEntities().contains(Pair.of(leafBefore, leafCurrent)))
                    intersection++;
            }
        }
        return union == 0 ? 0 : 2.0 * intersection / union;
    }

    public static double calculateBodyDice(LeafNode leafAdditional, LeafNode leafRefactored) {
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = leafAdditional.getDescendantsInBody(jdtService);
        List<ChildNode> list2 = leafRefactored.getDescendantsInBody(jdtService);
        int intersection = 0;
        Set<Integer> matched = new HashSet<>();
        for (ChildNode childBefore : list1) {
            for (int i = 0; i < list2.size(); i++) {
                if (matched.contains(i)) continue;
                ChildNode childCurrent = list2.get(i);
                if (childBefore.equals(childCurrent)) {
                    intersection++;
                    matched.add(i);
                    break;
                }
            }
        }
        double bodyDice = list1.size() == 0 ? 0 : 1.0 * intersection / list1.size();
        double ignoredBodyDice = calculateBodyDiceIgnoreSimpleName(leafAdditional, leafRefactored);
        return Math.max(bodyDice, ignoredBodyDice);
    }

    public static double calculateBodyDiceIgnoreSimpleName(LeafNode leafAdditional, LeafNode leafRefactored) {
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = leafAdditional.getDescendantsInBody(jdtService);
        List<ChildNode> list2 = leafRefactored.getDescendantsInBody(jdtService);
        int intersection = 0;
        Set<Integer> matched = new HashSet<>();
        for (int i = 0; i < list1.size(); i++) {
            ChildNode childBefore = list1.get(i);
            for (int j = 0; j < list2.size(); j++) {
                if (matched.contains(j)) continue;
                ChildNode childCurrent = list2.get(j);
                if (i > 1 && j > 1 && list1.get(i - 1).getLabel() == list2.get(j - 1).getLabel() &&
                        childBefore.equalsIgnoreSimpleName(childCurrent)) {
                    intersection++;
                    matched.add(j);
                    break;
                } else if (childBefore.equals(childCurrent)) {
                    intersection++;
                    matched.add(j);
                    break;
                }
            }
        }
        return list1.size() == 0 ? 0 : 1.0 * intersection / list1.size();
    }

    public static double calculateReference(MatchPair matchPair, DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        List<EntityInfo> list1 = dntBefore.getDependencies();
        List<EntityInfo> list2 = dntCurrent.getDependencies();
        Set<Pair<EntityInfo, EntityInfo>> matchedEntities = matchPair.getMatchedEntityInfos();
        Set<Pair<EntityInfo, EntityInfo>> candidateEntities = matchPair.getCandidateEntityInfos();
        int intersection = 0;
        int union = list1.size() + list2.size();
        Set<Integer> matched = new HashSet<>();
        for (EntityInfo entityBefore : list1) {
            for (int i = 0; i < list2.size(); i++) {
                if (matched.contains(i)) continue;
                EntityInfo entityCurrent = list2.get(i);
                if (entityBefore.equals(entityCurrent) || matchedEntities.contains(Pair.of(entityBefore, entityCurrent)) ||
                        candidateEntities.contains(Pair.of(entityBefore, entityCurrent))) {
                    intersection++;
                    matched.add(i);
                    break;
                }
            }
        }
        double dependencies = union == 0 ? 0 : 2.0 * intersection / union;
        if (dependencies > 0) {
            Set<EntityInfo> set1 = new HashSet<>(list1);
            Set<EntityInfo> set2 = new HashSet<>(list2);
            int intersection2 = 0;
            int union2 = set1.size() + set2.size();
            for (EntityInfo entityBefore : set1) {
                for (EntityInfo entityCurrent : set2) {
                    if (entityBefore.equals(entityCurrent) || matchedEntities.contains(Pair.of(entityBefore, entityCurrent)) ||
                            candidateEntities.contains(Pair.of(entityBefore, entityCurrent))) {
                        intersection2++;
                        break;
                    }
                }
            }
            double dependencies2 = union2 == 0 ? 0 : 2.0 * intersection2 / union2;
            if (dependencies2 > dependencies)
                dependencies = dependencies2;
        }
        return dependencies;
    }

    public static double calculateSimilarity(MatchPair matchPair, DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        double descendants = 0.0;
        if (dntBefore instanceof InternalNode && dntCurrent instanceof InternalNode)
            descendants = calculateDice(matchPair, (InternalNode) dntBefore, (InternalNode) dntCurrent);
        else if (dntBefore instanceof LeafNode && dntCurrent instanceof LeafNode)
            descendants = calculateDice((LeafNode) dntBefore, (LeafNode) dntCurrent);
        int union = dntBefore.getDependencies().size() + dntCurrent.getDependencies().size();
        double dependencies = calculateReference(matchPair, dntBefore, dntCurrent);
        NGram ngram = new NGram(2);
        double biGram = 1 - ngram.distance(dntBefore.getNamespace() + "." + dntBefore.getName(), dntCurrent.getNamespace() + "." + dntCurrent.getName());
        return (union == 0 ? descendants : 0.5 * descendants + 0.5 * dependencies) + 0.01 * biGram;
    }

    public static double calculateContextSimilarity(MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        if (matchPair.getMatchedStatements().contains(Pair.of(statement1.getParent(), statement2.getParent())) ||
                matchPair.getCandidateStatements().contains(Pair.of(statement1.getParent(), statement2.getParent())))
            return 1.0;
        StatementNodeTree blockBefore = statement1.getParent();
        StatementNodeTree blockAfter = statement2.getParent();
        List<StatementNodeTree> context1 = blockBefore == null ? new ArrayList<>() : new ArrayList<>(blockBefore.getChildren());
        context1.remove(statement1);
        List<StatementNodeTree> context2 = blockAfter == null ? new ArrayList<>() : new ArrayList<>(blockAfter.getChildren());
        context2.remove(statement2);
        int intersection = getMatchedElementCount(matchPair, context1, context2);
        int union = context1.size() + context2.size();
        return union == 0 ? 0 : 2.0 * intersection / union;
    }

    private static int getMatchedElementCount(MatchPair matchPair, List<StatementNodeTree> list1, List<StatementNodeTree> list2) {
        int intersection = 0;
        Set<Integer> matched = new HashSet<>();
        for (StatementNodeTree childBefore : list1) {
            for (int i = 0; i < list2.size(); i++) {
                if (matched.contains(i)) continue;
                StatementNodeTree childCurrent = list2.get(i);
                if (matchPair.getMatchedStatements().contains(Pair.of(childBefore, childCurrent))) {
                    intersection++;
                    matched.add(i);
                    break;
                }
                if (matchPair.getCandidateStatements().contains(Pair.of(childBefore, childCurrent))) {
                    intersection++;
                    matched.add(i);
                    break;
                }
            }
        }
        return intersection;
    }

    public static double calculateDiceSimilarity(StatementNodeTree statement1, StatementNodeTree statement2) {
        if (statement1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT && statement2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
            VariableDeclarationStatement vds1 = (VariableDeclarationStatement) statement1.getStatement();
            VariableDeclarationStatement vds2 = (VariableDeclarationStatement) statement2.getStatement();
            VariableDeclarationFragment vdf1 = (VariableDeclarationFragment) vds1.fragments().get(0);
            VariableDeclarationFragment vdf2 = (VariableDeclarationFragment) vds2.fragments().get(0);
            if (vds1.getType().toString().equals(vds2.getType().toString()) && vdf1.getName().getIdentifier().equals(vdf2.getName().getIdentifier()))
                return 1.0;
        }
        Expression expression1 = getExpression(statement1);
        Expression expression2 = getExpression(statement2);
        if (expression1 != null && expression2 != null && StringUtils.equals(expression1.toString(), expression2.toString()))
            return 1.0;
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = jdtService.getDescendants(statement1.getStatement());
        List<ChildNode> list2 = jdtService.getDescendants(statement2.getStatement());
        int intersection = 0;
        int union = list1.size() + list2.size();
        Set<Integer> matched = new HashSet<>();
        for (ChildNode childBefore : list1) {
            for (int i = 0; i < list2.size(); i++) {
                if (matched.contains(i)) continue;
                ChildNode childCurrent = list2.get(i);
                if (childBefore.equals(childCurrent)) {
                    intersection++;
                    matched.add(i);
                    break;
                }
            }
        }
        return union == 0 ? 0 : 2.0 * intersection / union;
    }

    private static Expression getExpression(StatementNodeTree statement) {
        if (statement.getType() == StatementType.EXPRESSION_STATEMENT) {
            ExpressionStatement expressionStatement = (ExpressionStatement) statement.getStatement();
            Expression expression = expressionStatement.getExpression();
            if (expression instanceof Assignment) {
                return ((Assignment) expression).getRightHandSide();
            } else
                return expression;
        }
        if (statement.getType() == StatementType.RETURN_STATEMENT) {
            ReturnStatement returnStatement = (ReturnStatement) statement.getStatement();
            return returnStatement.getExpression();
        }
        if (statement.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
            VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) statement.getStatement();
            if (variableDeclarationStatement.fragments().size() == 1) {
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) variableDeclarationStatement.fragments().get(0);
                return fragment.getInitializer();
            }
        }
        return null;
    }

    public static double calculateChildrenSimilarity(MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        List<StatementNodeTree> children1 = statement1.getChildren();
        List<StatementNodeTree> children2 = statement2.getChildren();
        int intersection = getMatchedElementCount(matchPair, children1, children2);
        int union = children1.size() + children2.size();
        return union == 0 ? 0 : 2.0 * intersection / union;
    }

    public static double calculateDescendantSimilarity(MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        List<StatementNodeTree> descendants1 = statement1.getDescendants();
        List<StatementNodeTree> descendants2 = statement2.getDescendants();
        if (descendants1.size() == 1 && descendants2.size() == 1) {
            return calculateDiceSimilarity(statement1, statement2);
        }
        int intersection = getMatchedElementCount(matchPair, descendants1, descendants2);
        int union = descendants1.size() + descendants2.size();
        return union == 0 ? 0 : 2.0 * intersection / union;
    }

    public static double calculateSimilarity(MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        double descendants = 0.0;
        double contexts = calculateContextSimilarity(matchPair, statement1, statement2);
        double type = 0.0;
        if (statement1 instanceof OperationNode && statement2 instanceof OperationNode) {
            descendants = calculateDiceSimilarity(statement1, statement2);
            /*if (statement1.getType() == statement2.getType())
                type += 1.0;
            else {
                if ((statement1.getType() == StatementType.EXPRESSION_STATEMENT || statement1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT ||
                        statement1.getType() == StatementType.RETURN_STATEMENT) &&
                        (statement2.getType() == StatementType.EXPRESSION_STATEMENT || statement2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT ||
                                statement2.getType() == StatementType.RETURN_STATEMENT))
                    type += 0.5;
            }*/
        }
        if (statement1 instanceof BlockNode && statement2 instanceof BlockNode) {
            descendants = Math.max(calculateChildrenSimilarity(matchPair, statement1, statement2), calculateDescendantSimilarity(matchPair, statement1, statement2));
            /*if (statement1.getBlockType() == statement2.getBlockType())
                type += 0.5;
            else {
                if (statement1.getBlockType() == BlockType.IF && statement2.getBlockType() == BlockType.ELSE)
                    type += 0.5;
                if (statement1.getBlockType() == BlockType.ELSE && statement2.getBlockType() == BlockType.IF)
                    type += 0.5;
                if (statement1.getBlockType() == BlockType.IF && statement2.getBlockType() == BlockType.CASE)
                    type += 0.5;
                if (statement1.getBlockType() == BlockType.CASE && statement2.getBlockType() == BlockType.IF)
                    type += 0.5;
            }
            NormalizedLevenshtein nl = new NormalizedLevenshtein();
            type += 0.5 * (1 - nl.distance(statement1.getBlockExpression(), statement2.getBlockExpression()));*/
        }
        if (statement1 instanceof ControlNode && statement2 instanceof ControlNode) {
            descendants = Math.max(calculateChildrenSimilarity(matchPair, statement1, statement2), calculateDescendantSimilarity(matchPair, statement1, statement2));
            /*if (statement1.getType() == statement2.getType())
                type += 0.5;
            else {
                if ((statement1.getType() == StatementType.FOR_STATEMENT || statement1.getType() == StatementType.ENHANCED_FOR_STATEMENT ||
                        statement1.getType() == StatementType.WHILE_STATEMENT || statement1.getType() == StatementType.DO_STATEMENT) &&
                        (statement2.getType() == StatementType.FOR_STATEMENT || statement2.getType() == StatementType.ENHANCED_FOR_STATEMENT ||
                                statement2.getType() == StatementType.WHILE_STATEMENT || statement2.getType() == StatementType.DO_STATEMENT))
                    type += 0.5;
                if (statement1.getType() == StatementType.IF_STATEMENT && statement2.getType() == StatementType.SWITCH_CASE)
                    type += 0.5;
                if (statement1.getType() == StatementType.SWITCH_CASE && statement2.getType() == StatementType.IF_STATEMENT)
                    type += 0.5;
            }
            NormalizedLevenshtein nl = new NormalizedLevenshtein();
            type += 0.5 * (1 - nl.distance(statement1.getExpression(), statement2.getExpression()));*/
        }
        if (contexts == 1.0 && descendants == 0.0 && statement1 instanceof OperationNode && statement2 instanceof  OperationNode)
            return 0.99;
        else
            return descendants + contexts;
    }
}
