package org.remapper.refactoring;

import org.refactoringminer.api.RefactoringType;
import org.remapper.dto.LocationInfo;
import org.remapper.util.StringUtils;

public class MoveClassRefactoring extends Refactoring {

    public MoveClassRefactoring(LocationInfo leftSide, LocationInfo rightSide) {
        super(RefactoringType.MOVE_CLASS, leftSide, rightSide);
    }

    public void setDisplayName(String leftPackage, String leftName, String rightPackage, String rightName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Move Class\t");
        if (StringUtils.isNotEmpty(leftPackage))
            sb.append(leftPackage).append(".").append(leftName);
        else
            sb.append(leftName);
        sb.append(" moved to ");
        if (StringUtils.isNotEmpty(rightPackage))
            sb.append(rightPackage).append(".").append(rightName);
        else
            sb.append(rightName);
        super.setDisplayName(sb.toString());
    }
}
