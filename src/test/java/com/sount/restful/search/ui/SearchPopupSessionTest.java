package com.sount.restful.search.ui;

import com.sount.restful.search.application.EndpointIndex;
import com.sount.restful.search.application.SearchHistory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchPopupSessionTest extends BasePlatformTestCase {

    public void testDisposeRemovesIndexListenerAndPersistsStateOnlyOnce() {
        TrackingEndpointIndex index = new TrackingEndpointIndex(getProject());
        AtomicInteger disposeCount = new AtomicInteger();
        SearchPopupSession session = new SearchPopupSession(index, new SearchHistory(),
                new JBList<>(new DefaultListModel<>()), new JLabel(), disposeCount::incrementAndGet);

        session.attachIndexListener(() -> { });
        session.dispose();
        session.dispose();

        assertEquals(1, index.addListenerCount);
        assertEquals(1, index.removeListenerCount);
        assertEquals(1, disposeCount.get());
    }

    private final class TrackingEndpointIndex extends EndpointIndex {
        private int addListenerCount;
        private int removeListenerCount;

        private TrackingEndpointIndex(Project project) {
            super(project);
            Disposer.register(getTestRootDisposable(), this);
        }

        @Override
        public void addListener(Runnable listener) {
            addListenerCount++;
            super.addListener(listener);
        }

        @Override
        public void removeListener(Runnable listener) {
            removeListenerCount++;
            super.removeListener(listener);
        }
    }
}
