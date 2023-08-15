package org.remapper.util;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.dom.*;
import org.remapper.dto.*;
import org.remapper.service.JDTService;
import org.remapper.visitor.AnonymousClassDeclarationVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JDTServiceImpl implements JDTService {

    /**
     * parse CompilationUnit without binding method
     */
    @Override
    public RootNode parseFileDNT(String filePath, String fileContent) {
        ASTParser parser = ASTParserUtils.getASTParser();
        parser.setSource(fileContent.toCharArray());
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
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

    /**
     * breadth-first search
     */
    private void breadthFirstSearch(CompilationUnit cu, String filePath, List<CustomizedBodyDeclaration> customizedBodyDeclarations) {
        int height = 1;
        while (customizedBodyDeclarations.size() > 0) {
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
                    internalNode.setNamespace(parent.getNamespace().equals("") ? parent.getName() : parent.getNamespace() + "." + parent.getName());
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
                    leafNode.setNamespace(parent.getNamespace().equals("") ? parent.getName() : parent.getNamespace() + "." + parent.getName());
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
                        leafNode.setNamespace(parent.getNamespace().equals("") ? parent.getName() : parent.getNamespace() + "." + parent.getName());
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
                    leafNode.setNamespace(parent.getNamespace().equals("") ? parent.getName() : parent.getNamespace() + "." + parent.getName());
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
                    leafNode.setNamespace(parent.getNamespace().equals("") ? parent.getName() : parent.getNamespace() + "." + parent.getName());
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
                    leafNode.setNamespace(parent.getNamespace().equals("") ? parent.getName() : parent.getNamespace() + "." + parent.getName());
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
    public List<ChildNode> getDescendants(ASTNode node) {
        List<ChildNode> descendants = new ArrayList<>();
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
