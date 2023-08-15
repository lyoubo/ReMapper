package org.remapper.service;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.lib.Repository;
import org.remapper.refactoring.*;
import org.remapper.dto.*;
import org.remapper.handler.MatchingHandler;
import org.remapper.util.DiceFunction;
import org.remapper.util.GitServiceImpl;
import org.remapper.util.MethodUtils;
import org.remapper.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReExtractor {

    public List<Refactoring> discover(String projectPath, String commitId) throws IOException {
        GitService gitService = new GitServiceImpl();
        MatchService matchService = new MatchServiceImpl(projectPath);
        List<Refactoring> refactorings = new ArrayList<>();
        try (Repository repo = gitService.openRepository(projectPath)) {
            matchService.matchAtCommit(repo, commitId, new MatchingHandler() {
                @Override
                public void handle(String commitId, MatchPair matchPair) {
                    Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
                    for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
                        DeclarationNodeTree left = pair.getLeft();
                        DeclarationNodeTree right = pair.getRight();
                        Pair<DeclarationNodeTree, DeclarationNodeTree> namespace = Pair.of(left.getParent(), right.getParent());
                        if (left.getType() == EntityType.METHOD && right.getType() == EntityType.METHOD &&
                                (StringUtils.equals(left.getNamespace(), right.getNamespace()) || matchedEntities.contains(namespace))) {
                            MethodDeclaration leftDeclaration = (MethodDeclaration) left.getDeclaration();
                            MethodDeclaration rightDeclaration = (MethodDeclaration) right.getDeclaration();
                            // 1. Rename Method
                            if (!StringUtils.equals(leftDeclaration.getName().getFullyQualifiedName(), rightDeclaration.getName().getFullyQualifiedName())) {
                                if (!leftDeclaration.isConstructor() || !rightDeclaration.isConstructor()) {
                                    RenameMethodRefactoring refactoring = new RenameMethodRefactoring(left.getLocation(), right.getLocation());
                                    refactoring.setDisplayName(leftDeclaration, rightDeclaration, right.getNamespace());
                                    refactorings.add(refactoring);
                                }
                            }
                            // 2. Change Return Type
                            if (leftDeclaration.getReturnType2() != null && rightDeclaration.getReturnType2() != null &&
                                    !StringUtils.equals(leftDeclaration.getReturnType2().toString(), rightDeclaration.getReturnType2().toString())) {
                                ChangeReturnTypeRefactoring refactoring = new ChangeReturnTypeRefactoring(left.getLocation(), right.getLocation());
                                refactoring.setDisplayName(leftDeclaration, rightDeclaration, right.getNamespace());
                                refactorings.add(refactoring);
                            }
                        } else if (left.getType() == EntityType.FIELD && right.getType() == EntityType.FIELD &&
                                (StringUtils.equals(left.getNamespace(), right.getNamespace()) || matchedEntities.contains(namespace))) {
                            FieldDeclaration leftDeclaration = (FieldDeclaration) left.getDeclaration();
                            FieldDeclaration rightDeclaration = (FieldDeclaration) right.getDeclaration();
                            VariableDeclarationFragment leftFragment = (VariableDeclarationFragment) leftDeclaration.fragments().get(0);
                            VariableDeclarationFragment rightFragment = (VariableDeclarationFragment) rightDeclaration.fragments().get(0);
                            // 3. Rename Attribute
                            if (!StringUtils.equals(leftFragment.getName().getFullyQualifiedName(), rightFragment.getName().getFullyQualifiedName())) {
                                RenameAttributeRefactoring refactoring = new RenameAttributeRefactoring(left.getLocation(), right.getLocation());
                                refactoring.setDisplayName(leftDeclaration, rightDeclaration, right.getNamespace());
                                refactorings.add(refactoring);
                            }
                            // 4. Change Attribute Type
                            if (!StringUtils.equals(leftDeclaration.getType().toString(), rightDeclaration.getType().toString())) {
                                ChangeAttributeTypeRefactoring refactoring = new ChangeAttributeTypeRefactoring(left.getLocation(), right.getLocation());
                                refactoring.setDisplayName(leftDeclaration, rightDeclaration, right.getNamespace());
                                refactorings.add(refactoring);
                            }
                        } else if (left.getType() == EntityType.ENUM_CONSTANT && right.getType() == EntityType.ENUM_CONSTANT &&
                                (StringUtils.equals(left.getNamespace(), right.getNamespace()) || matchedEntities.contains(namespace))) {
                            EnumConstantDeclaration leftDeclaration = (EnumConstantDeclaration) left.getDeclaration();
                            EnumConstantDeclaration rightDeclaration = (EnumConstantDeclaration) right.getDeclaration();
                            // 3. Rename Attribute
                            if (!StringUtils.equals(leftDeclaration.getName().getFullyQualifiedName(), rightDeclaration.getName().getFullyQualifiedName())) {
                                RenameAttributeRefactoring refactoring = new RenameAttributeRefactoring(left.getLocation(), right.getLocation());
                                refactoring.setDisplayName(leftDeclaration, rightDeclaration, left.getParent().getName(), right.getParent().getName(), right.getNamespace());
                                refactorings.add(refactoring);
                            }
                        } else if ((left.getType() == EntityType.CLASS && right.getType() == EntityType.CLASS) ||
                                (left.getType() == EntityType.INTERFACE && right.getType() == EntityType.INTERFACE) ||
                                (left.getType() == EntityType.ENUM && right.getType() == EntityType.ENUM)) {
                            AbstractTypeDeclaration leftDeclaration = (AbstractTypeDeclaration) left.getDeclaration();
                            AbstractTypeDeclaration rightDeclaration = (AbstractTypeDeclaration) right.getDeclaration();
                            // 5. Rename Class
                            if (!StringUtils.equals(leftDeclaration.getName().getFullyQualifiedName(), rightDeclaration.getName().getFullyQualifiedName()) &&
                                    StringUtils.equals(left.getNamespace(), right.getNamespace())) {
                                RenameClassRefactoring refactoring = new RenameClassRefactoring(left.getLocation(), right.getLocation());
                                refactoring.setDisplayName(left.getName(), right.getName(), right.getNamespace());
                                refactorings.add(refactoring);
                            }
                            // 6. Move Class
                            if (!StringUtils.equals(left.getNamespace(), right.getNamespace()) &&
                                    !matchedEntities.contains(Pair.of(left.getParent(), right.getParent())) &&
                                    StringUtils.equals(leftDeclaration.getName().getFullyQualifiedName(), rightDeclaration.getName().getFullyQualifiedName())) {
                                MoveClassRefactoring refactoring = new MoveClassRefactoring(left.getLocation(), right.getLocation());
                                refactoring.setDisplayName(left.getNamespace(), left.getName(), right.getNamespace(), right.getName());
                                refactorings.add(refactoring);
                            }
                        }
                    }
                    Set<DeclarationNodeTree> addedEntities = matchPair.getAddedEntities();
                    for (DeclarationNodeTree entity : addedEntities) {
                        if (entity.getType() == EntityType.METHOD) {
                            List<EntityInfo> dependencies = entity.getDependencies();
                            for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
                                DeclarationNodeTree left = pair.getLeft();
                                DeclarationNodeTree right = pair.getRight();
                                // 7. Extract Method
                                if (right.getType() == EntityType.METHOD && StringUtils.equals(left.getNamespace(), entity.getNamespace())) {
                                    double dice = DiceFunction.calculateSingleDice((LeafNode) entity, (LeafNode) left);
                                    if (dice < 0.5 || MethodUtils.isNewFunction(left.getDeclaration(), right.getDeclaration()))
                                        continue;
                                    if (dependencies.contains(right.getEntity())) {
                                        ExtractMethodRefactoring refactoring = new ExtractMethodRefactoring(left.getLocation(), entity.getLocation());
                                        refactoring.setDisplayName((MethodDeclaration) entity.getDeclaration(), (MethodDeclaration) left.getDeclaration(), right.getNamespace());
                                        refactorings.add(refactoring);
                                    } else {
                                        Set<Integer> set = new HashSet<>();
                                        MethodDeclaration addedMethodDeclaration = (MethodDeclaration) entity.getDeclaration();
                                        int size = addedMethodDeclaration.parameters().size();
                                        MethodDeclaration rightMethodDeclaration = (MethodDeclaration) right.getDeclaration();
                                        rightMethodDeclaration.accept(new ASTVisitor() {
                                            @Override
                                            public boolean visit(LambdaExpression node) {
                                                node.accept(new ASTVisitor() {
                                                    @Override
                                                    public boolean visit(MethodInvocation node) {
                                                        if (node.getName().getFullyQualifiedName().equals(entity.getName()) &&
                                                                node.arguments().size() == size) {
                                                            set.add(1);
                                                        }
                                                        return true;
                                                    }
                                                });
                                                return true;
                                            }
                                        });
                                        if (set.size() == 0)
                                            continue;
                                        ExtractMethodRefactoring refactoring = new ExtractMethodRefactoring(left.getLocation(), entity.getLocation());
                                        refactoring.setDisplayName((MethodDeclaration) entity.getDeclaration(), (MethodDeclaration) left.getDeclaration(), right.getNamespace());
                                        refactorings.add(refactoring);
                                    }
                                }
                            }
                        } else if (entity.getType() == EntityType.CLASS) {
                            // 8. Extract Class
                            Set<DeclarationNodeTree> set = new HashSet<>();
                            for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
                                DeclarationNodeTree left = pair.getLeft();
                                DeclarationNodeTree right = pair.getRight();
                                if (right.getParent() == entity) {
                                    set.add(left.getParent());
                                    if (left.getType() == EntityType.METHOD && right.getType() == EntityType.METHOD) {
                                        MethodDeclaration leftDeclaration = (MethodDeclaration) left.getDeclaration();
                                        MethodDeclaration rightDeclaration = (MethodDeclaration) right.getDeclaration();
                                        // 2. Change Return Type
                                        if (leftDeclaration.getReturnType2() != null && rightDeclaration.getReturnType2() != null &&
                                                !StringUtils.equals(leftDeclaration.getReturnType2().toString(), rightDeclaration.getReturnType2().toString())) {
                                            ChangeReturnTypeRefactoring refactoring = new ChangeReturnTypeRefactoring(left.getLocation(), right.getLocation());
                                            refactoring.setDisplayName(leftDeclaration, rightDeclaration, right.getNamespace());
                                            refactorings.add(refactoring);
                                        }
                                    } else if (left.getType() == EntityType.FIELD && right.getType() == EntityType.FIELD) {
                                        FieldDeclaration leftDeclaration = (FieldDeclaration) left.getDeclaration();
                                        FieldDeclaration rightDeclaration = (FieldDeclaration) right.getDeclaration();
                                        // 4. Change Attribute Type
                                        if (!StringUtils.equals(leftDeclaration.getType().toString(), rightDeclaration.getType().toString())) {
                                            ChangeAttributeTypeRefactoring refactoring = new ChangeAttributeTypeRefactoring(left.getLocation(), right.getLocation());
                                            refactoring.setDisplayName(leftDeclaration, rightDeclaration, right.getNamespace());
                                            refactorings.add(refactoring);
                                        }
                                    }
                                }
                            }
                            for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair2 : matchedEntities) {
                                DeclarationNodeTree left = pair2.getLeft();
                                if (set.contains(left)) {
                                    DeclarationNodeTree right = pair2.getRight();
                                    if (entity.getType() == EntityType.CLASS && left.getType() == EntityType.CLASS && right.getType() == EntityType.CLASS &&
                                            !isSubType((TypeDeclaration) entity.getDeclaration(), (TypeDeclaration) right.getDeclaration()) &&
                                            !isSubType((TypeDeclaration) right.getDeclaration(), (TypeDeclaration) entity.getDeclaration())) {
                                        ExtractClassRefactoring refactoring = new ExtractClassRefactoring(left.getLocation(), entity.getLocation());
                                        refactoring.setDisplayName(entity.getNamespace(), entity.getName(), left.getNamespace(), left.getName());
                                        refactorings.add(refactoring);
                                    }
                                }
                            }
                        }
                    }
                    Set<DeclarationNodeTree> deletedEntities = matchPair.getDeletedEntities();
                    for (DeclarationNodeTree entity : deletedEntities) {
                        if (entity.getType() == EntityType.METHOD) {
                            List<EntityInfo> dependencies = entity.getDependencies();
                            for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
                                DeclarationNodeTree left = pair.getLeft();
                                DeclarationNodeTree right = pair.getRight();
                                // 9. Inline Method
                                if (left.getType() == EntityType.METHOD && StringUtils.equals(right.getNamespace(), entity.getNamespace())) {
                                    double dice = DiceFunction.calculateSingleDice((LeafNode) entity, (LeafNode) right);
                                    if (dice < 0.5 || MethodUtils.isNewFunction(right.getDeclaration(), left.getDeclaration()))
                                        continue;
                                    if (dependencies.contains(left.getEntity())) {
                                        InlineMethodRefactoring refactoring = new InlineMethodRefactoring(entity.getLocation(), right.getLocation());
                                        refactoring.setDisplayName((MethodDeclaration) entity.getDeclaration(), (MethodDeclaration) right.getDeclaration(), right.getNamespace());
                                        refactorings.add(refactoring);
                                    } else {
                                        Set<Integer> set = new HashSet<>();
                                        MethodDeclaration deletedMethodDeclaration = (MethodDeclaration) entity.getDeclaration();
                                        int size = deletedMethodDeclaration.parameters().size();
                                        MethodDeclaration leftDeclaration = (MethodDeclaration) left.getDeclaration();
                                        leftDeclaration.accept(new ASTVisitor() {
                                            @Override
                                            public boolean visit(LambdaExpression node) {
                                                node.accept(new ASTVisitor() {
                                                    @Override
                                                    public boolean visit(MethodInvocation node) {
                                                        if (node.getName().getFullyQualifiedName().equals(entity.getName()) &&
                                                                node.arguments().size() == size) {
                                                            set.add(1);
                                                        }
                                                        return true;
                                                    }
                                                });
                                                return true;
                                            }
                                        });
                                        if (set.size() == 0)
                                            continue;
                                        InlineMethodRefactoring refactoring = new InlineMethodRefactoring(entity.getLocation(), right.getLocation());
                                        refactoring.setDisplayName((MethodDeclaration) entity.getDeclaration(), (MethodDeclaration) right.getDeclaration(), right.getNamespace());
                                        refactorings.add(refactoring);
                                    }
                                }
                            }
                        }
                    }
                }

                @Override
                public void handleException(String commitId, Exception e) {
                    System.out.println(commitId);
                    e.printStackTrace();
                }
            });
        }
        return refactorings;
    }

    private boolean isSubType(TypeDeclaration node1, TypeDeclaration node2) {
        ITypeBinding binding = node1.resolveBinding();
        if (binding != null) {
            if (binding.getSuperclass() != null && binding.getSuperclass().getTypeDeclaration() != null)
                return StringUtils.equals(binding.getSuperclass().getTypeDeclaration().getName(), node2.getName().getFullyQualifiedName());
        }
        Type superclassType = node1.getSuperclassType();
        if (superclassType == null) return false;
        if (superclassType.isParameterizedType()) {
            String name = superclassType.toString().substring(0, superclassType.toString().indexOf("<"));
            return StringUtils.equals(name, node2.getName().getFullyQualifiedName());
        } else
            return StringUtils.equals(superclassType.toString(), node2.getName().getFullyQualifiedName());
    }
}
