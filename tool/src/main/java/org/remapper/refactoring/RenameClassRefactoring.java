package org.remapper.refactoring;

import org.refactoringminer.api.RefactoringType;
import org.remapper.dto.LocationInfo;
import org.remapper.util.StringUtils;

public class RenameClassRefactoring extends Refactoring {

    public RenameClassRefactoring(LocationInfo leftSide, LocationInfo rightSide) {
        super(RefactoringType.RENAME_CLASS, leftSide, rightSide);
    }

    public void setDisplayName(String leftName, String rightName, String packageName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rename Class\t");
        if (StringUtils.isNotEmpty(packageName))
            sb.append(packageName).append(".").append(leftName);
        else
            sb.append(leftName);
        sb.append(" renamed to ");
        if (StringUtils.isNotEmpty(packageName))
            sb.append(packageName).append(".").append(rightName);
        else
            sb.append(rightName);
        super.setDisplayName(sb.toString());
    }
}
