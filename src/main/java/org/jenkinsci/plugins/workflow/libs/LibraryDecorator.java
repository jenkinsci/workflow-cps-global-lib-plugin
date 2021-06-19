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
import hudson.ExtensionList;
import hudson.Functions;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
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
                final HashMap<String, Boolean> changelogs = new HashMap<>();
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
                                Expression changelog = annotationNode.getMember("changelog");
                                if (value == null) {
                                    source.getErrorCollector().addErrorAndContinue(Message.create("@Library was missing a value", source));
                                } else {
                                    processExpression(source, libraries, value, changelogs, changelog);
                                }
                            }
                        }
                    }

                    private void processExpression(SourceUnit source, List<String> libraries, Expression value) {
                      processExpression(source, libraries, value, null, null);
                    }

                    private void processExpression(SourceUnit source, List<String> libraries, Expression value, HashMap<String, Boolean> changelogs, Expression changelog) {
                        if (value instanceof ConstantExpression) { // one library
                            Object constantValue = ((ConstantExpression) value).getValue();
                            if (constantValue instanceof String) {
                                libraries.add((String) constantValue);
                                if (changelog != null) {
                                changelogs.put((String) constantValue, (Boolean) ((ConstantExpression) changelog).getValue());
                                }
                            } else {
                                source.getErrorCollector().addErrorAndContinue(Message.create("@Library value ‘" + constantValue + "’ was not a string", source));
                            }
                        } else if (value instanceof ListExpression) { // several libraries
                            for (Expression element : ((ListExpression) value).getExpressions()) {
                                processExpression(source, libraries, element);
                            }
                        } else {
                            source.getErrorCollector().addErrorAndContinue(Message.create("@Library value ‘" + value.getText() + "’ was not a constant; did you mean to use the ‘library’ step instead?", source));
                        }
                    }
                }.visitClass(classNode);
                try {
                    for (ClasspathAdder adder : ExtensionList.lookup(ClasspathAdder.class)) {
                        for (ClasspathAdder.Addition addition : adder.add(source.getName(), execution, libraries, changelogs)) {  
                            addition.addTo(execution);
                        }
                    }
                    if (!libraries.isEmpty()) {
                        throw new AbortException(Messages.LibraryDecorator_could_not_find_any_definition_of_librari(libraries));
                    }
                } catch (Exception x) {
                    // Merely throwing CompilationFailedException does not cause compilation to…fail. Gotta love Groovy!
                    source.getErrorCollector().addErrorAndContinue(Message.create("Loading libraries failed", source));
                    try {
                        TaskListener listener = execution.getOwner().getListener();
                        if (x instanceof AbortException) {
                            listener.error(x.getMessage());
                        } else {
                            Functions.printStackTrace(x, listener.getLogger());
                        }
                        throw new CompilationFailedException(Phases.CONVERSION, source);
                    } catch (IOException x2) {
                        Logger.getLogger(LibraryDecorator.class.getName()).log(Level.WARNING, null, x2);
                        throw new CompilationFailedException(Phases.CONVERSION, source, x); // reported at least in Jenkins 2
                    }
                }
            }
        });
    }

}
