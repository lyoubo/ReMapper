package org.remapper.service;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.remapper.dto.MatchPair;
import org.remapper.handler.MatchingHandler;

public interface EntityMatcherService {

    /**
     * Detect refactorings performed in the specified commit.
     *
     * @param repository A git repository (from JGit library).
     * @param commitId   The SHA key that identifies the commit.
     * @param handler    A handler object that is responsible to process the matched entities.
     */
    void matchAtCommit(Repository repository, String commitId, MatchingHandler handler);

    /**
     * Detect refactorings performed in the specified commit.
     *
     * @param repository A git repository (from JGit library).
     * @param commitId   The SHA key that identifies the commit.
     * @param handler    A handler object that is responsible to process the matched entities.
     * @param timeout    A timeout, in seconds. When timeout is reached, the operation stops and returns no matched entities.
     */
    void matchAtCommit(Repository repository, String commitId, MatchingHandler handler, int timeout);

    MatchPair matchEntities(Repository repository, RevCommit currentCommit, final MatchingHandler handler) throws Exception;
}
