/*
 * Copyright (c) 2006 JMockit developers
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.testRedundancy;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import mockit.coverage.Configuration;

import org.checkerframework.checker.index.qual.NonNegative;

public final class TestCoverage {
    @Nullable
    public static final TestCoverage INSTANCE;

    static {
        INSTANCE = "true".equals(Configuration.getProperty("redundancy")) ? new TestCoverage() : null;
    }

    @NonNull
    private final Map<Method, Integer> testsToItemsCovered = new LinkedHashMap<>();
    @Nullable
    private Method currentTestMethod;

    private TestCoverage() {
    }

    public void setCurrentTestMethod(@Nullable Method testMethod) {
        if (testMethod != null) {
            testsToItemsCovered.put(testMethod, 0);
        }

        currentTestMethod = testMethod;
    }

    public void recordNewItemCoveredByTestIfApplicable(@NonNegative int previousExecutionCount) {
        if (previousExecutionCount == 0 && currentTestMethod != null) {
            Integer itemsCoveredByTest = testsToItemsCovered.get(currentTestMethod);
            testsToItemsCovered.put(currentTestMethod, itemsCoveredByTest == null ? 1 : itemsCoveredByTest + 1);
        }
    }

    @NonNull
    public List<Method> getRedundantTests() {
        List<Method> redundantTests = new ArrayList<>();

        for (Entry<Method, Integer> testAndItemsCovered : testsToItemsCovered.entrySet()) {
            Method testMethod = testAndItemsCovered.getKey();
            Integer itemsCovered = testAndItemsCovered.getValue();

            if (itemsCovered == 0) {
                redundantTests.add(testMethod);
            }
        }

        return redundantTests;
    }
}
