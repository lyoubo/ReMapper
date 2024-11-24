package org.remapper.util;

import info.debatty.java.stringsimilarity.NGram;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
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
        if (leafBefore.getType() == EntityType.METHOD && leafCurrent.getType() == EntityType.METHOD) {
            MethodDeclaration declaration1 = (MethodDeclaration) leafBefore.getDeclaration();
            MethodDeclaration declaration2 = (MethodDeclaration) leafCurrent.getDeclaration();
            if (declaration1.getBody() != null && declaration2.getBody() != null) {
                List<ChildNode> body1 = jdtService.getDescendants(declaration1.getBody());
                List<ChildNode> body2 = jdtService.getDescendants(declaration2.getBody());
                if (!body1.isEmpty() && !body2.isEmpty() && body1.size() * 2 < list1.size() && body2.size() * 2 < list2.size() &&
                        body1.toString().equals(body2.toString()) && leafBefore.getName().equals(leafCurrent.getName()) &&
                        leafBefore.getParent().getName().equals(leafCurrent.getParent().getName()) && declaration1.getReturnType2() != null &&
                        declaration2.getReturnType2() != null && declaration1.getReturnType2().toString().equals(declaration2.getReturnType2().toString())) {
                    return 1.0;
                }
                if (!body1.isEmpty() && !body2.isEmpty() && body1.size() * 2 < list1.size() && body2.size() * 2 < list2.size() &&
                        !leafBefore.getName().equals(leafCurrent.getName())) {
                    list1 = body1;
                    list2 = body2;
                }
            }
        }
        if (leafBefore.getType() == EntityType.FIELD && leafCurrent.getType() == EntityType.FIELD) {
            List<ChildNode> removed = new ArrayList<>();
            for (ChildNode node : list1) {
                if (node.getLabel() == 83)
                    removed.add(node);
            }
            for (ChildNode node : list2) {
                if (node.getLabel() == 83)
                    removed.add(node);
            }
            list1.removeAll(removed);
            list2.removeAll(removed);
        }
        return calculateDiceSimilarity(list1, list2);
    }

    private static double calculateDiceSimilarity(List<ChildNode> list1, List<ChildNode> list2) {
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

    public static double calculateDiceSimilarity(InternalNode internalBefore, InternalNode internalCurrent) {
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = jdtService.getDescendants(internalBefore.getDeclaration());
        List<ChildNode> list2 = jdtService.getDescendants(internalCurrent.getDeclaration());
        return calculateDiceSimilarity(list1, list2);
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
        Set<EntityInfo> matched3 = new HashSet<>();
        if (dependencies > 0) {
            Set<EntityInfo> set1 = new LinkedHashSet<>(list1);
            Set<EntityInfo> set2 = new LinkedHashSet<>(list2);
            Set<EntityInfo> removed = new HashSet<>();
            for (EntityInfo info : set1) {
                if (info.getType() == EntityType.METHOD && info.getName().startsWith("test"))
                    removed.add(info);
            }
            for (EntityInfo info : set2) {
                if (info.getType() == EntityType.METHOD && info.getName().startsWith("test"))
                    removed.add(info);
            }
            set1.removeAll(removed);
            set2.removeAll(removed);
            int intersection3 = 0;
            int union3 = set1.size() + set2.size();
            for (EntityInfo entityBefore : set1) {
                for (EntityInfo entityCurrent : set2) {
                    if (matched3.contains(entityCurrent)) continue;
                    if (isMatchedReference(matchPair, entityBefore, entityCurrent, matchedEntityInfos, candidateEntityInfos)) {
                        intersection3++;
                        matched3.add(entityCurrent);
                        break;
                    }
                }
            }
            double dependencies3 = union3 == 0 ? 0 : 2.0 * intersection3 / union3;
            if (dependencies3 > dependencies)
                dependencies = dependencies3;
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
        if (descendants == 1.0 && dntBefore.getDeclaration().toString().equals(dntCurrent.getDeclaration().toString()) &&
                matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))) {
            return 1.0 + biGram;
        }
        if (dntBefore.getType() == EntityType.METHOD && dntCurrent.getType() == EntityType.METHOD) {
            MethodDeclaration declaration1 = (MethodDeclaration) dntBefore.getDeclaration();
            MethodDeclaration declaration2 = (MethodDeclaration) dntCurrent.getDeclaration();
            Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
            Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> candidateEntities = matchPair.getCandidateEntities();
            if (declaration1.isConstructor() && declaration2.isConstructor()) {
                for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
                    DeclarationNodeTree node1 = pair.getLeft();
                    DeclarationNodeTree node2 = pair.getRight();
                    if (node1.getType() == EntityType.CLASS && node2.getType() == EntityType.CLASS) {
                        if (node1.getName().equals(dntBefore.getName()) && node2.getName().equals(dntCurrent.getName())) {
                            union = 1;
                            dependencies = 1.0;
                        }
                    }
                }
                for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : candidateEntities) {
                    DeclarationNodeTree node1 = pair.getLeft();
                    DeclarationNodeTree node2 = pair.getRight();
                    if (node1.getType() == EntityType.CLASS && node2.getType() == EntityType.CLASS) {
                        if (node1.getName().equals(dntBefore.getName()) && node2.getName().equals(dntCurrent.getName())) {
                            union = 1;
                            dependencies = 1.0;
                        }
                    }
                }
                if (dependencies == 0.0 && !(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                        matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))))
                    return 0.49;
            }
            List<IExtendedModifier> modifiers1 = declaration1.modifiers();
            List<IExtendedModifier> modifiers2 = declaration2.modifiers();
            boolean isTest1 = false;
            boolean isTest2 = false;
            boolean isOverride1 = false;
            boolean isOverride2 = false;
            for (IExtendedModifier modifier : modifiers1) {
                if (modifier.isAnnotation() && modifier.toString().equals("@Test"))
                    isTest1 = true;
                if (modifier.isAnnotation() && modifier.toString().equals("@Override"))
                    isOverride1 = true;
            }
            for (IExtendedModifier modifier : modifiers2) {
                if (modifier.isAnnotation() && modifier.toString().equals("@Test"))
                    isTest2 = true;
                if (modifier.isAnnotation() && modifier.toString().equals("@Override"))
                    isOverride2 = true;
            }
            if (union == 0 && isTest1 && isTest2) {
                if (!(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                        matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())))) {
                    Block body1 = declaration1.getBody();
                    Block body2 = declaration2.getBody();
                    if (body1 != null && body1.toString().equals("{\n}\n") && body2 != null && body2.toString().equals("{\n}\n") &&
                            dependencies == 0.0)
                        return 0.49;
                    if (body1 != null && body1.toString().equals("{\n}\n") && body2 != null && !body2.toString().equals("{\n}\n") &&
                            dependencies == 0.0)
                        return 0.49;
                    if (body1 != null && !body1.toString().equals("{\n}\n") && body2 != null && body2.toString().equals("{\n}\n") &&
                            dependencies == 0.0)
                        return 0.49;
                }
                if (dntBefore.getName().equals(dntCurrent.getName()) ||
                        matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                        matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))) {
                    return descendants + 0.01 * biGram;
                } else
                    return 0.8 * descendants + 0.01 * biGram;
            }
            if (isTest1 && !isTest2)
                return 0.49;
            if (!isTest1 && isTest2)
                return 0.49;
            if (isOverride1 && !isOverride2 && !dntBefore.getName().equals(dntCurrent.getName()) &&
                    !(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                            matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))))
                return 0.49;
            if (!isOverride1 && isOverride2 && !dntBefore.getName().equals(dntCurrent.getName()) &&
                    !(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                            matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))))
                return 0.49;
            if (MethodUtils.isSetter(declaration1) && !MethodUtils.isSetter(declaration2))
                return 0.49;
            if (!MethodUtils.isSetter(declaration1) && MethodUtils.isSetter(declaration2))
                return 0.49;
            if (MethodUtils.isGetter(declaration1) && !MethodUtils.isGetter(declaration2))
                return 0.49;
            if (!MethodUtils.isGetter(declaration1) && MethodUtils.isGetter(declaration2))
                return 0.49;
            if (MethodUtils.isGetter(declaration1) && MethodUtils.isGetter(declaration2) && !dntBefore.getName().equals(dntCurrent.getName()) &&
                    !(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                            matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))))
                return 0.49;
            if (!(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                    matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())))) {
                Block body1 = declaration1.getBody();
                Block body2 = declaration2.getBody();
                if ((body1 == null || body1.toString().equals("{\n}\n")) && (body2 == null || body2.toString().equals("{\n}\n")) &&
                        dependencies == 0.0)
                    return 0.49;
            }
        }
        if (dntBefore.getType() == EntityType.ENUM_CONSTANT && dntCurrent.getType() == EntityType.ENUM_CONSTANT) {
            List<DeclarationNodeTree> children1 = dntBefore.getChildren();
            List<DeclarationNodeTree> children2 = dntBefore.getChildren();
            boolean containSameElement = false;
            boolean isSameEnum = dntBefore.getParent().getName().equals(dntCurrent.getParent().getName());
            for (DeclarationNodeTree child1 : children1) {
                for (DeclarationNodeTree child2 : children2) {
                    if (!child1.getName().equals(child2.getName())) {
                        containSameElement = true;
                        break;
                    }
                }
            }
            if (!isSameEnum && !containSameElement) {
                return dependencies + 0.01 * biGram;
            } else {
                NormalizedLevenshtein levenshtein = new NormalizedLevenshtein();
                String name1 = dntBefore.getName();
                String name2 = dntCurrent.getName();
                double distance = levenshtein.distance(name1, name2);
                if (distance > 0.8)
                    return 0.49;
                descendants = 1.0 - distance;
            }
        }
        if (dntBefore.getType() == EntityType.FIELD && dntCurrent.getType() == EntityType.FIELD) {
            if (!(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                    matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))) &&
                    dependencies == 0.0) {
                Set<DeclarationNodeTree> candidateEntitiesLeft = matchPair.getDeletedEntities();
                Set<DeclarationNodeTree> candidateEntitiesRight = matchPair.getAddedEntities();
                boolean isMulti1 = false;
                boolean isMulti2 = false;
                for (DeclarationNodeTree entity : candidateEntitiesLeft) {
                    if (entity.getType() != EntityType.FIELD)
                        continue;
                    if (entity == dntBefore)
                        continue;
                    FieldDeclaration declaration1 = (FieldDeclaration) entity.getDeclaration();
                    FieldDeclaration declaration2 = (FieldDeclaration) dntBefore.getDeclaration();
                    VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) declaration1.fragments().get(0);
                    VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) declaration2.fragments().get(0);
                    if (declaration1.getType().toString().equals(declaration2.getType().toString()) &&
                            fragment1.getName().getIdentifier().equals(fragment2.getName().getIdentifier()) &&
                            !dntBefore.getNamespace().equals(entity.getNamespace())) {
                        isMulti1 = true;
                        break;
                    }
                }
                for (DeclarationNodeTree entity : candidateEntitiesRight) {
                    if (entity.getType() != EntityType.FIELD)
                        continue;
                    if (entity == dntCurrent)
                        continue;
                    FieldDeclaration declaration1 = (FieldDeclaration) entity.getDeclaration();
                    FieldDeclaration declaration2 = (FieldDeclaration) dntCurrent.getDeclaration();
                    VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) declaration1.fragments().get(0);
                    VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) declaration2.fragments().get(0);
                    if (declaration1.getType().toString().equals(declaration2.getType().toString()) &&
                            fragment1.getName().getIdentifier().equals(fragment2.getName().getIdentifier()) &&
                            !entity.getNamespace().equals(dntCurrent.getNamespace())) {
                        isMulti2 = true;
                        break;
                    }
                }
                if (isMulti1 || isMulti2)
                    return 0.49;
                if (!dntBefore.getName().equals(dntCurrent.getName()))
                    return 0.49;
            }
            List<DeclarationNodeTree> children = dntCurrent.getParent().getChildren();
            for (DeclarationNodeTree child : children) {
                if (child.getType() != EntityType.FIELD) continue;
                if (child == dntCurrent) continue;
                FieldDeclaration fd1 = (FieldDeclaration) dntCurrent.getDeclaration();
                FieldDeclaration fd2 = (FieldDeclaration) child.getDeclaration();
                if (fd1.getType().toString().equals(fd2.getType().toString())) {
                    double dice = calculateDiceSimilarity((LeafNode) dntBefore, (LeafNode) child);
                    if (dice == descendants) {
                        Set<EntityInfo> dependencies1 = new HashSet<>(dntCurrent.getDependencies());
                        Set<EntityInfo> dependencies2 = new HashSet<>(child.getDependencies());
                        String name1 = dntCurrent.getName();
                        String name2 = child.getName();
                        char c1 = name1.charAt(name1.length() - 1);
                        char c2 = name2.charAt(name2.length() - 1);
                        if (dependencies1.equals(dependencies2)) {
                            if (name1.substring(0, name1.length() - 1).equals(name2.substring(0, name2.length() - 1)) &&
                                    Character.isDigit(c1) && Character.isDigit(c2))
                                return 0.49;
                            name1 = name1.toLowerCase();
                            name2 = name2.toLowerCase();
                            if (name1.contains("prefix") && name2.contains("suffix")) {
                                name1 = name1.replace("prefix", "");
                                name2 = name2.replace("suffix", "");
                                if (name1.equals(name2))
                                    return 0.49;
                            }
                            if (name1.contains("suffix") && name2.contains("prefix")) {
                                name1 = name1.replace("suffix", "");
                                name2 = name2.replace("prefix", "");
                                if (name1.equals(name2))
                                    return 0.49;
                            }
                        }
                    }
                }
            }
            if ((matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                    matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))) &&
                    dependencies == 0.0 && dntBefore.getDependencies().isEmpty() && dntCurrent.getDependencies().isEmpty()) {
                FieldDeclaration declaration1 = (FieldDeclaration) dntBefore.getDeclaration();
                FieldDeclaration declaration2 = (FieldDeclaration) dntCurrent.getDeclaration();
                VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) declaration1.fragments().get(0);
                VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) declaration2.fragments().get(0);
                if (!declaration1.getType().toString().equals(declaration2.getType().toString()) &&
                        !fragment1.getName().getIdentifier().equals(fragment2.getName().getIdentifier())) {
                    if (descendants <= 0.5)
                        return 0.49;
                }
            }
        }
        if ((dntBefore.getType() == EntityType.CLASS || dntBefore.getType() == EntityType.INTERFACE || dntBefore.getType() == EntityType.ENUM) &&
                (dntCurrent.getType() == EntityType.CLASS || dntCurrent.getType() == EntityType.INTERFACE || dntCurrent.getType() == EntityType.ENUM)) {
            if (!dntBefore.hasChildren() && !dntCurrent.hasChildren() && dntBefore.getType() != dntCurrent.getType() && !dntBefore.getName().equals(dntCurrent.getName())) {
                return 0.49;
            }
            Set<DeclarationNodeTree> candidateEntitiesLeft = matchPair.getDeletedEntities();
            Set<DeclarationNodeTree> candidateEntitiesRight = matchPair.getAddedEntities();
            boolean isMulti1 = false;
            boolean isMulti2 = false;
            for (DeclarationNodeTree entity : candidateEntitiesLeft) {
                if (entity == dntBefore)
                    continue;
                if (dntBefore.getType() == entity.getType() &&
                        dntBefore.getName().equals(entity.getName()) &&
                        !dntBefore.getNamespace().equals(entity.getNamespace())) {
                    isMulti1 = true;
                    break;
                }
            }
            for (DeclarationNodeTree entity : candidateEntitiesRight) {
                if (entity == dntCurrent)
                    continue;
                if (entity.getType() == dntCurrent.getType() &&
                        entity.getName().equals(dntCurrent.getName()) &&
                        !entity.getNamespace().equals(dntCurrent.getNamespace())) {
                    isMulti2 = true;
                    break;
                }
            }
            if (isMulti1 || isMulti2) {
                return descendants + 0.01 * biGram;
            }
            if (dependencies == 1.0 && dntBefore.getDependencies().size() < 4 && dntCurrent.getDependencies().size() < 4 &&
                    new HashSet<>(dntBefore.getDependencies()).size() == 1 && new HashSet<>(dntCurrent.getDependencies()).size() == 1 &&
                    descendants == 0.0 && !(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                    matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())))) {
                return 0.49;
            }
        }
        if (dependencies == 1.0 && dntBefore.getDependencies().size() < 4 && dntCurrent.getDependencies().size() < 4 &&
                new HashSet<>(dntBefore.getDependencies()).size() == 1 && new HashSet<>(dntCurrent.getDependencies()).size() == 1 &&
                dntBefore.getType() == EntityType.FIELD && dntCurrent.getType() == EntityType.FIELD && descendants == 0.0) {
            if (!(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                    matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))))
                return 0.49;
            EntityInfo dependency1 = dntBefore.getDependencies().get(0);
            EntityInfo dependency2 = dntCurrent.getDependencies().get(0);
            if (!dependency1.getName().equals(dependency2.getName()))
                return 0.49;
        }
        if (dependencies == 1.0 && dntBefore.getDependencies().size() < 4 && dntCurrent.getDependencies().size() < 4 &&
                new HashSet<>(dntBefore.getDependencies()).size() == 1 && new HashSet<>(dntCurrent.getDependencies()).size() == 1 &&
                dntBefore.getType() == EntityType.METHOD && dntCurrent.getType() == EntityType.METHOD &&
                ((MethodDeclaration) dntBefore.getDeclaration()).getBody() != null &&
                ((MethodDeclaration) dntCurrent.getDeclaration()).getBody() != null) {
            if (dntBefore.getName().equals(dntCurrent.getName()) && (matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                    matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))))
                return descendants * 3;
            if (!(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                    matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))) &&
                    !dntBefore.getName().equals(dntCurrent.getName()))
                return descendants;
        }
        return (union == 0 ? descendants : 0.5 * descendants + 0.5 * dependencies) + 0.01 * biGram;
    }

    public static double calculateContextSimilarity(MatchPair originalPair, MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        if (statement1 instanceof BlockNode && statement2 instanceof BlockNode && statement1.getParent() instanceof ControlNode && statement2.getParent() instanceof ControlNode) {
            if (matchPair.getMatchedStatements().contains(Pair.of(statement1.getParent(), statement2.getParent())) ||
                    matchPair.getCandidateStatements().contains(Pair.of(statement1.getParent(), statement2.getParent())))
                return 1.0;
        }
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = originalPair.getMatchedEntities();
        MethodNode root1 = statement1.getRoot();
        MethodNode root2 = statement2.getRoot();
        DeclarationNodeTree entity1 = root1.getMethodEntity();
        DeclarationNodeTree entity2 = root2.getMethodEntity();
        List<StatementNodeTree> children1 = new ArrayList<>();
        List<StatementNodeTree> children2 = new ArrayList<>();
        if (matchedEntities.contains(Pair.of(entity1, entity2))) {
            StatementNodeTree parent1 = statement1.getParent();
            StatementNodeTree parent2 = statement2.getParent();
            children1 = parent1.getChildren();
            children2 = parent2.getChildren();
        } else {
            MethodNode higherRoot1 = statement1.getHigherRoot();
            MethodNode higherRoot2 = statement2.getHigherRoot();
            if (root1 == higherRoot1) {
                StatementNodeTree parent = statement1.getParent();
                children1 = parent.getChildren();
            } else {
                List<StatementNodeTree> allBlocks = higherRoot1.getAllBlocks();
                for (StatementNodeTree block : allBlocks) {
                    if (block.getChildren().contains(statement1)) {
                        children1 = block.getChildren();
                        break;
                    }
                }
            }
            if (root2 == higherRoot2) {
                StatementNodeTree parent = statement2.getParent();
                children2 = parent.getChildren();
            } else {
                List<StatementNodeTree> allBlocks = higherRoot2.getAllBlocks();
                for (StatementNodeTree block : allBlocks) {
                    if (block.getChildren().contains(statement2)) {
                        children2 = block.getChildren();
                        break;
                    }
                }
            }
        }
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
                if (originalPair.getDeletedStatements().contains(nextNode1) && (next1 + 1) < children1.size()) {
                    nextNode1 = children1.get(next1 + 1);
                }
                StatementNodeTree previousNode2 = children2.get(previous2);
                StatementNodeTree nextNode2 = children2.get(next2);
                if (originalPair.getAddedStatements().contains(nextNode2) && (next2 + 1) < children2.size()) {
                    nextNode2 = children2.get(next2 + 1);
                }
                if ((matchPair.getMatchedStatements().contains(Pair.of(previousNode1, previousNode2)) ||
                        matchPair.getCandidateStatements().contains(Pair.of(previousNode1, previousNode2))) &&
                        (matchPair.getMatchedStatements().contains(Pair.of(nextNode1, nextNode2)) ||
                                matchPair.getCandidateStatements().contains(Pair.of(nextNode1, nextNode2))))
                    return 1.0;
            }
            if (previous1 == -1 && previous2 == -1 && next1 < children1.size() && next2 < children2.size()) {
                StatementNodeTree nextNode1 = children1.get(next1);
                if (matchPair.getDeletedStatements().contains(nextNode1) && (next1 + 1) < children1.size()) {
                    nextNode1 = children1.get(next1 + 1);
                }
                StatementNodeTree nextNode2 = children2.get(next2);
                if (matchPair.getAddedStatements().contains(nextNode2) && (next2 + 1) < children2.size()) {
                    nextNode2 = children2.get(next2 + 1);
                }
                if (matchPair.getMatchedStatements().contains(Pair.of(nextNode1, nextNode2)) ||
                        matchPair.getCandidateStatements().contains(Pair.of(nextNode1, nextNode2)))
                    return 1.0;
            }
            if (previous1 >= 0 && previous2 >= 0 && next1 == children1.size() && next2 == children2.size()) {
                StatementNodeTree previousNode1 = children1.get(previous1);
                StatementNodeTree previousNode2 = children2.get(previous2);
                if (matchPair.getMatchedStatements().contains(Pair.of(previousNode1, previousNode2)) ||
                        matchPair.getCandidateStatements().contains(Pair.of(previousNode1, previousNode2)))
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
            if (statement1 instanceof ControlNode && statement2 instanceof ControlNode &&
                    statement1.getDepth() == 1 && statement2.getDepth() == 1)
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
        return calculateDiceSimilarity(list1, list2);
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
        return calculateSimilarity(matchPair, matchPair, statement1, statement2);
    }

    public static double calculateSimilarity(MatchPair originalPair, MatchPair matchPair, StatementNodeTree statement1, StatementNodeTree statement2) {
        double descendants = 0.0;
        double contexts = calculateContextSimilarity(originalPair, matchPair, statement1, statement2);
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
        if (contexts == 1.0 && descendants < 0.15 && statement1.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT &&
                statement2.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
            VariableDeclarationStatement variable1 = (VariableDeclarationStatement) statement1.getStatement();
            VariableDeclarationStatement variable2 = (VariableDeclarationStatement) statement2.getStatement();
            Type type1 = variable1.getType();
            Type type2 = variable2.getType();
            List<VariableDeclarationFragment> fragments1 = variable1.fragments();
            List<VariableDeclarationFragment> fragments2 = variable2.fragments();
            VariableDeclarationFragment fragment1 = fragments1.get(0);
            VariableDeclarationFragment fragment2 = fragments2.get(0);
            if (!StringUtils.equals(type1.toString(), type2.toString()) &&
                    !StringUtils.equals(fragment1.getName().getIdentifier(), fragment2.getName().getIdentifier()))
                return 0.49;
        }
        return (descendants + contexts) / 2;
    }

    public static double calculateBodyDice(LeafNode oldNode, LeafNode newNode, LeafNode anotherNode) {
        if (oldNode.getType() == EntityType.METHOD && newNode.getType() == EntityType.METHOD) {
            JDTService jdtService = new JDTServiceImpl();
            MethodDeclaration oldDeclaration = (MethodDeclaration) oldNode.getDeclaration();
            Block oldBody = oldDeclaration.getBody();
            List<ChildNode> list1 = jdtService.getDescendants(oldBody);
            MethodDeclaration newDeclaration = (MethodDeclaration) newNode.getDeclaration();
            Block newBody = newDeclaration.getBody();
            List<ChildNode> list2 = jdtService.getDescendants(newBody);
            MethodDeclaration anotherDeclaration = (MethodDeclaration) anotherNode.getDeclaration();
            Block anotherBody = anotherDeclaration.getBody();
            List<ChildNode> list3 = jdtService.getDescendants(anotherBody);
            return calculateBodyDice(list1, list2, list3);
        }
        if (oldNode.getType() == EntityType.INITIALIZER && newNode.getType() == EntityType.INITIALIZER) {
            JDTService jdtService = new JDTServiceImpl();
            Initializer oldDeclaration = (Initializer) oldNode.getDeclaration();
            Block oldBody = oldDeclaration.getBody();
            List<ChildNode> list1 = jdtService.getDescendants(oldBody);
            Initializer newDeclaration = (Initializer) newNode.getDeclaration();
            Block newBody = newDeclaration.getBody();
            List<ChildNode> list2 = jdtService.getDescendants(newBody);
            MethodDeclaration anotherDeclaration = (MethodDeclaration) anotherNode.getDeclaration();
            Block anotherBody = anotherDeclaration.getBody();
            List<ChildNode> list3 = jdtService.getDescendants(anotherBody);
            return calculateBodyDice(list1, list2, list3);
        }
        return 0.0;
    }

    public static double calculateBodyDice(List<ChildNode> list1, List<ChildNode> list2, List<ChildNode> list3) {
        int intersection = 0;
        Set<Integer> matched = new HashSet<>();
        for (ChildNode node2 : list2) {
            for (int i = 0; i < list1.size(); i++) {
                if (matched.contains(i)) continue;
                ChildNode node1 = list1.get(i);
                if (node2.equals(node1)) {
                    matched.add(i);
                    break;
                }
            }
        }
        for (int i = list1.size() - 1; i >= 0; i--) {
            if (matched.contains(i))
                list1.remove(i);
        }
        matched.clear();
        for (ChildNode node3 : list3) {
            for (int i = 0; i < list1.size(); i++) {
                if (matched.contains(i)) continue;
                ChildNode node1 = list1.get(i);
                if (node3.equals(node1)) {
                    intersection++;
                    matched.add(i);
                    break;
                }
            }
        }
        return list3.isEmpty() ? 0 : 1.0 * intersection / list3.size();
    }

    public static double calculateBodyDice(VariableDeclarationFragment fragment, StatementNodeTree oldStatement, StatementNodeTree newStatement) {
        JDTService jdtService = new JDTServiceImpl();
        List<ChildNode> list1 = new ArrayList<>();
        List<ChildNode> list2 = new ArrayList<>();
        List<ChildNode> list3 = jdtService.getDescendants(fragment.getInitializer());
        if (oldStatement.getType() == StatementType.DO_STATEMENT && newStatement.getType() == StatementType.DO_STATEMENT) {
            DoStatement statement1 = (DoStatement) oldStatement.getStatement();
            DoStatement statement2 = (DoStatement) newStatement.getStatement();
            list1.addAll(jdtService.getDescendants(statement1.getExpression()));
            list2.addAll(jdtService.getDescendants(statement2.getExpression()));
            return calculateBodyDice(list1, list2, list3);
        } else if (oldStatement.getType() == StatementType.ENHANCED_FOR_STATEMENT && newStatement.getType() == StatementType.ENHANCED_FOR_STATEMENT) {
            EnhancedForStatement statement1 = (EnhancedForStatement) oldStatement.getStatement();
            EnhancedForStatement statement2 = (EnhancedForStatement) newStatement.getStatement();
            list1.addAll(jdtService.getDescendants(statement1.getParameter()));
            list1.addAll(jdtService.getDescendants(statement1.getExpression()));
            list2.addAll(jdtService.getDescendants(statement2.getParameter()));
            list2.addAll(jdtService.getDescendants(statement2.getExpression()));
            return calculateBodyDice(list1, list2, list3);
        } else if (oldStatement.getType() == StatementType.FOR_STATEMENT && newStatement.getType() == StatementType.FOR_STATEMENT) {
            ForStatement statement1 = (ForStatement) oldStatement.getStatement();
            ForStatement statement2 = (ForStatement) newStatement.getStatement();
            List<Expression> initializers1 = statement1.initializers();
            Expression expression1 = statement1.getExpression();
            List<Expression> updaters1 = statement1.updaters();
            List<Expression> initializers2 = statement2.initializers();
            Expression expression2 = statement2.getExpression();
            List<Expression> updaters2 = statement2.updaters();
            for (Expression expression : initializers1) {
                list1.addAll(jdtService.getDescendants(expression));
            }
            list1.addAll(jdtService.getDescendants(expression1));
            for (Expression expression : updaters1) {
                list1.addAll(jdtService.getDescendants(expression));
            }
            for (Expression expression : initializers2) {
                list2.addAll(jdtService.getDescendants(expression));
            }
            list2.addAll(jdtService.getDescendants(expression2));
            for (Expression expression : updaters2) {
                list2.addAll(jdtService.getDescendants(expression));
            }
            return calculateBodyDice(list1, list2, list3);
        } else if (oldStatement.getType() == StatementType.IF_STATEMENT && newStatement.getType() == StatementType.IF_STATEMENT) {
            IfStatement statement1 = (IfStatement) oldStatement.getStatement();
            IfStatement statement2 = (IfStatement) newStatement.getStatement();
            list1.addAll(jdtService.getDescendants(statement1.getExpression()));
            list2.addAll(jdtService.getDescendants(statement2.getExpression()));
            return calculateBodyDice(list1, list2, list3);
        } else if (oldStatement.getType() == StatementType.SWITCH_STATEMENT && newStatement.getType() == StatementType.SWITCH_STATEMENT) {
            SwitchStatement statement1 = (SwitchStatement) oldStatement.getStatement();
            SwitchStatement statement2 = (SwitchStatement) newStatement.getStatement();
            list1.addAll(jdtService.getDescendants(statement1.getExpression()));
            list2.addAll(jdtService.getDescendants(statement2.getExpression()));
            return calculateBodyDice(list1, list2, list3);
        } else if (oldStatement.getType() == StatementType.TRY_STATEMENT && newStatement.getType() == StatementType.TRY_STATEMENT) {
            TryStatement statement1 = (TryStatement) oldStatement.getStatement();
            TryStatement statement2 = (TryStatement) newStatement.getStatement();
            List<Expression> resources1 = statement1.resources();
            List<Expression> resources2 = statement2.resources();
            for (Expression expression : resources1) {
                list1.addAll(jdtService.getDescendants(expression));
            }
            for (Expression expression : resources2) {
                list2.addAll(jdtService.getDescendants(expression));
            }
            return calculateBodyDice(list1, list2, list3);
        } else if (oldStatement.getType() == StatementType.WHILE_STATEMENT && newStatement.getType() == StatementType.WHILE_STATEMENT) {
            WhileStatement statement1 = (WhileStatement) oldStatement.getStatement();
            WhileStatement statement2 = (WhileStatement) newStatement.getStatement();
            list1.addAll(jdtService.getDescendants(statement1.getExpression()));
            list2.addAll(jdtService.getDescendants(statement2.getExpression()));
            return calculateBodyDice(list1, list2, list3);
        } else if (oldStatement.getType() == StatementType.CATCH_CLAUSE && newStatement.getType() == StatementType.CATCH_CLAUSE) {
            CatchClause statement1 = (CatchClause) oldStatement.getStatement();
            CatchClause statement2 = (CatchClause) newStatement.getStatement();
            list1.addAll(jdtService.getDescendants(statement1.getException()));
            list2.addAll(jdtService.getDescendants(statement2.getException()));
            return calculateBodyDice(list1, list2, list3);
        }
        list1 = jdtService.getDescendants(oldStatement.getStatement());
        list2 = jdtService.getDescendants(newStatement.getStatement());
        return calculateBodyDice(list1, list2, list3);
    }

    public static double calculateMethodInvocation(MethodDeclaration md1, MethodDeclaration md2) {
        List<IMethodBinding> list1 = new ArrayList<>();
        List<IMethodBinding> list2 = new ArrayList<>();
        md1.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                IMethodBinding iMethodBinding = node.resolveMethodBinding();
                if (iMethodBinding != null)
                    list1.add(iMethodBinding);
                return super.visit(node);
            }
        });
        md2.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                IMethodBinding iMethodBinding = node.resolveMethodBinding();
                if (iMethodBinding != null)
                    list2.add(iMethodBinding);
                return super.visit(node);
            }
        });
        int intersection = 0;
        int union = list1.size() + list2.size();
        Set<Integer> matched = new HashSet<>();
        for (IMethodBinding binding1 : list1) {
            for (int i = 0; i < list2.size(); i++) {
                if (matched.contains(i)) continue;
                IMethodBinding binding2 = list2.get(i);
                if (StringUtils.equals(binding1.toString(), binding2.toString())) {
                    intersection++;
                    matched.add(i);
                    break;
                }
            }
        }
        double dependencies = union == 0 ? 0 : 2.0 * intersection / union;
        return dependencies;
    }
}
