package org.jenkinsci.plugins.workflow.cps.global;

import org.codehaus.groovy.control.MultipleCompilationErrorsException;

import java.io.NotSerializableException;

/**
 * An exception that replaces Groovy's {@link MultipleCompilationErrorsException},
 * because that is not serializable (which would, under certain circumstances,
 * lead to the user being presented with a {@link NotSerializableException} instead
 * of a compilation error -- see JENKINS-40109).
 */
public class CompilationErrorException extends RuntimeException {
    public CompilationErrorException(MultipleCompilationErrorsException original) {
        super(original.toString());
        setStackTrace(original.getStackTrace());
    }
}
