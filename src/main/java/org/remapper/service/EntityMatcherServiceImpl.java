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

    public MatchPair matchEntities(Repository repository, RevCommit currentCommit, final MatchingHandler handler) throws Exception {
        GitService gitService = new GitServiceImpl();
        JDTService jdtService = new JDTServiceImpl();
        SoftwareEntityMatcherService entityMatchingService = new SoftwareEntityMatcherService();
        String commitId = currentCommit.getId().getName();
        MatchPair matchPair = new MatchPair();
        entityMatchingService.matchEntities(gitService, jdtService, repository, currentCommit, matchPair);
        matchStatementsInMethodPairs(matchPair, jdtService);
        handler.handle(commitId, matchPair);
        return matchPair;
    }

    private void matchStatementsInMethodPairs(MatchPair matchPair, JDTService jdtService) {
        MethodStatementMatcherService statementMatchingService = new MethodStatementMatcherService();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<DeclarationNodeTree> deletedEntities = matchPair.getDeletedEntities();
        Set<DeclarationNodeTree> addedEntities = matchPair.getAddedEntities();
        Set<DeclarationNodeTree> extractedEntities = new HashSet<>();
        Set<DeclarationNodeTree> inlinedEntities = new HashSet<>();
        List<Pair<String, String>> oldReplacements = new ArrayList<>();
        List<Pair<String, String>> newReplacements = new ArrayList<>();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
            DeclarationNodeTree oldEntity = pair.getLeft();
            DeclarationNodeTree newEntity = pair.getRight();
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
                        MethodDeclaration addedMethodDeclaration = (MethodDeclaration) addedEntity.getDeclaration();
                        if (MethodUtils.isGetter(addedMethodDeclaration) || MethodUtils.isSetter(addedMethodDeclaration))
                            continue;
                        double dice = DiceFunction.calculateBodyDice((LeafNode) oldEntity, (LeafNode) newEntity, (LeafNode) addedEntity);
                        if (dice < DiceFunction.minSimilarity)
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
                            MethodNode addedMethod = addedEntity.getMethodNode() == null ? jdtService.parseMethodSNT(addedEntity.getFilePath(), addedMethodDeclaration) : addedEntity.getMethodNode();
                            addedEntity.setMethodNode(addedMethod);
                            addedMethod.setMethodEntity(addedEntity);
                            if (!addedMethod.getChildren().isEmpty()) {
                                if (locations.size() > 1)
                                    addedMethod.setDuplicated();
                                for (StatementNodeTree statement : locations) {
                                    StatementNodeTree parent = statement.getParent();
                                    List<StatementNodeTree> children = parent.getChildren();
                                    int i = children.indexOf(statement);
                                    DeclarationNodeTree delegatedEntity = getDelegatedMethod(addedEntity, addedEntities);
                                    MethodDeclaration methodDeclaration = (MethodDeclaration) delegatedEntity.getDeclaration();
                                    MethodNode delegatedMethod = delegatedEntity.getMethodNode() == null ? jdtService.parseMethodSNT(delegatedEntity.getFilePath(), methodDeclaration) : delegatedEntity.getMethodNode();
                                    if (delegatedMethod != addedMethod) {
                                        StatementNodeTree proxyStatement = addedMethod.getAllOperations().get(0);
                                        proxyStatement.setMatched();
                                        matchPair.addDeletedStatement(proxyStatement);
                                        arguments2Parameters(new ArrayList<>(), statement, delegatedEntity, newReplacements);
                                    }
                                    addedMethod = delegatedMethod;
                                    if (!addedMethod.getChildren().isEmpty())
                                        children.addAll(i, addedMethod.getChildren().get(0).getChildren());
                                    if ((statement.getType() == StatementType.EXPRESSION_STATEMENT && ((ExpressionStatement) statement.getStatement()).getExpression() instanceof MethodInvocation) ||
                                            (statement.getType() == StatementType.RETURN_STATEMENT && ((ReturnStatement) statement.getStatement()).getExpression() instanceof MethodInvocation)) {
                                        statement.setMatched();
                                        matchPair.addAddedStatement(statement);
                                        arguments2Parameters(allOperations, statement, addedEntity, newReplacements);
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
                        double dice = DiceFunction.calculateBodyDice((LeafNode) newEntity, (LeafNode) oldEntity, (LeafNode) deletedEntity);
                        if (dice < DiceFunction.minSimilarity)
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
                            MethodNode deletedMethod = deletedEntity.getMethodNode() == null ? jdtService.parseMethodSNT(deletedEntity.getFilePath(), deletedMethodDeclaration) : deletedEntity.getMethodNode();
                            deletedEntity.setMethodNode(deletedMethod);
                            deletedMethod.setMethodEntity(deletedEntity);
                            if (!deletedMethod.getChildren().isEmpty()) {
                                if (locations.size() > 1)
                                    deletedMethod.setDuplicated();
                                for (StatementNodeTree statement : locations) {
                                    StatementNodeTree parent = statement.getParent();
                                    List<StatementNodeTree> children = parent.getChildren();
                                    int i = children.indexOf(statement);
                                    DeclarationNodeTree delegatedEntity = getDelegatedMethod(deletedEntity, deletedEntities);
                                    MethodDeclaration methodDeclaration = (MethodDeclaration) delegatedEntity.getDeclaration();
                                    MethodNode delegatedMethod = delegatedEntity.getMethodNode() == null ? jdtService.parseMethodSNT(delegatedEntity.getFilePath(), methodDeclaration) : delegatedEntity.getMethodNode();
                                    if (delegatedMethod != deletedMethod) {
                                        StatementNodeTree proxyStatement = deletedMethod.getAllOperations().get(0);
                                        proxyStatement.setMatched();
                                        matchPair.addDeletedStatement(proxyStatement);
                                        arguments2Parameters(new ArrayList<>(), proxyStatement, delegatedEntity, oldReplacements);
                                    }
                                    deletedMethod = delegatedMethod;
                                    if (!deletedMethod.getChildren().isEmpty())
                                        children.addAll(i, deletedMethod.getChildren().get(0).getChildren());
                                    if ((statement.getType() == StatementType.EXPRESSION_STATEMENT && ((ExpressionStatement) statement.getStatement()).getExpression() instanceof MethodInvocation) ||
                                            (statement.getType() == StatementType.RETURN_STATEMENT && ((ReturnStatement) statement.getStatement()).getExpression() instanceof MethodInvocation)) {
                                        statement.setMatched();
                                        matchPair.addDeletedStatement(statement);
                                        arguments2Parameters(allOperations, statement, deletedEntity, oldReplacements);
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
        statement.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                if (node.getName().getIdentifier().equals(declaration2.getName().getIdentifier()) &&
                        node.arguments().size() == declaration2.parameters().size())
                    invocations.add(node);
                return true;
            }
        });
        return !invocations.isEmpty();
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
        List<MethodInvocation> methodInvocations = new ArrayList<>();
        statement.getStatement().accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                methodInvocations.add(node);
                return true;
            }
        });
        MethodDeclaration declaration = (MethodDeclaration) additionalMethod.getDeclaration();
        List<SingleVariableDeclaration> parameters = declaration.parameters();
        for (MethodInvocation methodInvocation : methodInvocations) {
            if (!methodInvocation.getName().getIdentifier().equals(declaration.getName().getIdentifier()) ||
                    methodInvocation.arguments().size() != parameters.size())
                continue;
            List<Expression> arguments = methodInvocation.arguments();
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
}
