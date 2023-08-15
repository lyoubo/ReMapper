package org.remapper.dto;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class EntityMatchingResults {

    private List<Result> results;

    public EntityMatchingResults() {
        this.results = new ArrayList<>();
    }

    public void populateJSON(String repository, String sha1, String url,
                             Sets.SetView<Pair<EntityComparator, EntityComparator>> intersection,
                             Sets.SetView<Pair<EntityComparator, EntityComparator>> differenceOfApproach,
                             Sets.SetView<Pair<EntityComparator, EntityComparator>> differenceOfBaseline) {
        Result result = new Result(repository, sha1, url);
        for (Pair<EntityComparator, EntityComparator> pair : intersection) {
            result.addCommonMatching(pair);
        }
        for (Pair<EntityComparator, EntityComparator> pair : differenceOfApproach) {
            result.addOurApproach(pair);
        }
        for (Pair<EntityComparator, EntityComparator> pair : differenceOfBaseline) {
            result.addBaseline(pair);
        }
        results.add(result);
    }

    class Result {
        private String repository;
        private String sha1;
        private String url;
        private List<Entity> commonMatching;
        private List<Entity> ourApproach;
        private List<Entity> baseline;

        public Result(String repository, String sha1, String url) {
            this.repository = repository;
            this.sha1 = sha1;
            this.url = url;
            commonMatching = new ArrayList<>();
            ourApproach = new ArrayList<>();
            baseline = new ArrayList<>();
        }

        public void addCommonMatching(Pair<EntityComparator, EntityComparator> pair) {
            Location left = new Location(pair.getLeft());
            Location right = new Location(pair.getRight());
            Entity entity = new Entity(left, right);
            commonMatching.add(entity);
        }

        public void addOurApproach(Pair<EntityComparator, EntityComparator> pair) {
            Location left = new Location(pair.getLeft());
            Location right = new Location(pair.getRight());
            Entity entity = new Entity(left, right);
            ourApproach.add(entity);
        }

        public void addBaseline(Pair<EntityComparator, EntityComparator> pair) {
            Location left = new Location(pair.getLeft());
            Location right = new Location(pair.getRight());
            Entity entity = new Entity(left, right);
            baseline.add(entity);
        }
    }

    class Entity {
        private final Location leftSideLocation;
        private final Location rightSideLocation;
//        private boolean developerConfirmation;

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
