package org.remapper.dto;


import java.util.ArrayList;
import java.util.List;

public class RefactoringDiscoveryJSON {

    private List<Result> results;

    public RefactoringDiscoveryJSON() {
        results = new ArrayList<>();
    }

    public void populateJSON(String repository, String sha1, String url, List<org.remapper.refactoring.Refactoring> refactorings) {
        Result result = new Result(repository, sha1, url, refactorings);
        for (org.remapper.refactoring.Refactoring refactoring : refactorings) {
            Refactoring refactor = new Refactoring(refactoring);
            result.addRefactoring(refactor);
        }
        results.add(result);
    }

    class Result {
        private String repository;
        private String sha1;
        private String url;
        private List<Refactoring> refactorings;

        public Result(String repository, String sha1, String url, List<org.remapper.refactoring.Refactoring> refactorings) {
            this.repository = repository;
            this.sha1 = sha1;
            this.url = url;
            this.refactorings = new ArrayList<>();
        }

        public void addRefactoring(Refactoring refactoring) {
            refactorings.add(refactoring);
        }
    }

    class Refactoring {
        private String type;
        private String description;
        private Location leftSideLocation;
        private Location rightSideLocation;

        public Refactoring(org.remapper.refactoring.Refactoring refactoring) {
            this.type = refactoring.getRefactoringType().toString();
            this.description = refactoring.toString();
            this.leftSideLocation = new Location(refactoring.getLeftSide());
            this.rightSideLocation = new Location(refactoring.getRightSide());
        }
    }

    class Location {
        private final String filePath;
        private final int startLine;
        private final int endLine;
        private final int startColumn;
        private final int endColumn;

        public Location(LocationInfo location) {
            this.filePath = location.getFilePath();
            this.startLine = location.getStartLine();
            this.endLine = location.getEndLine();
            this.startColumn = location.getStartColumn();
            this.endColumn = location.getEndColumn();
        }

        public Location(gr.uom.java.xmi.LocationInfo location) {
            this.filePath = location.getFilePath();
            this.startLine = location.getStartLine();
            this.endLine = location.getEndLine();
            this.startColumn = location.getStartColumn();
            this.endColumn = location.getEndColumn();
        }
    }
}
