package org.remapper.service;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.remapper.util.ASTParserUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;

public class ProjectParser {

    private final String projectPath;
    private List<String> relatedJavaFiles;
    private String[] sourcepathEntries;
    private String[] encodings;

    public ProjectParser(String projectPath) {
        this.projectPath = projectPath;
    }

    public List<String> getRelatedJavaFiles() {
        return relatedJavaFiles;
    }

    public String[] getSourcepathEntries() {
        return sourcepathEntries;
    }

    public String[] getEncodings() {
        return encodings;
    }

    public void buildEntityDependencies(List<String> changedJavaFiles) {
        relatedJavaFiles = new ArrayList<>();
        populateRelatedJavaFiles(changedJavaFiles);
        populateSourcepathEntries();
    }

    private void populateRelatedJavaFiles(List<String> changedJavaFiles) {
        changedJavaFiles.forEach(file -> relatedJavaFiles.add(projectPath + "/" + file));
    }

    private void populateSourcepathEntries() {
        HashSet<String> sourceRootSet = new HashSet<>();
        for (String file : relatedJavaFiles) {
            ASTParser astParser = ASTParserUtils.getFastParser();
            try {
                String code = FileUtils.readFileToString(new File(file), StandardCharsets.UTF_8);
                astParser.setSource(code.toCharArray());
                CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
                if (cu.getPackage() == null) continue;
                String rootPath = parseRootPath(file, cu.getPackage().getName().toString());
                if (!rootPath.equals("") && Paths.get(rootPath).toFile().exists())
                    sourceRootSet.add(rootPath);
            } catch (Exception ignored) {
            }
        }
        sourcepathEntries = new String[sourceRootSet.size()];
        encodings = new String[sourceRootSet.size()];
        int index = 0;
        for (String sourceRoot : sourceRootSet) {
            sourcepathEntries[index] = sourceRoot;
            encodings[index] = "utf-8";
            index++;
        }
    }

    private String parseRootPath(String filePath, String packageName) {
        String path = packageName.replaceAll("\\.", Matcher.quoteReplacement(File.separator));
        Path relativePath = Paths.get(path);
        Path absolutePath = Paths.get(filePath).resolveSibling("");
        int end = absolutePath.toString().lastIndexOf(relativePath.toString());
        if (end == -1) return "";
        return absolutePath.toString().substring(0, end).replace("\\", "/");
    }
}
