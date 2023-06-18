package integrationTests;

import org.junit.*;
import org.junit.runners.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class UnreachableStatementsTest extends CoverageTest {
    UnreachableStatements tested;

    @Test
    public void staticClassInitializerShouldHaveNoBranches() {
        assertLine(3, 1, 1, 5); // one execution for each test (the constructor), plus one for the static initializer
    }

    @Test
    public void nonBranchingMethodWithUnreachableLines() {
        try {
            tested.nonBranchingMethodWithUnreachableLines();
        } catch (AssertionError ignore) {
        }

        assertLines(12, 15, 2);
        assertLine(12, 1, 1, 1);
        assertLine(13, 1, 1, 1);
        assertLine(14, 1, 0, 0);
        assertLine(15, 1, 0, 0);
    }

    @Test
    public void branchingMethodWithUnreachableLines_avoidAssertion() {
        tested.branchingMethodWithUnreachableLines(0);

        assertLines(23, 29, 3);
        assertLine(23, 1, 1, 1);
        assertLine(24, 1, 0, 0);
        assertLine(25, 1, 0, 0);
        assertLine(28, 1, 1, 1);
        assertLine(29, 1, 1, 1);
    }

    @Test
    public void branchingMethodWithUnreachableLines_hitAndFailAssertion() {
        try {
            tested.branchingMethodWithUnreachableLines(1);
        } catch (AssertionError ignore) {
        }

        // Accounts for executions from previous test.
        assertLines(23, 29, 4);
        assertLine(23, 1, 1, 2);
        assertLine(24, 1, 1, 1);
        assertLine(25, 1, 0, 0);
    }
}
