package org.remapper.util;

import org.eclipse.jdt.core.dom.Type;
import org.remapper.dto.UMLType;

public class StringUtils {

    public static boolean equals(String str1, String str2) {
        if (str1 == null || str2 == null) return false;
        return str1.strip().equals(str2.strip());
    }

    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static String type2String(Type type) {
        UMLType umlType = UMLType.extractTypeObject(type);
        return umlType.toString();
    }
}
