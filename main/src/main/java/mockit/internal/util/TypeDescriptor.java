/*
 * Copyright (c) 2006 JMockit developers
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import mockit.asm.types.ArrayType;
import mockit.asm.types.JavaType;
import mockit.asm.types.PrimitiveType;

import edu.umd.cs.findbugs.annotations.NonNull;

public final class TypeDescriptor {
    private static final Class<?>[] NO_PARAMETERS = {};

    private TypeDescriptor() {
    }

    @NonNull
    public static Class<?>[] getParameterTypes(@NonNull String methodDesc) {
        JavaType[] paramTypes = JavaType.getArgumentTypes(methodDesc);

        if (paramTypes.length == 0) {
            return NO_PARAMETERS;
        }

        Class<?>[] paramClasses = new Class<?>[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            paramClasses[i] = getClassForType(paramTypes[i]);
        }

        return paramClasses;
    }

    @NonNull
    public static Class<?> getReturnType(@NonNull String methodSignature) {
        String methodDesc = methodDescriptionWithoutTypeArguments(methodSignature);
        JavaType returnType = JavaType.getReturnType(methodDesc);
        return getClassForType(returnType);
    }

    @NonNull
    private static String methodDescriptionWithoutTypeArguments(@NonNull String methodSignature) {
        while (true) {
            int p = methodSignature.indexOf('<');

            if (p < 0) {
                return methodSignature;
            }

            String firstPart = methodSignature.substring(0, p);
            int q = methodSignature.indexOf('>', p) + 1;

            if (methodSignature.charAt(q) == '.') { // in case there is an inner class
                methodSignature = firstPart + '$' + methodSignature.substring(q + 1);
            } else {
                methodSignature = firstPart + methodSignature.substring(q);
            }
        }
    }

    @NonNull
    public static Class<?> getClassForType(@NonNull JavaType type) {
        if (type instanceof PrimitiveType) {
            return ((PrimitiveType) type).getType();
        }

        String className;

        if (type instanceof ArrayType) {
            className = type.getDescriptor().replace('/', '.');
        } else {
            className = type.getClassName();
        }

        return ClassLoad.loadClass(className);
    }
}
