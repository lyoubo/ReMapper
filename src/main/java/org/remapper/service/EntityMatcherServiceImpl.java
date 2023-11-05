package org.remapper.service;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.remapper.dto.*;
import org.remapper.handler.MatchingHandler;
import org.remapper.util.GitServiceImpl;
import org.remapper.util.JDTServiceImpl;
import org.remapper.util.MethodUtils;
import org.remapper.visitor.NodeUsageVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class EntityMatcherServiceImpl implements EntityMatcherService {

    @Override
    public void matchAtCommit(Repository repository, String commitId, MatchingHandler handler) {
        RevWalk walk = new RevWalk(repository);
        try {
            RevCommit commit = walk.parseCommit(repository.resolve(commitId));
            if (commit.getParentCount() > 0) {
                walk.parseCommit(commit.getParent(0));
                this.matchEntities(repository, commit, handler);
            }
        } catch (MissingObjectException ignored) {
        } catch (Exception e) {
            handler.handleException(commitId, e);
        } finally {
            walk.close();
            walk.dispose();
        }
    }

    @Override
    public void matchAtCommit(Repository repository, String commitId, MatchingHandler handler, int timeout) {
        ExecutorService service = Executors.newSingleThreadExecutor();
        Future<?> f = null;
        try {
            Runnable r = () -> matchAtCommit(repository, commitId, handler);
            f = service.submit(r);
            f.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            service.shutdown();
        }
    }

    public MatchPair matchEntities(Repository repository, RevCommit currentCommit, final MatchingHandler handler) throws Exception {
        GitService gitService = new GitServiceImpl();
        JDTService jdtService = new JDTServiceImpl();
        SoftwareEntityMatcherService emService = new SoftwareEntityMatcherService();
        MethodStatementMatcherService smService = new MethodStatementMatcherService();
        String commitId = currentCommit.getId().getName();
        MatchPair matchPair = new MatchPair();
        emService.matchEntities(gitService, jdtService, repository, currentCommit, matchPair);
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<DeclarationNodeTree> deletedEntities = matchPair.getDeletedEntities();
        Set<DeclarationNodeTree> addedEntities = matchPair.getAddedEntities();
        Set<DeclarationNodeTree> extractedEntities = new HashSet<>();
        Set<DeclarationNodeTree> inlinedEntities = new HashSet<>();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> matchedEntity : matchedEntities) {
            DeclarationNodeTree oldEntity = matchedEntity.getLeft();
            DeclarationNodeTree newEntity = matchedEntity.getRight();
            if (oldEntity.getType() == EntityType.METHOD && newEntity.getType() == EntityType.METHOD) {
                MethodDeclaration removedOperation = (MethodDeclaration) oldEntity.getDeclaration();
                MethodDeclaration addedOperation = (MethodDeclaration) newEntity.getDeclaration();
                MethodNode oldMethod = jdtService.parseMethodSNT(oldEntity.getFilePath(), removedOperation);
                MethodNode newMethod = jdtService.parseMethodSNT(newEntity.getFilePath(), addedOperation);
                oldEntity.setMethodNode(oldMethod);
                oldMethod.setMethodEntity(oldEntity);
                newEntity.setMethodNode(newMethod);
                newMethod.setMethodEntity(newEntity);
                if (!MethodUtils.isNewFunction(removedOperation, addedOperation)) {
                    for (DeclarationNodeTree addedEntity : addedEntities) {
                        if (addedEntity.getType() != EntityType.METHOD)
                            continue;
                        List<EntityInfo> dependencies = addedEntity.getDependencies();
                        if (!dependencies.contains(newEntity.getEntity()))
                            continue;
                        MethodDeclaration addedMethodDeclaration = (MethodDeclaration) addedEntity.getDeclaration();
                        if (MethodUtils.isGetter(addedMethodDeclaration) || MethodUtils.isSetter(addedMethodDeclaration))
                            continue;
                        /*double dice = DiceFunction.calculateBodyDice((LeafNode) addedEntity, (LeafNode) oldEntity);
                        if (dice < 0.2)
                            continue;*/
                        List<StatementNodeTree> allOperations = newMethod.getAllOperations();
                        List<StatementNodeTree> allControls = newMethod.getAllControls();
                        List<StatementNodeTree> locations = new ArrayList<>();
                        for (StatementNodeTree snt : allOperations) {
                            NodeUsageVisitor visitor = new NodeUsageVisitor();
                            snt.getStatement().accept(visitor);
                            for (EntityInfo entity : visitor.getEntityUsages()) {
                                if (entity.equals(addedEntity.getEntity())) {
                                    locations.add(snt);
                                    break;
                                }
                            }
                        }
                        for (StatementNodeTree snt : allControls) {
                            NodeUsageVisitor visitor = new NodeUsageVisitor();
                            snt.getStatement().accept(visitor);
                            for (EntityInfo entity : visitor.getEntityUsages()) {
                                if (entity.equals(addedEntity.getEntity())) {
                                    locations.add(snt);
                                    break;
                                }
                            }
                        }
                        if (!locations.isEmpty()) {
                            MethodNode addedMethod = jdtService.parseMethodSNT(addedEntity.getFilePath(), addedMethodDeclaration);
                            if (!addedMethod.getChildren().isEmpty()) {
                                addedEntity.setMethodNode(addedMethod);
                                addedMethod.setMethodEntity(addedEntity);
                                if (locations.size() > 1)
                                    addedMethod.setDuplicated();
                                for (StatementNodeTree snt : locations) {
                                    StatementNodeTree parent = snt.getParent();
                                    List<StatementNodeTree> children = parent.getChildren();
                                    int i = children.indexOf(snt);
                                    children.addAll(i, addedMethod.getChildren().get(0).getChildren());
                                    if (snt.getType() == StatementType.EXPRESSION_STATEMENT && ((ExpressionStatement) snt.getStatement()).getExpression() instanceof MethodInvocation) {
                                        snt.setMatched();
                                        matchPair.addAddedStatement(snt);
                                    }
                                    int position = snt.getPosition();
                                    boolean inserted = false;
                                    for (int j = 0; j < allControls.size(); j++) {
                                        StatementNodeTree control = allControls.get(j);
                                        if (position < control.getPosition()) {
                                            newMethod.addControls(j, addedMethod.getAllControls());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted)
                                        newMethod.addControls(-1, addedMethod.getAllControls());
                                    List<StatementNodeTree> allBlocks = newMethod.getAllBlocks();
                                    inserted = false;
                                    for (int j = 0; j < allBlocks.size(); j++) {
                                        StatementNodeTree block = allBlocks.get(j);
                                        if (position < block.getPosition()) {
                                            newMethod.addBlocks(j, addedMethod.getAllBlocks());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted)
                                        newMethod.addBlocks(-1, addedMethod.getAllBlocks());
                                    inserted = false;
                                    for (int j = 0; j < allOperations.size(); j++) {
                                        StatementNodeTree operation = allOperations.get(j);
                                        if (position < operation.getPosition()) {
                                            newMethod.addOperations(j, addedMethod.getAllOperations());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted)
                                        newMethod.addOperations(-1, addedMethod.getAllOperations());
                                }
                                extractedEntities.add(addedEntity);
                            }
                        }
                    }
                }
                if (!MethodUtils.isNewFunction(addedOperation, removedOperation)) {
                    for (DeclarationNodeTree deletedEntity : deletedEntities) {
                        if (deletedEntity.getType() != EntityType.METHOD)
                            continue;
                        List<EntityInfo> dependencies = deletedEntity.getDependencies();
                        if (!dependencies.contains(oldEntity.getEntity()))
                            continue;
                        MethodDeclaration deletedMethodDeclaration = (MethodDeclaration) deletedEntity.getDeclaration();
                        if (MethodUtils.isGetter(deletedMethodDeclaration) || MethodUtils.isSetter(deletedMethodDeclaration))
                            continue;
                        /*double dice = DiceFunction.calculateBodyDice((LeafNode) deletedEntity, (LeafNode) newEntity);
                        if (dice < 0.2)
                            continue;*/
                        List<StatementNodeTree> allOperations = oldMethod.getAllOperations();
                        List<StatementNodeTree> allControls = oldMethod.getAllControls();
                        List<StatementNodeTree> locations = new ArrayList<>();
                        for (StatementNodeTree snt : allOperations) {
                            NodeUsageVisitor visitor = new NodeUsageVisitor();
                            snt.getStatement().accept(visitor);
                            for (EntityInfo entity : visitor.getEntityUsages()) {
                                if (entity.equals(deletedEntity.getEntity())) {
                                    locations.add(snt);
                                    break;
                                }
                            }
                        }
                        for (StatementNodeTree snt : allControls) {
                            NodeUsageVisitor visitor = new NodeUsageVisitor();
                            snt.getStatement().accept(visitor);
                            for (EntityInfo entity : visitor.getEntityUsages()) {
                                if (entity.equals(deletedEntity.getEntity())) {
                                    locations.add(snt);
                                    break;
                                }
                            }
                        }
                        if (!locations.isEmpty()) {
                            MethodNode deletedMethod = jdtService.parseMethodSNT(deletedEntity.getFilePath(), deletedMethodDeclaration);
                            if (!deletedMethod.getChildren().isEmpty()) {
                                deletedEntity.setMethodNode(deletedMethod);
                                deletedMethod.setMethodEntity(deletedEntity);
                                if (locations.size() > 1)
                                    deletedMethod.setDuplicated();
                                for (StatementNodeTree snt : locations) {
                                    StatementNodeTree parent = snt.getParent();
                                    List<StatementNodeTree> children = parent.getChildren();
                                    int i = children.indexOf(snt);
                                    children.addAll(i, deletedMethod.getChildren().get(0).getChildren());
                                    if (snt.getType() == StatementType.EXPRESSION_STATEMENT && ((ExpressionStatement) snt.getStatement()).getExpression() instanceof MethodInvocation) {
                                        snt.setMatched();
                                        matchPair.addDeletedStatement(snt);
                                    }
                                    int position = snt.getPosition();
                                    boolean inserted = false;
                                    for (int j = 0; j < allControls.size(); j++) {
                                        StatementNodeTree control = allControls.get(j);
                                        if (position < control.getPosition()) {
                                            oldMethod.addControls(j, deletedMethod.getAllControls());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted)
                                        oldMethod.addControls(-1, deletedMethod.getAllControls());
                                    List<StatementNodeTree> allBlocks = oldMethod.getAllBlocks();
                                    inserted = false;
                                    for (int j = 0; j < allBlocks.size(); j++) {
                                        StatementNodeTree block = allBlocks.get(j);
                                        if (position < block.getPosition()) {
                                            oldMethod.addBlocks(j, deletedMethod.getAllBlocks());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted)
                                        oldMethod.addBlocks(-1, deletedMethod.getAllBlocks());
                                    inserted = false;
                                    for (int j = 0; j < allOperations.size(); j++) {
                                        StatementNodeTree operation = allOperations.get(j);
                                        if (position < operation.getPosition()) {
                                            oldMethod.addOperations(j, deletedMethod.getAllOperations());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted)
                                        oldMethod.addOperations(-1, deletedMethod.getAllOperations());
                                }
                                inlinedEntities.add(deletedEntity);
                            }
                        }
                    }
                }
                smService.matchStatements(oldMethod, newMethod, matchPair);
            }
        }
//        deletedEntities.removeAll(inlinedEntities);
//        addedEntities.removeAll(extractedEntities);
        matchPair.setInlinedEntities(inlinedEntities);
        matchPair.setExtractedEntities(extractedEntities);
        for (DeclarationNodeTree deletedEntity : deletedEntities) {
            if (!inlinedEntities.contains(deletedEntity) && deletedEntity.getType() == EntityType.METHOD) {
                String filePath = deletedEntity.getFilePath();
                MethodDeclaration methodDeclaration = (MethodDeclaration) deletedEntity.getDeclaration();
                MethodNode methodNode = jdtService.parseMethodSNT(filePath, methodDeclaration);
                deletedEntity.setMethodNode(methodNode);
                methodNode.setMethodEntity(deletedEntity);
                methodNode.addDeletedStatements(matchPair, methodNode.getChildren());
            }
        }
        for (DeclarationNodeTree addedEntity : addedEntities) {
            if (!extractedEntities.contains(addedEntity) && addedEntity.getType() == EntityType.METHOD) {
                String filePath = addedEntity.getFilePath();
                MethodDeclaration methodDeclaration = (MethodDeclaration) addedEntity.getDeclaration();
                MethodNode methodNode = jdtService.parseMethodSNT(filePath, methodDeclaration);
                addedEntity.setMethodNode(methodNode);
                methodNode.setMethodEntity(addedEntity);
                methodNode.addAddedStatements(matchPair, methodNode.getChildren());
            }
        }

        handler.handle(commitId, matchPair);
        return matchPair;
    }
}
