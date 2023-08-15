package org.remapper.util;

import info.debatty.java.stringsimilarity.NGram;
import org.apache.commons.lang3.tuple.Pair;
import org.remapper.dto.*;
import org.remapper.service.JDTService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiceFunction {

    public static double minDice = 0.5;

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

    public static double calculateSingleDice(LeafNode leafAdditional, LeafNode leafRefactored) {
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = leafAdditional.getDescendants(jdtService);
        List<ChildNode> list2 = leafRefactored.getDescendants(jdtService);
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
        double full = list1.size() == 0 ? 0 : 1.0 * intersection / list1.size();
        double body = calculateBodyDice(leafAdditional, leafRefactored);
        return Math.max(full, body);
    }

    public static double calculateBodyDice(LeafNode leafBefore, LeafNode leafCurrent) {
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = leafBefore.getDescendantsInBody(jdtService);
        List<ChildNode> list2 = leafCurrent.getDescendantsInBody(jdtService);
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
}
