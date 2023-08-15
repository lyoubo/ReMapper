package org.remapper.service;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.lib.Repository;
import org.remapper.dto.DeclarationNodeTree;
import org.remapper.dto.EntityInfo;
import org.remapper.dto.MatchPair;
import org.remapper.handler.MatchingHandler;
import org.remapper.util.GitServiceImpl;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public class ReMapper {

    public Set<Pair<EntityInfo, EntityInfo>> match(String projectPath, String commitId) throws IOException {
        GitService gitService = new GitServiceImpl();
        MatchService matchService = new MatchServiceImpl(projectPath);
        Set<Pair<EntityInfo, EntityInfo>> matchPairs = new LinkedHashSet<>();
        try (Repository repo = gitService.openRepository(projectPath)) {
            matchService.matchAtCommit(repo, commitId, new MatchingHandler() {
                @Override
                public void handle(String commitId, MatchPair matchPair) {
                    Set<Pair<DeclarationNodeTree, DeclarationNodeTree>> matchedEntities = matchPair.getMatchedEntities();
                    for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchedEntities) {
                        matchPairs.add(Pair.of(pair.getLeft().getEntityWithFilePath(), pair.getRight().getEntityWithFilePath()));
                    }
                }

                @Override
                public void handleException(String commitId, Exception e) {
                    System.out.println(commitId);
                    e.printStackTrace();
                }
            });
            return matchPairs;
        }
    }
}
