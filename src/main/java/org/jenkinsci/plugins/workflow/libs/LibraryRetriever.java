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

import hudson.AbortException;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.libs.cache.CacheKeyBuilder;
import org.jenkinsci.plugins.workflow.libs.cache.CacheStorage;
import org.jenkinsci.plugins.workflow.libs.cache.CacheStorageResult;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A way in which a library can be physically obtained for use in a build.
 */
public abstract class LibraryRetriever extends AbstractDescribableImpl<LibraryRetriever> implements ExtensionPoint {

    private SCMCacheConfiguration cache;

    public LibraryRetriever() {
        // do nothing
    }

    @DataBoundSetter public void setCache(SCMCacheConfiguration cache) {
        this.cache = cache;
    }

    public SCMCacheConfiguration getCache() {
        return cache;
    }

    /**
     * Obtains library sources.
     * @param name the {@link LibraryConfiguration#getName}
     * @param version the version of the library, such as from {@link LibraryConfiguration#getDefaultVersion} or an override
     * @param changelog whether to include changesets in the library in jobs using it from {@link LibraryConfiguration#getIncludeInChangesets}
     * @param target a directory in which to check out sources; should create {@code src/**}{@code /*.groovy} and/or {@code vars/*.groovy}, and optionally also {@code resources/}
     * @param run a build which will use the library
     * @param listener a way to report progress
     * @throws Exception if there is any problem (use {@link AbortException} for user errors)
     */
    public void retrieve(@Nonnull String name, @Nonnull String version, boolean changelog, @Nonnull FilePath target, @Nonnull Run<?, ?> run, @Nonnull TaskListener listener) throws Exception {
        if (isCacheEnabled(version)) {
            listener.getLogger().println("Caching is enabled");

            String key = new CacheKeyBuilder().name(name).version(version).additionalKey(cache.getAdditionalKey()).build();
            long ttl = cache.getTtl() * 1000;

            listener.getLogger().println("Cache ID: " + key);

            CacheStorage storage = CacheStorage.get();

            CacheStorageResult<Boolean> result = storage.tryRead(key, entry -> {
                if (entry.isUpToDate(ttl)) {
                    listener.getLogger().println("Loading the library from cache...");
                    entry.copyTo(target);
                    return true;
                }
                listener.getLogger().println("Cached version of the library is outdated/missing");
                return false;
            });
            if (result.isExecuted() && result.getValue()) {
                return;
            }

            result = storage.tryWrite(key, entry -> {
                if (entry.isUpToDate(ttl)) {
                    // cache is up-to-date so free write lock ASAP and use read lock to access data
                    return false;
                }
                listener.getLogger().println("Retrieving the library...");
                doRetrieve(name, version, changelog, target, run, listener);
                listener.getLogger().println("Saving retrieved files in cache...");
                entry.copyFrom(target);
                return true;
            });
            if (result.isExecuted() && result.getValue()) {
                return;
            }

            listener.getLogger().println("Other executor is downloading a fresh version of the library. Waiting...");
            result = storage.tryRead(key, entry -> {
                if (entry.isUpToDate(ttl)) {
                    listener.getLogger().println("Loading the library from cache...");
                    entry.copyTo(target);
                    return true;
                }
                listener.getLogger().println("Cached version of the library is still outdated...");
                return false;
            });
            if (result.isExecuted() && result.getValue()) {
                return;
            }

            listener.getLogger().println("Fallback to non-cache mode");
        }
        doRetrieve(name, version, changelog, target, run, listener);
    }

    protected boolean isCacheEnabled(String version) {
        return cache != null && cache.getTtl() != 0 && !version.matches(cache.getExcludedVersions());
    }

    /**
     * Obtains library sources.
     * @param name the {@link LibraryConfiguration#getName}
     * @param version the version of the library, such as from {@link LibraryConfiguration#getDefaultVersion} or an override
     * @param changelog whether to include changesets in the library in jobs using it from {@link LibraryConfiguration#getIncludeInChangesets}
     * @param target a directory in which to check out sources; should create {@code src/**}{@code /*.groovy} and/or {@code vars/*.groovy}, and optionally also {@code resources/}
     * @param run a build which will use the library
     * @param listener a way to report progress
     * @throws Exception if there is any problem (use {@link AbortException} for user errors)
     */
    protected abstract void doRetrieve(@Nonnull String name, @Nonnull String version, boolean changelog, @Nonnull FilePath target, @Nonnull Run<?, ?> run, @Nonnull TaskListener listener) throws Exception;

    /**
     * Obtains library sources.
     * @param name the {@link LibraryConfiguration#getName}
     * @param version the version of the library, such as from {@link LibraryConfiguration#getDefaultVersion} or an override
     * @param target a directory in which to check out sources; should create {@code src/**}{@code /*.groovy} and/or {@code vars/*.groovy}, and optionally also {@code resources/}
     * @param run a build which will use the library
     * @param listener a way to report progress
     * @throws Exception if there is any problem (use {@link AbortException} for user errors)
     * @deprecated use {@link #retrieve(String, String, boolean, FilePath, Run, TaskListener)} instead
     */
    @Deprecated public void retrieve(@Nonnull String name, @Nonnull String version, @Nonnull FilePath target, @Nonnull Run<?, ?> run, @Nonnull TaskListener listener) throws Exception {
        retrieve(name, version, true, target, run, listener);
    }

    @Deprecated public FormValidation validateVersion(@Nonnull String name, @Nonnull String version) {
        if (Util.isOverridden(LibraryRetriever.class, getClass(), "validateVersion", String.class, String.class, Item.class)) {
            return validateVersion(name, version, null);
        }
        return FormValidation.ok();
    }

    /**
     * Offer to validate a proposed {@code version} for {@link #retrieve}.
     * @param name the proposed library name
     * @param version a proposed version
     * @param context optional context in which this runs
     * @return by default, OK
     */
    public FormValidation validateVersion(@Nonnull String name, @Nonnull String version, @CheckForNull Item context) {
        return validateVersion(name, version);
    }

    @Override public LibraryRetrieverDescriptor getDescriptor() {
        return (LibraryRetrieverDescriptor) super.getDescriptor();
    }

}
