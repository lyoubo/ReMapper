package org.remapper.refactoring;

import org.refactoringminer.api.RefactoringType;
import org.remapper.dto.LocationInfo;
import org.remapper.util.StringUtils;

public class ExtractClassRefactoring extends Refactoring {

    public ExtractClassRefactoring(LocationInfo leftSide, LocationInfo rightSide) {
        super(RefactoringType.EXTRACT_CLASS, leftSide, rightSide);
    }

    public void setDisplayName(String extractedPackage, String extractedName, String leftPackage, String leftName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract Class\t");
        if (StringUtils.isNotEmpty(extractedPackage))
            sb.append(extractedPackage).append(".").append(extractedName);
        else
            sb.append(extractedName);
        sb.append(" from Class ");
        if (StringUtils.isNotEmpty(leftPackage))
            sb.append(leftPackage).append(".").append(leftName);
        else
            sb.append(leftName);
        super.setDisplayName(sb.toString());
    }
}
