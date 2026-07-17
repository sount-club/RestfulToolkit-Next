package com.sount.restful.search.application;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EndpointSearchServiceTest {

    @Test
    public void updateGuardRunsOnlyLatestGeneration() {
        EndpointSearchService.UpdateGuard guard = new EndpointSearchService.UpdateGuard();
        long first = guard.nextGeneration();
        long second = guard.nextGeneration();
        AtomicBoolean firstRan = new AtomicBoolean(false);
        AtomicBoolean secondRan = new AtomicBoolean(false);

        EndpointSearchService.runIfLatest(guard, first, () -> firstRan.set(true));
        EndpointSearchService.runIfLatest(guard, second, () -> secondRan.set(true));

        assertFalse(firstRan.get());
        assertTrue(secondRan.get());
    }
}
