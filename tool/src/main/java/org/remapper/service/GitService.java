package org.remapper.service;

import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public interface GitService {

    void fileTreeDiff(Repository repository, RevCommit currentCommit, Set<String> addedFiles, Set<String> deletedFiles,
                      Set<String> modifiedFiles, Map<String, String> renamedFiles) throws IOException, CanceledException;

    Repository openRepository(String folder) throws IOException;

    void closeRepository(Repository repository);

    void checkoutCurrent(Repository repository, String commitId) throws GitAPIException;

    void checkoutCurrent(String project, String commitId) throws GitAPIException, IOException;

    void checkoutParent(Repository repository, String commitId) throws GitAPIException, IOException;

    void resetHard(Repository repository) throws GitAPIException;

    Iterable<RevCommit> getAllCommits(String project) throws GitAPIException, IOException;

    boolean containJavaChange(Repository repository, RevCommit currentCommit) throws GitAPIException, IOException;
}
