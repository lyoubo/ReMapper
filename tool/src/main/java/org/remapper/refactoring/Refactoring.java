package org.remapper.refactoring;

import org.refactoringminer.api.RefactoringType;
import org.remapper.dto.LocationInfo;

public class Refactoring {

    private RefactoringType refactoringType;
    private String displayName;
    private LocationInfo leftSide;
    private LocationInfo rightSide;

    public Refactoring(RefactoringType refactoringType, LocationInfo leftSide, LocationInfo rightSide) {
        this.refactoringType = refactoringType;
        this.leftSide = leftSide;
        this.rightSide = rightSide;
    }

    public LocationInfo getLeftSide() {
        return leftSide;
    }

    public LocationInfo getRightSide() {
        return rightSide;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public RefactoringType getRefactoringType() {
        return refactoringType;
    }
}
