package org.remapper.dto;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EntityMatchingJSON {

    private List<Result> results;

    public EntityMatchingJSON() {
        this.results = new ArrayList<>();
    }

    public void populateJSON(String repository, String sha1, String url,
                             Set<Pair<EntityComparator, EntityComparator>> matchedPairs) {
        Result result = new Result(repository, sha1, url, matchedPairs);
        results.add(result);
    }

    class Result {
        private String repository;
        private String sha1;
        private String url;
        private List<Entity> matchedEntities;

        public Result(String repository, String sha1, String url,
                      Set<Pair<EntityComparator, EntityComparator>> matchedPairs) {
            this.repository = repository;
            this.sha1 = sha1;
            this.url = url;
            this.matchedEntities = new ArrayList<>();
            for (Pair<EntityComparator, EntityComparator> pair : matchedPairs) {
                Location left = new Location(pair.getLeft());
                Location right = new Location(pair.getRight());
                Entity entity = new Entity(left, right);
                this.matchedEntities.add(entity);
            }
        }
    }

    class Entity {
        private final Location leftSideLocation;
        private final Location rightSideLocation;

        public Entity(Location leftSideLocation, Location rightSideLocation) {
            this.leftSideLocation = leftSideLocation;
            this.rightSideLocation = rightSideLocation;
        }
    }

    class Location {
        private final String container;
        private final String type;
        private final String name;
        private final String filePath;
        private final int startLine;
        private final int endLine;
        private final int startColumn;
        private final int endColumn;

        public Location(EntityComparator entity) {
            this.container = entity.getContainer();
            this.type = entity.getType().getName();
            this.name = entity.getName();
            this.filePath = entity.getLocationInfo().getFilePath();
            this.startLine = entity.getLocationInfo().getStartLine();
            this.endLine = entity.getLocationInfo().getEndLine();
            this.startColumn = entity.getLocationInfo().getStartColumn();
            this.endColumn = entity.getLocationInfo().getEndColumn();
        }
    }
}
