package org.jenkinsci.plugins.workflow.cps.global;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

import jakarta.inject.Inject;
import java.io.File;
import java.net.MalformedURLException;

/**
 * Adds the global shared library space into classpath of the trusted {@link GroovyClassLoader}.
 *
 * @author Kohsuke Kawaguchi
 * @see CpsFlowExecution#getTrustedShell()
 */
@Extension
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {
    @Inject
    WorkflowLibRepository repo;

    @Override
    public GroovyShellDecorator forTrusted() {
        return new GroovyShellDecorator() {
            @Override
            public void configureShell(CpsFlowExecution context, GroovyShell shell) {
                try {
                    shell.getClassLoader().addURL(repo.workspace.resolve("src").toUri().toURL());
                    shell.getClassLoader().addURL(repo.workspace.resolve(UserDefinedGlobalVariableList.PREFIX).toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
            }
        };
    }
}
