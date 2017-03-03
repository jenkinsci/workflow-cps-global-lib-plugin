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
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
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
     * <p>By returning a library with a matching {@code name},
     * this resolver “claims” that entry of {@code libraryVersions};
     * subsequent resolvers will not be offered that entry.
     * It is an error if no resolver claims a given entry.
     * Multiple resolvers might return a library of a given name if the libraries are implicit,
     * in which case only the first will be loaded.
     * @param job a job
     * @param libraryVersions libraries explicitly requested in the job, as a map from {@link LibraryConfiguration#getName} to version or null; may be empty
     * @return a possibly empty collection of associated libraries
     */
    public abstract @Nonnull Collection<LibraryConfiguration> forJob(@Nonnull Job<?,?> job, @Nonnull Map<String,String> libraryVersions);

    /**
     * A list of libraries that may have already been configured in this context.
     * @param request a web request
     * @return known libraries, if any (empty by default)
     */
    public @Nonnull Collection<LibraryConfiguration> fromConfiguration(@Nonnull StaplerRequest request) {
        return Collections.emptySet();
    }

    /**
     * A list of libraries that might be visible in a given location.
     * Typically would be the same as {@link #forJob} applied to {@link Job#getParent}.
     * If a resolver can dynamically generate library configurations, it can simply return one or more examples here.
     * @param group Jenkins root or some folder
     * @return any suggested libraries (empty by default)
     */
    public @Nonnull Collection<LibraryConfiguration> suggestedConfigurations(@Nonnull ItemGroup<?> group) {
        return Collections.emptySet();
    }

}
