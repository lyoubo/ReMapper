package org.remapper.dto;


import gr.uom.java.xmi.diff.*;

import java.util.ArrayList;
import java.util.List;

public class RefactoringDiscoveryResults {

    private List<Result> results;

    public RefactoringDiscoveryResults() {
        results = new ArrayList<>();
    }

    public void populateJSON(String repository, String sha1, String url, List<org.remapper.refactoring.Refactoring> refactoring1, List<org.refactoringminer.api.Refactoring> refactoring2) {
        Result result = new Result(repository, sha1, url);
        for (org.remapper.refactoring.Refactoring refactoring : refactoring1) {
            Refactoring refactor = new Refactoring(refactoring);
            result.addOurApproach(refactor);
        }
        for (org.refactoringminer.api.Refactoring refactoring : refactoring2) {
            Refactoring refactor = new Refactoring(refactoring);
            result.addBaseline(refactor);
        }
        results.add(result);
    }

    class Result {
        private String repository;
        private String sha1;
        private String url;
        private List<Refactoring> ourApproach;
        private List<Refactoring> baseline;

        public Result(String repository, String sha1, String url) {
            this.repository = repository;
            this.sha1 = sha1;
            this.url = url;
            ourApproach = new ArrayList<>();
            baseline = new ArrayList<>();
        }

        public void addOurApproach(Refactoring refactoring) {
            ourApproach.add(refactoring);
        }

        public void addBaseline(Refactoring refactoring) {
            baseline.add(refactoring);
        }
    }

    class Refactoring {
        private String type;
        private String description;
        private Location leftSideLocation;
        private Location rightSideLocation;
//        private boolean developerConfirmation;

        public Refactoring(org.refactoringminer.api.Refactoring refactoring) {
            this.type = refactoring.getRefactoringType().toString();
            this.description = refactoring.toString();
            if (refactoring instanceof RenameClassRefactoring) {
                RenameClassRefactoring ref = (RenameClassRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getOriginalClass().getLocationInfo());
                this.rightSideLocation = new Location(ref.getRenamedClass().getLocationInfo());
            } else if (refactoring instanceof RenameOperationRefactoring) {
                RenameOperationRefactoring ref = (RenameOperationRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getOriginalOperation().getLocationInfo());
                this.rightSideLocation = new Location(ref.getRenamedOperation().getLocationInfo());
            } else if (refactoring instanceof RenameAttributeRefactoring) {
                RenameAttributeRefactoring ref = (RenameAttributeRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getOriginalAttribute().getLocationInfo());
                this.rightSideLocation = new Location(ref.getRenamedAttribute().getLocationInfo());
            } else if (refactoring instanceof MoveClassRefactoring) {
                MoveClassRefactoring ref = (MoveClassRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getOriginalClass().getLocationInfo());
                this.rightSideLocation = new Location(ref.getMovedClass().getLocationInfo());
            } else if (refactoring instanceof ChangeReturnTypeRefactoring) {
                ChangeReturnTypeRefactoring ref = (ChangeReturnTypeRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getOperationBefore().getLocationInfo());
                this.rightSideLocation = new Location(ref.getOperationAfter().getLocationInfo());
            } else if (refactoring instanceof ChangeAttributeTypeRefactoring) {
                ChangeAttributeTypeRefactoring ref = (ChangeAttributeTypeRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getOriginalAttribute().getLocationInfo());
                this.rightSideLocation = new Location(ref.getChangedTypeAttribute().getLocationInfo());
            } else if (refactoring instanceof ExtractClassRefactoring) {
                ExtractClassRefactoring ref = (ExtractClassRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getOriginalClass().getLocationInfo());
                this.rightSideLocation = new Location(ref.getExtractedClass().getLocationInfo());
            } else if (refactoring instanceof ExtractOperationRefactoring) {
                ExtractOperationRefactoring ref = (ExtractOperationRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getSourceOperationBeforeExtraction().getLocationInfo());
                this.rightSideLocation = new Location(ref.getExtractedOperation().getLocationInfo());
            } else if (refactoring instanceof InlineOperationRefactoring) {
                InlineOperationRefactoring ref = (InlineOperationRefactoring) refactoring;
                this.leftSideLocation = new Location(ref.getInlinedOperation().getLocationInfo());
                this.rightSideLocation = new Location(ref.getTargetOperationAfterInline().getLocationInfo());
            }
        }

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
