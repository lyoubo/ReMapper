package org.remapper.dto;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class EntityMatchingJSON {

    private List<Result> results;

    public EntityMatchingJSON() {
        this.results = new ArrayList<>();
    }

    public void populateJSON(String repository, String sha1, String url, MatchPair matchPair) {
        Result result = new Result(repository, sha1, url, matchPair);
        results.add(result);
    }

    @Getter
    @Setter
    public class Result {

        private String repository;
        private String sha1;
        private String url;
        private List<FileContent> files;
        private List<Entity> matchedEntities;

        public Result(String repository, String sha1, String url, MatchPair matchPair) {
            this.repository = repository;
            this.sha1 = sha1;
            this.url = url;
            this.files = new ArrayList<>();
            Map<String, String> fileContentsBefore = matchPair.getFileContentsBefore();
            Map<String, String> fileContentsCurrent = matchPair.getFileContentsCurrent();
            Set<String> modifiedFiles = matchPair.getModifiedFiles();
            Map<String, String> renamedFiles = matchPair.getRenamedFiles();
            Set<String> deletedFiles = matchPair.getDeletedFiles();
            Set<String> addedFiles = matchPair.getAddedFiles();
            for (String name : modifiedFiles) {
                FileContent fileContent = new FileContent(name, fileContentsBefore.get(name), fileContentsCurrent.get(name));
                files.add(fileContent);
            }
            for (String oldName : renamedFiles.keySet()) {
                String newName = renamedFiles.get(oldName);
                FileContent fileContent = new FileContent(oldName + " --> " + newName, fileContentsBefore.get(oldName), fileContentsCurrent.get(newName));
                files.add(fileContent);
            }
            for (String name : deletedFiles) {
                FileContent fileContent = new FileContent(name, fileContentsBefore.get(name), "");
                files.add(fileContent);
            }
            for (String name : addedFiles) {
                FileContent fileContent = new FileContent(name, "", fileContentsCurrent.get(name));
                files.add(fileContent);
            }
            this.matchedEntities = new ArrayList<>();
            for (Pair<DeclarationNodeTree, DeclarationNodeTree> pair : matchPair.getMatchedEntities()) {
                Location left = new EntityLocation(pair.getLeft().getEntity());
                Location right = new EntityLocation(pair.getRight().getEntity());
                Entity entity = new Entity(left, right);
                this.matchedEntities.add(entity);
            }
            for (Pair<StatementNodeTree, StatementNodeTree> pair : matchPair.getMatchedStatements()) {
                Location left = new StatementLocation(pair.getLeft().getEntity());
                Location right = new StatementLocation(pair.getRight().getEntity());
                Entity entity = new Entity(left, right);
                this.matchedEntities.add(entity);
            }
        }
    }

    @Getter
    @Setter
    public class FileContent {

        private String name;
        private String oldCode;
        private String newCode;

        public FileContent(String name, String oldCode, String newCode) {
            this.name = name;
            this.oldCode = oldCode;
            this.newCode = newCode;
        }
    }

    @Getter
    @Setter
    public class Entity {

        private final Location leftSideLocation;
        private final Location rightSideLocation;

        public Entity(Location leftSideLocation, Location rightSideLocation) {
            this.leftSideLocation = leftSideLocation;
            this.rightSideLocation = rightSideLocation;
        }
    }

    @Getter
    @Setter
    public class EntityLocation extends Location {

        private final String container;
        private final String type;
        private final String name;

        public EntityLocation(EntityInfo entity) {
            super(entity);
            this.container = entity.getContainer();
            this.type = entity.getType().getName();
            this.name = entity.getName();
        }
    }

    @Getter
    @Setter
    public class StatementLocation extends Location {

        private final String method;
        private final String type;
        private final String expression;

        public StatementLocation(StatementInfo entity) {
            super(entity);
            this.method = entity.getMethod();
            this.type = entity.getType().getName();
            this.expression = entity.getExpression();
        }
    }

    @Getter
    @Setter
    public class Location {

        private final String filePath;
        private final int startLine;
        private final int endLine;
        private final int startColumn;
        private final int endColumn;
        private final String codeElementType;

        public Location(EntityInfo entity) {
            this.filePath = entity.getLocationInfo().getFilePath();
            this.startLine = entity.getLocationInfo().getStartLine();
            this.endLine = entity.getLocationInfo().getEndLine();
            this.startColumn = entity.getLocationInfo().getStartColumn();
            this.endColumn = entity.getLocationInfo().getEndColumn();
            this.codeElementType = entity.getLocationInfo().getCodeElementType().name();
        }

        public Location(StatementInfo entity) {
            this.filePath = entity.getLocationInfo().getFilePath();
            this.startLine = entity.getLocationInfo().getStartLine();
            this.endLine = entity.getLocationInfo().getEndLine();
            this.startColumn = entity.getLocationInfo().getStartColumn();
            this.endColumn = entity.getLocationInfo().getEndColumn();
            this.codeElementType = entity.getLocationInfo().getCodeElementType().name();
        }
    }
}
