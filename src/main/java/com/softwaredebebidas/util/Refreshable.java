package com.softwaredebebidas.util;

/**
 * Interface for view controllers that need to refresh their data
 * when their cached view is shown again after navigating away.
 * <p>
 * MainPresenter calls {@link #refresh()} every time a cached view is displayed,
 * ensuring data is always current without re-initializing the entire controller.
 */
public interface Refreshable {

    /**
     * Refreshes the controller's data from the database or other source.
     * Called by MainPresenter when a cached view is shown.
     */
    void refresh();
}
