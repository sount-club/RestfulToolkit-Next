package com.sount.restful.search;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchControllerTest {

    @Test
    public void updateGuardRunsOnlyLatestGeneration() {
        SearchController.UpdateGuard guard = new SearchController.UpdateGuard();
        long first = guard.nextGeneration();
        long second = guard.nextGeneration();
        AtomicBoolean firstRan = new AtomicBoolean(false);
        AtomicBoolean secondRan = new AtomicBoolean(false);

        SearchController.runIfLatest(guard, first, () -> firstRan.set(true));
        SearchController.runIfLatest(guard, second, () -> secondRan.set(true));

        assertFalse(firstRan.get());
        assertTrue(secondRan.get());
    }
}
