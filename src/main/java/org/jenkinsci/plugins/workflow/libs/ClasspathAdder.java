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

import groovy.lang.GroovyShell;
import hudson.ExtensionPoint;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;

/**
 * Allows libraries to be mapped to actual classpath additions.
 */
public abstract class ClasspathAdder implements ExtensionPoint {

    public static final class Addition {

        /** URL to add to the classpath. */
        @Nonnull public final URL url;

        /** Whether this should be loaded in the trusted class loader, or untrusted alongside the main script. */
        public final boolean trusted;

        public Addition(@Nonnull URL url, boolean trusted) {
            this.url = url;
            this.trusted = trusted;
        }

        void addTo(@Nonnull CpsFlowExecution execution) {
            GroovyShell shell = trusted ? execution.getTrustedShell() : execution.getShell();
            shell.getClassLoader().addURL(url);
        }

    }

    /**
     * @see #add(String, CpsFlowExecution, List, HashMap)
     */
    public @Nonnull List<Addition> add(@Nonnull CpsFlowExecution execution, @Nonnull List<String> libraries, @Nonnull HashMap<String, Boolean> changelogs) throws Exception {
        return add( null, execution, libraries, changelogs );
    }
    
    /**
     * May add to the classpath.
     * @param scope identifier for the source file that the addition came from
     * @param execution a running build (possibly newly started, possibly resumed)
     * @param libraries aggregated entries from all encountered {@link Library#value} (will be empty if {@link Library} is never used at all); an implementation should remove entries it “claims”
     * @return a possibly empty list of additions
     * @throws Exception for whatever reason (will fail compilation)
     */
    public abstract @Nonnull List<Addition> add(@Nullable String scope, @Nonnull CpsFlowExecution execution, @Nonnull List<String> libraries, @Nonnull HashMap<String, Boolean> changelogs) throws Exception;

}
