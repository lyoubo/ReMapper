package org.remapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;
import org.remapper.dto.*;
import org.remapper.handler.MatchingHandler;
import org.remapper.service.EntityMatcherService;
import org.remapper.service.EntityMatcherServiceImpl;
import org.remapper.service.GitService;
import org.remapper.util.GitServiceImpl;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ReMapperTest {

    @Test
    public void detectAtCommit() throws Exception {
        String folder = "E:\\refactoring-toy-example";
        String commitId = "c286db365e7374fe4d08f54077abb7fba81dd296";
        GitService gitService = new GitServiceImpl();
        try (Repository repo = gitService.openRepository(folder)) {
            String gitURL = GitServiceImpl.getRemoteUrl(folder);
            EntityMatcherService service = new EntityMatcherServiceImpl();
            long start = System.nanoTime();
            service.matchAtCommit(repo, commitId, new MatchingHandler() {
                @Override
                public void handle(String commitId, MatchPair matchPair) {
                    for (Pair<StatementNodeTree, StatementNodeTree> matchedEntity : matchPair.getMatchedStatements()) {
                        StatementNodeTree left = matchedEntity.getLeft();
                        StatementNodeTree right = matchedEntity.getRight();
                        System.out.println(left.getStatement());
                        System.out.println(right.getStatement());
                    }

//                    commitJSON(gitURL, commitId, matchPair);
                }

                @Override
                public void handleException(String commit, Exception e) {
                    System.err.println("Error processing commit " + commit);
                    e.printStackTrace(System.err);
                }
            });
            long end = System.nanoTime();
//            System.out.println("total time " + (end - start));
        }
    }

    @Test
    public void detectBetweenCommit() throws Exception {
        String folder = "E:\\refactoring-toy-example";
        String startCommitId = "a5a7f852e45c7cadc8d1524bd4d14a1e39785aa5";
        String endCommitId = "d4bce13a443cf12da40a77c16c1e591f4f985b47";
        GitService gitService = new GitServiceImpl();
        try (Repository repo = gitService.openRepository(folder)) {
            Iterable<RevCommit> revsWalkBetweenCommits = gitService.createRevsWalkBetweenCommits(repo, startCommitId, endCommitId);
            System.out.println(revsWalkBetweenCommits);
            EntityMatcherService service = new EntityMatcherServiceImpl();
            service.matchBetweenCommits(repo, startCommitId, endCommitId, new MatchingHandler() {
                @Override
                public void handle(String startCommitId, String endCommitId, MatchPair matchPair) {
                    for (Pair<DeclarationNodeTree, DeclarationNodeTree> matchedEntity : matchPair.getMatchedEntities()) {
                        DeclarationNodeTree left = matchedEntity.getLeft();
                        DeclarationNodeTree right = matchedEntity.getRight();
                        if (left.getType() == EntityType.FIELD)
                            System.out.println(left);
                    }
                }

                @Override
                public void handleException(String startCommitId, String endCommitId, Exception e) {
                    System.err.println("Error processing commit " + endCommitId);
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    @Test
    public void detectAtFiles() {
        File before = new File("E:\\contributions\\TOSEM2024\\Analysis\\bugs\\22\\before.java");
        File after = new File("E:\\contributions\\TOSEM2024\\Analysis\\bugs\\22\\after.java");
        EntityMatcherService service = new EntityMatcherServiceImpl();
        service.matchAtFiles(before, after, new MatchingHandler() {
            @Override
            public void handle(String commitId, MatchPair matchPair) {
                System.out.println(matchPair);
            }

            @Override
            public void handleException(String commit, Exception e) {
                System.err.println("Error processing commit " + commit);
                e.printStackTrace(System.err);
            }
        });
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

    @Test
    public void diffFiles() throws Exception {
        GitService service = new GitServiceImpl();
        String folder = "E:\\rminer\\RefactoringMiner";
        String startCommitId = "3.0.1";
        String endCommitId = "3.0.2";
        List<EntityMatchingJSON.FileContent> diffFiles = service.getDiffFiles(folder, startCommitId, endCommitId);
        for (EntityMatchingJSON.FileContent diffFile : diffFiles) {
            System.out.println(diffFile.getName());
        }
    }

    @Test
    public void temp1() {
        String folder = "E:\\refactoring-toy-example";
        GitService gitService = new GitServiceImpl();
        String startId = "1328d7873efe6caaffaf635424e19a4bb5e786a8";
        String endId = "c286db365e7374fe4d08f54077abb7fba81dd296";
        try (Repository repository = gitService.openRepository(folder)) {
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(repository.resolve(startId));
                LocalDateTime dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(commit.getCommitTime()), // 秒级时间戳
                        ZoneId.systemDefault() // 使用系统默认时区
                );
                System.out.println(commit.getName() + "\t" + dateTime);
                System.out.println("=====================================");
            }
            ObjectId from = repository.resolve(startId);
            ObjectId to = repository.resolve(endId);
            try (Git git = new Git(repository); RevWalk walk = new RevWalk(repository)) {
                RevCommit commit2 = walk.parseCommit(repository.resolve(startId));
                Iterable<RevCommit> call = git.log().addRange(from, to).call();
                List<RevCommit> revCommits = StreamSupport.stream(call
                                .spliterator(), false)
                        .filter(r -> r.getCommitTime() > commit2.getCommitTime())
                        .collect(Collectors.toList());
                Collections.reverse(revCommits);
                for (RevCommit commit : revCommits) {
                    LocalDateTime dateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(commit.getCommitTime()), // 秒级时间戳
                            ZoneId.systemDefault() // 使用系统默认时区
                    );
                    System.out.println(commit.getName() + "\t" + dateTime);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}