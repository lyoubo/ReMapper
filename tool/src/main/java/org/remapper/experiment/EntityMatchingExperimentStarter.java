package org.remapper.experiment;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.refactoringminer.RefactoringMiner;
import org.remapper.dto.EntityComparator;
import org.remapper.dto.EntityInfo;
import org.remapper.dto.EntityMatchingResults;
import org.remapper.service.GitService;
import org.remapper.service.ReMapper;
import org.remapper.util.BeanUtils;
import org.remapper.util.GitServiceImpl;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class EntityMatchingExperimentStarter {

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
            new EntityMatchingExperimentStarter().start(projectName);
        }
    }

    private void start(String projectName) {
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            InputStream stream = classLoader.getResourceAsStream("benchmark/entity matching/" + projectName + ".txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String commitId;
            while ((commitId = reader.readLine()) != null) {
                entityMatcher(projectName, commitId);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void entityMatcher(String projectName, String commitId) throws Exception {
        String projectPath = datasetPath + projectName;
        GitService gitService = new GitServiceImpl();
        gitService.checkoutCurrent(projectPath, commitId);
        Set<Pair<org.refactoringminer.api.EntityInfo, org.refactoringminer.api.EntityInfo>> rMatcher = new RefactoringMiner().match(projectPath, commitId);
        long time1 = System.nanoTime();
        Set<Pair<EntityInfo, EntityInfo>> reMapper = new ReMapper().match(projectPath, commitId);
        long time2 = System.nanoTime();
        Set<Pair<EntityComparator, EntityComparator>> baseline = new LinkedHashSet<>();
        Set<Pair<EntityComparator, EntityComparator>> ourApproach = new LinkedHashSet<>();
        BeanUtils.copyRMatcherProperties(rMatcher, baseline);
        BeanUtils.copyReMapperProperties(reMapper, ourApproach);
        Sets.SetView<Pair<EntityComparator, EntityComparator>> differenceOfBaseline = Sets.difference(baseline, ourApproach);
        Sets.SetView<Pair<EntityComparator, EntityComparator>> differenceOfApproach = Sets.difference(ourApproach, baseline);
        Sets.SetView<Pair<EntityComparator, EntityComparator>> intersection = Sets.intersection(ourApproach, baseline);
        if (!differenceOfApproach.isEmpty() || !differenceOfBaseline.isEmpty()) {
            System.out.println("ProjectName:  \t" + projectName);
            System.out.println("Commit ID:  \t" + commitId + "\n");
            Iterator<Pair<EntityComparator, EntityComparator>> iterator1 = intersection.iterator();
            if (iterator1.hasNext()) {
                System.out.println("CommonMatching:\n");
                while (iterator1.hasNext()) {
                    Pair<EntityComparator, EntityComparator> pair = iterator1.next();
                    System.out.println(pair.getLeft().toString() + "\n" + pair.getRight().toString() + "\n");
                }
            } else {
                System.out.println("CommonMatching: No Report!\n");
            }
            Iterator<Pair<EntityComparator, EntityComparator>> iterator2 = differenceOfApproach.iterator();
            if (iterator2.hasNext()) {
                System.out.println("OurApproach:\n");
                while (iterator2.hasNext()) {
                    Pair<EntityComparator, EntityComparator> pair = iterator2.next();
                    System.out.println(pair.getLeft().toString() + "\n" + pair.getRight().toString() + "\n");
                }
            } else {
                System.out.println("OurApproach: No Report!\n");
            }
            Iterator<Pair<EntityComparator, EntityComparator>> iterator3 = differenceOfBaseline.iterator();
            if (iterator3.hasNext()) {
                System.out.println("Baseline:\n");
                while (iterator3.hasNext()) {
                    Pair<EntityComparator, EntityComparator> pair = iterator3.next();
                    System.out.println(pair.getLeft().toString() + "\n" + pair.getRight().toString() + "\n");
                }
            } else {
                System.out.println("Baseline: No Report!\n");
            }
            System.out.println("Execution Time:\t\t" + (time2 - time1) + "\n");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println();
            String filePath = "./data/entity matching/" + projectName + ".json";
            File file = new File(filePath);
            File directory = file.getParentFile();
            Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
            String remote_repo = GitServiceImpl.getRemoteUrl(projectPath);
            String remote_url = remote_repo.replace(".git", "/commit/") + commitId;
            if (remote_repo.equals("https://git.eclipse.org/r/jgit/jgit.git"))
                remote_url = "https://git.eclipse.org/c/jgit/jgit.git/commit/?id=" + commitId;
            if (file.exists()) {
                FileReader reader = new FileReader(filePath);
                EntityMatchingResults results = gson.fromJson(reader, EntityMatchingResults.class);
                results.populateJSON(remote_repo, commitId, remote_url, intersection, differenceOfApproach, differenceOfBaseline);
                String jsonString = gson.toJson(results, EntityMatchingResults.class);
                BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
                out.write(jsonString);
                out.close();
            } else {
                if (!directory.exists())
                    directory.mkdirs();
                file.createNewFile();
                EntityMatchingResults results = new EntityMatchingResults();
                results.populateJSON(remote_repo, commitId, remote_url, intersection, differenceOfApproach, differenceOfBaseline);
                String jsonString = gson.toJson(results, EntityMatchingResults.class);
                BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
                out.write(jsonString);
                out.close();
            }
        }
    }
}
