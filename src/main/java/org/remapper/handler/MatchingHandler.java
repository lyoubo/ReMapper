package org.remapper.handler;

import org.remapper.dto.MatchPair;

public abstract class MatchingHandler {

    /**
     * This method is called after each commit is analyzed.
     * You should override this method to do your custom logic with the list of matched entities.
     *
     * @param commitId  The sha of the analyzed commit.
     * @param matchPair List of entities matched in the commit.
     */
    public void handle(String commitId, MatchPair matchPair) {
    }

    /**
     * This method is called whenever an exception is thrown during the analysis of the given commit.
     * You should override this method to do your custom logic in the case of exceptions (e.g. skip or rethrow).
     *
     * @param commitId The SHA key that identifies the commit.
     * @param e        The exception thrown.
     */
    public void handleException(String commitId, Exception e) {
        throw new RuntimeException(e);
    }
}
