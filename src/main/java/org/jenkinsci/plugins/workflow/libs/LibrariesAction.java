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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;

/**
 * A run action recording libraries used in a given build.
 */
class LibrariesAction extends InvisibleAction {

    private final List<LibraryRecord> libraries;
    
    private final String scope;
    
    LibrariesAction(List<LibraryRecord> libraries) {
        this(null, libraries);
    }

    LibrariesAction(String scope, List<LibraryRecord> libraries) {
        this.scope = scope;
        this.libraries = libraries;
    }

    /**
     * A list of libraries in use.
     */
    public List<LibraryRecord> getLibraries() {
        return libraries;
    }
    
    /**
     * @return An identifier of the source file that these library definitions are for
     */
    public String getScope() {
        return scope;
    }

    @Extension public static class LibraryEnvironment extends EnvironmentContributor {

        @SuppressWarnings("rawtypes")
        @Override public void buildEnvironmentFor(Run r, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
            LibrariesAction action = r.getAction(LibrariesAction.class);
            if (action != null) {
                for (LibraryRecord library : action.libraries) {
                    envs.put("library." + library.name + ".version", library.version);
                }
            }
        }

    }

}
