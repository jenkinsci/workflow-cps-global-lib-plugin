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

import com.google.common.collect.Lists;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyRuntimeException;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.net.URL;
import java.util.Collections;
import javax.inject.Inject;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Dynamically injects a library into the running build.
 */
public class LibraryStep extends AbstractStepImpl {

    private final String identifier;
    private LibraryRetriever retriever;

    @DataBoundConstructor public LibraryStep(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public LibraryRetriever getRetriever() {
        return retriever;
    }

    @DataBoundSetter public void setRetriever(LibraryRetriever retriever) {
        this.retriever = retriever;
    }

    // TODO config.jelly

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "library";
        }

        @Override public String getDisplayName() {
            return "Load a shared library on the fly";
        }

    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<LoadedClasses> {

        private static final long serialVersionUID = 1L;

        @Inject private transient LibraryStep step;
        @StepContextParameter private transient Run<?,?> run;
        @StepContextParameter private transient TaskListener listener;

        @Override protected LoadedClasses run() throws Exception {
            String[] parsed = LibraryAdder.parse(step.identifier);
            String name = parsed[0], version = parsed[1];
            boolean trusted = false;
            LibraryRetriever retriever = step.getRetriever();
            if (retriever == null) {
                for (LibraryResolver resolver : ExtensionList.lookup(LibraryResolver.class)) {
                    for (LibraryConfiguration cfg : resolver.forJob(run.getParent(), Collections.singletonMap(name, version))) {
                        if (cfg.getName().equals(name)) {
                            retriever = cfg.getRetriever();
                            trusted = resolver.isTrusted();
                            version = cfg.defaultedVersion(version);
                            break;
                        }
                    }
                }
                if (retriever == null) {
                    throw new AbortException("No library named " + name + " found");
                }
            } else if (version == null) {
                throw new AbortException("Must specify a version for library " + name);
            }
            LibraryRecord record = new LibraryRecord(name, version, trusted);
            LibrariesAction action = run.getAction(LibrariesAction.class);
            if (action == null) {
                action = new LibrariesAction(Lists.newArrayList(record));
                run.addAction(action);
            } else {
                action.getLibraries().add(record);
            }
            listener.getLogger().println("Loading library " + record.name + "@" + record.version);
            CpsFlowExecution exec = (CpsFlowExecution) getContext().get(FlowExecution.class);
            GroovyClassLoader loader = (trusted ? exec.getTrustedShell() : exec.getShell()).getClassLoader();
            for (URL u : LibraryAdder.retrieve(record.name, record.version, retriever, record.trusted, listener, run, (CpsFlowExecution) getContext().get(FlowExecution.class), record.variables)) {
                loader.addURL(u);
            }
            run.save(); // persist changes to LibrariesAction.libraries*.variables
            return new LoadedClasses(name, trusted);
        }

    }

    public static class LoadedClasses extends GroovyObjectSupport implements Serializable {

        private final String library;
        private final boolean trusted;

        LoadedClasses(String library, boolean trusted) {
            this.library = library;
            this.trusted = trusted;
        }

        @Override public Object getProperty(String property) {
            CpsFlowExecution exec = CpsThread.current().getExecution();
            GroovyClassLoader loader = (trusted ? exec.getTrustedShell() : exec.getShell()).getClassLoader();
            try {
                // TODO verify that the specified class is in fact in the named library
                // TODO allow you to select package components piecemeal, like library('x').com.yoyodyne.jenkins.Utils.method()
                return loader.loadClass(property);
            } catch (ClassNotFoundException x) {
                throw new GroovyRuntimeException(x);
            }
        }

    }


}
