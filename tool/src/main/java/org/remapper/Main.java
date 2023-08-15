package org.remapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.remapper.refactoring.Refactoring;
import org.remapper.dto.EntityComparator;
import org.remapper.dto.EntityInfo;
import org.remapper.dto.EntityMatchingJSON;
import org.remapper.dto.RefactoringDiscoveryJSON;
import org.remapper.service.GitService;
import org.remapper.service.ReMapper;
import org.remapper.service.ReExtractor;
import org.remapper.util.BeanUtils;
import org.remapper.util.GitServiceImpl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {

    private static String path = null;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw argumentException();
        }

        final String option = args[0];
        if (option.equalsIgnoreCase("-h") || option.equalsIgnoreCase("--h") || option.equalsIgnoreCase("-help")
                || option.equalsIgnoreCase("--help")) {
            printTips();
            return;
        }

        if (args[args.length - 2].equalsIgnoreCase("-json")) {
            path = args[args.length - 1];
        }

        if (option.equalsIgnoreCase("-em")) {
            matchingEntities(args);
        } else if (option.equalsIgnoreCase("-rd")) {
            discoveryRefactorings(args);
        } else {
            throw argumentException();
        }
        System.exit(0);
    }

    private static void matchingEntities(String[] args) throws Exception {
        final String projectPath = args[1];
        final String commitId = args[2];
        GitService gitService = new GitServiceImpl();
        gitService.checkoutCurrent(projectPath, commitId);
        Set<Pair<EntityInfo, EntityInfo>> reMapper = new ReMapper().match(projectPath, commitId);
        Set<Pair<EntityComparator, EntityComparator>> matchPairs = new LinkedHashSet<>();
        BeanUtils.copyReMapperProperties(reMapper, matchPairs);
        System.out.println("Matched Entities at Commit ID: " + commitId + "\n");
        for (Pair<EntityComparator, EntityComparator> pair : matchPairs) {
            System.out.println(pair.getLeft().toString() + "\n" + pair.getRight().toString() + "\n");
        }
        if (path != null) {
            File file = new File(path);
            File directory = file.getParentFile();
            Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
            String remote_repo = GitServiceImpl.getRemoteUrl(projectPath);
            String remote_url = remote_repo.replace(".git", "/commit/") + commitId;
            if (file.exists()) {
                FileReader reader = new FileReader(path);
                EntityMatchingJSON results = gson.fromJson(reader, EntityMatchingJSON.class);
                results.populateJSON(remote_repo, commitId, remote_url, matchPairs);
                String jsonString = gson.toJson(results, EntityMatchingJSON.class);
                BufferedWriter out = new BufferedWriter(new FileWriter(path));
                out.write(jsonString);
                out.close();
            } else {
                if (!directory.exists())
                    directory.mkdirs();
                file.createNewFile();
                EntityMatchingJSON results = new EntityMatchingJSON();
                results.populateJSON(remote_repo, commitId, remote_url, matchPairs);
                String jsonString = gson.toJson(results, EntityMatchingJSON.class);
                BufferedWriter out = new BufferedWriter(new FileWriter(path));
                out.write(jsonString);
                out.close();
            }
        }
    }

    private static void discoveryRefactorings(String[] args) throws Exception {
        final String projectPath = args[1];
        final String commitId = args[2];
        GitService gitService = new GitServiceImpl();
        gitService.checkoutCurrent(projectPath, commitId);
        List<Refactoring> refactorings = new ReExtractor().discover(projectPath, commitId);
        System.out.println("Discovered Refactorings at Commit ID: " + commitId + "\n");
        for (Refactoring refactoring : refactorings) {
            System.out.println(refactoring);
        }
        File file = new File(path);
        File directory = file.getParentFile();
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String remote_repo = GitServiceImpl.getRemoteUrl(projectPath);
        String remote_url = remote_repo.replace(".git", "/commit/") + commitId;
        if (file.exists()) {
            FileReader reader = new FileReader(path);
            RefactoringDiscoveryJSON results = gson.fromJson(reader, RefactoringDiscoveryJSON.class);
            results.populateJSON(remote_repo, commitId, remote_url, refactorings);
            String jsonString = gson.toJson(results, RefactoringDiscoveryJSON.class);
            BufferedWriter out = new BufferedWriter(new FileWriter(path));
            out.write(jsonString);
            out.close();
        } else {
            if (!directory.exists())
                directory.mkdirs();
            file.createNewFile();
            RefactoringDiscoveryJSON results = new RefactoringDiscoveryJSON();
            results.populateJSON(remote_repo, commitId, remote_url, refactorings);
            String jsonString = gson.toJson(results, RefactoringDiscoveryJSON.class);
            BufferedWriter out = new BufferedWriter(new FileWriter(path));
            out.write(jsonString);
            out.close();
        }
    }

    private static void printTips() {
        System.out.println("-h\t\t\t\t\t\t\t\t\t\t\tShow options");
        System.out.println(
                "-em <git-repo-folder> <commit-sha1> -json <path-to-json-file>\t\t\t\tMatching entities at specified commit <commit-sha1> for project <git-repo-folder>");
        System.out.println(
                "-rd <git-repo-folder> <commit-sha1> -json <path-to-json-file>\t\t\t\tDiscover refactorings at specified commit <commit-sha1> for project <git-repo-folder>");
    }

    private static IllegalArgumentException argumentException() {
        return new IllegalArgumentException("Type `ReMapper -h` to show usage.");
    }
}
