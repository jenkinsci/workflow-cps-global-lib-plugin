package org.jenkinsci.plugins.workflow.cps.global;

import com.google.common.collect.Lists;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;
import org.jenkinsci.plugins.workflow.cps.global.loader.GitLoader;
import org.jenkinsci.plugins.workflow.cps.global.loader.Loader;
import org.jenkinsci.plugins.workflow.cps.global.loader.Parser;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds the global shared library space into classpath of the trusted
 * {@link GroovyClassLoader}.
 *
 * @author Kohsuke Kawaguchi
 * @see CpsFlowExecution#getTrustedShell()
 */
@Extension
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {

    @Inject
    WorkflowLibRepository repo;

    private static final List<? extends Parser> PARSERS = Lists.newArrayList(new GitLoader());

    @Override
    public GroovyShellDecorator forTrusted() {
        return new GroovyShellDecorator() {
            @Override
            public void configureShell(CpsFlowExecution context, GroovyShell shell) {
                final List<Loader> loaders = new ArrayList<>();
                for (Parser parser : PARSERS) {
                    if(null != context) {
                        loaders.addAll(parser.parse(context.getScript()));
                    }
                }
                for (Loader loader : loaders) {
                    URL targetUrl = null;
                    try {
                        targetUrl = loader.load(context != null ? context.getStorageDir() : null);
                    } catch (IOException e) {
                        Logger.getLogger(GroovyShellDecoratorImpl.class.getName()).log(Level.SEVERE, null, e);
                    }
                    if (targetUrl != null) {
                        Logger.getLogger(GroovyShellDecoratorImpl.class.getName()).log(Level.INFO, "add path to classLoader " + targetUrl);
                        shell.getClassLoader().addURL(targetUrl);
                    }
                }
                try {
                    shell.getClassLoader().addURL(new File(repo.workspace, "src").toURI().toURL());
                    shell.getClassLoader().addURL(new File(repo.workspace, UserDefinedGlobalVariable.PREFIX).toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
            }
        };
    }
}
