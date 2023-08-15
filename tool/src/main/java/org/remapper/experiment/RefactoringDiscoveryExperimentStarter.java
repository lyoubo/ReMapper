package org.remapper.experiment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.remapper.dto.RefactoringDiscoveryResults;
import org.remapper.refactoring.Refactoring;
import org.remapper.service.GitService;
import org.remapper.service.ReExtractor;
import org.remapper.service.RefactoringMiner;
import org.remapper.util.GitServiceImpl;

import java.io.*;
import java.util.Iterator;
import java.util.List;

public class RefactoringDiscoveryExperimentStarter {

    // TODO: PLease specify as your local dataset path
    final String datasetPath = "/home/remapper/dataset/";

    public static void main(String[] args) {
        String[] projects = new String[]{
                "checkstyle",
                "commons-io",
                "commons-lang",
                "elasticsearch",
                "flink",
                "hadoop",
                "hibernate-orm",
                "hibernate-search",
                "intellij-community",
                "javaparser",
                "jetty.project",
                "jgit",
                "junit4",
                "junit5",
                "lucene-solr",
                "mockito",
                "okhttp",
                "pmd",
                "spring-boot",
                "spring-framework",
        };
        for (String projectName : projects) {
            new RefactoringDiscoveryExperimentStarter().start(projectName);
        }
    }

    private void start(String projectName) {
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            InputStream stream = classLoader.getResourceAsStream("benchmark/refactoring discovery/" + projectName + ".txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String commitId;
            while ((commitId = reader.readLine()) != null) {
                refactoringDiscoverer(projectName, commitId);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refactoringDiscoverer(String projectName, String commitId) throws Exception {
        String projectPath = datasetPath + projectName;
        GitService gitService = new GitServiceImpl();
        gitService.checkoutCurrent(projectPath, commitId);
        List<org.refactoringminer.api.Refactoring> rMiner = new RefactoringMiner().discover(projectPath, commitId);
        List<Refactoring> reMapper = new ReExtractor().discover(projectPath, commitId);
        System.out.println("ProjectName:  \t" + projectName);
        System.out.println("Commit ID:  \t" + commitId + "\n");
        Iterator<Refactoring> iterator1 = reMapper.iterator();
        if (iterator1.hasNext()) {
            System.out.println("OurApproach:\n");
            while (iterator1.hasNext()) {
                Refactoring refactoring = iterator1.next();
                System.out.println(refactoring);
            }
        } else {
            System.out.println("OurApproach: No Report!");
        }
        System.out.println();
        Iterator<org.refactoringminer.api.Refactoring> iterator2 = rMiner.iterator();
        if (iterator2.hasNext()) {
            System.out.println("Baseline:\n");
            while (iterator2.hasNext()) {
                org.refactoringminer.api.Refactoring refactoring = iterator2.next();
                System.out.println(refactoring);
            }
        } else {
            System.out.println("Baseline: No Report!");
        }
        System.out.println();
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println();
        String filePath = "./data/refactoring discovery/" + projectName + ".json";
        File file = new File(filePath);
        File directory = file.getParentFile();
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String remote_repo = GitServiceImpl.getRemoteUrl(projectPath);
        String remote_url = remote_repo.replace(".git", "/commit/") + commitId;
        if (remote_repo.equals("https://git.eclipse.org/r/jgit/jgit.git"))
            remote_url = "https://git.eclipse.org/c/jgit/jgit.git/commit/?id=" + commitId;
        if (file.exists()) {
            FileReader reader = new FileReader(filePath);
            RefactoringDiscoveryResults results = gson.fromJson(reader, RefactoringDiscoveryResults.class);
            results.populateJSON(remote_repo, commitId, remote_url, reMapper, rMiner);
            String jsonString = gson.toJson(results, RefactoringDiscoveryResults.class);
            BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
            out.write(jsonString);
            out.close();
        } else {
            if (!directory.exists())
                directory.mkdirs();
            file.createNewFile();
            RefactoringDiscoveryResults results = new RefactoringDiscoveryResults();
            results.populateJSON(remote_repo, commitId, remote_url, reMapper, rMiner);
            String jsonString = gson.toJson(results, RefactoringDiscoveryResults.class);
            BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
            out.write(jsonString);
            out.close();
        }
    }
}
