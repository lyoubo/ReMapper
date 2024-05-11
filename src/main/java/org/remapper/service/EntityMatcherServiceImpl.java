package org.remapper.service;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.remapper.dto.*;
import org.remapper.handler.MatchingHandler;
import org.remapper.util.*;
import org.remapper.visitor.NodeUsageVisitor;

import java.io.File;
import java.util.*;
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

    @Override
    public MatchPair matchEntities(Repository repository, RevCommit currentCommit, final MatchingHandler handler) throws Exception {
        GitService gitService = new GitServiceImpl();
        JDTService jdtService = new JDTServiceImpl();
        SoftwareEntityMatcherService entityMatchingService = new SoftwareEntityMatcherService();
        String commitId = currentCommit.getId().getName();
        MatchPair matchPair = new MatchPair();
        entityMatchingService.matchEntities(gitService, jdtService, repository, currentCommit, matchPair);
        matchStatementsInMethodPairs(matchPair, jdtService);
        findRefactoringsBetweenAttributesAndVariables(matchPair);
        handler.handle(commitId, matchPair);
        return matchPair;
    }

    @Override
    public void matchAtFiles(File previousFile, File nextFile, MatchingHandler handler) {
        String id = previousFile.getName() + " -> " + nextFile.getName();
        try {
            this.matchEntities(previousFile, nextFile, handler);
        } catch (Exception e) {
            handler.handleException(id, e);
        }
    }

    @Override
    public MatchPair matchEntities(File previousFile, File nextFile, final MatchingHandler handler) throws Exception {
        MatchPair matchPair = new MatchPair();
        if (previousFile.exists() && nextFile.exists() && previousFile.isFile() && nextFile.isFile() &&
                previousFile.getName().endsWith(".java") && nextFile.getName().endsWith(".java")) {
            String id = previousFile.getName() + " -> " + nextFile.getName();
            JDTService jdtService = new JDTServiceImpl();
            SoftwareEntityMatcherService entityMatchingService = new SoftwareEntityMatcherService();
            entityMatchingService.matchEntities(jdtService, previousFile, nextFile, matchPair);
            matchStatementsInMethodPairs(matchPair, jdtService);
            handler.handle(id, matchPair);
        }
        return matchPair;
    }

    private void matchStatementsInMethodPairs(MatchPair matchPair, JDTService jdtService) {
        MethodStatementMatcherService statementMatchingService = new MethodStatementMatcherService();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<DeclarationNodeTree> deletedEntities = matchPair.getDeletedEntities();
        Set<DeclarationNodeTree> addedEntities = matchPair.getAddedEntities();
        Set<DeclarationNodeTree> extractedEntities = new HashSet<>();
        Set<DeclarationNodeTree> inlinedEntities = new HashSet<>();
        for (DeclarationNodeTree deletedEntity : deletedEntities) {
            if (deletedEntity.getType() != EntityType.METHOD)
                continue;
            MethodDeclaration deletedMethodDeclaration = (MethodDeclaration) deletedEntity.getDeclaration();
            MethodNode deletedMethod = jdtService.parseMethodSNT(deletedEntity.getFilePath(), deletedMethodDeclaration);
            deletedEntity.setMethodNode(deletedMethod);
            deletedMethod.setMethodEntity(deletedEntity);
        }
        for (DeclarationNodeTree addedEntity : addedEntities) {
            if (addedEntity.getType() != EntityType.METHOD)
                continue;
            MethodDeclaration addedMethodDeclaration = (MethodDeclaration) addedEntity.getDeclaration();
            MethodNode addedMethod = jdtService.parseMethodSNT(addedEntity.getFilePath(), addedMethodDeclaration);
            addedEntity.setMethodNode(addedMethod);
            addedMethod.setMethodEntity(addedEntity);
        }
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
            DeclarationNodeTree oldEntity = pair.getLeft();
            DeclarationNodeTree newEntity = pair.getRight();
            List<Pair<String, String>> oldReplacements = new ArrayList<>();
            List<Pair<String, String>> newReplacements = new ArrayList<>();
            if ((oldEntity.getType() == EntityType.METHOD && newEntity.getType() == EntityType.METHOD) ||
                    (oldEntity.getType() == EntityType.INITIALIZER && newEntity.getType() == EntityType.INITIALIZER)) {
                if (StringUtils.equals(oldEntity.getDeclaration().toString(), newEntity.getDeclaration().toString()))
                    continue;
                ASTNode removedOperation = oldEntity.getDeclaration();
                ASTNode addedOperation = newEntity.getDeclaration();
                MethodNode oldMethod = null;
                MethodNode newMethod = null;
                if (oldEntity.getType() == EntityType.METHOD && newEntity.getType() == EntityType.METHOD) {
                    oldMethod = jdtService.parseMethodSNT(oldEntity.getFilePath(), (MethodDeclaration) removedOperation);
                    newMethod = jdtService.parseMethodSNT(newEntity.getFilePath(), (MethodDeclaration) addedOperation);
                }
                if (oldEntity.getType() == EntityType.INITIALIZER && newEntity.getType() == EntityType.INITIALIZER) {
                    oldMethod = jdtService.parseMethodSNT(oldEntity.getFilePath(), (Initializer) removedOperation);
                    newMethod = jdtService.parseMethodSNT(newEntity.getFilePath(), (Initializer) addedOperation);
                }
                oldEntity.setMethodNode(oldMethod);
                oldMethod.setMethodEntity(oldEntity);
                newEntity.setMethodNode(newMethod);
                newMethod.setMethodEntity(newEntity);
                if (!MethodUtils.isNewFunction(removedOperation, addedOperation)) {
                    for (DeclarationNodeTree addedEntity : addedEntities) {
                        if (addedEntity.getType() != EntityType.METHOD)
                            continue;
                        MethodDeclaration addedMethodDeclaration = (MethodDeclaration) addedEntity.getDeclaration();
                        if (MethodUtils.isGetter(addedMethodDeclaration) || MethodUtils.isSetter(addedMethodDeclaration))
                                continue;
                        DeclarationNodeTree delegatedEntity = getDelegatedMethod(addedEntity, addedEntities);
                        double dice;
                        if (delegatedEntity != addedEntity)
                            dice = DiceFunction.calculateBodyDice((LeafNode) oldEntity, (LeafNode) newEntity, (LeafNode) delegatedEntity);
                        else
                            dice = DiceFunction.calculateBodyDice((LeafNode) oldEntity, (LeafNode) newEntity, (LeafNode) addedEntity);
                        if (dice == 0)
                            continue;
                        List<StatementNodeTree> allOperations = newMethod.getAllOperations();
                        List<StatementNodeTree> allControls = newMethod.getAllControls();
                        List<EntityInfo> dependencies = addedEntity.getDependencies();
                        List<StatementNodeTree> locations = new ArrayList<>();
                        if (dependencies.contains(newEntity.getEntity())) {
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
                                if (isLocation(snt, addedEntity)) {
                                    locations.add(snt);
                                    break;
                                }
                            }
                        } else if (StringUtils.equals(newEntity.getNamespace(), addedEntity.getNamespace())) {
                            findMethodInvocation(allOperations, allControls, addedEntity, locations);
                        }
                        if (!locations.isEmpty()) {
                            MethodNode addedMethod = addedEntity.getMethodNode();
                            MethodDeclaration methodDeclaration = (MethodDeclaration) delegatedEntity.getDeclaration();
                            MethodNode delegatedMethod = delegatedEntity.getMethodNode() == null ? jdtService.parseMethodSNT(delegatedEntity.getFilePath(), methodDeclaration) : delegatedEntity.getMethodNode();
                            if (!addedMethod.getChildren().isEmpty()) {
                                if (locations.size() > 1) {
                                    addedMethod.setDuplicated();
                                    if (delegatedMethod != addedMethod)
                                        delegatedMethod.setDuplicated();
                                }
                                if (dependencies.size() > 1) {
                                    int i = dependencies.size();
                                    for (EntityInfo dependency : dependencies) {
                                        for (DeclarationNodeTree entity : addedEntities) {
                                            if (dependency.equals(entity.getEntity())) {
                                                i -= 1;
                                                break;
                                            }
                                        }
                                    }
                                    if (i > 1) {
                                        addedMethod.setDuplicated();
                                        if (delegatedMethod != addedMethod)
                                            delegatedMethod.setDuplicated();
                                    }
                                }
                                for (StatementNodeTree statement : locations) {
                                    StatementNodeTree parent = statement.getParent();
                                    List<StatementNodeTree> children = parent.getChildren();
                                    int i = children.indexOf(statement);
                                    if (delegatedMethod != addedMethod) {
                                        StatementNodeTree proxyStatement = addedMethod.getAllOperations().get(0);
                                        proxyStatement.setMatched();
                                        matchPair.addAddedStatement(proxyStatement);
                                        arguments2Parameters(statement, addedEntity, proxyStatement, delegatedEntity, newReplacements);
                                    } else
                                        arguments2Parameters(new ArrayList<>(), statement, addedEntity, newReplacements);
                                    addedMethod = delegatedMethod;
                                    if (!addedMethod.getChildren().isEmpty()) {
                                        List<StatementNodeTree> children1 = addedMethod.getChildren().get(0).getChildren();
                                        children.addAll(i, children1);
                                        for (StatementNodeTree child : children1) {
                                            child.setHigherRoot(newMethod);
                                        }
                                    }
                                    if (statement.getType() == StatementType.EXPRESSION_STATEMENT && ((ExpressionStatement) statement.getStatement()).getExpression() instanceof MethodInvocation) {
                                        MethodInvocation invocation = (MethodInvocation) ((ExpressionStatement) statement.getStatement()).getExpression();
                                        MethodDeclaration declaration = (MethodDeclaration) addedEntity.getDeclaration();
                                        if (invocation.getName().getIdentifier().equals(declaration.getName().getIdentifier()) && invocation.arguments().size() == declaration.parameters().size()) {
                                            statement.setMatched();
                                            matchPair.addAddedStatement(statement);
                                            arguments2Parameters(allOperations, statement, addedEntity, newReplacements);
                                        }
                                    }
                                    if (statement.getType() == StatementType.RETURN_STATEMENT && ((ReturnStatement) statement.getStatement()).getExpression() instanceof MethodInvocation) {
                                        MethodInvocation invocation = (MethodInvocation) ((ReturnStatement) statement.getStatement()).getExpression();
                                        MethodDeclaration declaration = (MethodDeclaration) addedEntity.getDeclaration();
                                        if (invocation.getName().getIdentifier().equals(declaration.getName().getIdentifier()) && invocation.arguments().size() == declaration.parameters().size()) {
                                            statement.setMatched();
                                            matchPair.addAddedStatement(statement);
                                            arguments2Parameters(allOperations, statement, addedEntity, newReplacements);
                                        }
                                    }
                                    int position = statement.getPosition();
                                    boolean inserted = false;
                                    for (int j = 0; j < allControls.size(); j++) {
                                        StatementNodeTree control = allControls.get(j);
                                        if (position < control.getPosition()) {
                                            for (StatementNodeTree snt : addedMethod.getAllControls()) {
                                                snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                            }
                                            newMethod.addControls(j, addedMethod.getAllControls());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted) {
                                        for (StatementNodeTree snt : addedMethod.getAllControls()) {
                                            snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                        }
                                        newMethod.addControls(-1, addedMethod.getAllControls());
                                    }
                                    List<StatementNodeTree> allBlocks = newMethod.getAllBlocks();
                                    inserted = false;
                                    for (int j = 0; j < allBlocks.size(); j++) {
                                        StatementNodeTree block = allBlocks.get(j);
                                        if (position < block.getPosition()) {
                                            for (StatementNodeTree snt : addedMethod.getAllBlocks()) {
                                                snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                            }
                                            newMethod.addBlocks(j, addedMethod.getAllBlocks());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted) {
                                        for (StatementNodeTree snt : addedMethod.getAllBlocks()) {
                                            snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                        }
                                        newMethod.addBlocks(-1, addedMethod.getAllBlocks());
                                    }
                                    inserted = false;
                                    for (int j = 0; j < allOperations.size(); j++) {
                                        StatementNodeTree operation = allOperations.get(j);
                                        if (position < operation.getPosition()) {
                                            for (StatementNodeTree snt : addedMethod.getAllOperations()) {
                                                snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                            }
                                            newMethod.addOperations(j, addedMethod.getAllOperations());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted) {
                                        for (StatementNodeTree snt : addedMethod.getAllOperations()) {
                                            snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                        }
                                        newMethod.addOperations(-1, addedMethod.getAllOperations());
                                    }
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
                        MethodDeclaration deletedMethodDeclaration = (MethodDeclaration) deletedEntity.getDeclaration();
                        if (MethodUtils.isGetter(deletedMethodDeclaration) || MethodUtils.isSetter(deletedMethodDeclaration))
                            continue;
                        DeclarationNodeTree delegatedEntity = getDelegatedMethod(deletedEntity, deletedEntities);
                        double dice;
                        if (delegatedEntity != deletedEntity)
                            dice = DiceFunction.calculateBodyDice((LeafNode) newEntity, (LeafNode) oldEntity, (LeafNode) delegatedEntity);
                        else
                            dice = DiceFunction.calculateBodyDice((LeafNode) newEntity, (LeafNode) oldEntity, (LeafNode) deletedEntity);
                        if (dice == 0)
                            continue;
                        List<StatementNodeTree> allOperations = oldMethod.getAllOperations();
                        List<StatementNodeTree> allControls = oldMethod.getAllControls();
                        List<EntityInfo> dependencies = deletedEntity.getDependencies();
                        List<StatementNodeTree> locations = new ArrayList<>();
                        if (dependencies.contains(oldEntity.getEntity())) {
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
                                if (isLocation(snt, deletedEntity)) {
                                    locations.add(snt);
                                    break;
                                }
                            }
                        } else if (StringUtils.equals(oldEntity.getNamespace(), deletedEntity.getNamespace())) {
                            findMethodInvocation(allOperations, allControls, deletedEntity, locations);
                        }
                        if (!locations.isEmpty()) {
                            MethodNode deletedMethod = deletedEntity.getMethodNode();
                            MethodDeclaration methodDeclaration = (MethodDeclaration) delegatedEntity.getDeclaration();
                            MethodNode delegatedMethod = delegatedEntity.getMethodNode() == null ? jdtService.parseMethodSNT(delegatedEntity.getFilePath(), methodDeclaration) : delegatedEntity.getMethodNode();
                            if (!deletedMethod.getChildren().isEmpty()) {
                                if (locations.size() > 1) {
                                    deletedMethod.setDuplicated();
                                    if (delegatedMethod != deletedMethod)
                                        delegatedMethod.setDuplicated();
                                }
                                if (dependencies.size() > 1) {
                                    int i = dependencies.size();
                                    for (EntityInfo dependency : dependencies) {
                                        for (DeclarationNodeTree entity : deletedEntities) {
                                            if (dependency.equals(entity.getEntity())) {
                                                i -= 1;
                                                break;
                                            }
                                        }
                                    }
                                    if (i > 1) {
                                        deletedMethod.setDuplicated();
                                        if (delegatedMethod != deletedMethod)
                                            delegatedMethod.setDuplicated();
                                    }
                                }
                                for (StatementNodeTree statement : locations) {
                                    StatementNodeTree parent = statement.getParent();
                                    List<StatementNodeTree> children = parent.getChildren();
                                    int i = children.indexOf(statement);
                                    if (delegatedMethod != deletedMethod) {
                                        StatementNodeTree proxyStatement = deletedMethod.getAllOperations().get(0);
                                        proxyStatement.setMatched();
                                        matchPair.addDeletedStatement(proxyStatement);
                                        arguments2Parameters(statement, deletedEntity, proxyStatement, delegatedEntity, oldReplacements);
                                    } else {
                                        arguments2Parameters(new ArrayList<>(), statement, deletedEntity, oldReplacements);
                                    }
                                    deletedMethod = delegatedMethod;
                                    if (!deletedMethod.getChildren().isEmpty()) {
                                        List<StatementNodeTree> children1 = deletedMethod.getChildren().get(0).getChildren();
                                        children.addAll(i, children1);
                                        for (StatementNodeTree child : children1) {
                                            child.setHigherRoot(oldMethod);
                                        }
                                    }
                                    if (statement.getType() == StatementType.EXPRESSION_STATEMENT && ((ExpressionStatement) statement.getStatement()).getExpression() instanceof MethodInvocation) {
                                        MethodInvocation invocation = (MethodInvocation) ((ExpressionStatement) statement.getStatement()).getExpression();
                                        MethodDeclaration declaration = (MethodDeclaration) deletedEntity.getDeclaration();
                                        if (invocation.getName().getIdentifier().equals(declaration.getName().getIdentifier()) && invocation.arguments().size() == declaration.parameters().size()) {
                                            statement.setMatched();
                                            matchPair.addDeletedStatement(statement);
                                            arguments2Parameters(allOperations, statement, deletedEntity, oldReplacements);
                                        }
                                    }
                                    if (statement.getType() == StatementType.RETURN_STATEMENT && ((ReturnStatement) statement.getStatement()).getExpression() instanceof MethodInvocation) {
                                        MethodInvocation invocation = (MethodInvocation) ((ReturnStatement) statement.getStatement()).getExpression();
                                        MethodDeclaration declaration = (MethodDeclaration) deletedEntity.getDeclaration();
                                        if (invocation.getName().getIdentifier().equals(declaration.getName().getIdentifier()) && invocation.arguments().size() == declaration.parameters().size()) {
                                            statement.setMatched();
                                            matchPair.addDeletedStatement(statement);
                                            arguments2Parameters(allOperations, statement, deletedEntity, oldReplacements);
                                        }
                                    }
                                    int position = statement.getPosition();
                                    boolean inserted = false;
                                    for (int j = 0; j < allControls.size(); j++) {
                                        StatementNodeTree control = allControls.get(j);
                                        if (position < control.getPosition()) {
                                            for (StatementNodeTree snt : deletedMethod.getAllControls()) {
                                                snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                            }
                                            oldMethod.addControls(j, deletedMethod.getAllControls());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted) {
                                        for (StatementNodeTree snt : deletedMethod.getAllControls()) {
                                            snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                        }
                                        oldMethod.addControls(-1, deletedMethod.getAllControls());
                                    }
                                    List<StatementNodeTree> allBlocks = oldMethod.getAllBlocks();
                                    inserted = false;
                                    for (int j = 0; j < allBlocks.size(); j++) {
                                        StatementNodeTree block = allBlocks.get(j);
                                        if (position < block.getPosition()) {
                                            for (StatementNodeTree snt : deletedMethod.getAllBlocks()) {
                                                snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                            }
                                            oldMethod.addBlocks(j, deletedMethod.getAllBlocks());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted) {
                                        for (StatementNodeTree snt : deletedMethod.getAllBlocks()) {
                                            snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                        }
                                        oldMethod.addBlocks(-1, deletedMethod.getAllBlocks());
                                    }
                                    inserted = false;
                                    for (int j = 0; j < allOperations.size(); j++) {
                                        StatementNodeTree operation = allOperations.get(j);
                                        if (position < operation.getPosition()) {
                                            for (StatementNodeTree snt : deletedMethod.getAllOperations()) {
                                                snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                            }
                                            oldMethod.addOperations(j, deletedMethod.getAllOperations());
                                            inserted = true;
                                            break;
                                        }
                                    }
                                    if (!inserted) {
                                        for (StatementNodeTree snt : deletedMethod.getAllOperations()) {
                                            snt.setDepth(snt.getDepth() + statement.getDepth() - 1);
                                        }
                                        oldMethod.addOperations(-1, deletedMethod.getAllOperations());
                                    }
                                }
                                inlinedEntities.add(deletedEntity);
                            }
                        }
                    }
                }
                statementMatchingService.matchStatements(oldMethod, newMethod, matchPair, oldReplacements, newReplacements);
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
    }

    private void findMethodInvocation(List<StatementNodeTree> allOperations, List<StatementNodeTree> allControls,
                                      DeclarationNodeTree dnt, List<StatementNodeTree> locations) {
        MethodDeclaration declaration2 = (MethodDeclaration) dnt.getDeclaration();
        for (StatementNodeTree snt : allOperations) {
            ASTNode statement = snt.getStatement();
            if (hasMethodInvocation(statement, declaration2)) {
                locations.add(snt);
            }
        }
        for (StatementNodeTree snt : allControls) {
            if (snt.getType() == StatementType.DO_STATEMENT) {
                DoStatement statement = (DoStatement) snt.getStatement();
                if (hasMethodInvocation(statement.getExpression(), declaration2)) {
                    locations.add(snt);
                }
            } else if (snt.getType() == StatementType.WHILE_STATEMENT) {
                WhileStatement statement = (WhileStatement) snt.getStatement();
                if (hasMethodInvocation(statement.getExpression(), declaration2)) {
                    locations.add(snt);
                }
            } else if (snt.getType() == StatementType.FOR_STATEMENT) {
                ForStatement statement = (ForStatement) snt.getStatement();
                if (hasMethodInvocation(statement.getExpression(), declaration2)) {
                    locations.add(snt);
                }
            } else if (snt.getType() == StatementType.ENHANCED_FOR_STATEMENT) {
                EnhancedForStatement statement = (EnhancedForStatement) snt.getStatement();
                if (hasMethodInvocation(statement.getExpression(), declaration2)) {
                    locations.add(snt);
                }
            } else if (snt.getType() == StatementType.IF_STATEMENT) {
                IfStatement statement = (IfStatement) snt.getStatement();
                if (hasMethodInvocation(statement.getExpression(), declaration2)) {
                    locations.add(snt);
                }
            } else if (snt.getType() == StatementType.SWITCH_STATEMENT) {
                SwitchStatement statement = (SwitchStatement) snt.getStatement();
                if (hasMethodInvocation(statement.getExpression(), declaration2)) {
                    locations.add(snt);
                }
            } else if (snt.getType() == StatementType.TRY_STATEMENT) {
                TryStatement statement = (TryStatement) snt.getStatement();
                List<Expression> resources = statement.resources();
                for (Expression expression : resources) {
                    if (hasMethodInvocation(expression, declaration2)) {
                        locations.add(snt);
                    }
                }
            }
        }
    }

    private boolean hasMethodInvocation(ASTNode statement, MethodDeclaration declaration2) {
        if (statement == null) return false;
        List<MethodInvocation> invocations = new ArrayList<>();
        List<ConstructorInvocation> constructors = new ArrayList<>();
        statement.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                if (node.getName().getIdentifier().equals(declaration2.getName().getIdentifier()) &&
                        node.arguments().size() == declaration2.parameters().size())
                    invocations.add(node);
                return true;
            }

            public boolean visit(ConstructorInvocation node) {
                if (declaration2.isConstructor() &&
                        node.arguments().size() == declaration2.parameters().size())
                    constructors.add(node);
                return true;
            }
        });
        return !invocations.isEmpty() || !constructors.isEmpty();
    }

    /**
     * Judge whether the method is a proxy method
     */
    private DeclarationNodeTree getDelegatedMethod(DeclarationNodeTree proxyMethod, Set<DeclarationNodeTree> methods) {
        MethodNode methodNode = proxyMethod.getMethodNode();
        if (methodNode.getAllOperations().size() == 1 && methodNode.getAllBlocks().size() == 1 && methodNode.getAllControls().isEmpty()) {
            StatementNodeTree snt = methodNode.getAllOperations().get(0);
            MethodInvocation methodInvocation = null;
            boolean isProxy = false;
            if (snt.getType() == StatementType.EXPRESSION_STATEMENT) {
                ExpressionStatement statement = (ExpressionStatement) snt.getStatement();
                if (statement.getExpression() instanceof MethodInvocation) {
                    isProxy = true;
                    methodInvocation = (MethodInvocation) statement.getExpression();
                }
            }
            if (snt.getType() == StatementType.RETURN_STATEMENT) {
                ReturnStatement statement = (ReturnStatement) snt.getStatement();
                if (statement.getExpression() instanceof MethodInvocation) {
                    isProxy = true;
                    methodInvocation = (MethodInvocation) statement.getExpression();
                }
            }
            if (isProxy) {
                for (DeclarationNodeTree method : methods) {
                    if (method == proxyMethod)
                        continue;
                    if (method.getType() != EntityType.METHOD)
                        continue;
                    List<EntityInfo> dependencies = method.getDependencies();
                    if (!dependencies.contains(proxyMethod.getEntity()))
                        continue;
                    if (method.getName().equals(methodInvocation.getName().getIdentifier()) &&
                            ((MethodDeclaration) method.getDeclaration()).parameters().size() == methodInvocation.arguments().size()) {
                        return method;
                    }
                }
            }
        }
        return proxyMethod;
    }

    private void arguments2Parameters(StatementNodeTree statement1, DeclarationNodeTree additionalMethod1, StatementNodeTree statement2,
                                      DeclarationNodeTree additionalMethod2, List<Pair<String, String>> replacements) {
        Map<String, String> variables1 = getReplacements(statement1, additionalMethod1);
        Map<String, String> variables2 = getReplacements(statement2, additionalMethod2);
        for (String key1 : variables1.keySet()) {
            String value1 = variables1.get(key1);
            for (String key2 : variables2.keySet()) {
                String value2 = variables2.get(key2);
                if (key2.equals(value1)) {
                    if (!key1.equals(value2))
                        replacements.add(Pair.of(key1, value2));
                }
            }
        }
    }

    private Map<String, String> getReplacements(StatementNodeTree statement, DeclarationNodeTree additionalMethod) {
        Map<String, String> variables = new HashMap<>();
        List<MethodInvocation> invocations = new ArrayList<>();
        List<ConstructorInvocation> constructors = new ArrayList<>();
        statement.getStatement().accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                invocations.add(node);
                return true;
            }

            @Override
            public boolean visit(ConstructorInvocation node) {
                constructors.add(node);
                return true;
            }
        });
        MethodDeclaration declaration = (MethodDeclaration) additionalMethod.getDeclaration();
        List<SingleVariableDeclaration> parameters = declaration.parameters();
        for (MethodInvocation invocation : invocations) {
            if (!invocation.getName().getIdentifier().equals(declaration.getName().getIdentifier()) ||
                    invocation.arguments().size() != parameters.size())
                continue;
            List<Expression> arguments = invocation.arguments();
            for (int i = 0; i < parameters.size(); i++) {
                String argument = arguments.get(i).toString();
                String parameter = parameters.get(i).getName().getIdentifier();
                variables.put(argument, parameter);
            }
        }
        for (ConstructorInvocation constructor : constructors) {
            if (constructor.arguments().size() != parameters.size() || !declaration.isConstructor())
                continue;
            List<Expression> arguments = constructor.arguments();
            for (int i = 0; i < parameters.size(); i++) {
                String argument = arguments.get(i).toString();
                String parameter = parameters.get(i).getName().getIdentifier();
                variables.put(argument, parameter);
            }
        }
        return variables;
    }

    private void arguments2Parameters(List<StatementNodeTree> allOperations, StatementNodeTree statement, DeclarationNodeTree additionalMethod, List<Pair<String, String>> replacements) {
        Map<String, String> variables = new HashMap<>();
        for (StatementNodeTree snt : allOperations) {
            if (snt.getType() == StatementType.VARIABLE_DECLARATION_STATEMENT) {
                VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) snt.getStatement();
                List<VariableDeclarationFragment> fragments = variableDeclarationStatement.fragments();
                for (VariableDeclarationFragment fragment : fragments) {
                    if (fragment.getInitializer() != null)
                        variables.put(fragment.getName().getIdentifier(), fragment.getInitializer().toString());
                }
            }
        }
        List<MethodInvocation> invocations = new ArrayList<>();
        List<ConstructorInvocation> constructors = new ArrayList<>();
        statement.getStatement().accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                invocations.add(node);
                return true;
            }

            @Override
            public boolean visit(ConstructorInvocation node) {
                constructors.add(node);
                return true;
            }
        });
        MethodDeclaration declaration = (MethodDeclaration) additionalMethod.getDeclaration();
        List<SingleVariableDeclaration> parameters = declaration.parameters();
        for (MethodInvocation invocation : invocations) {
            if (!invocation.getName().getIdentifier().equals(declaration.getName().getIdentifier()) ||
                    invocation.arguments().size() != parameters.size())
                continue;
            List<Expression> arguments = invocation.arguments();
            for (int i = 0; i < parameters.size(); i++) {
                String argument = arguments.get(i).toString();
                String parameter = parameters.get(i).getName().getIdentifier();
                if (variables.containsKey(argument)) {
                    if (!parameter.equals(variables.get(argument)))
                        replacements.add(Pair.of(parameter, variables.get(argument)));
                } else {
                    if (!parameter.equals(argument))
                        replacements.add(Pair.of(parameter, argument));
                }
            }
        }
        for (ConstructorInvocation constructor : constructors) {
            if (constructor.arguments().size() != parameters.size() || !declaration.isConstructor())
                continue;
            List<Expression> arguments = constructor.arguments();
            for (int i = 0; i < parameters.size(); i++) {
                String argument = arguments.get(i).toString();
                String parameter = parameters.get(i).getName().getIdentifier();
                if (variables.containsKey(argument)) {
                    if (!parameter.equals(variables.get(argument)))
                        replacements.add(Pair.of(parameter, variables.get(argument)));
                } else {
                    if (!parameter.equals(argument))
                        replacements.add(Pair.of(parameter, argument));
                }
            }
        }
    }

    private boolean isLocation(StatementNodeTree snt, DeclarationNodeTree deletedEntity) {
        NodeUsageVisitor visitor = new NodeUsageVisitor();
        if (snt.getType() == StatementType.DO_STATEMENT) {
            DoStatement statement = (DoStatement) snt.getStatement();
            statement.getExpression().accept(visitor);
            for (EntityInfo entity : visitor.getEntityUsages()) {
                if (entity.equals(deletedEntity.getEntity())) {
                    return true;
                }
            }
        } else if (snt.getType() == StatementType.WHILE_STATEMENT) {
            WhileStatement statement = (WhileStatement) snt.getStatement();
            statement.getExpression().accept(visitor);
            for (EntityInfo entity : visitor.getEntityUsages()) {
                if (entity.equals(deletedEntity.getEntity())) {
                    return true;
                }
            }
        } else if (snt.getType() == StatementType.FOR_STATEMENT) {
            ForStatement statement = (ForStatement) snt.getStatement();
            Expression expression = statement.getExpression();
            if (expression != null) {
                expression.accept(visitor);
                for (EntityInfo entity : visitor.getEntityUsages()) {
                    if (entity.equals(deletedEntity.getEntity())) {
                        return true;
                    }
                }
            }
        } else if (snt.getType() == StatementType.ENHANCED_FOR_STATEMENT) {
            EnhancedForStatement statement = (EnhancedForStatement) snt.getStatement();
            statement.getExpression().accept(visitor);
            for (EntityInfo entity : visitor.getEntityUsages()) {
                if (entity.equals(deletedEntity.getEntity())) {
                    return true;
                }
            }
        } else if (snt.getType() == StatementType.IF_STATEMENT) {
            IfStatement statement = (IfStatement) snt.getStatement();
            statement.getExpression().accept(visitor);
            for (EntityInfo entity : visitor.getEntityUsages()) {
                if (entity.equals(deletedEntity.getEntity())) {
                    return true;
                }
            }
        } else if (snt.getType() == StatementType.SWITCH_STATEMENT) {
            SwitchStatement statement = (SwitchStatement) snt.getStatement();
            statement.getExpression().accept(visitor);
            for (EntityInfo entity : visitor.getEntityUsages()) {
                if (entity.equals(deletedEntity.getEntity())) {
                    return true;
                }
            }
        } else if (snt.getType() == StatementType.TRY_STATEMENT) {
            TryStatement statement = (TryStatement) snt.getStatement();
            List<Expression> resources = statement.resources();
            for (Expression expression : resources) {
                expression.accept(visitor);
                for (EntityInfo entity : visitor.getEntityUsages()) {
                    if (entity.equals(deletedEntity.getEntity())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void findRefactoringsBetweenAttributesAndVariables(MatchPair matchPair) {
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntityDeletion = new HashSet<>();
        Set<StatementNodeTree> deletedStatementDeletion = new HashSet<>();
        Set<StatementNodeTree> addedStatementDeletion = new HashSet<>();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<StatementNodeTree> deletedStatements = matchPair.getDeletedStatements();
        Set<StatementNodeTree> addedStatements = matchPair.getAddedStatements();
        Set<Pair<StatementNodeTree, StatementNodeTree>> matchedStatements = matchPair.getMatchedStatements();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> entityPair : matchedEntities) {
            DeclarationNodeTree dntBefore = entityPair.getLeft();
            DeclarationNodeTree dntCurrent = entityPair.getRight();
            if (dntBefore.getType() != EntityType.FIELD || dntCurrent.getType() != EntityType.FIELD)
                continue;
            FieldDeclaration fd1 = (FieldDeclaration) dntBefore.getDeclaration();
            FieldDeclaration fd2 = (FieldDeclaration) dntCurrent.getDeclaration();
            String type1 = StringUtils.type2String(fd1.getType());
            String type2 = StringUtils.type2String(fd2.getType());
            VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) fd1.fragments().get(0);
            VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) fd2.fragments().get(0);
            if (StringUtils.equals(type1, type2) || StringUtils.equals(fragment1.getName().getIdentifier(), fragment2.getName().getIdentifier()))
                continue;
            for (StatementNodeTree statement : deletedStatements) {
                if (statement.getType() != StatementType.VARIABLE_DECLARATION_STATEMENT)
                    continue;
                DeclarationNodeTree parent = statement.getRoot().getMethodEntity().getParent();
                if (dntBefore.getParent() != parent) continue;
                VariableDeclarationStatement variableDeclaration = (VariableDeclarationStatement) statement.getStatement();
                String type = StringUtils.type2String(variableDeclaration.getType());
                if (!StringUtils.equals(type, type2) && !type.equals("var") && !type2.equals("var"))
                    continue;
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) variableDeclaration.fragments().get(0);
                for (Pair<StatementNodeTree, StatementNodeTree> statementPair : matchedStatements) {
                    List<StatementNodeTree> children = statement.getParent().getChildren();
                    StatementNodeTree sntBefore = statementPair.getLeft();
                    StatementNodeTree sntCurrent = statementPair.getRight();
                    if (!children.contains(sntBefore)) continue;
                    String expression1 = sntBefore.getExpression();
                    String expression2 = sntCurrent.getExpression();
                    if (!fragment.getName().getIdentifier().equals(fragment2.getName().getIdentifier()) && !expression1.equals(expression2) &&
                            expression1.replace(fragment.getName().getIdentifier(), fragment2.getName().getIdentifier()).equals(expression2)) {
                        matchPair.addVariableMapAttributes(statement, dntCurrent);
                        dntBefore.setMatched(false);
                        matchedEntityDeletion.add(entityPair);
                        deletedStatementDeletion.add(statement);
                    }
                    if (fragment.getName().getIdentifier().equals(fragment2.getName().getIdentifier()) && expression1.equals(expression2) &&
                            contains(sntBefore, fragment.getName().getIdentifier())) {
                        matchPair.addVariableMapAttributes(statement, dntCurrent);
                        dntBefore.setMatched(false);
                        matchedEntityDeletion.add(entityPair);
                        deletedStatementDeletion.add(statement);
                    }
                }
            }
            for (StatementNodeTree statement : addedStatements) {
                if (statement.getType() != StatementType.VARIABLE_DECLARATION_STATEMENT)
                    continue;
                DeclarationNodeTree parent = statement.getRoot().getMethodEntity().getParent();
                if (dntCurrent.getParent() != parent) continue;
                VariableDeclarationStatement variableDeclaration = (VariableDeclarationStatement) statement.getStatement();
                String type = StringUtils.type2String(variableDeclaration.getType());
                if (!StringUtils.equals(type1, type) && !type1.equals("var") && !type.equals("var"))
                    continue;
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) variableDeclaration.fragments().get(0);
                for (Pair<StatementNodeTree, StatementNodeTree> statementPair : matchedStatements) {
                    List<StatementNodeTree> children = statement.getParent().getChildren();
                    StatementNodeTree sntBefore = statementPair.getLeft();
                    StatementNodeTree sntCurrent = statementPair.getRight();
                    if (!children.contains(sntCurrent)) continue;
                    String expression1 = sntBefore.getExpression();
                    String expression2 = sntCurrent.getExpression();
                    if (!fragment1.getName().getIdentifier().equals(fragment.getName().getIdentifier()) && !expression1.equals(expression2) &&
                            expression1.replace(fragment1.getName().getIdentifier(), fragment.getName().getIdentifier()).equals(expression2)) {
                        matchPair.addAttributeMapVariables(dntBefore, statement);
                        dntCurrent.setMatched(false);
                        matchedEntityDeletion.add(entityPair);
                        addedStatementDeletion.add(statement);
                    }
                    if (fragment1.getName().getIdentifier().equals(fragment.getName().getIdentifier()) && expression1.equals(expression2) &&
                            contains(sntBefore, fragment1.getName().getIdentifier())) {
                        matchPair.addAttributeMapVariables(dntBefore, statement);
                        dntCurrent.setMatched(false);
                        matchedEntityDeletion.add(entityPair);
                        addedStatementDeletion.add(statement);
                    }
                }
            }
        }
        matchedEntities.removeAll(matchedEntityDeletion);
        for (StatementNodeTree statement : deletedStatementDeletion) {
            statement.setMatched();
            deletedStatements.remove(statement);
        }
        for (StatementNodeTree statement : addedStatementDeletion) {
            statement.setMatched();
            addedStatements.remove(statement);
        }
    }

    private boolean contains(StatementNodeTree snt, String name) {
        List<String> list = new ArrayList<>();
        if (snt.getType() == StatementType.DO_STATEMENT) {
            DoStatement doStatement = (DoStatement) snt.getStatement();
            Expression expression = doStatement.getExpression();
            expression.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    list.add(node.getIdentifier());
                    return true;
                }
            });
            return list.contains(name);
        } else if (snt.getType() == StatementType.ENHANCED_FOR_STATEMENT) {
            EnhancedForStatement enhancedForStatement = (EnhancedForStatement) snt.getStatement();
            SingleVariableDeclaration parameter = enhancedForStatement.getParameter();
            Expression expression = enhancedForStatement.getExpression();
            parameter.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    list.add(node.getIdentifier());
                    return true;
                }
            });
            parameter.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    list.add(node.getIdentifier());
                    return true;
                }
            });
            return list.contains(name);
        } else if (snt.getType() == StatementType.FOR_STATEMENT) {
            ForStatement forStatement = (ForStatement) snt.getStatement();
            List<Expression> initializers = forStatement.initializers();
            Expression expression = forStatement.getExpression();
            List<Expression> updaters = forStatement.updaters();
            for (Expression initializer : initializers) {
                initializer.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(SimpleName node) {
                        list.add(node.getIdentifier());
                        return true;
                    }
                });
            }
            expression.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    list.add(node.getIdentifier());
                    return true;
                }
            });
            for (Expression updater : updaters) {
                updater.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(SimpleName node) {
                        list.add(node.getIdentifier());
                        return true;
                    }
                });
            }
            return list.contains(name);
        } else if (snt.getType() == StatementType.IF_STATEMENT) {
            IfStatement ifStatement = (IfStatement) snt.getStatement();
            Expression expression = ifStatement.getExpression();
            expression.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    list.add(node.getIdentifier());
                    return true;
                }
            });
            return list.contains(name);
        } else if (snt.getType() == StatementType.SWITCH_STATEMENT) {
            SwitchStatement switchStatement = (SwitchStatement) snt.getStatement();
            Expression expression = switchStatement.getExpression();
            expression.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    list.add(node.getIdentifier());
                    return true;
                }
            });
            return list.contains(name);
        } else if (snt.getType() == StatementType.TRY_STATEMENT) {
            TryStatement tryStatement = (TryStatement) snt.getStatement();
            List<Expression> resources = tryStatement.resources();
            for (Expression resource : resources) {
                resource.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(SimpleName node) {
                        list.add(node.getIdentifier());
                        return true;
                    }
                });
            }
            return list.contains(name);
        } else if (snt.getType() == StatementType.WHILE_STATEMENT) {
            WhileStatement whileStatement = (WhileStatement) snt.getStatement();
            Expression expression = whileStatement.getExpression();
            expression.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    list.add(node.getIdentifier());
                    return true;
                }
            });
            return list.contains(name);
        } else if (snt.getType() == StatementType.CATCH_CLAUSE) {
            CatchClause catchClause = (CatchClause) snt.getStatement();
            SingleVariableDeclaration variable = catchClause.getException();
            variable.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    list.add(node.getIdentifier());
                    return true;
                }
            });
            return list.contains(name);
        }
        ASTNode statement = snt.getStatement();
        statement.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                list.add(node.getIdentifier());
                return true;
            }
        });
        return list.contains(name);
    }
}
