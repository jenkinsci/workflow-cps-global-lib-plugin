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
import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyRuntimeException;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.AbstractWhitelist;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
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

        @Restricted(DoNotUse.class) // Jelly
        public Collection<LibraryRetrieverDescriptor> getRetrieverDescriptors() {
            return Jenkins.getActiveInstance().getDescriptorByType(LibraryConfiguration.DescriptorImpl.class).getRetrieverDescriptors();
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
            String srcUrl = new File(run.getRootDir(), "libs/" + name + "/src").toURI().toString(); // cf. LibraryAdder.retrieve
            return new LoadedClasses(name, trusted, "", null, srcUrl);
        }

    }

    public static final class LoadedClasses extends GroovyObjectSupport implements Serializable {

        private final @Nonnull String library;
        private final boolean trusted;
        /** package prefix, like {@code } or {@code some.pkg.} */
        private final @Nonnull String prefix;
        /** {@link Class#getName} minus package prefix */
        private final @CheckForNull String clazz;
        /** {@code file:/…/libs/NAME/src/} */
        private final @Nonnull String srcUrl;

        LoadedClasses(String library, boolean trusted, String prefix, String clazz, String srcUrl) {
            this.library = library;
            this.trusted = trusted;
            this.prefix = prefix;
            this.clazz = clazz;
            this.srcUrl = srcUrl;
        }

        @Override public Object getProperty(String property) {
            if (property.matches("^[A-Z].*")) {
                // looks like a class name component
                String fullClazz = clazz != null ? clazz + '$' + property : property;
                loadClass(prefix + fullClazz);
                // OK, class really exists, stash it and await methods
                return new LoadedClasses(library, trusted, prefix, fullClazz, srcUrl);
            } else if (clazz != null) {
                // Field access?
                try {
                    // not doing a Whitelist check since GroovyClassLoaderWhitelist would be allowing it anyway
                    return loadClass(prefix + clazz).getField(property).get(null);
                } catch (NoSuchFieldException | IllegalAccessException x) {
                    throw new GroovyRuntimeException(x);
                }
            } else {
                // Still selecting package components.
                return new LoadedClasses(library, trusted, prefix + property + '.', null, srcUrl);
            }
        }

        @Override public Object invokeMethod(String name, Object _args) {
            Class<?> c = loadClass(prefix + clazz);
            Object[] args = _args instanceof Object[] ? (Object[]) _args : new Object[] {_args}; // TODO why does Groovy not just pass an Object[] to begin with?!
            try {
                if (name.equals("new")) {
                    return ConstructorUtils.invokeConstructor(c, args);
                } else {
                    return MethodUtils.invokeStaticMethod(c, name, args);
                }
            } catch (InvocationTargetException x) {
                throw new GroovyRuntimeException(x.getCause());
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException x) {
                throw new GroovyRuntimeException(x);
            }
        }

        // TODO putProperty for static field set

        private Class<?> loadClass(String name) {
            CpsFlowExecution exec = CpsThread.current().getExecution();
            GroovyClassLoader loader = (trusted ? exec.getTrustedShell() : exec.getShell()).getClassLoader();
            try {
                Class<?> c = loader.loadClass(name);
                ClassLoader definingLoader = c.getClassLoader();
                if (definingLoader instanceof GroovyClassLoader.InnerLoader) {
                    definingLoader = definingLoader.getParent();
                }
                if (definingLoader != loader) {
                    throw new IllegalAccessException("cannot access " + c + " via library handle: " + definingLoader + " is not " + loader);
                }
                // Note that this goes through GroovyCodeSource.<init>(File, String), which unlike (say) URLClassLoader set the “location” to the actual file, *not* the root.
                CodeSource codeSource = c.getProtectionDomain().getCodeSource();
                String actual = codeSource != null ? codeSource.getLocation().toString() : "<unknown>";
                if (!actual.startsWith(srcUrl)) {
                    throw new IllegalAccessException(name + " was defined in " + actual + " which was not inside " + srcUrl);
                }
                if (!Modifier.isPublic(c.getModifiers())) { // unlikely since Groovy makes classes implicitly public
                    throw new IllegalAccessException(c + " is not public");
                }
                return c;
            } catch (ClassNotFoundException | IllegalAccessException x) {
                throw new GroovyRuntimeException(x);
            }
        }

    }

    @Extension public static class LoadedClassesWhitelist extends AbstractWhitelist { // TODO JENKINS-24982 @Whitelisted does not suffice
        @Override public boolean permitsMethod(Method method, Object receiver, Object[] args) {
            String name = method.getName();
            return receiver instanceof LoadedClasses && method.getDeclaringClass() == GroovyObject.class && (name.equals("getProperty") || name.equals("invokeMethod"));
        }
    }

}
