package org.remapper.util;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import org.remapper.dto.*;
import org.remapper.service.JDTService;
import org.remapper.visitor.AnonymousClassDeclarationVisitor;
import org.remapper.visitor.StatementVisitor;

import java.util.*;
import java.util.stream.Collectors;

public class JDTServiceImpl implements JDTService {

    /**
     * parse CompilationUnit without binding method
     */
    @Override
    public RootNode parseFileDNT(String filePath, String fileContent) {
        ASTParser parser = ASTParserUtils.getASTParser();
        char[] charArray = fileContent.toCharArray();
        parser.setSource(charArray);
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        String maxRecommendedVersionFromProblems = getMaxRecommendedVersionFromProblems(cu);
        if (maxRecommendedVersionFromProblems != null)
            cu = ASTParserUtils.getCompilationUnit(maxRecommendedVersionFromProblems, parser, charArray);
        PackageDeclaration packageDeclaration = cu.getPackage();
        String container = packageDeclaration != null ? packageDeclaration.getName().getFullyQualifiedName() : "";
        RootNode rootNode = new RootNode(cu, filePath, cu);
        rootNode.setDeclaration(cu);
        rootNode.setFilePath(filePath);
        List<AbstractTypeDeclaration> types = cu.types();
        for (AbstractTypeDeclaration type : types) {
            DeclarationNodeTree topLevelNode = new InternalNode(cu, filePath, type);
            topLevelNode.setHeight(1);
            if (type instanceof TypeDeclaration) {
                if (((TypeDeclaration) type).isInterface())
                    topLevelNode.setType(EntityType.INTERFACE);
                else
                    topLevelNode.setType(EntityType.CLASS);
            } else if (type instanceof EnumDeclaration)
                topLevelNode.setType(EntityType.ENUM);
            else if (type instanceof RecordDeclaration)
                topLevelNode.setType(EntityType.RECORD);
            else if (type instanceof AnnotationTypeDeclaration)
                topLevelNode.setType(EntityType.ANNOTATION_TYPE);
            topLevelNode.setNamespace(container);
            topLevelNode.setName(type.getName().getFullyQualifiedName());
            topLevelNode.setParent(rootNode);
            type.setJavadoc(null);
            topLevelNode.setDeclaration(type);
            topLevelNode.setFilePath(filePath);
            rootNode.addChild(topLevelNode);
            List<CustomizedBodyDeclaration> customizedBodyDeclarations = new ArrayList<>();
            if (type instanceof EnumDeclaration) {
                List<EnumConstantDeclaration> ecs = ((EnumDeclaration) type).enumConstants();
                for (EnumConstantDeclaration ec : ecs)
                    customizedBodyDeclarations.add(new CustomizedBodyDeclaration(topLevelNode, ec));
            }
            List<BodyDeclaration> bodies = type.bodyDeclarations();
            for (BodyDeclaration body : bodies)
                customizedBodyDeclarations.add(new CustomizedBodyDeclaration(topLevelNode, body));
            breadthFirstSearch(cu, filePath, customizedBodyDeclarations);
            AnonymousClassDeclarationVisitor visitor = new AnonymousClassDeclarationVisitor();
            type.accept(visitor);
            Set<AnonymousClassDeclaration> anonymousClassDeclarations = visitor.getAnonymousClassDeclarations();
            for (AnonymousClassDeclaration anonymous : anonymousClassDeclarations) {
                List<BodyDeclaration> bodyDeclarations = anonymous.bodyDeclarations();
                for (BodyDeclaration bodyDeclaration : bodyDeclarations) {
                    if (bodyDeclaration instanceof FieldDeclaration || bodyDeclaration instanceof MethodDeclaration ||
                            bodyDeclaration instanceof Initializer)
                        bodyDeclaration.setJavadoc(null);
                }
            }
        }
        return rootNode;
    }

    private static String getMaxRecommendedVersionFromProblems(CompilationUnit compilationUnit) {
        IProblem[] problems = compilationUnit.getProblems();
        String result = null;
        for (IProblem problem : problems) {
            String[] arguments = problem.getArguments();
            if (arguments != null && arguments.length > 1) {
                try {
                    double value = Double.parseDouble(arguments[1]);
                    if (result == null || value > Double.parseDouble(result)) {
                        result = arguments[1];
                    }
                } catch (NumberFormatException e) {
//					System.out.println("Invalid number format in arguments: " + arguments[1]);
                }
            }
        }
        return result;
    }

    /**
     * breadth-first search
     */
    private void breadthFirstSearch(CompilationUnit cu, String filePath, List<CustomizedBodyDeclaration> customizedBodyDeclarations) {
        int height = 1;
        while (!customizedBodyDeclarations.isEmpty()) {
            height++;
            List<CustomizedBodyDeclaration> deletion = new ArrayList<>();
            List<CustomizedBodyDeclaration> addition = new ArrayList<>();
            for (CustomizedBodyDeclaration cbd : customizedBodyDeclarations) {
                BodyDeclaration body = cbd.getBodyDeclaration();
                DeclarationNodeTree parent = cbd.getParent();
                if (body instanceof AbstractTypeDeclaration) {
                    deletion.add(cbd);
                    DeclarationNodeTree internalNode = new InternalNode(cu, filePath, body);
                    internalNode.setHeight(height);
                    if (body instanceof TypeDeclaration)
                        internalNode.setType(((TypeDeclaration) body).isInterface() ? EntityType.INTERFACE : EntityType.CLASS);
                    else if (body instanceof EnumDeclaration) {
                        internalNode.setType(EntityType.ENUM);
                    } else if (body instanceof RecordDeclaration)
                        internalNode.setType(EntityType.RECORD);
                    else if (body instanceof AnnotationTypeDeclaration)
                        internalNode.setType(EntityType.ANNOTATION_TYPE);
                    internalNode.setNamespace("".equals(parent.getNamespace()) ? parent.getName() : parent.getNamespace() + "." + parent.getName());
                    internalNode.setName(((AbstractTypeDeclaration) body).getName().getFullyQualifiedName());
                    internalNode.setParent(parent);
                    body.setJavadoc(null);
                    internalNode.setDeclaration(body);
                    internalNode.setFilePath(filePath);
                    parent.addChild(internalNode);
                    if (body instanceof EnumDeclaration) {
                        List<EnumConstantDeclaration> ecs = ((EnumDeclaration) body).enumConstants();
                        for (EnumConstantDeclaration ec : ecs) {
                            CustomizedBodyDeclaration customizedBodyDeclaration = new CustomizedBodyDeclaration(internalNode, ec);
                            addition.add(customizedBodyDeclaration);
                        }
                    }
                    List<BodyDeclaration> bds = ((AbstractTypeDeclaration) body).bodyDeclarations();
                    for (BodyDeclaration bd : bds) {
                        if (bd instanceof AbstractTypeDeclaration || bd instanceof Initializer ||
                                bd instanceof FieldDeclaration || bd instanceof MethodDeclaration) {
                            CustomizedBodyDeclaration customizedBodyDeclaration = new CustomizedBodyDeclaration(internalNode, bd);
                            addition.add(customizedBodyDeclaration);
                        }
                    }
                } else if (body instanceof Initializer) {
                    deletion.add(cbd);
                    DeclarationNodeTree leafNode = new LeafNode(cu, filePath, body);
                    leafNode.setHeight(height);
                    leafNode.setType(EntityType.INITIALIZER);
                    leafNode.setNamespace("".equals(parent.getNamespace()) ? parent.getName() : parent.getNamespace() + "." + parent.getName());
                    int modifiers = body.getModifiers();
                    leafNode.setName(Flags.isStatic(modifiers) ? "static" : "instance");
                    leafNode.setParent(parent);
                    body.setJavadoc(null);
                    leafNode.setDeclaration(body);
                    leafNode.setFilePath(filePath);
                    parent.addChild(leafNode);
                } else if (body instanceof FieldDeclaration) {
                    deletion.add(cbd);
                    List<VariableDeclarationFragment> fragments = ((FieldDeclaration) body).fragments();
                    for (VariableDeclarationFragment fragment : fragments) {
                        DeclarationNodeTree leafNode = new LeafNode(cu, filePath, fragment);
                        leafNode.setHeight(height);
                        leafNode.setType(EntityType.FIELD);
                        leafNode.setNamespace("".equals(parent.getNamespace()) ? parent.getName() : parent.getNamespace() + "." + parent.getName());
                        leafNode.setName(fragment.getName().getFullyQualifiedName());
                        leafNode.setParent(parent);
                        body.setJavadoc(null);
                        if (fragments.size() > 1) {
                            FieldDeclaration fieldNode = (FieldDeclaration) ASTNode.copySubtree(body.getParent().getAST(), body);
                            fieldNode.fragments().clear();
                            VariableDeclarationFragment fragmentNode = (VariableDeclarationFragment) ASTNode.copySubtree(fragment.getParent().getAST(), fragment);
                            fieldNode.fragments().add(fragmentNode);
                            fieldNode.setJavadoc(null);
                            leafNode.setDeclaration(fieldNode);
                        } else
                            leafNode.setDeclaration(body);
                        leafNode.setFilePath(filePath);
                        parent.addChild(leafNode);
                    }
                } else if (body instanceof MethodDeclaration) {
                    deletion.add(cbd);
                    DeclarationNodeTree leafNode = new LeafNode(cu, filePath, body);
                    leafNode.setHeight(height);
                    leafNode.setType(EntityType.METHOD);
                    leafNode.setNamespace("".equals(parent.getNamespace()) ? parent.getName() : parent.getNamespace() + "." + parent.getName());
                    leafNode.setName(((MethodDeclaration) body).getName().getFullyQualifiedName());
                    leafNode.setParent(parent);
                    body.setJavadoc(null);
                    leafNode.setDeclaration(body);
                    leafNode.setFilePath(filePath);
                    parent.addChild(leafNode);
                } else if (body instanceof AnnotationTypeMemberDeclaration) {
                    deletion.add(cbd);
                    DeclarationNodeTree leafNode = new LeafNode(cu, filePath, body);
                    leafNode.setHeight(height);
                    leafNode.setType(EntityType.ANNOTATION_MEMBER);
                    leafNode.setNamespace("".equals(parent.getNamespace()) ? parent.getName() : parent.getNamespace() + "." + parent.getName());
                    leafNode.setName(((AnnotationTypeMemberDeclaration) body).getName().getFullyQualifiedName());
                    leafNode.setParent(parent);
                    body.setJavadoc(null);
                    leafNode.setDeclaration(body);
                    leafNode.setFilePath(filePath);
                    parent.addChild(leafNode);
                } else if (body instanceof EnumConstantDeclaration) {
                    deletion.add(cbd);
                    DeclarationNodeTree leafNode = new LeafNode(cu, filePath, body);
                    leafNode.setHeight(height);
                    leafNode.setType(EntityType.ENUM_CONSTANT);
                    leafNode.setNamespace("".equals(parent.getNamespace()) ? parent.getName() : parent.getNamespace() + "." + parent.getName());
                    leafNode.setName(((EnumConstantDeclaration) body).getName().getFullyQualifiedName());
                    leafNode.setParent(parent);
                    body.setJavadoc(null);
                    leafNode.setDeclaration(body);
                    leafNode.setFilePath(filePath);
                    parent.addChild(leafNode);
                }
            }
            customizedBodyDeclarations.removeAll(deletion);
            customizedBodyDeclarations.addAll(addition);
        }
    }

    @Override
    public MethodNode parseMethodSNT(String filePath, MethodDeclaration methodDeclaration) {
        CompilationUnit cu = (CompilationUnit) methodDeclaration.getRoot();
        MethodNode methodNode = new MethodNode(cu, filePath, methodDeclaration);
        methodNode.setStatement(methodDeclaration);
        methodNode.setPosition(methodDeclaration.getStartPosition());
        parseMethodSNT(filePath, methodDeclaration, methodNode, cu);
        return methodNode;
    }

    private void parseMethodSNT(String filePath, ASTNode methodDeclaration, MethodNode methodNode, CompilationUnit cu) {
        StatementVisitor visitor = new StatementVisitor();
        methodDeclaration.accept(visitor);
        List<ASTNode> statements = visitor.getStatements();
        List<ASTNode> nodes = new ArrayList<>();
        for (ASTNode statement : statements) {
            if (statement instanceof Block)
                continue;
            if (statement instanceof TypeDeclarationStatement)
                continue;
            statement.accept(new ASTVisitor() {
                @Override
                public boolean visit(LambdaExpression node) {
                    nodes.add(node.getBody());
                    StatementVisitor visitor = new StatementVisitor();
                    node.accept(visitor);
                    nodes.addAll(visitor.getStatements());
                    return true;
                }

                @Override
                public boolean visit(AnonymousClassDeclaration node) {
                    StatementVisitor visitor = new StatementVisitor();
                    node.accept(visitor);
                    nodes.addAll(visitor.getStatements());
                    return true;
                }
            });
        }
        statements.addAll(nodes);
        Map<ASTNode, StatementNodeTree> initializedSNT = new HashMap<>();
        initializedSNT.put(methodDeclaration, methodNode);
        for (ASTNode statement : statements) {
            StatementNodeTree snt;
            if (initializedSNT.containsKey(statement)) {
                snt = initializedSNT.get(statement);
            } else {
                snt = createSNT(cu, filePath, statement, methodNode, initializedSNT);
                initializedSNT.put(statement, snt);
            }
            StatementNodeTree parent;
            if (!(statement.getParent() instanceof Statement) && statement.getParent() != methodDeclaration) {
                ASTNode temp = statement.getParent();
                if (!(temp instanceof CatchClause))
                    while (!(temp instanceof Statement)) {
                        temp = temp.getParent();
                    }
                parent = initializedSNT.get(temp);
                if (parent == null) {
                    parent = createSNT(cu, filePath, temp, methodNode, initializedSNT);
                    initializedSNT.put(temp, parent);
                }
            } else {
                parent = initializedSNT.get(statement.getParent());
                if (parent == null) {
                    parent = createSNT(cu, filePath, statement.getParent(), methodNode, initializedSNT);
                    initializedSNT.put(statement.getParent(), parent);
                }
            }
            if (snt.getParent() == null) {
                snt.setParent(parent);
                parent.addChild(snt);
            }
            int depth = 0;
            ASTNode node = statement;
            while (node.getParent() != methodDeclaration) {
                node = node.getParent();
                if (node instanceof Block || node instanceof SwitchStatement)
                    depth += 1;
            }
            snt.setDepth(depth);
            snt.setRoot(methodNode);
        }
    }

    @Override
    public MethodNode parseMethodSNT(String filePath, Initializer initializer) {
        CompilationUnit cu = (CompilationUnit) initializer.getRoot();
        MethodNode methodNode = new MethodNode(cu, filePath, initializer);
        methodNode.setStatement(initializer);
        methodNode.setPosition(initializer.getStartPosition());
        parseMethodSNT(filePath, initializer, methodNode, cu);
        return methodNode;
    }

    private StatementNodeTree createSNT(CompilationUnit cu, String filePath, ASTNode statement, MethodNode methodNode, Map<ASTNode, StatementNodeTree> initializedSNT) {
        StatementNodeTree snt = null;
        if (statement instanceof AssertStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.ASSERT_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof Block) {
            snt = new BlockNode(cu, filePath, statement, initializedSNT);
            snt.setType(StatementType.BLOCK);
            snt.setStatement(statement);
            if (snt.getBlockType() == BlockType.FINALLY_BLOCK)
                snt.setExpression("finally");
            else
                snt.setExpression("{");
            snt.setPosition(statement.getStartPosition());
            methodNode.addBlock(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof BreakStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.BREAK_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof ConstructorInvocation) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.CONSTRUCTOR_INVOCATION);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof ContinueStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.CONTINUE_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof DoStatement) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.DO_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression("do(" + ((DoStatement) statement).getExpression().toString() + ")");
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof EmptyStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.EMPTY_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof EnhancedForStatement) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.ENHANCED_FOR_STATEMENT);
            snt.setStatement(statement);
            EnhancedForStatement enhancedForStatement = (EnhancedForStatement) statement;
            SingleVariableDeclaration parameter = enhancedForStatement.getParameter();
            Expression expression = enhancedForStatement.getExpression();
            int modifiers = parameter.getModifiers();
            snt.setExpression("for(" + (Flags.isFinal(modifiers) ? "final " : "") + parameter.getName().getFullyQualifiedName() + ": " + expression.toString() + ")");
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof ExpressionStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.EXPRESSION_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof ForStatement) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.FOR_STATEMENT);
            snt.setStatement(statement);
            ForStatement forStatement = (ForStatement) statement;
            List<Expression> initializers = forStatement.initializers();
            Expression expression = forStatement.getExpression();
            List<Expression> updaters = forStatement.updaters();
            String initializer = initializers.stream().map(Expression::toString).collect(Collectors.joining(", "));
            String updater = updaters.stream().map(Expression::toString).collect(Collectors.joining(", "));
            snt.setExpression("for(" + initializer + "; " + (expression == null ? "" : expression.toString()) + "; " + updater + ")");
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof IfStatement) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.IF_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression("if(" + ((IfStatement) statement).getExpression().toString() + ")");
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof LabeledStatement) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.LABELED_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof ReturnStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.RETURN_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof SuperConstructorInvocation) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.SUPER_CONSTRUCTOR_INVOCATION);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof SwitchCase) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.SWITCH_CASE);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
        } else if (statement instanceof SwitchStatement) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.SWITCH_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression("switch(" + ((SwitchStatement) statement).getExpression().toString() + ")");
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof SynchronizedStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.SYNCHRONIZED_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof ThrowStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.THROW_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof TryStatement) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.TRY_STATEMENT);
            snt.setStatement(statement);
            List<Expression> resources = ((TryStatement) statement).resources();
            String resource = resources.stream().map(Expression::toString).collect(Collectors.joining("; "));
            snt.setExpression("".equals(resource) ? "try" : "try(" + resource + ")");
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof TypeDeclarationStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.TYPE_DECLARATION_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof VariableDeclarationStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.VARIABLE_DECLARATION_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof WhileStatement) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.WHILE_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression("while(" + ((WhileStatement) statement).getExpression().toString() + ")");
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof CatchClause) {
            snt = new ControlNode(cu, filePath, statement);
            snt.setType(StatementType.CATCH_CLAUSE);
            snt.setStatement(statement);
            snt.setExpression("catch(" + ((CatchClause) statement).getException().getName().getFullyQualifiedName() + ")");
            snt.setPosition(statement.getStartPosition());
            methodNode.addControl(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement instanceof YieldStatement) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.YIELD_STATEMENT);
            snt.setStatement(statement);
            snt.setExpression(statement.toString().replaceFirst("yield ", "-> "));
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
            arrayStatementsInSwitchCase(statement, initializedSNT, snt);
        } else if (statement.getParent() instanceof LambdaExpression) {
            snt = new OperationNode(cu, filePath, statement);
            snt.setType(StatementType.LAMBDA_EXPRESSION_BODY);
            snt.setStatement(statement);
            snt.setExpression(statement.toString());
            snt.setPosition(statement.getStartPosition());
            methodNode.addOperation(snt);
        }
        return snt;
    }

    private void arrayStatementsInSwitchCase(ASTNode statement, Map<ASTNode, StatementNodeTree> initializedSNT, StatementNodeTree snt) {
        if (statement.getParent() instanceof SwitchStatement) {
            SwitchStatement switchStatement = (SwitchStatement) statement.getParent();
            List<Statement> statements = switchStatement.statements();
            for (int i = 0; i < statements.size(); i++) {
                Statement current = statements.get(i);
                boolean exist = false;
                if (statement == current) {
                    for (int j = i - 1; j >= 0; j--) {
                        if (statements.get(j) instanceof SwitchCase) {
                            StatementNodeTree caseSNT = initializedSNT.get(statements.get(j));
                            if (caseSNT != null) {
                                snt.setParent(caseSNT);
                                caseSNT.addChild(snt);
                                exist = true;
                                break;
                            }
                        }
                    }
                    if (exist)
                        break;
                }
            }
        }
    }

    @Override
    public List<ChildNode> getDescendants(ASTNode node) {
        List<ChildNode> descendants = new ArrayList<>();
        if (node == null) return descendants;
        node.accept(new ASTVisitor() {
            @Override
            public boolean preVisit2(ASTNode node) {
                ChildNode child = new ChildNode();
                child.setLabel(node.getNodeType());
                child.setValue(node.toString().strip());
                descendants.add(child);
                return true;
            }
        });
        descendants.remove(0);
        return descendants;
    }
}
