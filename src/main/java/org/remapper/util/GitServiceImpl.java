package org.remapper.util;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.remapper.service.GitService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
        walk.setRevFilter(commitsFilter);
        return walk;
    }

    @Override
    public Iterable<RevCommit> createRevsWalkBetweenTags(Repository repository, String startTag, String endTag)
            throws Exception {
        Ref refFrom = repository.findRef(startTag);
        Ref refTo = repository.findRef(endTag);
        try (Git git = new Git(repository)) {
            List<RevCommit> revCommits = StreamSupport.stream(git.log().addRange(getActualRefObjectId(refFrom), getActualRefObjectId(refTo)).call()
                            .spliterator(), false)
                    .filter(r -> r.getParentCount() == 1)
                    .collect(Collectors.toList());
            Collections.reverse(revCommits);
            return revCommits;
        }
    }

    public ObjectId getActualRefObjectId(Ref ref) {
        if(ref.getPeeledObjectId() != null) {
            return ref.getPeeledObjectId();
        }
        return ref.getObjectId();
    }

    @Override
    public Iterable<RevCommit> createRevsWalkBetweenCommits(Repository repository, String startCommitId, String endCommitId)
            throws Exception {
        ObjectId from = repository.resolve(startCommitId);
        ObjectId to = repository.resolve(endCommitId);
        try (Git git = new Git(repository)) {
            List<RevCommit> revCommits = StreamSupport.stream(git.log().addRange(from, to).call()
                            .spliterator(), false)
                    .filter(r -> r.getParentCount() == 1)
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

    public Iterable<RevCommit> getAllCommits(Repository repository) throws GitAPIException {
        try (Git git = new Git(repository)) {
            LogCommand log = git.log().setRevFilter(commitsFilter);
            return log.call();
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
