package org.remapper.service;

import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.remapper.dto.EntityMatchingJSON;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GitService {

    void fileTreeDiff(Repository repository, RevCommit currentCommit, Set<String> addedFiles, Set<String> deletedFiles,
                      Set<String> modifiedFiles, Map<String, String> renamedFiles) throws IOException, CanceledException;

    void fileTreeDiff(Repository repository, RevCommit startCommit, RevCommit endCommit, Set<String> addedFiles, Set<String> deletedFiles,
                      Set<String> modifiedFiles, Map<String, String> renamedFiles) throws IOException, CanceledException;

    RevWalk createAllRevsWalk(Repository repository, String branch) throws Exception;

    Iterable<RevCommit> createRevsWalkBetweenTags(Repository repository, String startTag, String endTag) throws Exception;

    Iterable<RevCommit> createRevsWalkBetweenCommits(Repository repository, String startCommitId, String endCommitId) throws Exception;

    Repository openRepository(String folder) throws IOException;

    void closeRepository(Repository repository);

    void checkoutCurrent(Repository repository, String commitId) throws GitAPIException;

    void checkoutCurrent(String project, String commitId) throws GitAPIException, IOException;

    void checkoutParent(Repository repository, String commitId) throws GitAPIException, IOException;

    void checkoutBranch(Repository repository) throws GitAPIException, IOException;

    void resetHard(Repository repository) throws GitAPIException;

    void resetHard(String project) throws GitAPIException, IOException;

    Iterable<RevCommit> getAllCommits(String project) throws GitAPIException, IOException;

    Iterable<RevCommit> getAllCommits(Repository repository) throws GitAPIException;

    List<Ref> getAllTags(String project) throws GitAPIException, IOException;

    List<Ref> getAllTags(Repository repository) throws GitAPIException;

    boolean containJavaChange(Repository repository, RevCommit currentCommit) throws GitAPIException, IOException;

    ObjectId getActualRefObjectId(Ref refFrom);

    List<EntityMatchingJSON.FileContent> getDiffFiles(String repository, String... commits) throws Exception;
}
