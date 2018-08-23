
package org.jenkinsci.plugins.workflow.libs;

import java.util.ArrayList;
import java.util.List;

public final class LibraryCachingConfiguration {
    final private Boolean enabled;
    final private int refreshTimeMinutes;
    final private List<String> excludedVersions;

    public LibraryCachingConfiguration() {
        this.enabled = false;
        this.refreshTimeMinutes = 0;
        this.excludedVersions = new ArrayList<String>();
    }

    public LibraryCachingConfiguration(Boolean enabled, int refreshTimeMinutes, List<String> excludedVersions) {
        this.enabled = enabled;
        this.refreshTimeMinutes = refreshTimeMinutes;
        this.excludedVersions = excludedVersions;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public int getRefreshTimeMinutes() {
        return refreshTimeMinutes;
    }

    public long getRefreshTimeMilliseconds() {
        return Long.valueOf(getRefreshTimeMinutes()) * 60000;
    }

    public Boolean isRefreshEnabled() {
        return refreshTimeMinutes > 0;
    }

    public Boolean isExcluded(String version) {
        return excludedVersions.contains(version);
    }

    @Override
    public String toString() {
        return "LibraryCachingConfiguration{enabled=" + enabled + ", refreshTimeMinutes=" + refreshTimeMinutes
                + ", excludedVersions=" + excludedVersions + '}';
    }
}
