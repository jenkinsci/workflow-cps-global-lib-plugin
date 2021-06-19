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
import hudson.Extension;
import hudson.Util;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.inject.Inject;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Step to load a resource from a library.
 */
public class ResourceStep extends AbstractStepImpl {

    private final String resource;
    private String libraryName;
    private String encoding;

    @DataBoundConstructor public ResourceStep(String resource) {
        this.resource = resource;
    }

    public String getResource() {
        return resource;
    }

    public String getLibraryName() {
        return libraryName;
    }

    @DataBoundSetter
    public void setLibraryName(String libraryName) {
        this.libraryName = Util.fixEmptyAndTrim(libraryName);
    }

    public @CheckForNull String getEncoding() {
        return encoding;
    }

    /**
     * Set the encoding to be used when loading the resource. If the specified value is null or
     * whitespace-only, then the platform default encoding will be used. Binary resources can be
     * loaded as a Base64-encoded string by specifying {@code Base64} as the encoding.
     */
    @DataBoundSetter public void setEncoding(String encoding) {
        this.encoding = Util.fixEmptyAndTrim(encoding);
    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getDisplayName() {
            return "Load a resource file from a shared library";
        }

        @Override public String getFunctionName() {
            return "libraryResource";
        }

    }

    public static class Execution extends AbstractSynchronousStepExecution<String> {

        private static final long serialVersionUID = 1L;

        @Inject private transient ResourceStep step;

        @Override protected String run() throws Exception {
            String resource = step.resource;
            String libraryName = step.libraryName;
            Map<String,String> contents = LibraryAdder.findResources((CpsFlowExecution) getContext().get(FlowExecution.class), resource, step.encoding);
            if (libraryName != null && contents.containsKey(libraryName)) {
                return contents.get(libraryName);
            } else if (contents.isEmpty()) {
                throw new AbortException(Messages.ResourceStep_no_such_library_resource_could_be_found_(resource));
            } else if (libraryName != null && !contents.containsKey(libraryName)) {
               throw new AbortException(Messages.ResourceStep_no_matching_resource_found_in_library(resource, libraryName));
            }  else if (libraryName == null && contents.size() == 1) {
                return contents.values().iterator().next();
            } else {
                throw new AbortException(Messages.ResourceStep_library_resource_ambiguous_among_librari(resource, contents.keySet()));
            }
        }

    }

}
