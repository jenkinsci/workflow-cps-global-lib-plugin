package org.jenkinsci.plugins.workflow.libs;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jenkins.util.SystemProperties;

@Extension public class LibraryCachingCleanup extends AsyncPeriodicWork {
    public static /* non-final for script console */ int EXPIRE_AFTER_READ_DAYS =
            SystemProperties.getInteger(LibraryCachingCleanup.class.getName() + ".EXPIRE_AFTER_READ_DAYS", 7);

    public LibraryCachingCleanup() {
        super("LibraryCachingCleanup");
    }

    @Override public long getRecurrencePeriod() {
        return TimeUnit.HOURS.toMillis(12);
    }

    @Override protected void execute(TaskListener listener) throws IOException, InterruptedException {
        FilePath globalCacheDir = LibraryCachingConfiguration.getGlobalLibrariesCacheDir();
        for (FilePath library : globalCacheDir.list()) {
            if (!removeIfOldCacheDirectory(library, TimeUnit.DAYS.toMillis(EXPIRE_AFTER_READ_DAYS))) {
                // Prior to SECURITY-2586 security fixes, library caches had a two-level directory structure.
                // These caches will never be used again, so we delete any that we find.
                for (FilePath version: library.list()) {
                    removeIfOldCacheDirectory(version, 0);
                }
            }
        }
    }

    /**
     * Delete cache directories for the given library if they are outdated.
     * @return true if specified directory is a cache directory, regardless of whether it was outdated. Used to detect
     * whether the cache was created before or after the fix for SECURITY-2586.
     */
    private boolean removeIfOldCacheDirectory(FilePath library, long maxDurationMillis) throws IOException, InterruptedException {
        final FilePath lastReadFile = new FilePath(library, LibraryCachingConfiguration.LAST_READ_FILE);
        if (lastReadFile.exists()) {
            if ((System.currentTimeMillis() - lastReadFile.lastModified()) > maxDurationMillis) {
                library.deleteRecursive();
                library.withSuffix("-name.txt").delete(); // Harmless if this is a pre-SECURITY-2586 cache directory.
            }
            return true;
        }
        return false;
    }
}
