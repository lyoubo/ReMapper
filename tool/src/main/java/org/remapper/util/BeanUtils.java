package org.remapper.util;

import org.apache.commons.lang3.tuple.Pair;
import org.remapper.dto.EntityComparator;
import org.remapper.dto.EntityInfo;
import org.remapper.dto.LocationInfo;

import java.util.Set;

public class BeanUtils {

    public static void copyRMatcherProperties(Set<Pair<org.refactoringminer.api.EntityInfo, org.refactoringminer.api.EntityInfo>> source,
                                              Set<Pair<EntityComparator, EntityComparator>> target) {
        for (Pair<org.refactoringminer.api.EntityInfo, org.refactoringminer.api.EntityInfo> pair : source) {
            org.refactoringminer.api.EntityInfo left = pair.getLeft();
            org.refactoringminer.api.EntityInfo right = pair.getRight();
            target.add(Pair.of(copyProperties(left), copyProperties(right)));
        }
    }

    public static void copyReMapperProperties(Set<Pair<EntityInfo, EntityInfo>> source,
                                              Set<Pair<EntityComparator, EntityComparator>> target) {
        for (Pair<EntityInfo, EntityInfo> pair : source) {
            EntityInfo left = pair.getLeft();
            EntityInfo right = pair.getRight();
            target.add(Pair.of(copyProperties(left), copyProperties(right)));
        }
    }

    private static EntityComparator copyProperties(org.refactoringminer.api.EntityInfo source) {
        EntityComparator target = new EntityComparator();
        target.setContainer(source.getContainer());
        target.setType(source.getType().getName());
        target.setName(source.getName());
        target.setLocationInfo(copyProperties(source.getLocationInfo()));
        return target;
    }

    private static EntityComparator copyProperties(EntityInfo source) {
        EntityComparator target = new EntityComparator();
        target.setContainer(source.getContainer());
        target.setType(source.getType().getName());
        target.setName(source.getName());
        target.setLocationInfo(copyProperties(source.getLocationInfo()));
        return target;
    }

    private static LocationInfo copyProperties(gr.uom.java.xmi.LocationInfo source) {
        LocationInfo target = new LocationInfo();
        target.setFilePath(source.getFilePath());
        target.setStartLine(source.getStartLine());
        target.setEndLine(source.getEndLine());
        target.setStartColumn(source.getStartColumn());
        target.setEndColumn(source.getEndColumn());
        return target;
    }

    private static LocationInfo copyProperties(LocationInfo source) {
        LocationInfo target = new LocationInfo();
        target.setFilePath(source.getFilePath());
        target.setStartLine(source.getStartLine());
        target.setEndLine(source.getEndLine());
        target.setStartColumn(source.getStartColumn());
        target.setEndColumn(source.getEndColumn());
        return target;
    }
}
