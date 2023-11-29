package org.remapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;
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

public class ReMapperTest {

    @Test
    public void detectAtCommit() throws Exception {
        String folder = "E:/contributions/ASE2023/Dataset/lucene-solr";
        String commitId = "e6d9eaaf000bb00e52a776e298b3c7e9f37a4cd5";
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
        Path path = Paths.get("E:/results.json");
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
                String jsonString = gson.toJson(results, EntityMatchingJSON.class).replace("\\t", "\t");
                out.write(jsonString);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try (FileReader reader = new FileReader(path.toFile())) {
                EntityMatchingJSON results = gson.fromJson(reader, EntityMatchingJSON.class);
                results.populateJSON(cloneURL, currentCommitId, url, matchPair);
                String jsonString = gson.toJson(results, EntityMatchingJSON.class).replace("\\t", "\t");
                BufferedWriter out = new BufferedWriter(new FileWriter(path.toFile()));
                out.write(jsonString);
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}