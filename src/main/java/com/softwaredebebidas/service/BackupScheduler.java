package com.softwaredebebidas.service;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class BackupScheduler {
    private static final Logger LOGGER = Logger.getLogger(BackupScheduler.class.getName());

    private final BackupService backupService;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    public void start(int intervalMinutes, int keepCount) {
        if (scheduler != null && !scheduler.isShutdown()) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        });

        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                Path backup = backupService.createBackup();
                backupService.cleanOldBackups(keepCount);
                LOGGER.info("Backup created: " + backup);
            } catch (Exception e) {
                LOGGER.warning("Backup failed: " + e.getMessage());
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    public void stop() {
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdown();
    }
}
