package org.remapper.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.remapper.dto.EntityMatchingJSON;
import org.remapper.service.GitService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GitServiceImpl implements GitService {

    private static final String REMOTE_REFS_PREFIX = "refs/remotes/origin/";

    DefaultCommitsFilter commitsFilter = new DefaultCommitsFilter();

    public boolean isCommitAnalyzed(String sha1) {
        return false;
    }

    private class DefaultCommitsFilter extends RevFilter {
        @Override
        public final boolean include(final RevWalk walker, final RevCommit c) {
            return c.getParentCount() == 1;
        }

        @Override
        public final RevFilter clone() {
            return this;
        }

        @Override
        public final boolean requiresCommitBody() {
            return false;
        }

        @Override
        public String toString() {
            return "RegularCommitsFilter";
        }
    }

    @Override
    public void fileTreeDiff(Repository repository, RevCommit currentCommit, Set<String> addedFiles, Set<String> deletedFiles,
                             Set<String> modifiedFiles, Map<String, String> renamedFiles) throws IOException, CanceledException {
        if (currentCommit.getParentCount() > 0) {
            ObjectId oldTree = currentCommit.getParent(0).getTree();
            ObjectId newTree = currentCommit.getTree();
            populateTreeDiff(repository, addedFiles, deletedFiles, modifiedFiles, renamedFiles, oldTree, newTree);
        }
    }

    private void populateTreeDiff(Repository repository, Set<String> addedFiles, Set<String> deletedFiles, Set<String> modifiedFiles,
                                  Map<String, String> renamedFiles, ObjectId oldTree, ObjectId newTree) throws IOException, CanceledException {
        final TreeWalk tw = new TreeWalk(repository);
        tw.setRecursive(true);
        tw.addTree(oldTree);
        tw.addTree(newTree);
        RenameDetector rd = new RenameDetector(repository);
        rd.addAll(DiffEntry.scan(tw));
        for (DiffEntry diff : rd.compute(tw.getObjectReader(), null)) {
            DiffEntry.ChangeType changeType = diff.getChangeType();
            String oldPath = diff.getOldPath();
            String newPath = diff.getNewPath();
            if (changeType == DiffEntry.ChangeType.ADD) {
                if (isJavaFile(newPath))
                    addedFiles.add(newPath);
            } else if (changeType == DiffEntry.ChangeType.DELETE) {
                if (isJavaFile(oldPath))
                    deletedFiles.add(oldPath);
            } else if (changeType == DiffEntry.ChangeType.MODIFY) {
                if (isJavaFile(oldPath) && isJavaFile(newPath))
                    modifiedFiles.add(oldPath);
            } else if (changeType == DiffEntry.ChangeType.RENAME) {
                if (isJavaFile(oldPath) && isJavaFile(newPath))
                    renamedFiles.put(oldPath, newPath);
            } else if (changeType == DiffEntry.ChangeType.COPY) {
                if (isJavaFile(newPath))
                    addedFiles.add(newPath);
            }
        }
    }

    @Override
    public void fileTreeDiff(Repository repository, RevCommit startCommit, RevCommit endCommit, Set<String> addedFiles, Set<String> deletedFiles,
                             Set<String> modifiedFiles, Map<String, String> renamedFiles) throws IOException, CanceledException {
        ObjectId oldTree = startCommit.getTree();
        ObjectId newTree = endCommit.getTree();
        populateTreeDiff(repository, addedFiles, deletedFiles, modifiedFiles, renamedFiles, oldTree, newTree);
    }

    public RevWalk createAllRevsWalk(Repository repository, String branch) throws Exception {
        List<ObjectId> currentRemoteRefs = new ArrayList<ObjectId>();
        for (Ref ref : repository.getRefDatabase().getRefs()) {
            String refName = ref.getName();
            if (refName.startsWith(REMOTE_REFS_PREFIX)) {
                if (branch == null || refName.endsWith("/" + branch)) {
                    currentRemoteRefs.add(ref.getObjectId());
                }
            }
        }

        RevWalk walk = new RevWalk(repository);
        for (ObjectId newRef : currentRemoteRefs) {
            walk.markStart(walk.parseCommit(newRef));
        }
//        walk.setRevFilter(commitsFilter);
        return walk;
    }

    @Override
    public Iterable<RevCommit> createRevsWalkBetweenTags(Repository repository, String startTag, String endTag)
            throws Exception {
        Ref refFrom = repository.findRef(startTag);
        Ref refTo = repository.findRef(endTag);
        try (Git git = new Git(repository); RevWalk walk = new RevWalk(repository)) {
            RevCommit start = walk.parseCommit(getActualRefObjectId(refFrom));
            int commitTime = start.getCommitTime();
            List<RevCommit> revCommits = StreamSupport.stream(git.log().addRange(getActualRefObjectId(refFrom), getActualRefObjectId(refTo)).call()
                            .spliterator(), false)
//                    .filter(r -> r.getParentCount() == 1)
                    .filter(r -> r.getCommitTime() > commitTime)
                    .collect(Collectors.toList());
            Collections.reverse(revCommits);
            return revCommits;
        }
    }

    @Override
    public ObjectId getActualRefObjectId(Ref ref) {
        if (ref.getPeeledObjectId() != null) {
            return ref.getPeeledObjectId();
        }
        return ref.getObjectId();
    }

    @Override
    public List<EntityMatchingJSON.FileContent> getDiffFiles(String projectPath, String... commits) throws Exception {
        try (Repository repository = openRepository(projectPath); RevWalk walk = new RevWalk(repository)) {
            if (commits.length == 1) {
                String commitId = commits[0];
                if (projectPath.endsWith(".java") && commitId.endsWith(".java")) {
                    return getDiffFiles(projectPath, commitId);
                } else {
                    RevCommit currentCommit = walk.parseCommit(repository.resolve(commitId));
                    if (currentCommit.getParentCount() > 0) {
                        walk.parseCommit(currentCommit.getParent(0));
                    }
                    RevCommit parentCommit = currentCommit.getParent(0);
                    return getDiffFiles(repository, parentCommit, currentCommit);
                }
            } else if (commits.length == 2) {
                RevCommit startCommit = walk.parseCommit(repository.resolve(commits[0]));
                RevCommit endCommit = walk.parseCommit(repository.resolve(commits[1]));
                return getDiffFiles(repository, startCommit, endCommit);
            }
            return Collections.emptyList();
        }
    }

    public List<EntityMatchingJSON.FileContent> getDiffFiles(Repository repository, RevCommit startCommit, RevCommit endCommit) throws Exception {
        Set<String> addedFiles = new LinkedHashSet<>();
        Set<String> deletedFiles = new LinkedHashSet<>();
        Set<String> modifiedFiles = new LinkedHashSet<>();
        Map<String, String> renamedFiles = new LinkedHashMap<>();
        Map<String, String> fileContentsBefore = new LinkedHashMap<>();
        Map<String, String> fileContentsCurrent = new LinkedHashMap<>();
        fileTreeDiff(repository, startCommit, endCommit, addedFiles, deletedFiles, modifiedFiles, renamedFiles);

        populateFileContents(repository, startCommit, deletedFiles, fileContentsBefore);
        populateFileContents(repository, startCommit, modifiedFiles, fileContentsBefore);
        populateFileContents(repository, startCommit, renamedFiles.keySet(), fileContentsBefore);
        populateFileContents(repository, endCommit, addedFiles, fileContentsCurrent);
        populateFileContents(repository, endCommit, modifiedFiles, fileContentsCurrent);
        populateFileContents(repository, endCommit, new HashSet<>(renamedFiles.values()), fileContentsCurrent);

        List<EntityMatchingJSON.FileContent> files = new ArrayList<>();
        EntityMatchingJSON json = new EntityMatchingJSON();
        for (String name : modifiedFiles) {
            EntityMatchingJSON.FileContent fileContent = json.new FileContent(name, fileContentsBefore.get(name), fileContentsCurrent.get(name));
            files.add(fileContent);
        }
        for (String oldName : renamedFiles.keySet()) {
            String newName = renamedFiles.get(oldName);
            EntityMatchingJSON.FileContent fileContent = json.new FileContent(oldName + " --> " + newName, fileContentsBefore.get(oldName), fileContentsCurrent.get(newName));
            files.add(fileContent);
        }
        for (String name : deletedFiles) {
            EntityMatchingJSON.FileContent fileContent = json.new FileContent(name, fileContentsBefore.get(name), "");
            files.add(fileContent);
        }
        for (String name : addedFiles) {
            EntityMatchingJSON.FileContent fileContent = json.new FileContent(name, "", fileContentsCurrent.get(name));
            files.add(fileContent);
        }
        return files;
    }

    private List<EntityMatchingJSON.FileContent> getDiffFiles(String before, String after) throws Exception {
        File previousFile = new File(before);
        File nextFile = new File(after);
        Map<String, String> renamedFiles = new LinkedHashMap<>();
        Map<String, String> fileContentsBefore = new LinkedHashMap<>();
        Map<String, String> fileContentsCurrent = new LinkedHashMap<>();

        renamedFiles.put(previousFile.getPath().replace("\\", "/"), nextFile.getPath().replace("\\", "/"));
        populateFileContents(previousFile, fileContentsBefore);
        populateFileContents(nextFile, fileContentsCurrent);
        List<EntityMatchingJSON.FileContent> files = new ArrayList<>();
        EntityMatchingJSON json = new EntityMatchingJSON();
        for (String oldName : renamedFiles.keySet()) {
            String newName = renamedFiles.get(oldName);
            EntityMatchingJSON.FileContent fileContent = json.new FileContent(oldName + " --> " + newName, fileContentsBefore.get(oldName), fileContentsCurrent.get(newName));
            files.add(fileContent);
        }
        return files;
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

    @Override
    public Iterable<RevCommit> createRevsWalkBetweenCommits(Repository repository, String startCommitId, String endCommitId)
            throws Exception {
        ObjectId from = repository.resolve(startCommitId);
        ObjectId to = repository.resolve(endCommitId);
        try (Git git = new Git(repository); RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(from);
            int commitTime = commit.getCommitTime();
            List<RevCommit> revCommits = StreamSupport.stream(git.log().addRange(from, to).call()
                            .spliterator(), false)
//                    .filter(r -> r.getParentCount() == 1)
                    .filter(r -> r.getCommitTime() > commitTime)
                    .collect(Collectors.toList());
            Collections.reverse(revCommits);
            return revCommits;
        }
    }

    private boolean isJavaFile(String path) {
        return path.endsWith(".java");
    }

    @Override
    public Repository openRepository(String repositoryPath) throws IOException {
        File folder = new File(repositoryPath);
        Repository repository;
        if (folder.exists()) {
            String[] contents = folder.list();
            boolean dotGitFound = false;
            for (String content : contents) {
                if (content.equals(".git")) {
                    dotGitFound = true;
                    break;
                }
            }
            RepositoryBuilder builder = new RepositoryBuilder();
            repository = builder
                    .setGitDir(dotGitFound ? new File(folder, ".git") : folder)
                    .readEnvironment()
                    .findGitDir()
                    .build();
        } else {
            throw new FileNotFoundException(repositoryPath);
        }
        return repository;
    }

    public void closeRepository(Repository repository) {
        repository.close();
    }

    @Override
    public void checkoutCurrent(Repository repository, String commitId) throws GitAPIException {
        try (Git git = new Git(repository)) {
            CheckoutCommand checkout = git.checkout().setForced(true).setName(commitId);
            checkout.call();
        }
    }

    @Override
    public void checkoutCurrent(String project, String commitId) throws GitAPIException, IOException {
        try (Repository repository = openRepository(project); Git git = new Git(repository)) {
            CheckoutCommand checkout = git.checkout().setForced(true).setName(commitId);
            checkout.call();
        }
    }

    @Override
    public void checkoutParent(Repository repository, String commitId) throws GitAPIException, IOException {
        try (Git git = new Git(repository)) {
            RevCommit currentCommit = getRevCommit(repository, commitId);
            CheckoutCommand checkout = git.checkout().setForced(true).setName(currentCommit.getParent(0).getName());
            checkout.call();
        }
    }

    @Override
    public void checkoutBranch(Repository repository) throws GitAPIException, IOException {
        try (Git git = new Git(repository)) {
            String defaultBranch = repository.findRef(REMOTE_REFS_PREFIX + "HEAD").getTarget().getName();
            String branch = defaultBranch.substring(REMOTE_REFS_PREFIX.length());
            CheckoutCommand checkout = git.checkout().setForced(true).setName(branch);
            checkout.call();
        }
    }

    @Override
    public void resetHard(Repository repository) throws GitAPIException {
        try (Git git = new Git(repository)) {
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
        }
    }

    @Override
    public void resetHard(String project) throws GitAPIException, IOException {
        try (Repository repository = openRepository(project); Git git = new Git(repository)) {
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
        }
    }

    private RevCommit getRevCommit(Repository repository, String commitId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(commitId));
            if (commit.getParentCount() > 0) {
                walk.parseCommit(commit.getParent(0));
                return commit;
            }
        }
        throw new RuntimeException(String.format("Ignored revision %s because it has no parent", commitId));
    }

    @Override
    public Iterable<RevCommit> getAllCommits(String projectPath) throws GitAPIException, IOException {
        try (Repository repository = openRepository(projectPath)) {
            return getAllCommits(repository);
        }
    }

    @Override
    public Iterable<RevCommit> getAllCommits(Repository repository) throws GitAPIException {
        try (Git git = new Git(repository)) {
            LogCommand log = git.log();
            return log.call();
        }
    }

    @Override
    public List<Ref> getAllTags(String projectPath) throws GitAPIException, IOException {
        try (Repository repository = openRepository(projectPath)) {
            return getAllTags(repository);
        }
    }

    @Override
    public List<Ref> getAllTags(Repository repository) throws GitAPIException {
        try (Git git = new Git(repository)) {
            ListTagCommand listTagCommand = git.tagList();
            return listTagCommand.call();
        }
    }

    @Override
    public boolean containJavaChange(Repository repository, RevCommit currentCommit) throws GitAPIException, IOException {
        if (currentCommit.getParentCount() > 0) {
            ObjectId oldTree = currentCommit.getParent(0).getTree();
            ObjectId newTree = currentCommit.getTree();
            try (ObjectReader reader = repository.newObjectReader()) {
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset(reader, oldTree);
                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                newTreeIter.reset(reader, newTree);
                try (Git git = new Git(repository)) {
                    List<DiffEntry> diffs = git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();
                    boolean deleted = false;
                    boolean added = false;
                    for (DiffEntry diff : diffs) {
                        if (diff.getChangeType() == DiffEntry.ChangeType.MODIFY && isJavaFile(diff.getOldPath()))
                            return true;
                        else if (diff.getChangeType() == DiffEntry.ChangeType.DELETE && isJavaFile(diff.getOldPath()))
                            deleted = true;
                        else if (diff.getChangeType() == DiffEntry.ChangeType.ADD && isJavaFile(diff.getNewPath()))
                            added = true;
                        if (deleted && added)
                            return true;
                    }
                }
            }
        }
        return false;
    }

    public static String getRemoteUrl(String projectPath) throws IOException, GitAPIException {
        if (dotGitFound(projectPath))
            try (Git git = Git.open(new File(projectPath))) {
                List<RemoteConfig> configs = git.remoteList().call();
                for (RemoteConfig config : configs) {
                    List<URIish> urIs = config.getURIs();
                    for (URIish uri : urIs)
                        if (uri.toString().startsWith("git@"))
                            return uri.toString().replace(":", "/").
                                    replace("git@", "https://");
                        else return uri.toString();
                }
            }
        return "";
    }

    private static boolean dotGitFound(String projectPath) {
        File folder = new File(projectPath);
        if (folder.exists()) {
            String[] contents = folder.list();
            for (String content : contents)
                if (content.equals(".git"))
                    return true;
        }
        return false;
    }
}
