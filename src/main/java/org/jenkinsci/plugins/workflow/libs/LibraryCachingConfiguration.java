
package org.jenkinsci.plugins.workflow.libs;

import java.util.List;

public final class LibraryCachingConfiguration {
    private Boolean enabled;
    private int refreshTimeMinutes;
    private List<String> excludedVersions;

    public LibraryCachingConfiguration() {
        this.enabled = false;
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
        return  refreshTimeMinutes;
    }

    public Boolean isRefreshEnabled() {
        return refreshTimeMinutes > 0;
    }

    public Boolean isExcluded(String version) {
        return excludedVersions.contains(version);
    }
}
