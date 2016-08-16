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
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.messages.Message;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

/**
 * Adds an import for {@link Library}, checks for it being used, and actually loads the library.
 */
@Extension public class LibraryDecorator extends GroovyShellDecorator {

    @Override public void customizeImports(CpsFlowExecution execution, ImportCustomizer ic) {
        ic.addImports(Library.class.getName());
    }

    @Override public void configureCompiler(final CpsFlowExecution execution, CompilerConfiguration cc) {
        if (execution == null) {
            // TODO cannot inject libraries during form validation.
            // Adder could have a method to look up libraries from the last build,
            // but the current GroovyShellDecorator API does not allow us to even detect the Job!
            return;
        }
        cc.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.CONVERSION) {
            @Override public void call(final SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
                final List<String> libraries = new ArrayList<>();
                new ClassCodeVisitorSupport() {
                    @Override protected SourceUnit getSourceUnit() {
                        return source;
                    }
                    @Override public void visitAnnotations(AnnotatedNode node) {
                        super.visitAnnotations(node);
                        for (AnnotationNode annotationNode : node.getAnnotations()) {
                            String name = annotationNode.getClassNode().getName();
                            if (name.equals(Library.class.getCanonicalName()) ||
                                    // In the CONVERSION phase we will not have resolved the implicit import yet.
                                    name.equals(Library.class.getSimpleName())) {
                                Expression value = annotationNode.getMember("value");
                                if (value instanceof ConstantExpression) { // one library
                                    libraries.add((String) ((ConstantExpression) value).getValue());
                                } else { // several libraries
                                    for (Expression element : ((ListExpression) value).getExpressions()) {
                                        libraries.add((String) ((ConstantExpression) element).getValue());
                                    }
                                }
                            }
                        }
                    }
                }.visitClass(classNode);
                for (Adder adder : ExtensionList.lookup(Adder.class)) {
                    try {
                        for (Adder.Addition addition : adder.add(execution, libraries)) {
                            GroovyShell shell = addition.trusted ? execution.getTrustedShell() : execution.getShell();
                            shell.getClassLoader().addURL(addition.url);
                        }
                    } catch (Exception x) {
                        // Merely throwing CompilationFailedException does not cause compilation to…fail. Gotta love Groovy!
                        source.getErrorCollector().addErrorAndContinue(Message.create("Loading libraries failed", source));
                        try {
                            TaskListener listener = execution.getOwner().getListener();
                            if (x instanceof AbortException) {
                                listener.error(x.getMessage());
                            } else {
                                x.printStackTrace(listener.getLogger());
                            }
                            throw new CompilationFailedException(Phases.CONVERSION, source);
                        } catch (IOException x2) {
                            Logger.getLogger(LibraryDecorator.class.getName()).log(Level.WARNING, null, x2);
                            throw new CompilationFailedException(Phases.CONVERSION, source, x); // reported at least in Jenkins 2
                        }
                    }
                }
            }
        });
    }

    /**
     * Allows libraries to be mapped to actual classpath additions.
     */
    public interface Adder extends ExtensionPoint {
        class Addition {
            /** URL to add to the classpath. */
            public final @Nonnull URL url;
            /** Whether this should be loaded in the trusted class loader, or untrusted alongside the main script. */
            public final boolean trusted;
            public Addition(@Nonnull URL url, boolean trusted) {
                this.url = url;
                this.trusted = trusted;
            }
        }
        /**
         * May add to the classpath.
         * @param execution a running build
         * @param libraries aggregated entries from all encountered {@link Library#value} (will be empty if {@link Library} is never used at all)
         * @return a possibly empty list of additions
         * @throws Exception for whatever reason (will fail compilation)
         */
        @Nonnull List<Addition> add(@Nonnull CpsFlowExecution execution, @Nonnull List<String> libraries) throws Exception;
    }

}
