/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.libs;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.libs.cache.CacheKeyBuilder;
import org.jenkinsci.plugins.workflow.libs.cache.CacheStorage;
import org.jenkinsci.plugins.workflow.libs.cache.CacheStorageResult;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class SCMCacheConfiguration extends AbstractDescribableImpl<SCMCacheConfiguration> {

    private final int ttl;
    private String additionalKey;
    private String excludedVersions;

    @DataBoundConstructor public SCMCacheConfiguration(int ttl) {
        this.ttl = ttl;
    }

    public int getTtl() {
        return ttl;
    }

    public String getAdditionalKey() {
        return StringUtils.defaultString(additionalKey);
    }

    @DataBoundSetter public void setAdditionalKey(String additionalKey) {
        this.additionalKey = additionalKey;
    }

    public String getExcludedVersions() {
        return StringUtils.defaultString(excludedVersions);
    }

    @DataBoundSetter public void setExcludedVersions(String excludedVersions) {
        this.excludedVersions = excludedVersions;
    }

    @Extension public static class DescriptorImpl extends Descriptor<SCMCacheConfiguration> {

        public FormValidation doDeleteCache(@QueryParameter String name, @QueryParameter String defaultVersion, @QueryParameter String additionalKey) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            CacheStorage cacheStorage = CacheStorage.get();
            String key = new CacheKeyBuilder().name(name).version(defaultVersion).additionalKey(additionalKey).build();

            CacheStorageResult<Void> result;
            try {
                result = cacheStorage.tryWrite(key, entry -> {
                    entry.delete();
                    return null;
                });
            } catch (Exception e) {
                return FormValidation.error(e, "Couldn't delete the cache (id: " + key + ").");
            }

            if (result.isExecuted()) {
                return FormValidation.ok("The cache (id: " + key + ") has been sucessfully deleted.");
            }
            return FormValidation.ok("The cache (id: " + key + ") is in use and couldn't be deleted.");
        }

        public FormValidation doForceDeleteCache(@QueryParameter String name, @QueryParameter String defaultVersion, @QueryParameter String additionalKey) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            CacheStorage cacheStorage = CacheStorage.get();
            String key = new CacheKeyBuilder().name(name).version(defaultVersion).additionalKey(additionalKey).build();

            try {
                cacheStorage.forceDelete(key);
            } catch (Exception e) {
                return FormValidation.error(e, "Couldn't delete the cache (id: " + key + ").");
            }
            return FormValidation.ok("The cache (id: " + key + ") has been sucessfully deleted.");
        }
    }
}
