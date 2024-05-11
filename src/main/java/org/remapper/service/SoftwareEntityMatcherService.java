package org.remapper.service;

import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.remapper.dto.*;
import org.remapper.util.ASTParserUtils;
import org.remapper.util.DiceFunction;
import org.remapper.util.EntityUtils;
import org.remapper.util.StringUtils;
import org.remapper.visitor.NodeDeclarationVisitor;
import org.remapper.visitor.NodeUsageVisitor;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class SoftwareEntityMatcherService {

    protected void matchEntities(GitService gitService, JDTService jdtService, Repository repository,
                                 RevCommit currentCommit, MatchPair matchPair) throws Exception {
        String commitId = currentCommit.getId().getName();
        Set<String> addedFiles = new LinkedHashSet<>();
        Set<String> deletedFiles = new LinkedHashSet<>();
        Set<String> modifiedFiles = new LinkedHashSet<>();
        Map<String, String> renamedFiles = new LinkedHashMap<>();
        Map<String, String> fileContentsBefore = new LinkedHashMap<>();
        Map<String, String> fileContentsCurrent = new LinkedHashMap<>();
        Map<String, RootNode> fileDNTsBefore = new LinkedHashMap<>();
        Map<String, RootNode> fileDNTsCurrent = new LinkedHashMap<>();
        gitService.fileTreeDiff(repository, currentCommit, addedFiles, deletedFiles, modifiedFiles, renamedFiles);

        RevCommit parentCommit = currentCommit.getParent(0);
        populateFileContents(repository, parentCommit, deletedFiles, fileContentsBefore);
        populateFileContents(repository, parentCommit, modifiedFiles, fileContentsBefore);
        populateFileContents(repository, parentCommit, renamedFiles.keySet(), fileContentsBefore);
        populateFileContents(repository, currentCommit, addedFiles, fileContentsCurrent);
        populateFileContents(repository, currentCommit, modifiedFiles, fileContentsCurrent);
        populateFileContents(repository, currentCommit, new HashSet<>(renamedFiles.values()), fileContentsCurrent);

        populateFileDNTs(jdtService, fileContentsBefore, fileDNTsBefore);
        populateFileDNTs(jdtService, fileContentsCurrent, fileDNTsCurrent);

        pruneUnchangedEntitiesInModifiedFiles(matchPair, modifiedFiles, fileDNTsBefore, fileDNTsCurrent);
        pruneUnchangedEntitiesInRenamedFiles(matchPair, renamedFiles, fileDNTsBefore, fileDNTsCurrent);

        matchByNameAndSignature(matchPair, modifiedFiles, fileDNTsBefore, fileDNTsCurrent);
        matchByDiceCoefficient(matchPair, modifiedFiles, renamedFiles, deletedFiles, addedFiles, fileDNTsBefore, fileDNTsCurrent);
        matchByIntroduceObjectRefactoring(matchPair);

        gitService.checkoutCurrent(repository, commitId);
        String projectPath = repository.getWorkTree().getPath();
        populateCurrentDependencies(matchPair, projectPath, modifiedFiles, renamedFiles, addedFiles);
        gitService.resetHard(repository);
        gitService.checkoutParent(repository, commitId);
        populateBeforeDependencies(matchPair, projectPath, modifiedFiles, renamedFiles, deletedFiles);
        gitService.resetHard(repository);

        fineMatching(matchPair);
        additionalMatchByName(matchPair);
        additionalMatchByDice(matchPair);
        additionalMatchByReference(matchPair);
        repairMatching(matchPair);
        filter(matchPair);
    }

    protected void matchEntities(JDTService jdtService, File previousFile, File nextFile, MatchPair matchPair) throws Exception {
        Set<String> addedFiles = new LinkedHashSet<>();
        Set<String> deletedFiles = new LinkedHashSet<>();
        Set<String> modifiedFiles = new LinkedHashSet<>();
        Map<String, String> renamedFiles = new LinkedHashMap<>();
        Map<String, String> fileContentsBefore = new LinkedHashMap<>();
        Map<String, String> fileContentsCurrent = new LinkedHashMap<>();
        Map<String, RootNode> fileDNTsBefore = new LinkedHashMap<>();
        Map<String, RootNode> fileDNTsCurrent = new LinkedHashMap<>();

        String projectPath = "";
        renamedFiles.put(previousFile.getPath().replace("\\", "/"), nextFile.getPath().replace("\\", "/"));

        populateFileContents(previousFile, fileContentsBefore);
        populateFileContents(nextFile, fileContentsCurrent);

        populateFileDNTs(jdtService, fileContentsBefore, fileDNTsBefore);
        populateFileDNTs(jdtService, fileContentsCurrent, fileDNTsCurrent);

        pruneUnchangedEntitiesInRenamedFiles(matchPair, renamedFiles, fileDNTsBefore, fileDNTsCurrent);

        matchByNameAndSignature(matchPair, modifiedFiles, fileDNTsBefore, fileDNTsCurrent);
        matchByDiceCoefficient(matchPair, modifiedFiles, renamedFiles, deletedFiles, addedFiles, fileDNTsBefore, fileDNTsCurrent);
        matchByIntroduceObjectRefactoring(matchPair);

        populateCurrentDependencies(matchPair, projectPath, modifiedFiles, renamedFiles, addedFiles);
        populateBeforeDependencies(matchPair, projectPath, modifiedFiles, renamedFiles, deletedFiles);

        fineMatching(matchPair);
        additionalMatchByName(matchPair);
        additionalMatchByDice(matchPair);
        additionalMatchByReference(matchPair);
        repairMatching(matchPair);
        filter(matchPair);
    }

    private void populateFileContents(Repository repository, RevCommit commit,
                                      Set<String> filePaths, Map<String, String> fileContents) throws IOException {
        RevTree parentTree = commit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(parentTree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String pathString = treeWalk.getPathString();
                if (filePaths.contains(pathString)) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);
                    StringWriter writer = new StringWriter();
                    IOUtils.copy(loader.openStream(), writer, StandardCharsets.UTF_8);
                    fileContents.put(pathString, writer.toString());
                }
            }
        }
    }

    private void populateFileContents(File file, Map<String, String> fileContents) throws IOException {
        String path = file.getPath().replace("\\", "/");
        String contents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        fileContents.put(path, contents);
    }

    private void populateFileDNTs(JDTService jdtService, Map<String, String> fileContents, Map<String, RootNode> fileDNTs) {
        for (String filePath : fileContents.keySet()) {
            RootNode dntBefore = jdtService.parseFileDNT(filePath, fileContents.get(filePath));
            fileDNTs.put(filePath, dntBefore);
        }
    }

    private void pruneUnchangedEntitiesInModifiedFiles(MatchPair matchPair, Set<String> modifiedFiles, Map<String, RootNode> fileDNTsBefore, Map<String, RootNode> fileDNTsCurrent) {
        for (String filePath : modifiedFiles) {
            RootNode dntBefore = fileDNTsBefore.get(filePath);
            RootNode dntCurrent = fileDNTsCurrent.get(filePath);
            pruneUnchangedEntities(matchPair, dntBefore, dntCurrent);
        }
    }

    private void pruneUnchangedEntitiesInRenamedFiles(MatchPair matchPair, Map<String, String> renamedFiles, Map<String, RootNode> fileDNTsBefore, Map<String, RootNode> fileDNTsCurrent) {
        for (String filePath : renamedFiles.keySet()) {
            String renamedFilePath = renamedFiles.get(filePath);
            RootNode dntBefore = fileDNTsBefore.get(filePath);
            RootNode dntCurrent = fileDNTsCurrent.get(renamedFilePath);
            pruneUnchangedEntities(matchPair, filePath, renamedFilePath, dntBefore, dntCurrent);
        }
    }

    private boolean pruneUnchangedEntities(MatchPair matchPair, DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        if (StringUtils.equals(dntBefore.getDeclaration().toString(), dntCurrent.getDeclaration().toString())) {
            if (dntBefore.isRoot() && dntCurrent.isRoot() && dntBefore.hasChildren() && dntCurrent.hasChildren())
                pruneUnchangedEntities(matchPair, dntBefore.getChildren(), dntCurrent.getChildren());
            return true;
        } else if (dntBefore.hasChildren() && dntCurrent.hasChildren())
            pruneUnchangedEntities(matchPair, dntBefore.getChildren(), dntCurrent.getChildren());
        return false;
    }

    private void pruneUnchangedEntities(MatchPair matchPair, List<DeclarationNodeTree> childDNTsBefore, List<DeclarationNodeTree> childDNTsCurrent) {
        List<DeclarationNodeTree> deletionBefore = new ArrayList<>();
        List<DeclarationNodeTree> deletionCurrent = new ArrayList<>();
        for (DeclarationNodeTree node1 : childDNTsBefore) {
            for (DeclarationNodeTree node2 : childDNTsCurrent) {
                if (!node1.equals(node2) || !pruneUnchangedEntities(matchPair, node1, node2))
                    continue;
                deletionBefore.add(node1);
                deletionCurrent.add(node2);
                node1.setMatched();
                node2.setMatched();
                matchPair.addUnchangedEntity(node1, node2);
                break;
            }
        }
        pruneEntities(childDNTsBefore, childDNTsCurrent, deletionBefore, deletionCurrent);
    }

    private boolean pruneUnchangedEntities(MatchPair matchPair, String filePath, String renamedFilePath, DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        if (StringUtils.equals(dntBefore.getDeclaration().toString(), dntCurrent.getDeclaration().toString())) {
            if (dntBefore.isRoot() && dntCurrent.isRoot() && dntBefore.hasChildren() && dntCurrent.hasChildren())
                pruneUnchangedEntities(matchPair, filePath, renamedFilePath, dntBefore.getChildren(), dntCurrent.getChildren());
            return true;
        } else if (dntBefore.hasChildren() && dntCurrent.hasChildren())
            pruneUnchangedEntities(matchPair, filePath, renamedFilePath, dntBefore.getChildren(), dntCurrent.getChildren());
        return false;
    }

    private void pruneUnchangedEntities(MatchPair matchPair, String filePath, String renamedFilePath, List<DeclarationNodeTree> childDNTsBefore, List<DeclarationNodeTree> childDNTsCurrent) {
        List<DeclarationNodeTree> deletionBefore = new ArrayList<>();
        List<DeclarationNodeTree> deletionCurrent = new ArrayList<>();
        for (DeclarationNodeTree node1 : childDNTsBefore) {
            for (DeclarationNodeTree node2 : childDNTsCurrent) {
                if (!node1.equals(node2, filePath, renamedFilePath) || !pruneUnchangedEntities(matchPair, filePath, renamedFilePath, node1, node2))
                    continue;
                deletionBefore.add(node1);
                deletionCurrent.add(node2);
                addInternalCandidateEntity(matchPair, filePath, renamedFilePath, node1, node2);
                break;
            }
        }
        pruneEntities(childDNTsBefore, childDNTsCurrent, deletionBefore, deletionCurrent);
    }

    private void addInternalCandidateEntity(MatchPair matchPair, String filePath, String renamedFilePath, DeclarationNodeTree node1, DeclarationNodeTree node2) {
        if (node1.equals(node2, filePath, renamedFilePath) &&
                StringUtils.equals(node1.getDeclaration().toString(), node2.getDeclaration().toString())) {
            node1.setMatched();
            node2.setMatched();
            matchPair.addMatchedEntity(node1, node2);
            if (node1.hasChildren() && node2.hasChildren()) {
                List<DeclarationNodeTree> children1 = node1.getChildren();
                List<DeclarationNodeTree> children2 = node2.getChildren();
                for (DeclarationNodeTree child1 : children1)
                    for (DeclarationNodeTree child2 : children2)
                        addInternalCandidateEntity(matchPair, filePath, renamedFilePath, child1, child2);
            }
        }
    }

    private void pruneEntities(List<DeclarationNodeTree> childDNTsBefore, List<DeclarationNodeTree> childDNTsCurrent, List<DeclarationNodeTree> deletionBefore, List<DeclarationNodeTree> deletionCurrent) {
        DeclarationNodeTree parentBefore = childDNTsBefore.get(0).getParent();
        if (parentBefore instanceof InternalNode)
            ((InternalNode) parentBefore).addDescendants(parentBefore.getChildren());
        parentBefore.getChildren().removeAll(deletionBefore);
        DeclarationNodeTree parentCurrent = childDNTsCurrent.get(0).getParent();
        if (parentCurrent instanceof InternalNode)
            ((InternalNode) parentCurrent).addDescendants(parentCurrent.getChildren());
        parentCurrent.getChildren().removeAll(deletionCurrent);
    }

    private void matchByNameAndSignature(MatchPair matchPair, Set<String> modifiedFiles, Map<String, RootNode> fileDNTsBefore, Map<String, RootNode> fileDNTsCurrent) {
        for (String filePath : modifiedFiles) {
            RootNode dntBefore = fileDNTsBefore.get(filePath);
            RootNode dntCurrent = fileDNTsCurrent.get(filePath);
            boolean repairPublicClass = repairPublicClass(dntBefore, dntCurrent);
            if (!dntBefore.hasChildren() || !dntCurrent.hasChildren())
                continue;
            List<DeclarationNodeTree> treeNodesBefore = dntBefore.getAllNodes();
            List<DeclarationNodeTree> treeNodesCurrent = dntCurrent.getAllNodes();
            for (DeclarationNodeTree node1 : treeNodesBefore) {
                for (DeclarationNodeTree node2 : treeNodesCurrent) {
                    if (node1.isMatched() || node2.isMatched())
                        continue;
                    if (!node1.equals(node2) && !(repairPublicClass && node1.getType() == node2.getType() && node1.getName().equals(node2.getName())))
                        continue;
                    if (node1.getType() == EntityType.CLASS || node1.getType() == EntityType.INTERFACE ||
                            node1.getType() == EntityType.ENUM || node1.getType() == EntityType.RECORD ||
                            node1.getType() == EntityType.ANNOTATION_TYPE || node1.getType() == EntityType.INITIALIZER ||
                            node1.getType() == EntityType.ENUM_CONSTANT) {
                        node1.setMatched();
                        node2.setMatched();
                        matchPair.addMatchedEntity(node1, node2);
                        break;
                    } else if (node1.getType() == EntityType.FIELD) {
                        FieldDeclaration fd1 = ((FieldDeclaration) node1.getDeclaration());
                        FieldDeclaration fd2 = ((FieldDeclaration) node2.getDeclaration());
                        UMLType type1 = UMLType.extractTypeObject(fd1.getType());
                        UMLType type2 = UMLType.extractTypeObject(fd2.getType());
                        if (type1 != null && type2 != null && type1.equalClassType(type2)) {
                            node1.setMatched();
                            node2.setMatched();
                            matchPair.addMatchedEntity(node1, node2);
                            break;
                        }
                    } else if (node1.getType() == EntityType.METHOD) {
                        MethodDeclaration md1 = ((MethodDeclaration) node1.getDeclaration());
                        MethodDeclaration md2 = ((MethodDeclaration) node2.getDeclaration());
                        String pl1 = ((List<SingleVariableDeclaration>) md1.parameters()).stream().
                                map(declaration -> declaration.isVarargs() ? StringUtils.type2String(declaration.getType()) + "[]" : StringUtils.type2String(declaration.getType())).
                                collect(Collectors.joining(","));
                        String pl2 = ((List<SingleVariableDeclaration>) md2.parameters()).stream().
                                map(declaration -> declaration.isVarargs() ? StringUtils.type2String(declaration.getType()) + "[]" : StringUtils.type2String(declaration.getType())).
                                collect(Collectors.joining(","));
                        String tp1 = ((List<TypeParameter>) md1.typeParameters()).stream().
                                map(TypeParameter::toString).
                                collect(Collectors.joining(","));
                        String tp2 = ((List<TypeParameter>) md2.typeParameters()).stream().
                                map(TypeParameter::toString).
                                collect(Collectors.joining(","));
                        if (md1.getReturnType2() == null && md2.getReturnType2() == null && StringUtils.equals(pl1, pl2) &&
                                StringUtils.equals(tp1, tp2)) {
                            node1.setMatched();
                            node2.setMatched();
                            matchPair.addMatchedEntity(node1, node2);
                            break;
                        }
                        if (md1.getReturnType2() != null && md2.getReturnType2() != null) {
                            UMLType type1 = UMLType.extractTypeObject(md1.getReturnType2());
                            UMLType type2 = UMLType.extractTypeObject(md2.getReturnType2());
                            if (type1 != null && type2 != null && type1.equalClassType(type2) && StringUtils.equals(pl1, pl2) && StringUtils.equals(tp1, tp2)) {
                                node1.setMatched();
                                node2.setMatched();
                                matchPair.addMatchedEntity(node1, node2);
                                break;
                            }
                        }
                    } else if (node1.getType() == EntityType.ANNOTATION_MEMBER) {
                        AnnotationTypeMemberDeclaration atd1 = ((AnnotationTypeMemberDeclaration) node1.getDeclaration());
                        AnnotationTypeMemberDeclaration atd2 = ((AnnotationTypeMemberDeclaration) node2.getDeclaration());
                        UMLType type1 = UMLType.extractTypeObject(atd1.getType());
                        UMLType type2 = UMLType.extractTypeObject(atd2.getType());
                        if (type1 != null && type2 != null && type1.equalClassType(type2)) {
                            node1.setMatched();
                            node2.setMatched();
                            matchPair.addMatchedEntity(node1, node2);
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean repairPublicClass(RootNode dntBefore, RootNode dntCurrent) {
        CompilationUnit cuBefore = (CompilationUnit) dntBefore.getDeclaration();
        CompilationUnit cuCurrent = (CompilationUnit) dntCurrent.getDeclaration();
        List<AbstractTypeDeclaration> atdListBefore = cuBefore.types();
        List<AbstractTypeDeclaration> atdListCurrent = cuCurrent.types();
        boolean repairPublicClass = false;
        if (atdListBefore.size() == 1 && atdListCurrent.size() == 1) {
            AbstractTypeDeclaration atdBefore = atdListBefore.get(0);
            AbstractTypeDeclaration atdCurrent = atdListCurrent.get(0);
            String identifierBefore = atdBefore.getName().getIdentifier();
            String identifierCurrent = atdCurrent.getName().getIdentifier();
            String tempBefore = "", tempCurrent = "";
            if (atdBefore instanceof TypeDeclaration) {
                TypeDeclaration tdBefore = (TypeDeclaration) atdBefore;
                if (tdBefore.isInterface()) {
                    tempBefore = "public interface " + identifierBefore;
                } else {
                    tempBefore = "public class " + identifierBefore;
                }
            } else if (atdBefore instanceof EnumDeclaration) {
                tempBefore = "public enum " + identifierBefore;
            }
            if (atdCurrent instanceof TypeDeclaration) {
                TypeDeclaration tdCurrent = (TypeDeclaration) atdCurrent;
                if (tdCurrent.isInterface()) {
                    tempCurrent = "public interface " + identifierCurrent;
                } else {
                    tempCurrent = "public class " + identifierCurrent;
                }
            } else if (atdCurrent instanceof EnumDeclaration) {
                tempCurrent = "public enum " + identifierCurrent;
            }
            if (atdBefore.toString().replace(tempBefore, tempCurrent).equals(atdCurrent.toString()))
                repairPublicClass = true;
        }
        return repairPublicClass;
    }

    private void matchByDiceCoefficient(MatchPair matchPair, Set<String> modifiedFiles, Map<String, String> renamedFiles,
                                        Set<String> deletedFiles, Set<String> addedFiles, Map<String, RootNode> fileDNTsBefore,
                                        Map<String, RootNode> fileDNTsCurrent) {
        for (String filePath : modifiedFiles) {
            RootNode dntBefore = fileDNTsBefore.get(filePath);
            RootNode dntCurrent = fileDNTsCurrent.get(filePath);
            matchLeafNodesByDice(matchPair, dntBefore.getLeafNodes(), dntCurrent.getLeafNodes());
            matchInternalNodesByDice(matchPair, dntBefore.getInternalNodes(), dntCurrent.getInternalNodes());
            matchPair.addDeletedEntities(dntBefore.getUnmatchedNodes());
            matchPair.addAddedEntities(dntCurrent.getUnmatchedNodes());
        }
        for (String filePath : renamedFiles.keySet()) {
            RootNode dntBefore = fileDNTsBefore.get(filePath);
            RootNode dntCurrent = fileDNTsCurrent.get(renamedFiles.get(filePath));
            matchLeafNodesByDice(matchPair, dntBefore.getLeafNodes(), dntCurrent.getLeafNodes());
            matchInternalNodesByDice(matchPair, dntBefore.getInternalNodes(), dntCurrent.getInternalNodes());
            matchPair.addDeletedEntities(dntBefore.getUnmatchedNodes());
            matchPair.addAddedEntities(dntCurrent.getUnmatchedNodes());
        }
        for (String filePath : deletedFiles) {
            RootNode dntBefore = fileDNTsBefore.get(filePath);
            matchPair.addDeletedEntities(dntBefore.getUnmatchedNodes());
        }
        for (String filePath : addedFiles) {
            RootNode dntCurrent = fileDNTsCurrent.get(filePath);
            matchPair.addAddedEntities(dntCurrent.getUnmatchedNodes());
        }
        List<LeafNode> leafDeletion = new ArrayList<>();
        List<InternalNode> internalDeletion = new ArrayList<>();
        matchPair.getDeletedEntities().forEach(dnt -> {
            if (dnt.isLeaf())
                leafDeletion.add((LeafNode) dnt);
            else if (!dnt.isLeaf() && !dnt.isRoot())
                internalDeletion.add((InternalNode) dnt);
        });
        List<LeafNode> leafAddition = new ArrayList<>();
        List<InternalNode> internalAddition = new ArrayList<>();
        matchPair.getAddedEntities().forEach(dnt -> {
            if (dnt.isLeaf())
                leafAddition.add((LeafNode) dnt);
            else if (!dnt.isLeaf() && !dnt.isRoot())
                internalAddition.add((InternalNode) dnt);
        });
        matchLeafNodesByDice(matchPair, leafDeletion, leafAddition);
        matchInternalNodesByDice(matchPair, internalDeletion, internalAddition);
        matchPair.getDeletedEntities().removeAll(matchPair.getCandidateEntitiesLeft());
        matchPair.getAddedEntities().removeAll(matchPair.getCandidateEntitiesRight());
    }

    private void matchLeafNodesByDice(MatchPair matchPair, List<LeafNode> leafNodesBefore, List<LeafNode> leafNodesCurrent) {
        List<EntityPair> entityPairs = new ArrayList<>();
        for (LeafNode leafBefore : leafNodesBefore) {
            for (LeafNode leafCurrent : leafNodesCurrent) {
                if (leafBefore.getType() != leafCurrent.getType())
                    continue;
                double dice = DiceFunction.calculateDiceSimilarity(leafBefore, leafCurrent);
                if (dice < DiceFunction.minSimilarity)
                    continue;
                EntityPair entityPair = new EntityPair(leafBefore, leafCurrent);
                entityPair.setDice(dice);
                entityPairs.add(entityPair);
            }
        }
        addCandidateEntities(matchPair, entityPairs);
    }

    private void matchInternalNodesByDice(MatchPair matchPair, List<InternalNode> internalNodesBefore, List<InternalNode> internalNodesCurrent) {
        List<EntityPair> entityPairs = new ArrayList<>();
        for (InternalNode internalBefore : internalNodesBefore) {
            for (InternalNode internalCurrent : internalNodesCurrent) {
                if (internalBefore.getType() != internalCurrent.getType())
                    continue;
                double dice = DiceFunction.calculateDiceSimilarity(matchPair, internalBefore, internalCurrent);
                if (dice < DiceFunction.minSimilarity)
                    continue;
                EntityPair entityPair = new EntityPair(internalBefore, internalCurrent);
                entityPair.setDice(dice);
                entityPairs.add(entityPair);
            }
        }
        addCandidateEntities(matchPair, entityPairs);
    }

    private void addCandidateEntities(MatchPair matchPair, List<EntityPair> entityPairs) {
        Collections.sort(entityPairs);
        Set<DeclarationNodeTree> existBefore = new HashSet<>();
        Set<DeclarationNodeTree> existCurrent = new HashSet<>();
        for (EntityPair entityPair : entityPairs) {
            DeclarationNodeTree node1 = entityPair.getLeft();
            DeclarationNodeTree node2 = entityPair.getRight();
            if (existBefore.contains(node1) || existCurrent.contains(node2))
                continue;
            existBefore.add(node1);
            existCurrent.add(node2);
            matchPair.addCandidateEntity(node1, node2);
        }
    }

    private void populateEntityDependencies(ProjectParser parser, Map<EntityInfo, List<EntityInfo>> dependencies, Map<String, List<ASTNode>> astNodes) {
        for (String filePath : parser.getRelatedJavaFiles()) {
            ASTParser astParser = ASTParserUtils.getASTParser(parser.getSourcepathEntries(), parser.getEncodings());
            try {
                String code = FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
                astParser.setSource(code.toCharArray());
                CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
                NodeDeclarationVisitor visitor = new NodeDeclarationVisitor();
                cu.accept(visitor);
                astNodes.put(filePath, visitor.getASTNodes());
                List<TypeDeclaration> typeDeclarations = visitor.getTypeDeclarations();
                List<EnumDeclaration> enumDeclarations = visitor.getEnumDeclarations();
                List<AnnotationTypeDeclaration> annotationTypeDeclarations = visitor.getAnnotationTypeDeclarations();
                List<RecordDeclaration> recordDeclarations = visitor.getRecordDeclarations();
                List<Initializer> initializers = visitor.getInitializers();
                List<EnumConstantDeclaration> enumConstantDeclarations = visitor.getEnumConstantDeclarations();
                List<FieldDeclaration> fieldDeclarations = visitor.getFieldDeclarations();
                List<MethodDeclaration> methodDeclarations = visitor.getMethodDeclarations();
                List<AnnotationTypeMemberDeclaration> annotationMemberDeclarations = visitor.getAnnotationMemberDeclarations();
                populateDependencyOnTypeDeclaration(typeDeclarations, dependencies, cu, filePath);
                populateDependencyOnEnumDeclaration(enumDeclarations, dependencies, cu, filePath);
                populateDependencyOnAnnotationTypeDeclaration(annotationTypeDeclarations, dependencies, cu, filePath);
                populateDependencyOnRecordDeclaration(recordDeclarations, dependencies, cu, filePath);
                populateDependencyInInitializers(initializers, dependencies, cu, filePath);
                populateDependencyInFieldDeclaration(fieldDeclarations, dependencies, cu, filePath);
                populateDependencyInMethodDeclaration(methodDeclarations, dependencies, cu, filePath);
                populateDependencyInAnnotationMemberDeclaration(annotationMemberDeclarations, dependencies, cu, filePath);
                populateDependencyInEnumConstant(enumConstantDeclarations, dependencies, cu, filePath);
            } catch (IOException ignored) {
            }
        }
    }

    private void populateDependencyOnTypeDeclaration(List<TypeDeclaration> typeDeclarations,
                                                     Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit cu, String filePath) {
        for (TypeDeclaration declaration : typeDeclarations) {
            Type superclassType = declaration.getSuperclassType();
            List<Type> superInterfaceTypes = declaration.superInterfaceTypes();
            List<TypeParameter> typeParameters = declaration.typeParameters();
            List<IExtendedModifier> modifiers = declaration.modifiers();
            List<EntityInfo> entityUsages = new ArrayList<>();
            populateWithSuperClassType(superclassType, entityUsages);
            populateWithSuperInterfaceTypes(superInterfaceTypes, entityUsages);
            populateWithTypeParameters(typeParameters, entityUsages);
            populateWithModifiers(modifiers, entityUsages);
            EntityInfo typeEntity = EntityUtils.generateTypeEntity(declaration.resolveBinding());
            LocationInfo locationInfo = new LocationInfo(cu, filePath, declaration);
            typeEntity.setLocationInfo(locationInfo);
            populateDependencyInReverse(typeEntity, entityUsages, dependencies);
        }
    }

    private void populateDependencyOnEnumDeclaration(List<EnumDeclaration> enumDeclarations,
                                                     Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit cu, String filePath) {
        for (EnumDeclaration declaration : enumDeclarations) {
            List<Type> superInterfaceTypes = declaration.superInterfaceTypes();
            List<IExtendedModifier> modifiers = declaration.modifiers();
            List<EntityInfo> entityUsages = new ArrayList<>();
            populateWithSuperInterfaceTypes(superInterfaceTypes, entityUsages);
            populateWithModifiers(modifiers, entityUsages);
            EntityInfo enumEntity = EntityUtils.generateTypeEntity(declaration.resolveBinding());
            LocationInfo locationInfo = new LocationInfo(cu, filePath, declaration);
            enumEntity.setLocationInfo(locationInfo);
            populateDependencyInReverse(enumEntity, entityUsages, dependencies);
        }
    }

    private void populateDependencyOnAnnotationTypeDeclaration(List<AnnotationTypeDeclaration> annotationTypeDeclarations,
                                                               Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit cu, String filePath) {
        for (AnnotationTypeDeclaration declaration : annotationTypeDeclarations) {
            List<IExtendedModifier> modifiers = declaration.modifiers();
            List<EntityInfo> entityUsages = new ArrayList<>();
            populateWithModifiers(modifiers, entityUsages);
            EntityInfo annotationTypeEntity = EntityUtils.generateTypeEntity(declaration.resolveBinding());
            LocationInfo locationInfo = new LocationInfo(cu, filePath, declaration);
            annotationTypeEntity.setLocationInfo(locationInfo);
            populateDependencyInReverse(annotationTypeEntity, entityUsages, dependencies);
        }
    }

    private void populateDependencyOnRecordDeclaration
            (List<RecordDeclaration> recordDeclarations, Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit
                    cu, String filePath) {
        for (RecordDeclaration declaration : recordDeclarations) {
            List<IExtendedModifier> modifiers = declaration.modifiers();
            List<TypeParameter> typeParameters = declaration.typeParameters();
            List<Type> superInterfaceTypes = declaration.superInterfaceTypes();
            List<EntityInfo> entityUsages = new ArrayList<>();
            populateWithSuperInterfaceTypes(superInterfaceTypes, entityUsages);
            populateWithTypeParameters(typeParameters, entityUsages);
            populateWithModifiers(modifiers, entityUsages);
            EntityInfo recordEntity = EntityUtils.generateTypeEntity(declaration.resolveBinding());
            LocationInfo locationInfo = new LocationInfo(cu, filePath, declaration);
            recordEntity.setLocationInfo(locationInfo);
            populateDependencyInReverse(recordEntity, entityUsages, dependencies);
        }
    }

    private void populateWithSuperClassType(Type superclassType, List<EntityInfo> entityUsages) {
        if (superclassType != null) {
            NodeUsageVisitor visitor = new NodeUsageVisitor();
            superclassType.accept(visitor);
            entityUsages.addAll(visitor.getEntityUsages());
        }
    }

    private void populateWithSuperInterfaceTypes(List<Type> superInterfaceTypes, List<EntityInfo> dependencies) {
        for (Type superInterfaceType : superInterfaceTypes) {
            NodeUsageVisitor visitor = new NodeUsageVisitor();
            superInterfaceType.accept(visitor);
            dependencies.addAll(visitor.getEntityUsages());
        }
    }

    private void populateWithTypeParameters(List<TypeParameter> typeParameters, List<EntityInfo> entityUsages) {
        for (TypeParameter typeParameter : typeParameters) {
            NodeUsageVisitor visitor = new NodeUsageVisitor();
            typeParameter.accept(visitor);
            entityUsages.addAll(visitor.getEntityUsages());
        }
    }

    private void populateWithModifiers(List<IExtendedModifier> modifiers, List<EntityInfo> entityUsages) {
        for (IExtendedModifier modifier : modifiers) {
            if (modifier.isAnnotation()) {
                Annotation annotation = (Annotation) modifier;
                NodeUsageVisitor visitor = new NodeUsageVisitor();
                annotation.accept(visitor);
                entityUsages.addAll(visitor.getEntityUsages());
            }
        }
    }

    private void populateDependencyInInitializers(List<Initializer> initializers,
                                                  Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit cu, String filePath) {
        for (Initializer initializer : initializers) {
            NodeUsageVisitor visitor = new NodeUsageVisitor();
            initializer.accept(visitor);
            List<EntityInfo> entityUsages = visitor.getEntityUsages();
            ITypeBinding typeBinding = ((AbstractTypeDeclaration) initializer.getParent()).resolveBinding();
            int modifiers = initializer.getModifiers();
            EntityInfo initializerEntity = EntityUtils.generateInitializerEntity(typeBinding, Flags.isStatic(modifiers) ? "static block" : "non-static block");
            LocationInfo locationInfo = new LocationInfo(cu, filePath, initializer);
            initializerEntity.setLocationInfo(locationInfo);
            populateDependencyInReverse(initializerEntity, entityUsages, dependencies);
        }
    }

    private void populateDependencyInFieldDeclaration(List<FieldDeclaration> fieldDeclarations,
                                                      Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit cu, String filePath) {
        for (FieldDeclaration declaration : fieldDeclarations) {
            List<VariableDeclarationFragment> fragments = declaration.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                NodeUsageVisitor visitor = new NodeUsageVisitor();
                declaration.getType().accept(visitor);
                fragment.accept(visitor);
                List<EntityInfo> entityUsages = visitor.getEntityUsages();
                IVariableBinding variableBinding = fragment.resolveBinding();
                ITypeBinding declaringClass = variableBinding.getDeclaringClass();
                entityUsages.removeIf(dependency -> dependency.getType() == EntityType.FIELD &&
                        StringUtils.equals(dependency.getName(), variableBinding.getName()) &&
                        StringUtils.equals(dependency.getContainer(), declaringClass.getQualifiedName()));
                EntityInfo fieldEntity = EntityUtils.generateFieldEntity(variableBinding);
                LocationInfo locationInfo = new LocationInfo(cu, filePath, fragment);
                fieldEntity.setLocationInfo(locationInfo);
                populateDependencyInReverse(fieldEntity, entityUsages, dependencies);
            }
        }
    }

    private void populateDependencyInMethodDeclaration(List<MethodDeclaration> methodDeclarations,
                                                       Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit cu, String filePath) {
        for (MethodDeclaration declaration : methodDeclarations) {
            NodeUsageVisitor visitor = new NodeUsageVisitor();
            declaration.accept(visitor);
            List<EntityInfo> entityUsages = visitor.getEntityUsages();
            IMethodBinding methodBinding = declaration.resolveBinding();
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            ITypeBinding[] parameterTypes = methodBinding.getParameterTypes();
            String params = Arrays.stream(parameterTypes).map(ITypeBinding::getName).collect(Collectors.joining(","));
            entityUsages.removeIf(dependency -> dependency.getType() == EntityType.METHOD &&
                    StringUtils.equals(dependency.getName(), methodBinding.getName()) &&
                    StringUtils.equals(dependency.getParams(), params) &&
                    StringUtils.equals(dependency.getContainer(), declaringClass.getQualifiedName()));
            EntityInfo methodEntity = EntityUtils.generateMethodEntity(declaration.resolveBinding());
            LocationInfo locationInfo = new LocationInfo(cu, filePath, declaration);
            methodEntity.setLocationInfo(locationInfo);
            populateDependencyInReverse(methodEntity, entityUsages, dependencies);
        }
    }

    private void populateDependencyInAnnotationMemberDeclaration(List<AnnotationTypeMemberDeclaration> annotationMemberDeclarations,
                                                                 Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit cu, String filePath) {
        for (AnnotationTypeMemberDeclaration declaration : annotationMemberDeclarations) {
            NodeUsageVisitor visitor = new NodeUsageVisitor();
            declaration.accept(visitor);
            List<EntityInfo> entityUsages = visitor.getEntityUsages();
            EntityInfo typeMemberEntity = EntityUtils.generateMethodEntity(declaration.resolveBinding());
            LocationInfo locationInfo = new LocationInfo(cu, filePath, declaration);
            typeMemberEntity.setLocationInfo(locationInfo);
            populateDependencyInReverse(typeMemberEntity, entityUsages, dependencies);
        }
    }

    private void populateDependencyInEnumConstant(List<EnumConstantDeclaration> enumConstantDeclarations,
                                                  Map<EntityInfo, List<EntityInfo>> dependencies, CompilationUnit cu, String filePath) {
        for (EnumConstantDeclaration declaration : enumConstantDeclarations) {
            NodeUsageVisitor visitor = new NodeUsageVisitor();
            declaration.accept(visitor);
            List<EntityInfo> entityUsages = visitor.getEntityUsages();
            IVariableBinding variableBinding = declaration.resolveVariable();
            ITypeBinding declaringClass = variableBinding.getDeclaringClass();
            entityUsages.removeIf(dependency -> dependency.getType() == EntityType.ENUM_CONSTANT &&
                    StringUtils.equals(dependency.getName(), variableBinding.getName()) &&
                    StringUtils.equals(dependency.getContainer(), declaringClass.getQualifiedName()));
            EntityInfo enumConstantEntity = EntityUtils.generateFieldEntity(declaration.resolveVariable());
            LocationInfo locationInfo = new LocationInfo(cu, filePath, declaration);
            enumConstantEntity.setLocationInfo(locationInfo);
            populateDependencyInReverse(enumConstantEntity, entityUsages, dependencies);
        }
    }

    private void populateDependencyInReverse(EntityInfo entity, List<EntityInfo> entityUsages, Map<EntityInfo, List<EntityInfo>> dependencies) {
        for (EntityInfo dependency : entityUsages) {
            if (dependencies.containsKey(dependency))
                dependencies.get(dependency).add(entity);
            else
                dependencies.put(dependency, new ArrayList<>(Collections.singletonList(entity)));
        }
    }

    private void populateCurrentDependencies(MatchPair matchPair, String projectPath, Set<String> modifiedFiles,
                                             Map<String, String> renamedFiles, Set<String> addedFiles) {
        ProjectParser parser = new ProjectParser(projectPath);
        Map<EntityInfo, DeclarationNodeTree> entities = new HashMap<>();
        List<String> changedJavaFiles = new ArrayList<>();
        changedJavaFiles.addAll(modifiedFiles);
        changedJavaFiles.addAll(addedFiles);
        changedJavaFiles.addAll(renamedFiles.values());
        Map<EntityInfo, List<EntityInfo>> dependencies = new HashMap<>();
        Map<String, List<ASTNode>> nodeMap = new HashMap<>();
        parser.buildEntityDependencies(changedJavaFiles);
        populateEntityDependencies(parser, dependencies, nodeMap);
        for (DeclarationNodeTree dnt : matchPair.getMatchedEntitiesRight()) {
            entities.put(dnt.getEntity(), dnt);
            replaceASTNodeWithBinding(nodeMap, projectPath, dnt);
        }
        for (DeclarationNodeTree dnt : matchPair.getCandidateEntitiesRight()) {
            entities.put(dnt.getEntity(), dnt);
            replaceASTNodeWithBinding(nodeMap, projectPath, dnt);
        }
        for (DeclarationNodeTree dnt : matchPair.getAddedEntities()) {
            entities.put(dnt.getEntity(), dnt);
            replaceASTNodeWithBinding(nodeMap, projectPath, dnt);
        }
        for (EntityInfo entity : entities.keySet()) {
            if (dependencies.containsKey(entity))
                entities.get(entity).addDependencies(dependencies.get(entity));
        }
    }

    private void replaceASTNodeWithBinding(Map<String, List<ASTNode>> nodeMap, String projectPath, DeclarationNodeTree dnt) {
        List<ASTNode> astNodes = nodeMap.get(projectPath.isEmpty() ? dnt.getFilePath() : projectPath + "/" + dnt.getFilePath());
        if (dnt.getType() == EntityType.METHOD) {
            for (ASTNode node : astNodes) {
                if (node instanceof MethodDeclaration) {
                    MethodDeclaration declaration = (MethodDeclaration) node;
                    LocationInfo location = new LocationInfo((CompilationUnit) declaration.getRoot(), dnt.getFilePath(), declaration);
                    if (location.getStartLine() == dnt.getLocationInfo().getStartLine() &&
                            location.getEndLine() == dnt.getLocationInfo().getEndLine() &&
                            location.getStartColumn() == dnt.getLocationInfo().getStartColumn() &&
                            location.getEndColumn() == dnt.getLocationInfo().getEndColumn())
                        dnt.setDeclaration(declaration);
                }
            }
        } else if (dnt.getType() == EntityType.FIELD) {
            for (ASTNode node : astNodes) {
                if (node instanceof FieldDeclaration) {
                    FieldDeclaration declaration = (FieldDeclaration) node;
                    LocationInfo location = new LocationInfo((CompilationUnit) declaration.getRoot(), dnt.getFilePath(), declaration);
                    if (location.getStartLine() == dnt.getLocationInfo().getStartLine() &&
                            location.getEndLine() == dnt.getLocationInfo().getEndLine() &&
                            location.getStartColumn() == dnt.getLocationInfo().getStartColumn() &&
                            location.getEndColumn() == dnt.getLocationInfo().getEndColumn())
                        dnt.setDeclaration(declaration);
                }
            }
        } else if (dnt.getType() == EntityType.ENUM_CONSTANT) {
            for (ASTNode node : astNodes) {
                if (node instanceof EnumConstantDeclaration) {
                    EnumConstantDeclaration declaration = (EnumConstantDeclaration) node;
                    LocationInfo location = new LocationInfo((CompilationUnit) declaration.getRoot(), dnt.getFilePath(), declaration);
                    if (location.getStartLine() == dnt.getLocationInfo().getStartLine() &&
                            location.getEndLine() == dnt.getLocationInfo().getEndLine() &&
                            location.getStartColumn() == dnt.getLocationInfo().getStartColumn() &&
                            location.getEndColumn() == dnt.getLocationInfo().getEndColumn())
                        dnt.setDeclaration(declaration);
                }
            }
        } else if (dnt.getType() == EntityType.CLASS || dnt.getType() == EntityType.INTERFACE || dnt.getType() == EntityType.ENUM) {
            for (ASTNode node : astNodes) {
                if (node instanceof TypeDeclaration) {
                    TypeDeclaration declaration = (TypeDeclaration) node;
                    LocationInfo location = new LocationInfo((CompilationUnit) declaration.getRoot(), dnt.getFilePath(), declaration);
                    if (location.getStartLine() == dnt.getLocationInfo().getStartLine() &&
                            location.getEndLine() == dnt.getLocationInfo().getEndLine() &&
                            location.getStartColumn() == dnt.getLocationInfo().getStartColumn() &&
                            location.getEndColumn() == dnt.getLocationInfo().getEndColumn())
                        dnt.setDeclaration(declaration);
                }
            }
        } else if (dnt.getType() == EntityType.ANNOTATION_TYPE) {
            for (ASTNode node : astNodes) {
                if (node instanceof AnnotationTypeDeclaration) {
                    AnnotationTypeDeclaration declaration = (AnnotationTypeDeclaration) node;
                    LocationInfo location = new LocationInfo((CompilationUnit) declaration.getRoot(), dnt.getFilePath(), declaration);
                    if (location.getStartLine() == dnt.getLocationInfo().getStartLine() &&
                            location.getEndLine() == dnt.getLocationInfo().getEndLine() &&
                            location.getStartColumn() == dnt.getLocationInfo().getStartColumn() &&
                            location.getEndColumn() == dnt.getLocationInfo().getEndColumn())
                        dnt.setDeclaration(declaration);
                }
            }
        } else if (dnt.getType() == EntityType.ANNOTATION_MEMBER) {
            for (ASTNode node : astNodes) {
                if (node instanceof AnnotationTypeMemberDeclaration) {
                    AnnotationTypeMemberDeclaration declaration = (AnnotationTypeMemberDeclaration) node;
                    LocationInfo location = new LocationInfo((CompilationUnit) declaration.getRoot(), dnt.getFilePath(), declaration);
                    if (location.getStartLine() == dnt.getLocationInfo().getStartLine() &&
                            location.getEndLine() == dnt.getLocationInfo().getEndLine() &&
                            location.getStartColumn() == dnt.getLocationInfo().getStartColumn() &&
                            location.getEndColumn() == dnt.getLocationInfo().getEndColumn())
                        dnt.setDeclaration(declaration);
                }
            }
        } else if (dnt.getType() == EntityType.INITIALIZER) {
            for (ASTNode node : astNodes) {
                if (node instanceof Initializer) {
                    Initializer declaration = (Initializer) node;
                    LocationInfo location = new LocationInfo((CompilationUnit) declaration.getRoot(), dnt.getFilePath(), declaration);
                    if (location.getStartLine() == dnt.getLocationInfo().getStartLine() &&
                            location.getEndLine() == dnt.getLocationInfo().getEndLine() &&
                            location.getStartColumn() == dnt.getLocationInfo().getStartColumn() &&
                            location.getEndColumn() == dnt.getLocationInfo().getEndColumn())
                        dnt.setDeclaration(declaration);
                }
            }
        }
    }

    private void populateBeforeDependencies(MatchPair matchPair, String projectPath, Set<String> modifiedFiles,
                                            Map<String, String> renamedFiles, Set<String> deletedFiles) {
        ProjectParser parser = new ProjectParser(projectPath);
        List<String> changedJavaFiles = new ArrayList<>();
        Map<EntityInfo, DeclarationNodeTree> entities = new HashMap<>();
        changedJavaFiles.addAll(modifiedFiles);
        changedJavaFiles.addAll(deletedFiles);
        changedJavaFiles.addAll(renamedFiles.keySet());
        Map<EntityInfo, List<EntityInfo>> dependencies = new HashMap<>();
        Map<String, List<ASTNode>> nodeMap = new HashMap<>();
        parser.buildEntityDependencies(changedJavaFiles);
        populateEntityDependencies(parser, dependencies, nodeMap);
        for (DeclarationNodeTree dnt : matchPair.getMatchedEntitiesLeft()) {
            entities.put(dnt.getEntity(), dnt);
            replaceASTNodeWithBinding(nodeMap, projectPath, dnt);
        }
        for (DeclarationNodeTree dnt : matchPair.getCandidateEntitiesLeft()) {
            entities.put(dnt.getEntity(), dnt);
            replaceASTNodeWithBinding(nodeMap, projectPath, dnt);
        }
        for (DeclarationNodeTree dnt : matchPair.getDeletedEntities()) {
            entities.put(dnt.getEntity(), dnt);
            replaceASTNodeWithBinding(nodeMap, projectPath, dnt);
        }
        for (EntityInfo entity : entities.keySet()) {
            if (dependencies.containsKey(entity))
                entities.get(entity).addDependencies(dependencies.get(entity));
        }
    }

    private void matchByIntroduceObjectRefactoring(MatchPair matchPair) {
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntitiesAddition = new LinkedHashSet<>();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> candidateEntities = matchPair.getCandidateEntities();
        Set<DeclarationNodeTree> deletedEntities = matchPair.getDeletedEntities();
        Set<DeclarationNodeTree> addedEntities = matchPair.getAddedEntities();
        Set<DeclarationNodeTree> deletedEntitiesDeletion = new HashSet<>();
        Set<DeclarationNodeTree> addedEntitiesDeletion = new HashSet<>();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
            DeclarationNodeTree dntBefore = pair.getLeft();
            DeclarationNodeTree dntCurrent = pair.getRight();
            if (!isSameType(dntBefore, dntCurrent))
                continue;
            List<DeclarationNodeTree> children1 = dntBefore.getChildren();
            List<DeclarationNodeTree> children2 = dntCurrent.getChildren();
            List<DeclarationNodeTree> fields1 = children1.stream().filter(child -> !child.isMatched() && child.getType() == EntityType.FIELD).collect(Collectors.toList());
            List<DeclarationNodeTree> fields2 = children2.stream().filter(child -> !child.isMatched() && child.getType() == EntityType.FIELD).collect(Collectors.toList());
            for (DeclarationNodeTree field : fields2) {
                FieldDeclaration fieldDeclaration = (FieldDeclaration) field.getDeclaration();
                Type type = fieldDeclaration.getType();
                UMLType umlType = UMLType.extractTypeObject(type);
                for (DeclarationNodeTree addedEntity : addedEntities) {
                    if (addedEntity.getType() != EntityType.CLASS && addedEntity.getType() != EntityType.INTERFACE && addedEntity.getType() != EntityType.ENUM)
                        continue;
                    AbstractTypeDeclaration declaration = (AbstractTypeDeclaration) addedEntity.getDeclaration();
                    if (umlType == null || !umlType.getClassType().equals(declaration.getName().getIdentifier()))
                        continue;
                    List<DeclarationNodeTree> children = addedEntity.getChildren();
                    List<DeclarationNodeTree> addedFields = children.stream().filter(child -> !child.isMatched() && child.getType() == EntityType.FIELD).collect(Collectors.toList());
                    Set<DeclarationNodeTree> matchedFields = new HashSet<>();
                    for (DeclarationNodeTree field1 : fields1) {
                        for (DeclarationNodeTree addedField : addedFields) {
                            if (matchedFields.contains(addedField)) continue;
                            FieldDeclaration fieldDeclaration1 = (FieldDeclaration) field1.getDeclaration();
                            FieldDeclaration addedFieldDeclaration = (FieldDeclaration) addedField.getDeclaration();
                            UMLType type1 = UMLType.extractTypeObject(fieldDeclaration1.getType());
                            UMLType type2 = UMLType.extractTypeObject(addedFieldDeclaration.getType());
                            if (type1 == null || type2 == null || !type1.equalClassType(type2))
                                continue;
                            matchedFields.add(addedField);
                            matchPair.addIntroducedObjects(addedEntity, field1, addedField);
                            addedEntitiesDeletion.add(addedEntity);
                            if (fields1.size() > 1) {
                                field1.setMatched();
                                addedField.setMatched();
                                deletedEntitiesDeletion.add(field1);
                                addedEntitiesDeletion.add(addedField);
                            }
                            break;
                        }
                    }
                }
            }
        }
        deletedEntities.removeAll(deletedEntitiesDeletion);
        addedEntities.removeAll(addedEntitiesDeletion);
        if (deletedEntitiesDeletion.isEmpty() && addedEntitiesDeletion.isEmpty())
            return;
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> candidateEntitiesDeletion = new HashSet<>();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : candidateEntities) {
            DeclarationNodeTree left = pair.getLeft();
            DeclarationNodeTree right = pair.getRight();
            if (deletedEntitiesDeletion.contains(left)) {
                candidateEntitiesDeletion.add(pair);
                addedEntities.add(right);
            }
            if (addedEntitiesDeletion.contains(right)) {
                candidateEntitiesDeletion.add(pair);
                deletedEntities.add(left);
            }
        }
        candidateEntities.removeAll(candidateEntitiesDeletion);
        matchedEntities.addAll(matchedEntitiesAddition);
    }

    private void fineMatching(MatchPair matchPair) {
        for (int i = 0; i < 5; i++) {
            Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> temp = new HashSet<>();
            Set<DeclarationNodeTree> beforeEntities = new HashSet<>();
            Set<DeclarationNodeTree> currentEntities = new HashSet<>();
            List<EntityPair> entityPairs = new ArrayList<>();
            beforeEntities.addAll(matchPair.getCandidateEntitiesLeft());
            beforeEntities.addAll(matchPair.getDeletedEntities());
            currentEntities.addAll(matchPair.getCandidateEntitiesRight());
            currentEntities.addAll(matchPair.getAddedEntities());
            for (DeclarationNodeTree dntBefore : beforeEntities) {
                for (DeclarationNodeTree dntCurrent : currentEntities) {
                    if (typeCompatible(dntBefore, dntCurrent)) {
                        if (dntBefore.getType() == EntityType.METHOD && dntCurrent.getType() == EntityType.METHOD) {
                            MethodDeclaration oldMethod = (MethodDeclaration) dntBefore.getDeclaration();
                            MethodDeclaration newMethod = (MethodDeclaration) dntCurrent.getDeclaration();
                            if (oldMethod.getBody() != null && newMethod.getBody() == null &&
                                    !isSubTypeOf(matchPair.getMatchedEntities(), dntBefore, dntCurrent, 0))
                                continue;
                            if (oldMethod.getBody() == null && newMethod.getBody() != null &&
                                    !isSubTypeOf(matchPair.getMatchedEntities(), dntBefore, dntCurrent, 1))
                                continue;
                            DeclarationNodeTree dntBeforeParent = dntBefore.getParent();
                            DeclarationNodeTree dntCurrentParent = dntCurrent.getParent();
                            Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
                            Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> candidateEntities = matchPair.getCandidateEntities();
                            if (matchedEntities.contains(Pair.of(dntBeforeParent, dntCurrentParent)) ||
                                    candidateEntities.contains(Pair.of(dntBeforeParent, dntCurrentParent))) {
                                List<IExtendedModifier> modifiers1 = oldMethod.modifiers();
                                List<IExtendedModifier> modifiers2 = newMethod.modifiers();
                                boolean isOverride1 = false;
                                boolean isOverride2 = false;
                                for (IExtendedModifier modifier : modifiers1) {
                                    if (modifier.isAnnotation() && modifier.toString().equals("@Override")) {
                                        isOverride1 = true;
                                        break;
                                    }
                                }
                                for (IExtendedModifier modifier : modifiers2) {
                                    if (modifier.isAnnotation() && modifier.toString().equals("@Override")) {
                                        isOverride2 = true;
                                        break;
                                    }
                                }
                                for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : candidateEntities) {
                                    DeclarationNodeTree left = pair.getLeft();
                                    DeclarationNodeTree right = pair.getRight();
                                    DeclarationNodeTree parent1 = left.getParent();
                                    DeclarationNodeTree parent2 = right.getParent();
                                    if (((isSubTypeOf(dntBeforeParent, parent1) && isSubTypeOf(dntCurrentParent, parent2)) ||
                                            (isOverride1 && isOverride2)) &&
                                            isSameSignature(dntBefore, left) && isSameSignature(dntCurrent, right)) {
                                        EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                                        entityPairs.add(entityPair);
                                        entityPair.setDice(1.0);
                                        break;
                                    }
                                }
                            }
                        }
                        double dice = DiceFunction.calculateSimilarity(matchPair, dntBefore, dntCurrent);
                        if (dice < DiceFunction.minSimilarity)
                            continue;
                        EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                        entityPairs.add(entityPair);
                        entityPair.setDice(dice);
                    }
                }
            }
            Collections.sort(entityPairs);
            Set<DeclarationNodeTree> existBefore = new HashSet<>();
            Set<DeclarationNodeTree> existCurrent = new HashSet<>();
            for (EntityPair entityPair : entityPairs) {
                DeclarationNodeTree node1 = entityPair.getLeft();
                DeclarationNodeTree node2 = entityPair.getRight();
                if (existBefore.contains(node1) || existCurrent.contains(node2))
                    continue;
                existBefore.add(node1);
                existCurrent.add(node2);
                temp.add(Pair.of(node1, node2));
            }
            if (matchPair.getCandidateEntities().size() == temp.size() && matchPair.getCandidateEntities().equals(temp)) {
//                System.out.println("At the " + (i + 1) + "th iteration, the candidate set of entity mapping does not change.");
                break;
            }
            matchPair.setCandidateEntities(temp);
            beforeEntities.removeAll(existBefore);
            matchPair.updateDeletedEntities(beforeEntities);
            currentEntities.removeAll(existCurrent);
            matchPair.updateAddedEntities(currentEntities);
        }
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchPair.getCandidateEntities()) {
            pair.getLeft().setMatched();
            pair.getRight().setMatched();
            matchPair.getMatchedEntities().add(pair);
        }
        matchPair.getCandidateEntities().clear();
    }


    private boolean typeCompatible(DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        return dntBefore.getType() == dntCurrent.getType() ||
                isSameType(dntBefore, dntCurrent);
    }

    private boolean isSameType(DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        return (dntBefore.getType() == EntityType.CLASS || dntBefore.getType() == EntityType.INTERFACE ||
                dntBefore.getType() == EntityType.ENUM || dntBefore.getType() == EntityType.ANNOTATION_TYPE) &&
                (dntCurrent.getType() == EntityType.CLASS || dntCurrent.getType() == EntityType.INTERFACE ||
                        dntCurrent.getType() == EntityType.ENUM || dntCurrent.getType() == EntityType.ANNOTATION_TYPE);
    }

    private void additionalMatchByName(MatchPair matchPair) {
        List<EntityPair> entityPairs = new ArrayList<>();
        for (DeclarationNodeTree dntBefore : matchPair.getDeletedEntities()) {
            for (DeclarationNodeTree dntCurrent : matchPair.getAddedEntities()) {
                if (dntBefore.equals(dntCurrent)) {
                    double dice = 0;
                    if (dntBefore instanceof InternalNode && dntCurrent instanceof InternalNode) {
                        dice = DiceFunction.calculateDiceSimilarity(matchPair, (InternalNode) dntBefore, (InternalNode) dntCurrent);
                    } else if (dntBefore instanceof LeafNode && dntCurrent instanceof LeafNode) {
                        dice = DiceFunction.calculateDiceSimilarity((LeafNode) dntBefore, (LeafNode) dntCurrent);
                    }
                    EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                    entityPairs.add(entityPair);
                    entityPair.setDice(dice);
                } else {
                    if (dntBefore.getType() == EntityType.METHOD && dntCurrent.getType() == EntityType.METHOD) {
                        MethodDeclaration md1 = ((MethodDeclaration) dntBefore.getDeclaration());
                        MethodDeclaration md2 = ((MethodDeclaration) dntCurrent.getDeclaration());
                        List<IExtendedModifier> modifiers1 = md1.modifiers();
                        List<IExtendedModifier> modifiers2 = md2.modifiers();
                        boolean isTestMethod1 = false;
                        boolean isTestMethod2 = false;
                        for (IExtendedModifier modifier : modifiers1) {
                            if (modifier.isAnnotation()) {
                                Annotation annotation = (Annotation) modifier;
                                if (annotation.getTypeName().getFullyQualifiedName().equals("Test")) {
                                    isTestMethod1 = true;
                                    break;
                                }
                            }
                        }
                        for (IExtendedModifier modifier : modifiers2) {
                            if (modifier.isAnnotation()) {
                                Annotation annotation = (Annotation) modifier;
                                if (annotation.getTypeName().getFullyQualifiedName().equals("Test")) {
                                    isTestMethod2 = true;
                                    break;
                                }
                            }
                        }
                        if (isSameSignature(dntBefore, dntCurrent) && isTestMethod1 && isTestMethod2) {
                            double dependencies = DiceFunction.calculateMethodInvocation(md1, md2);
                            if (dependencies > 0.15) {
                                double dice = DiceFunction.calculateDiceSimilarity((LeafNode) dntBefore, (LeafNode) dntCurrent);
                                EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                                entityPairs.add(entityPair);
                                entityPair.setDice(dice);
                            }
                        }
                    }
                    if (dntBefore.getType() == EntityType.FIELD && dntCurrent.getType() == EntityType.FIELD) {
                        if (isSameSignature(dntBefore, dntCurrent)) {
                            double refs = DiceFunction.calculateReferenceSimilarity(matchPair, dntBefore, dntCurrent);
                            if (refs < 0.4) continue;
                            EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                            entityPairs.add(entityPair);
                            entityPair.setDice(refs);
                        }
                    }
                    if (dntBefore instanceof InternalNode && dntCurrent instanceof InternalNode &&
                            (dntBefore.getType() == EntityType.CLASS || dntBefore.getType() == EntityType.INTERFACE) &&
                            (dntCurrent.getType() == EntityType.CLASS || dntCurrent.getType() == EntityType.INTERFACE)) {
                        if (dntBefore.getType() == dntCurrent.getType() && dntBefore.getName().equals(dntCurrent.getName())) {
                            double refs = DiceFunction.calculateReferenceSimilarity(matchPair, dntBefore, dntCurrent);
                            if (refs < 0.4) continue;
                            double dice = DiceFunction.calculateDiceSimilarity(matchPair, (InternalNode) dntBefore, (InternalNode) dntCurrent);
                            if (dice < 0.25) continue;
                            EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                            entityPairs.add(entityPair);
                            entityPair.setDice(refs);
                        }
                    }
                }
            }
        }
        selectByDice(matchPair, entityPairs);
    }

    private boolean isSameSignatureExceptName(DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        if (dntBefore.getType() == EntityType.METHOD && dntCurrent.getType() == EntityType.METHOD) {
            MethodDeclaration md1 = ((MethodDeclaration) dntBefore.getDeclaration());
            MethodDeclaration md2 = ((MethodDeclaration) dntCurrent.getDeclaration());
            String pl1 = ((List<SingleVariableDeclaration>) md1.parameters()).stream().
                    map(declaration -> declaration.isVarargs() ? StringUtils.type2String(declaration.getType()) + "[]" : StringUtils.type2String(declaration.getType())).
                    collect(Collectors.joining(","));
            String pl2 = ((List<SingleVariableDeclaration>) md2.parameters()).stream().
                    map(declaration -> declaration.isVarargs() ? StringUtils.type2String(declaration.getType()) + "[]" : StringUtils.type2String(declaration.getType())).
                    collect(Collectors.joining(","));
            String tp1 = ((List<TypeParameter>) md1.typeParameters()).stream().
                    map(TypeParameter::toString).
                    collect(Collectors.joining(","));
            String tp2 = ((List<TypeParameter>) md2.typeParameters()).stream().
                    map(TypeParameter::toString).
                    collect(Collectors.joining(","));
            if (md1.getReturnType2() != null && md2.getReturnType2() != null) {
                UMLType type1 = UMLType.extractTypeObject(md1.getReturnType2());
                UMLType type2 = UMLType.extractTypeObject(md2.getReturnType2());
                if (type1 != null && type2 != null && type1.equalClassType(type2) &&
                        StringUtils.equals(pl1, pl2) && StringUtils.equals(tp1, tp2))
                    return true;
            }
        }
        if (dntBefore.getType() == EntityType.FIELD && dntCurrent.getType() == EntityType.FIELD) {
            FieldDeclaration fd1 = (FieldDeclaration) dntBefore.getDeclaration();
            FieldDeclaration fd2 = (FieldDeclaration) dntCurrent.getDeclaration();
            List<VariableDeclarationFragment> fragments1 = fd1.fragments();
            List<VariableDeclarationFragment> fragments2 = fd2.fragments();
            if (fragments1.size() == 1 && fragments2.size() == 1) {
                VariableDeclarationFragment fragment1 = fragments1.get(0);
                VariableDeclarationFragment fragment2 = fragments2.get(0);
                UMLType type1 = UMLType.extractTypeObject(fd1.getType());
                UMLType type2 = UMLType.extractTypeObject(fd2.getType());
                if (type1 != null && type2 != null && type1.equalClassType(type2)) {
                    if (isEmptyOrCreation(fd1.getType(), fragment1) && isEmptyOrCreation(fd2.getType(), fragment2))
                        return true;
                    if (fragment1.getInitializer() != null && fragment2.getInitializer() != null &&
                            fragment1.getInitializer().toString().equals(fragment2.getInitializer().toString()))
                        return true;
                }
            }
        }
        return false;
    }

    private boolean isSameSignature(DeclarationNodeTree dntBefore, DeclarationNodeTree dntCurrent) {
        return isSameSignatureExceptName(dntBefore, dntCurrent) && dntBefore.getName().equals(dntCurrent.getName());
    }

    private boolean isEmptyOrCreation(Type type, VariableDeclarationFragment fragment) {
        Expression initializer = fragment.getInitializer();
        if (initializer == null)
            return true;
        else {
            if (initializer instanceof ClassInstanceCreation) {
                ClassInstanceCreation creation = (ClassInstanceCreation) initializer;
                if (type.toString().equals(creation.getType().toString()))
                    return true;
            }
        }
        return false;
    }

    private void additionalMatchByDice(MatchPair matchPair) {
        List<EntityPair> entityPairs = new ArrayList<>();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> unchangedEntities = matchPair.getUnchangedEntities();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        NormalizedLevenshtein levenshtein = new NormalizedLevenshtein();
        for (DeclarationNodeTree dntBefore : matchPair.getDeletedEntities()) {
            for (DeclarationNodeTree dntCurrent : matchPair.getAddedEntities()) {
                if (dntBefore.getType() == dntCurrent.getType()) {
                    double dice = 0;
                    if (dntBefore instanceof LeafNode && dntCurrent instanceof LeafNode) {
                        dice = DiceFunction.calculateDiceSimilarity((LeafNode) dntBefore, (LeafNode) dntCurrent);
                        double references = DiceFunction.calculateReferenceSimilarity(matchPair, dntBefore, dntCurrent);
                        if (references == 0 && dice < 0.85)
                            dice = 0;
                        if (references == 0 && !dntBefore.getDependencies().isEmpty() && !dntCurrent.getDependencies().isEmpty()
                                && !dntBefore.getName().equals(dntCurrent.getName()))
                            dice = 0;
                        if (dntBefore.getType() == EntityType.FIELD && dntCurrent.getType() == EntityType.FIELD) {
                            if (!matchedEntities.contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))) {
                                List<DeclarationNodeTree> children = dntCurrent.getParent().getChildren();
                                for (DeclarationNodeTree child : children) {
                                    if (child.getType() == EntityType.FIELD) continue;
                                    if (child == dntCurrent) continue;
                                    if (dntBefore.getDeclaration().toString().equals(child.getDeclaration().toString())) {
                                        dice = 0;
                                        break;
                                    }
                                }
                                for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : unchangedEntities) {
                                    DeclarationNodeTree right = pair.getRight();
                                    if (dntBefore.getDeclaration().toString().equals(right.getDeclaration().toString()) &&
                                            right.getParent() == dntCurrent.getParent()) {
                                        dice = 0;
                                        break;
                                    }
                                }
                            }
                            if (!(matchPair.getMatchedEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent())) ||
                                    matchPair.getCandidateEntities().contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))) &&
                                    references == 0.0) {
                                boolean isMulti1 = false;
                                boolean isMulti2 = false;
                                for (DeclarationNodeTree entity : matchPair.getDeletedEntities()) {
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
                                for (DeclarationNodeTree entity : matchPair.getAddedEntities()) {
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
                                    dice = 0;
                            }
                            List<DeclarationNodeTree> children = dntCurrent.getParent().getChildren();
                            for (DeclarationNodeTree child : children) {
                                if (child.getType() != EntityType.FIELD) continue;
                                if (child == dntCurrent) continue;
                                FieldDeclaration fd1 = (FieldDeclaration) dntCurrent.getDeclaration();
                                FieldDeclaration fd2 = (FieldDeclaration) child.getDeclaration();
                                if (!fd1.getType().toString().equals(fd2.getType().toString()))
                                    continue;
                                double dice2 = DiceFunction.calculateDiceSimilarity((LeafNode) dntBefore, (LeafNode) child);
                                if (dice != dice2)
                                    continue;
                                Set<EntityInfo> dependencies1 = new HashSet<>(dntCurrent.getDependencies());
                                Set<EntityInfo> dependencies2 = new HashSet<>(child.getDependencies());
                                String name1 = dntCurrent.getName();
                                String name2 = child.getName();
                                char c1 = name1.charAt(name1.length() - 1);
                                char c2 = name2.charAt(name2.length() - 1);
                                if (dependencies1.equals(dependencies2)) {
                                    if (name1.substring(0, name1.length() - 1).equals(name2.substring(0, name2.length() - 1)) &&
                                            Character.isDigit(c1) && Character.isDigit(c2)) {
                                        dice = 0;
                                        break;
                                    }
                                    name1 = name1.toLowerCase();
                                    name2 = name2.toLowerCase();
                                    if (name1.contains("prefix") && name2.contains("suffix")) {
                                        name1 = name1.replace("prefix", "");
                                        name2 = name2.replace("suffix", "");
                                        if (name1.equals(name2)) {
                                            dice = 0;
                                            break;
                                        }
                                    }
                                    if (name1.contains("suffix") && name2.contains("prefix")) {
                                        name1 = name1.replace("suffix", "");
                                        name2 = name2.replace("prefix", "");
                                        if (name1.equals(name2)) {
                                            dice = 0;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (matchedEntities.contains(Pair.of(dntBefore.getParent(), dntCurrent.getParent()))) {
                                List<DeclarationNodeTree> children1 = dntBefore.getParent().getChildren();
                                List<DeclarationNodeTree> children2 = dntCurrent.getParent().getChildren();
                                List<DeclarationNodeTree> fields1 = children1.stream().filter(child -> !child.isMatched() && child.getType() == EntityType.FIELD).collect(Collectors.toList());
                                List<DeclarationNodeTree> fields2 = children2.stream().filter(child -> !child.isMatched() && child.getType() == EntityType.FIELD).collect(Collectors.toList());
                                if (fields1.size() == fields2.size() && references > DiceFunction.minSimilarity) {
                                    FieldDeclaration fd1 = (FieldDeclaration) dntBefore.getDeclaration();
                                    FieldDeclaration fd2 = (FieldDeclaration) dntCurrent.getDeclaration();
                                    if (fd1.getType().toString().equals("Object") || fd2.getType().toString().equals("Object"))
                                        dice = references + 1 - levenshtein.distance(dntBefore.getName().toLowerCase(), dntCurrent.getName().toLowerCase());
                                }
                                if ((fields1.size() == fields2.size() + 1 || fields1.size() == fields2.size() - 1) &&
                                        references > 0.85) {
                                    FieldDeclaration fd1 = (FieldDeclaration) dntBefore.getDeclaration();
                                    FieldDeclaration fd2 = (FieldDeclaration) dntCurrent.getDeclaration();
                                    if (fd1.getType().toString().equals("Object") || fd2.getType().toString().equals("Object"))
                                        dice = references + 1 - levenshtein.distance(dntBefore.getName().toLowerCase(), dntCurrent.getName().toLowerCase());
                                }
                            }
                        }
                    }
                    if (dice < DiceFunction.minSimilarity) continue;
                    EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                    entityPairs.add(entityPair);
                    entityPair.setDice(dice);
                }
            }
        }
        selectByDice(matchPair, entityPairs);
        entityPairs.clear();
        for (DeclarationNodeTree dntBefore : matchPair.getDeletedEntities()) {
            for (DeclarationNodeTree dntCurrent : matchPair.getAddedEntities()) {
                if (dntBefore.getType() == dntCurrent.getType()) {
                    double dice = 0;
                    if (dntBefore instanceof InternalNode && dntCurrent instanceof InternalNode) {
                        if (dntBefore.getType() == dntCurrent.getType() &&
                                !dntBefore.hasChildren() && !dntCurrent.hasChildren() &&
                                dntBefore.getDependencies().isEmpty() && dntCurrent.getDependencies().isEmpty() &&
                                dntBefore.getHeight() == 1 && dntCurrent.getHeight() == 1 &&
                                StringUtils.equals(dntBefore.getNamespace(), dntCurrent.getNamespace())) {
                            EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                            entityPairs.add(entityPair);
                            entityPair.setDice(1.0);
                            continue;
                        }
                        dice = DiceFunction.calculateDiceSimilarity(matchPair, (InternalNode) dntBefore, (InternalNode) dntCurrent);
                        if (dntBefore.getName().equals(dntCurrent.getName())) {
                            double dice2 = DiceFunction.calculateDiceSimilarity((InternalNode) dntBefore, (InternalNode) dntCurrent);
                            dice = Math.max(dice, dice2);
                            if (dice < 0.35) continue;
                            EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                            entityPairs.add(entityPair);
                            entityPair.setDice(dice);
                            continue;
                        }
                    }
                    if (dice < DiceFunction.minSimilarity) continue;
                    EntityPair entityPair = new EntityPair(dntBefore, dntCurrent);
                    entityPairs.add(entityPair);
                    entityPair.setDice(dice);
                }
            }
        }
        selectByDice(matchPair, entityPairs);
    }

    private void selectByDice(MatchPair matchPair, List<EntityPair> entityPairs) {
        Set<DeclarationNodeTree> deletionBefore = new HashSet<>();
        Set<DeclarationNodeTree> deletionCurrent = new HashSet<>();
        Collections.sort(entityPairs);
        Set<DeclarationNodeTree> existBefore = new HashSet<>();
        Set<DeclarationNodeTree> existCurrent = new HashSet<>();
        for (EntityPair entityPair : entityPairs) {
            DeclarationNodeTree node1 = entityPair.getLeft();
            DeclarationNodeTree node2 = entityPair.getRight();
            if (existBefore.contains(node1) || existCurrent.contains(node2))
                continue;
            existBefore.add(node1);
            existCurrent.add(node2);
            node1.setMatched();
            node2.setMatched();
            matchPair.addMatchedEntity(node1, node2);
            deletionBefore.add(node1);
            deletionCurrent.add(node2);
            if (node1 instanceof InternalNode && node2 instanceof InternalNode) {
                List<DeclarationNodeTree> children1 = node1.getChildren();
                List<DeclarationNodeTree> children2 = node2.getChildren();
                for (DeclarationNodeTree child1 : children1) {
                    for (DeclarationNodeTree child2 : children2) {
                        if (isSameSignature(child1, child2)) {
                            child1.setMatched();
                            child2.setMatched();
                            matchPair.addMatchedEntity(child1, child2);
                            deletionBefore.add(child1);
                            deletionCurrent.add(child2);
                        } else if (child1.getName().equals(child2.getName())) {
                            double reference = DiceFunction.calculateReferenceSimilarity(matchPair, child1, child2);
                            if (reference > 0) {
                                child1.setMatched();
                                child2.setMatched();
                                matchPair.addMatchedEntity(child1, child2);
                                deletionBefore.add(child1);
                                deletionCurrent.add(child2);
                            }
                        }
                    }
                }
            }
        }
        matchPair.getDeletedEntities().removeAll(deletionBefore);
        matchPair.getAddedEntities().removeAll(deletionCurrent);
    }

    private void additionalMatchByReference(MatchPair matchPair) {
        Set<DeclarationNodeTree> deletionBefore = new HashSet<>();
        Set<DeclarationNodeTree> deletionCurrent = new HashSet<>();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntitiesAdded = new HashSet<>();
        Set<DeclarationNodeTree> deletedEntities = matchPair.getDeletedEntities();
        Set<DeclarationNodeTree> addedEntities = matchPair.getAddedEntities();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
            DeclarationNodeTree dntBefore = pair.getLeft();
            DeclarationNodeTree dntCurrent = pair.getRight();
            for (DeclarationNodeTree dntDeleted : deletedEntities) {
                if ((dntDeleted.getType() == EntityType.FIELD || dntDeleted.getType() == EntityType.METHOD) &&
                        dntDeleted.getType() == dntCurrent.getType()) {
                    boolean isAbstract = false;
                    if (dntBefore.getType() == EntityType.METHOD) {
                        MethodDeclaration methodDeclaration = (MethodDeclaration) dntBefore.getDeclaration();
                        int methodModifiers = methodDeclaration.getModifiers();
                        if ((methodModifiers & Modifier.ABSTRACT) != 0)
                            isAbstract = true;
                        if (methodDeclaration.getBody() == null)
                            isAbstract = true;
                    } else if (dntBefore.getType() == EntityType.FIELD) {
                        FieldDeclaration fieldDeclaration = (FieldDeclaration) dntBefore.getDeclaration();
                        int methodModifiers = fieldDeclaration.getModifiers();
                        if ((methodModifiers & Modifier.ABSTRACT) != 0)
                            isAbstract = true;
                    }
                    if ((isSubTypeOf(matchedEntities, dntBefore, dntCurrent, 0) || isAbstract) &&
                            isSubTypeOf(matchedEntities, dntDeleted, dntCurrent, 0)
                            && isSameSignatureExceptName(dntDeleted, dntCurrent) && dntDeleted.getName().equals(dntBefore.getName())) {
                        matchedEntitiesAdded.add(Pair.of(dntDeleted, dntCurrent));
                        dntDeleted.setMatched();
                        deletionBefore.add(dntDeleted);
                    }
                }
            }
            for (DeclarationNodeTree dntAdded : addedEntities) {
                if ((dntAdded.getType() == EntityType.FIELD || dntAdded.getType() == EntityType.METHOD) &&
                        dntAdded.getType() == dntBefore.getType()) {
                    boolean isAbstract = false;
                    if (dntCurrent.getType() == EntityType.METHOD) {
                        MethodDeclaration methodDeclaration = (MethodDeclaration) dntCurrent.getDeclaration();
                        int methodModifiers = methodDeclaration.getModifiers();
                        if ((methodModifiers & Modifier.ABSTRACT) != 0)
                            isAbstract = true;
                        if (methodDeclaration.getBody() == null)
                            isAbstract = true;
                    } else if (dntCurrent.getType() == EntityType.FIELD) {
                        FieldDeclaration fieldDeclaration = (FieldDeclaration) dntCurrent.getDeclaration();
                        int methodModifiers = fieldDeclaration.getModifiers();
                        if ((methodModifiers & Modifier.ABSTRACT) != 0)
                            isAbstract = true;
                    }
                    if ((isSubTypeOf(matchedEntities, dntCurrent, dntBefore, 1) || isAbstract) &&
                            isSubTypeOf(matchedEntities, dntAdded, dntBefore, 1)
                            && isSameSignatureExceptName(dntBefore, dntAdded) && dntBefore.getName().equals(dntAdded.getName())) {
                        matchedEntitiesAdded.add(Pair.of(dntBefore, dntAdded));
                        dntAdded.setMatched();
                        deletionCurrent.add(dntAdded);
                    }
                }
            }
        }
        matchedEntities.addAll(matchedEntitiesAdded);
        deletedEntities.removeAll(deletionBefore);
        addedEntities.removeAll(deletionCurrent);
    }

    private DeclarationNodeTree findMatchedEntity(Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities, DeclarationNodeTree entity) {
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
            if (pair.getLeft() == entity)
                return pair.getRight();
            if (pair.getRight() == entity)
                return pair.getLeft();
        }
        return null;
    }

    /**
     * @param range 0: superclass (pull up)  1: subclass (push down)
     */
    private boolean isSubTypeOf(Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities, DeclarationNodeTree oldEntity, DeclarationNodeTree newEntity, int range) {
        DeclarationNodeTree oldParent = oldEntity.getParent();
        DeclarationNodeTree newParent = newEntity.getParent();
        if ((oldParent.getType() == EntityType.CLASS || oldParent.getType() == EntityType.INTERFACE) &&
                (newParent.getType() == EntityType.CLASS || newParent.getType() == EntityType.INTERFACE)) {
            TypeDeclaration removedClass = (TypeDeclaration) oldParent.getDeclaration();
            TypeDeclaration addedClass = (TypeDeclaration) newParent.getDeclaration();
            DeclarationNodeTree matchedAddedEntity = findMatchedEntity(matchedEntities, newParent);
            TypeDeclaration matchedAddedClass = matchedAddedEntity == null ? null : (TypeDeclaration) (matchedAddedEntity.getDeclaration());
            DeclarationNodeTree matchedDeletedEntity = findMatchedEntity(matchedEntities, oldParent);
            TypeDeclaration matchedDeletedClass = matchedDeletedEntity == null ? null : (TypeDeclaration) (matchedDeletedEntity.getDeclaration());
            if (range == 0)
                return matchedDeletedClass != null && isSubTypeOf(matchedDeletedClass, addedClass);
            if (range == 1)
                return matchedAddedClass != null && isSubTypeOf(removedClass, matchedAddedClass);
        }
        return false;
    }

    private boolean isSubTypeOf(DeclarationNodeTree removedClass, DeclarationNodeTree addedClass) {
        if ((removedClass.getType() == EntityType.CLASS || removedClass.getType() == EntityType.INTERFACE) &&
                (addedClass.getType() == EntityType.CLASS || addedClass.getType() == EntityType.INTERFACE)) {
            TypeDeclaration typeDeclaration1 = (TypeDeclaration) removedClass.getDeclaration();
            TypeDeclaration typeDeclaration2 = (TypeDeclaration) addedClass.getDeclaration();
            return isSubTypeOf(typeDeclaration1, typeDeclaration2);
        }
        return false;
    }

    private boolean isSubTypeOf(TypeDeclaration removedClass, TypeDeclaration addedClass) {
        ITypeBinding removedBinding = removedClass.resolveBinding();
        ITypeBinding addedBinding = addedClass.resolveBinding();
        if (removedBinding != null) {
            ITypeBinding superClassBinding = removedBinding.getSuperclass();
            if (superClassBinding != null && addedBinding != null) {
                boolean superClass = isSubclassOrImplementation(superClassBinding, addedBinding);
                if (superClass)
                    return true;
            }
            ITypeBinding[] interfaces = removedBinding.getInterfaces();
            for (ITypeBinding typeBinding : interfaces) {
                if (addedBinding != null) {
                    boolean isInterface = isSubclassOrImplementation(typeBinding, addedBinding);
                    if (isInterface)
                        return true;
                }
            }
        }
        return false;
    }

    public static boolean isSubclassOrImplementation(ITypeBinding removedBinding, ITypeBinding addedBinding) {
        if (removedBinding == addedBinding || removedBinding.getTypeDeclaration().isEqualTo(addedBinding) ||
                StringUtils.equals(removedBinding.getQualifiedName(), addedBinding.getQualifiedName())) {
            return true;
        }
        ITypeBinding superClassBinding = removedBinding.getSuperclass();
        if (superClassBinding != null && isSubclassOrImplementation(superClassBinding, addedBinding)) {
            return true;
        }
        ITypeBinding[] interfaceBindings = removedBinding.getInterfaces();
        for (ITypeBinding interfaceBinding : interfaceBindings) {
            if (isSubclassOrImplementation(interfaceBinding, addedBinding)) {
                return true;
            }
        }
        return false;
    }

    private void filter(MatchPair matchPair) {
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> filteredEntities = new HashSet<>();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
            DeclarationNodeTree dntBefore = pair.getLeft();
            DeclarationNodeTree dntCurrent = pair.getRight();
            if (dntBefore.getDeclaration().toString().equals(dntCurrent.getDeclaration().toString()) &&
                    StringUtils.equals(dntBefore.getNamespace(), dntCurrent.getNamespace())) {
                filteredEntities.add(pair);
            }
        }
        matchedEntities.removeAll(filteredEntities);
        matchPair.addUnchangedEntities(filteredEntities);
    }

    private void repairMatching(MatchPair matchPair) {
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
        Set<DeclarationNodeTree> deletedEntities = matchPair.getDeletedEntities();
        Set<DeclarationNodeTree> addedEntities = matchPair.getAddedEntities();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntitiesAdded = new HashSet<>();
        Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntitiesDeleted = new HashSet<>();
        Set<DeclarationNodeTree> deletedEntitiesAdded = new HashSet<>();
        Set<DeclarationNodeTree> deletedEntitiesDeleted = new HashSet<>();
        Set<DeclarationNodeTree> addedEntitiesAdded = new HashSet<>();
        Set<DeclarationNodeTree> addedEntitiesDeleted = new HashSet<>();
        for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
            DeclarationNodeTree left = pair.getLeft();
            DeclarationNodeTree right = pair.getRight();
            if (left.getType() == EntityType.METHOD && right.getType() == EntityType.METHOD) {
                for (DeclarationNodeTree entity : deletedEntities) {
                    if (entity.getDependencies().isEmpty() && left.getDependencies().isEmpty() &&
                            right.getDependencies().isEmpty() && entity.getType() == EntityType.METHOD) {
                        MethodDeclaration declaration1 = (MethodDeclaration) entity.getDeclaration();
                        MethodDeclaration declaration2 = (MethodDeclaration) left.getDeclaration();
                        MethodDeclaration declaration3 = (MethodDeclaration) right.getDeclaration();
                        List<SingleVariableDeclaration> parameters1 = declaration1.parameters();
                        List<SingleVariableDeclaration> parameters2 = declaration2.parameters();
                        List<SingleVariableDeclaration> parameters3 = declaration3.parameters();
                        if (parameters1.isEmpty() && parameters2.isEmpty() && parameters3.isEmpty() &&
                                declaration1.getBody() != null && declaration2.getBody() != null && declaration3.getBody() != null &&
                                StringUtils.equals(declaration1.getBody().toString(), declaration3.getBody().toString()) &&
                                !StringUtils.equals(declaration2.getBody().toString(), declaration3.getBody().toString())) {
                            double sim1 = DiceFunction.calculateReferenceSimilarity(matchPair, left, right);
                            double sim2 = DiceFunction.calculateReferenceSimilarity(matchPair, entity, right);
                            double dice1 = DiceFunction.calculateDiceSimilarity((LeafNode) left, (LeafNode) right);
                            double dice2 = DiceFunction.calculateDiceSimilarity((LeafNode) entity, (LeafNode) right);
                            if (sim1 < sim2 || dice1 * 2 < dice2) {
                                matchedEntitiesDeleted.add(pair);
                                matchedEntitiesAdded.add(Pair.of(entity, right));
                                deletedEntitiesDeleted.add(entity);
                                deletedEntitiesAdded.add(left);
                                entity.setMatched();
                                left.setMatched(false);
                            }
                        }
                        boolean isTest1 = false;
                        boolean isTest2 = false;
                        boolean isTest3 = false;
                        List<IExtendedModifier> modifiers1 = declaration1.modifiers();
                        List<IExtendedModifier> modifiers2 = declaration2.modifiers();
                        List<IExtendedModifier> modifiers3 = declaration3.modifiers();
                        for (IExtendedModifier modifier : modifiers1) {
                            if (modifier.isAnnotation() && modifier.toString().equals("@Test")) {
                                isTest1 = true;
                                break;
                            }
                        }
                        for (IExtendedModifier modifier : modifiers2) {
                            if (modifier.isAnnotation() && modifier.toString().equals("@Test")) {
                                isTest2 = true;
                                break;
                            }
                        }
                        for (IExtendedModifier modifier : modifiers3) {
                            if (modifier.isAnnotation() && modifier.toString().equals("@Test")) {
                                isTest3 = true;
                                break;
                            }
                        }
                        if (isTest1 && isTest2 && isTest3 && matchedEntities.contains(Pair.of(entity.getParent(), right.getParent())) &&
                                declaration1.getBody() != null && declaration2.getBody() != null && declaration3.getBody() != null &&
                                StringUtils.equals(declaration1.getBody().toString(), declaration3.getBody().toString()) &&
                                !StringUtils.equals(declaration1.getBody().toString(), declaration2.getBody().toString())) {
                            matchedEntitiesDeleted.add(pair);
                            matchedEntitiesAdded.add(Pair.of(entity, right));
                            deletedEntitiesDeleted.add(entity);
                            deletedEntitiesAdded.add(left);
                            entity.setMatched();
                            left.setMatched(false);
                        }
                    }
                    if (matchedEntities.contains(Pair.of(entity.getParent(), right.getParent())) && isSameSignature(entity, right)) {
                        matchedEntitiesDeleted.add(pair);
                        matchedEntitiesAdded.add(Pair.of(entity, right));
                        deletedEntitiesDeleted.add(entity);
                        deletedEntitiesAdded.add(left);
                        entity.setMatched();
                        left.setMatched(false);
                    }
                }
                for (DeclarationNodeTree entity : addedEntities) {
                    if (left.getDependencies().isEmpty() && right.getDependencies().isEmpty() &&
                            entity.getDependencies().isEmpty() && entity.getType() == EntityType.METHOD) {
                        MethodDeclaration declaration1 = (MethodDeclaration) left.getDeclaration();
                        MethodDeclaration declaration2 = (MethodDeclaration) right.getDeclaration();
                        MethodDeclaration declaration3 = (MethodDeclaration) entity.getDeclaration();
                        List<SingleVariableDeclaration> parameters1 = declaration1.parameters();
                        List<SingleVariableDeclaration> parameters2 = declaration2.parameters();
                        List<SingleVariableDeclaration> parameters3 = declaration3.parameters();
                        if (parameters1.isEmpty() && parameters2.isEmpty() && parameters3.isEmpty() &&
                                declaration1.getBody() != null && declaration2.getBody() != null && declaration3.getBody() != null &&
                                StringUtils.equals(declaration1.getBody().toString(), declaration3.getBody().toString()) &&
                                !StringUtils.equals(declaration1.getBody().toString(), declaration2.getBody().toString())) {
                            double sim1 = DiceFunction.calculateReferenceSimilarity(matchPair, left, right);
                            double sim2 = DiceFunction.calculateReferenceSimilarity(matchPair, left, entity);
                            double dice1 = DiceFunction.calculateDiceSimilarity((LeafNode) left, (LeafNode) right);
                            double dice2 = DiceFunction.calculateDiceSimilarity((LeafNode) left, (LeafNode) entity);
                            if (sim1 < sim2 || dice1 * 2 < dice2) {
                                matchedEntitiesDeleted.add(pair);
                                matchedEntitiesAdded.add(Pair.of(left, entity));
                                addedEntitiesDeleted.add(entity);
                                addedEntitiesAdded.add(right);
                                entity.setMatched();
                                right.setMatched(false);
                            }
                        }
                        boolean isTest1 = false;
                        boolean isTest2 = false;
                        boolean isTest3 = false;
                        List<IExtendedModifier> modifiers1 = declaration1.modifiers();
                        List<IExtendedModifier> modifiers2 = declaration2.modifiers();
                        List<IExtendedModifier> modifiers3 = declaration3.modifiers();
                        for (IExtendedModifier modifier : modifiers1) {
                            if (modifier.isAnnotation() && modifier.toString().equals("@Test")) {
                                isTest1 = true;
                                break;
                            }
                        }
                        for (IExtendedModifier modifier : modifiers2) {
                            if (modifier.isAnnotation() && modifier.toString().equals("@Test")) {
                                isTest2 = true;
                                break;
                            }
                        }
                        for (IExtendedModifier modifier : modifiers3) {
                            if (modifier.isAnnotation() && modifier.toString().equals("@Test")) {
                                isTest3 = true;
                                break;
                            }
                        }
                        if (isTest1 && isTest2 && isTest3 && matchedEntities.contains(Pair.of(left.getParent(), entity.getParent())) &&
                                declaration1.getBody() != null && declaration2.getBody() != null && declaration3.getBody() != null &&
                                StringUtils.equals(declaration1.getBody().toString(), declaration3.getBody().toString()) &&
                                !StringUtils.equals(declaration1.getBody().toString(), declaration2.getBody().toString())) {
                            matchedEntitiesDeleted.add(pair);
                            matchedEntitiesAdded.add(Pair.of(left, entity));
                            addedEntitiesDeleted.add(entity);
                            addedEntitiesAdded.add(right);
                            entity.setMatched();
                            right.setMatched(false);
                        }
                    }
                    if (matchedEntities.contains(Pair.of(left.getParent(), entity.getParent())) && isSameSignature(left, entity)) {
                        matchedEntitiesDeleted.add(pair);
                        matchedEntitiesAdded.add(Pair.of(left, entity));
                        addedEntitiesDeleted.add(entity);
                        addedEntitiesAdded.add(right);
                        entity.setMatched();
                        right.setMatched(false);
                    }
                }
            }
            if (left.getType() == EntityType.FIELD && right.getType() == EntityType.FIELD) {
                for (DeclarationNodeTree entity : addedEntities) {
                    if (entity.getType() == EntityType.FIELD) {
                        DeclarationNodeTree matchedEntity = findMatchedEntity(matchedEntities, left.getParent());
                        if ((entity.getParent().getType() == EntityType.CLASS && entity.getParent().getParent() == matchedEntity) ||
                                Objects.equals(left.getLocationInfo().getFilePath(), entity.getLocationInfo().getFilePath())) {
                            FieldDeclaration declaration1 = (FieldDeclaration) left.getDeclaration();
                            FieldDeclaration declaration2 = (FieldDeclaration) entity.getDeclaration();
                            VariableDeclarationFragment fragment1 = (VariableDeclarationFragment) declaration1.fragments().get(0);
                            VariableDeclarationFragment fragment2 = (VariableDeclarationFragment) declaration2.fragments().get(0);
                            if (fragment1.getName().getIdentifier().equals(fragment2.getName().getIdentifier()) &&
                                    isSameType(declaration1, declaration2)) {
                                double sim1 = DiceFunction.calculateReferenceSimilarity(matchPair, left, right);
                                double sim2 = DiceFunction.calculateReferenceSimilarity(matchPair, left, entity);
                                if (sim2 > sim1) {
                                    matchedEntitiesDeleted.add(pair);
                                    matchedEntitiesAdded.add(Pair.of(left, entity));
                                    addedEntitiesDeleted.add(entity);
                                    addedEntitiesAdded.add(right);
                                    entity.setMatched();
                                    right.setMatched(false);
                                }
                            }
                        }
                    }
                }
            }
        }
        matchedEntities.addAll(matchedEntitiesAdded);
        matchedEntities.removeAll(matchedEntitiesDeleted);
        deletedEntities.addAll(deletedEntitiesAdded);
        deletedEntities.removeAll(deletedEntitiesDeleted);
        addedEntities.addAll(addedEntitiesAdded);
        addedEntities.removeAll(addedEntitiesDeleted);
    }

    private boolean isSameType(FieldDeclaration declaration1, FieldDeclaration declaration2) {
        String type1 = declaration1.getType().toString();
        String type2 = declaration2.getType().toString();
        if (type1.equals(type2))
            return true;
        if (type1.equals("int") && type2.equals("Integer"))
            return true;
        if (type1.equals("int") && type2.equals("AtomicInteger"))
            return true;
        if (type1.equals("Integer") && type2.equals("int"))
            return true;
        if (type1.equals("AtomicInteger") && type2.equals("int"))
            return true;
        if (type1.equals("Integer") && type2.equals("AtomicInteger"))
            return true;
        if (type1.equals("AtomicInteger") && type2.equals("Integer"))
            return true;
        if (type1.equals("long") && type2.equals("Long"))
            return true;
        if (type1.equals("long") && type2.equals("AtomicLong"))
            return true;
        if (type1.equals("Long") && type2.equals("long"))
            return true;
        if (type1.equals("AtomicLong") && type2.equals("long"))
            return true;
        if (type1.equals("Long") && type2.equals("AtomicLong"))
            return true;
        if (type1.equals("AtomicLong") && type2.equals("Long"))
            return true;
        if (type1.equals("boolean") && type2.equals("Boolean"))
            return true;
        if (type1.equals("boolean") && type2.equals("AtomicBoolean"))
            return true;
        if (type1.equals("Boolean") && type2.equals("boolean"))
            return true;
        if (type1.equals("AtomicBoolean") && type2.equals("boolean"))
            return true;
        if (type1.equals("Boolean") && type2.equals("AtomicBoolean"))
            return true;
        if (type1.equals("AtomicBoolean") && type2.equals("Boolean"))
            return true;
        if (type1.equals("float") && type2.equals("Float"))
            return true;
        if (type1.equals("Float") && type2.equals("float"))
            return true;
        if (type1.equals("double") && type2.equals("Double"))
            return true;
        if (type1.equals("Double") && type2.equals("double"))
            return true;
        if (type1.equals("char") && type2.equals("Character"))
            return true;
        if (type1.equals("Character") && type2.equals("char"))
            return true;
        if (type1.equals("byte") && type2.equals("Byte"))
            return true;
        if (type1.equals("Byte") && type2.equals("byte"))
            return true;
        if (type1.equals("short") && type2.equals("Short"))
            return true;
        if (type1.equals("Short") && type2.equals("short"))
            return true;
        return false;
    }
}
