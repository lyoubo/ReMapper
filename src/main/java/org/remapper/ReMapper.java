package org.remapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jgit.lib.Repository;
import org.remapper.dto.EntityMatchingJSON;
import org.remapper.dto.LocationDeserializer;
import org.remapper.dto.MatchPair;
import org.remapper.handler.MatchingHandler;
import org.remapper.service.EntityMatcherService;
import org.remapper.service.EntityMatcherServiceImpl;
import org.remapper.service.GitService;
import org.remapper.util.GitServiceImpl;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ReMapper {

    private static Path path = null;

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

        if (option.equalsIgnoreCase("-bc")) {
            detectBetweenCommits(args);
        } else if (option.equalsIgnoreCase("-bt")) {
            detectBetweenTags(args);
        } else if (option.equalsIgnoreCase("-c")) {
            detectAtCommit(args);
        } else {
            throw argumentException();
        }
    }

    public static void detectBetweenCommits(String[] args) throws Exception {
        int maxArgLength = processJSONoption(args, 4);
        if (!(args.length == maxArgLength - 1 || args.length == maxArgLength)) {
            throw argumentException();
        }
        String folder = args[1];
        String startCommit = args[2];
        String endCommit = containsEndArgument(args) ? args[3] : null;
        GitService gitService = new GitServiceImpl();
        try (Repository repo = gitService.openRepository(folder)) {
            String gitURL = GitServiceImpl.getRemoteUrl(folder);
            EntityMatcherService service = new EntityMatcherServiceImpl();
            service.matchBetweenCommits(repo, startCommit, endCommit, new MatchingHandler() {
                @Override
                public void handle(String startCommitId, String endCommitId, MatchPair matchPair) {
                    commitJSON(gitURL, endCommitId, matchPair);
                }

                @Override
                public void handleException(String startCommitId, String endCommitId, Exception e) {
                    System.err.println("Error processing commit " + endCommitId);
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    public static void detectBetweenTags(String[] args) throws Exception {
        int maxArgLength = processJSONoption(args, 4);
        if (!(args.length == maxArgLength - 1 || args.length == maxArgLength)) {
            throw argumentException();
        }
        String folder = args[1];
        String startTag = args[2];
        String endTag = containsEndArgument(args) ? args[3] : null;
        GitService gitService = new GitServiceImpl();
        try (Repository repo = gitService.openRepository(folder)) {
            String gitURL = GitServiceImpl.getRemoteUrl(folder);
            EntityMatcherService service = new EntityMatcherServiceImpl();
            service.matchBetweenTags(repo, startTag, endTag, new MatchingHandler() {
                @Override
                public void handle(String startTag, String endTag, MatchPair matchPair) {
                    commitJSON(gitURL, endTag, matchPair);
                }

                @Override
                public void handleException(String startTag, String endTag, Exception e) {
                    System.err.println("Error processing tag " + endTag);
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    public static void detectAtCommit(String[] args) throws Exception {
        int maxArgLength = processJSONoption(args, 3);
        if (args.length != maxArgLength) {
            throw argumentException();
        }
        String folder = args[1];
        String commitId = args[2];
        GitService gitService = new GitServiceImpl();
        try (Repository repo = gitService.openRepository(folder)) {
            String gitURL = GitServiceImpl.getRemoteUrl(folder);
            EntityMatcherService service = new EntityMatcherServiceImpl();
            service.matchAtCommit(repo, commitId, new MatchingHandler() {
                @Override
                public void handle(String commitId, MatchPair matchPair) {
                    commitJSON(gitURL, commitId, matchPair);
                }

                @Override
                public void handleException(String commit, Exception e) {
                    System.err.println("Error processing commit " + commit);
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    private static int processJSONoption(String[] args, int maxArgLength) {
        if (args[args.length - 2].equalsIgnoreCase("-json")) {
            path = Paths.get(args[args.length - 1]);
            maxArgLength = maxArgLength + 2;
        }
        return maxArgLength;
    }

    private static boolean containsEndArgument(String[] args) {
        return args.length == 4 || (args.length > 4 && args[4].equalsIgnoreCase("-json"));
    }

    private static void commitJSON(String cloneURL, String currentCommitId, MatchPair matchPair) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().registerTypeAdapter(EntityMatchingJSON.Location.class, new LocationDeserializer()).create();
        String url = cloneURL.replace(".git", "/commit/") + currentCommitId;
        if (Files.notExists(path)) {
            Path parent = path.getParent();
            try {
                if (Files.notExists(parent)) {
                    Files.createDirectories(parent);
                }
                Files.createFile(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try (BufferedWriter out = new BufferedWriter(new FileWriter(path.toFile()))) {
                EntityMatchingJSON results = new EntityMatchingJSON();
                results.populateJSON(cloneURL, currentCommitId, url, matchPair);
                String jsonString = gson.toJson(results, EntityMatchingJSON.class);
                out.write(jsonString);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try (FileReader reader = new FileReader(path.toFile())) {
                EntityMatchingJSON results = gson.fromJson(reader, EntityMatchingJSON.class);
                results.populateJSON(cloneURL, currentCommitId, url, matchPair);
                String jsonString = gson.toJson(results, EntityMatchingJSON.class);
                BufferedWriter out = new BufferedWriter(new FileWriter(path.toFile()));
                out.write(jsonString);
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void printTips() {
        System.out.println("-h\t\t\t\t\t\t\t\t\t\t\tShow options");
        System.out.println(
                "-bc <git-repo-folder> <start-commit-sha1> <end-commit-sha1> -json <path-to-json-file>\tMatch entities between <start-commit-sha1> and <end-commit-sha1> for project <git-repo-folder>");
        System.out.println(
                "-bt <git-repo-folder> <start-tag> <end-tag> -json <path-to-json-file>\t\t\tMatch entities between <start-tag> and <end-tag> for project <git-repo-folder>");
        System.out.println(
                "-c <git-repo-folder> <commit-sha1> -json <path-to-json-file>\t\t\t\tMatch entities at specified commit <commit-sha1> for project <git-repo-folder>");
    }

    private static IllegalArgumentException argumentException() {
        return new IllegalArgumentException("Type `ReMapper -h` to show usage.");
    }
}
