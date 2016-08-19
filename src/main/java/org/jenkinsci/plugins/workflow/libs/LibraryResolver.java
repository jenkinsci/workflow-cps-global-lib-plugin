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

import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Job;
import java.util.Collection;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Allows a provider of libraries to indicate which libraries should be visible to a given job.
 */
public abstract class LibraryResolver implements ExtensionPoint {

    /**
     * Whether these libraries should be run outside the sandbox.
     */
    public abstract boolean isTrusted();

    /**
     * Check for libraries visible to a given job.
     * <p>An implementation may ignore the {@code libraryVersions} parameter
     * and simply list configured libraries visible to the job;
     * the caller will select which libraries to actually load,
     * taking into account {@link LibraryConfiguration#isImplicit}.
     * Or it may dynamically generate library configurations
     * by matching library names against some predefined pattern.
     * @param job a job
     * @param libraryVersions libraries explicitly requested in the job, as a map from {@link LibraryConfiguration#getName} to version or null; may be empty
     * @return a possibly empty collection of associated libraries
     */
    public abstract @Nonnull Collection<LibraryConfiguration> forJob(@Nonnull Job<?,?> job, @Nonnull Map<String,String> libraryVersions);

    /**
     * Looks up a known SCM used as part of some {@link LibraryConfiguration} previously configured by this resolver.
     * Similar to {@link SCMSourceOwner#getSCMSource} but does not require implementation of {@link Item} and may be contextualized to a {@link StaplerRequest}.
     * @param sourceId from {@link SCMSource#getId}
     * @param request a web request
     * @return a matching source, or null if unknown (null by default)
     */
    public @CheckForNull SCMSource getSCMSource(@Nonnull String sourceId, @Nonnull StaplerRequest request) {
        return null;
    }

}
