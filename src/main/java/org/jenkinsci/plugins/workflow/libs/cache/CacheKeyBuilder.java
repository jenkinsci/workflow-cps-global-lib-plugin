package org.jenkinsci.plugins.workflow.libs.cache;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;

public class CacheKeyBuilder {

    protected static final String SEPARATOR = "@cache-storage-separator@";

    private String name;
    private String version;
    private String additionalKey;

    public CacheKeyBuilder name(String name) {
        this.name = name;
        return this;
    }

    public CacheKeyBuilder version(String version) {
        this.version = version;
        return this;
    }

    public CacheKeyBuilder additionalKey(String additionalKey) {
        this.additionalKey = additionalKey;
        return this;
    }

    public String build() {
        StringBuilder text = new StringBuilder();
        text.append(name);
        text.append(SEPARATOR);
        text.append(version);
        text.append(SEPARATOR);
        if (StringUtils.isNotEmpty(additionalKey)) {
            text.append(additionalKey);
        }
        return DigestUtils.sha1Hex(text.toString());
    }
}
