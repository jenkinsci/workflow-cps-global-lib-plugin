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

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

public class WorkspaceRetriever extends LibraryRetriever {

    private final String sourcePath;

    @DataBoundConstructor
    public WorkspaceRetriever(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public void retrieve(@Nonnull String name, @Nonnull String version, @Nonnull FilePath target, @Nonnull Run<?, ?> run, @Nonnull TaskListener listener) throws Exception {
        Node node = Jenkins.getActiveInstance();

        Computer computer = node.toComputer();
        if (computer == null) {
            throw new IOException(node.getDisplayName() + " may be offline");
        }

        target.mkdirs();
        FilePath source = node.createPath(sourcePath);
        listener.getLogger().println("Copying files from:[" + source.getRemote() + "] to:[" + target.getRemote()+"].");
        source.copyRecursiveTo("src/**/*.groovy,vars/*.groovy,vars/*.txt,resources/", null, target);
    }

    @Symbol("workspaceRetriever")
    @Extension
    public static class DescriptorImpl extends LibraryRetrieverDescriptor {

        @Override public String getDisplayName() {
            return "Workspace Retriever";
        }
    }
}
