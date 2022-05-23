package org.jenkinsci.plugins.workflow.libs;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jenkins.util.SystemProperties;

@Extension public class LibraryCachingCleanup extends AsyncPeriodicWork {
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "non-final for script console access")
    public static int EXPIRE_AFTER_READ_DAYS =
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
            for (FilePath versionDir : library.listDirectories()) {
                if (!removeIfExpiredCacheDirectory(versionDir)) {
                    FilePath parent = versionDir.getParent();
                    if (parent != null) {
                        parent.deleteRecursive();
                    }
                    break;
                }
            }
        }
    }

    /**
     * Delete the specified cache directory if it is outdated.
     * @return true if specified directory is a cache directory, regardless of whether it was outdated. Used to detect
     * whether the cache was created before or after the fix for SECURITY-2586.
     */
    private boolean removeIfExpiredCacheDirectory(FilePath library) throws IOException, InterruptedException {
        final FilePath lastReadFile = new FilePath(library, LibraryCachingConfiguration.LAST_READ_FILE);
        if (lastReadFile.exists() && library.withSuffix("-name.txt").exists()) {
            if (System.currentTimeMillis() - lastReadFile.lastModified() > TimeUnit.DAYS.toMillis(EXPIRE_AFTER_READ_DAYS)) {
                FilePath parent = library.getParent();
                if (parent != null) {
                    parent.deleteRecursive();
                }
            }
            return true;
        }
        return false;
    }
}
