package com.indeed.imhotep.scheduling;

import javax.annotation.Nullable;
import java.io.Closeable;

/**
 * Used only for testing
 */
class NoopTaskScheduler extends TaskScheduler {
    public NoopTaskScheduler() {
        super(0, 0, 0, null);
    }

    @Nullable
    @Override
    public synchronized Closeable lockSlot() {
        return null;
    }

    @Override
    public synchronized Closeable lockSlotFromAnotherScheduler(TaskScheduler schedulerToReleaseFrom) {
        return null;
    }
}