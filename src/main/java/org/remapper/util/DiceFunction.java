package org.remapper.util;

import info.debatty.java.stringsimilarity.NGram;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import org.remapper.dto.*;
import org.remapper.service.JDTService;

import java.util.*;

public class DiceFunction {

    public static double minSimilarity = 0.5;

    public static double calculateDiceSimilarity(LeafNode leafBefore, LeafNode leafCurrent) {
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = jdtService.getDescendants(leafBefore.getDeclaration());
        List<ChildNode> list2 = jdtService.getDescendants(leafCurrent.getDeclaration());
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

    public static double calculateDiceSimilarity(MatchPair matchPair, InternalNode internalBefore, InternalNode internalCurrent) {
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

    public static double calculateReferenceSimilarity(MatchPair matchPair, DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        List<EntityInfo> list1 = dntBefore.getDependencies();
        List<EntityInfo> list2 = dntCurrent.getDependencies();
        Set<Pair<EntityInfo, EntityInfo>> matchedEntityInfos = matchPair.getMatchedEntityInfos();
        Set<Pair<EntityInfo, EntityInfo>> candidateEntityInfos = matchPair.getCandidateEntityInfos();
        int intersection = 0;
        int union = list1.size() + list2.size();
        Set<Integer> matched = new HashSet<>();
        for (EntityInfo entityBefore : list1) {
            for (int i = 0; i < list2.size(); i++) {
                if (matched.contains(i)) continue;
                EntityInfo entityCurrent = list2.get(i);
                if (isMatchedReference(matchPair, entityBefore, entityCurrent, matchedEntityInfos, candidateEntityInfos)) {
                    intersection++;
                    matched.add(i);
                    break;
                }
            }
        }
        double dependencies = union == 0 ? 0 : 2.0 * intersection / union;
        Set<EntityInfo> matched2 = new HashSet<>();
        if (dependencies > 0) {
            Set<EntityInfo> set1 = new LinkedHashSet<>(list1);
            Set<EntityInfo> set2 = new LinkedHashSet<>(list2);
            int intersection2 = 0;
            int union2 = set1.size() + set2.size();
            for (EntityInfo entityBefore : set1) {
                for (EntityInfo entityCurrent : set2) {
                    if (matched2.contains(entityCurrent)) continue;
                    if (isMatchedReference(matchPair, entityBefore, entityCurrent, matchedEntityInfos, candidateEntityInfos)) {
                        intersection2++;
                        matched2.add(entityCurrent);
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

    private static boolean isMatchedReference(MatchPair matchPair, EntityInfo entityBefore, EntityInfo entityCurrent,
                                              Set<Pair<EntityInfo, EntityInfo>> matchedEntityInfos,
                                              Set<Pair<EntityInfo, EntityInfo>> candidateEntityInfos) {
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> candidateEntities = matchPair.getCandidateEntities();
        Set<DeclarationNodeTree> addedEntities = matchPair.getAddedEntities();
        Set<DeclarationNodeTree> deletedEntities = matchPair.getDeletedEntities();
        if (entityBefore.equals(entityCurrent) || matchedEntityInfos.contains(Pair.of(entityBefore, entityCurrent)) ||
                candidateEntityInfos.contains(Pair.of(entityBefore, entityCurrent)))
            return true;
        /**
         * Reference-based similarity calculation for extract methods
         */
        for (DeclarationNodeTree addedEntity : addedEntities) {
            if (addedEntity.getEntity().equals(entityCurrent)) {
                for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
                    DeclarationNodeTree left = pair.getLeft();
                    DeclarationNodeTree right = pair.getRight();
                    if (addedEntity.getDependencies().contains(right.getEntity()) && left.getEntity().equals(entityBefore)) {
                        return true;
                    }
                }
                for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : candidateEntities) {
                    DeclarationNodeTree right = pair.getRight();
                    DeclarationNodeTree left = pair.getLeft();
                    if (addedEntity.getDependencies().contains(right.getEntity()) && left.getEntity().equals(entityBefore)) {
                        return true;
                    }
                }
            }
        }
        /**
         * Reference-based similarity calculation for inline methods
         */
        for (DeclarationNodeTree deletedEntity : deletedEntities) {
            if (deletedEntity.getEntity().equals(entityBefore)) {
                for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
                    DeclarationNodeTree left = pair.getLeft();
                    DeclarationNodeTree right = pair.getRight();
                    if (deletedEntity.getDependencies().contains(left.getEntity()) && right.getEntity().equals(entityCurrent)) {
                        return true;
                    }
                }
                for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : candidateEntities) {
                    DeclarationNodeTree right = pair.getRight();
                    DeclarationNodeTree left = pair.getLeft();
                    if (deletedEntity.getDependencies().contains(left.getEntity()) && right.getEntity().equals(entityCurrent)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static double calculateSimilarity(MatchPair matchPair, DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        double descendants = 0.0;
        if (dntBefore instanceof InternalNode && dntCurrent instanceof InternalNode)
            descendants = calculateDiceSimilarity(matchPair, (InternalNode) dntBefore, (InternalNode) dntCurrent);
        else if (dntBefore instanceof LeafNode && dntCurrent instanceof LeafNode)
            descendants = calculateDiceSimilarity((LeafNode) dntBefore, (LeafNode) dntCurrent);
        int union = dntBefore.getDependencies().size() + dntCurrent.getDependencies().size();
        double dependencies = calculateReferenceSimilarity(matchPair, dntBefore, dntCurrent);
        NGram ngram = new NGram(2);
        double biGram = 1 - ngram.distance(dntBefore.getNamespace() + "." + dntBefore.getName(), dntCurrent.getNamespace() + "." + dntCurrent.getName());
        if (dependencies == 1.0) {
            return descendants * 3 + 0.01 * biGram;
        }
        return (union == 0 ? descendants : 0.5 * descendants + 0.5 * dependencies) + 0.01 * biGram;
    }

    public static double calculateContextSimilarity(MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        StatementNodeTree parent1 = statement1.getParent();
        StatementNodeTree parent2 = statement2.getParent();
        List<StatementNodeTree> children1 = parent1.getChildren();
        List<StatementNodeTree> children2 = parent2.getChildren();
        int index1 = children1.indexOf(statement1);
        int index2 = children2.indexOf(statement2);
        if (index1 != -1 && index2 != -1) {
            int previous1 = index1 - 1;
            int next1 = index1 + 1;
            int previous2 = index2 - 1;
            int next2 = index2 + 1;
            if (previous1 >= 0 && previous2 >= 0 && next1 < children1.size() && next2 < children2.size()) {
                StatementNodeTree previousNode1 = children1.get(previous1);
                StatementNodeTree nextNode1 = children1.get(next1);
                StatementNodeTree previousNode2 = children2.get(previous2);
                StatementNodeTree nextNode2 = children2.get(next2);
                if ((matchPair.getMatchedStatements().contains(Pair.of(previousNode1, previousNode2)) ||
                        matchPair.getCandidateStatements().contains(Pair.of(previousNode1, previousNode2))) &&
                        (matchPair.getMatchedStatements().contains(Pair.of(nextNode1, nextNode2)) ||
                                matchPair.getCandidateStatements().contains(Pair.of(nextNode1, nextNode2))))
                    return 1.0;
            }
        }
        List<StatementNodeTree> context1 = new ArrayList<>(children1);
        context1.remove(statement1);
        List<StatementNodeTree> context2 = new ArrayList<>(children2);
        context2.remove(statement2);
        if (context1.isEmpty() || context2.isEmpty()) {
            if (matchPair.getMatchedStatements().contains(Pair.of(statement1.getParent(), statement2.getParent())) ||
                    matchPair.getCandidateStatements().contains(Pair.of(statement1.getParent(), statement2.getParent())))
                return 1.0;
        }
        int intersection = getMatchedElementCount(matchPair, context1, context2);
        int lessOne = Math.min(context1.size(), context2.size());
        return lessOne == 0 ? 0 : 1.0 * intersection / lessOne;
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
        return Math.max(union == 0 ? 0 : 2.0 * intersection / union, calculateDescendantSimilarity(matchPair, statement1, statement2));
    }


    public static double calculateDescendantSimilarity(MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        List<StatementNodeTree> descendants1 = statement1.getDescendants();
        List<StatementNodeTree> descendants2 = statement2.getDescendants();
        if (descendants1.size() == 1 && descendants2.size() == 1) {
            int intersection = getMatchedElementCount(matchPair, descendants1, descendants2);
            if (intersection == 1)
                return 1.0;
            return calculateDiceSimilarity(statement1, statement2);
        }
        int intersection = getMatchedElementCount(matchPair, descendants1, descendants2);
        int union = descendants1.size() + descendants2.size();
        return union == 0 ? 0 : 2.0 * intersection / union;
    }

    public static boolean isMatchedGreaterThanAnyOne(MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        List<StatementNodeTree> children1 = statement1.getChildren();
        List<StatementNodeTree> children2 = statement2.getChildren();
        int intersection = getMatchedElementCount(matchPair, children1, children2);
        if (2.0 * intersection >= children1.size() || 2.0 * intersection >= children2.size())
            return true;
        else {
            List<StatementNodeTree> descendants1 = statement1.getDescendants();
            List<StatementNodeTree> descendants2 = statement2.getDescendants();
            intersection = getMatchedElementCount(matchPair, descendants1, descendants2);
            if (2.0 * intersection >= descendants1.size() || 2.0 * intersection >= descendants2.size())
                return true;
        }
        return false;
    }

    public static double calculateSimilarity(MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        double descendants = 0.0;
        double contexts = calculateContextSimilarity(matchPair, statement1, statement2);
        if (statement1 instanceof OperationNode && statement2 instanceof OperationNode) {
            descendants = calculateDiceSimilarity(statement1, statement2);
            if (statement1.hasChildren() && statement2.hasChildren()) {
                double temp = calculateChildrenSimilarity(matchPair, statement1, statement2);
                descendants = Math.max(descendants, temp);
            }
        }
        if (statement1 instanceof BlockNode && statement2 instanceof BlockNode) {
            descendants = calculateChildrenSimilarity(matchPair, statement1, statement2);
        }
        if (statement1 instanceof ControlNode && statement2 instanceof ControlNode) {
            descendants = calculateChildrenSimilarity(matchPair, statement1, statement2);
        }
        if (contexts == 1.0 && descendants == 0.0 && statement1 instanceof OperationNode && statement2 instanceof OperationNode)
            return 0.49;
        else
            return (descendants + contexts) / 2;
    }
}
