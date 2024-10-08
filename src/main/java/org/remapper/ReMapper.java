package org.remapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jgit.lib.Repository;
import org.remapper.dto.EntityMatchingJSON;
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

        if (option.equalsIgnoreCase("-c")) {
            detectAtCommit(args);
        } else {
            throw argumentException();
        }
    }

    private static void processJSONOption(String[] args) {
        if (args[args.length - 2].equalsIgnoreCase("-json")) {
            path = Paths.get(args[args.length - 1]);
            if (Files.exists(path) && path.toFile().length() == 0) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static void detectAtCommit(String[] args) throws Exception {
        processJSONOption(args);
        if (args.length != 5) {
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

    private static void commitJSON(String cloneURL, String currentCommitId, MatchPair matchPair) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
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
                "-c <git-repo-folder> <commit-sha1> -json <path-to-json-file>\t\t\t\tMatch entities at specified commit <commit-sha1> for project <git-repo-folder>");
    }

    private static IllegalArgumentException argumentException() {
        return new IllegalArgumentException("Type `ReMapper -h` to show usage.");
    }
}
