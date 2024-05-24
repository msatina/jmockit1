/*
 * Copyright (c) 2006 JMockit developers
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.dataItems;

import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import mockit.internal.state.TestRun;

import edu.umd.cs.findbugs.annotations.NonNull;

public final class InstanceFieldData extends FieldData {
    private static final long serialVersionUID = 6991762113575259754L;

    @NonNull
    private final transient Map<Integer, List<Integer>> testIdsToAssignments = new HashMap<>();

    void registerAssignment(@NonNull Object instance) {
        List<Integer> dataForRunningTest = getDataForRunningTest();
        Integer instanceId = System.identityHashCode(instance);

        if (!dataForRunningTest.contains(instanceId)) {
            dataForRunningTest.add(instanceId);
        }

        writeCount++;
    }

    void registerRead(@NonNull Object instance) {
        List<Integer> dataForRunningTest = getDataForRunningTest();
        Integer instanceId = System.identityHashCode(instance);

        dataForRunningTest.remove(instanceId);
        readCount++;
    }

    @NonNull
    private List<Integer> getDataForRunningTest() {
        int testId = TestRun.getTestId();
        List<Integer> fieldData = testIdsToAssignments.get(testId);

        if (fieldData == null) {
            fieldData = new LinkedList<>();
            testIdsToAssignments.put(testId, fieldData);
        }

        return fieldData;
    }

    @Override
    void markAsCoveredIfNoUnreadValuesAreLeft() {
        for (List<Integer> unreadInstances : testIdsToAssignments.values()) {
            if (unreadInstances.isEmpty()) {
                covered = true;
                break;
            }
        }
    }

    @NonNull
    public List<Integer> getOwnerInstancesWithUnreadAssignments() {
        if (isCovered()) {
            return emptyList();
        }

        Collection<List<Integer>> assignments = testIdsToAssignments.values();
        return assignments.iterator().next();
    }
}
