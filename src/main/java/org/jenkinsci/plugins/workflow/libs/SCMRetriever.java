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

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Items;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Uses legacy {@link SCM} to check out sources based on variable interpolation.
 */
public class SCMRetriever extends LibraryRetriever {

    private final SCM scm;

    @DataBoundConstructor public SCMRetriever(SCM scm) {
        this.scm = scm;
    }

    public SCM getScm() {
        return scm;
    }

    @Override public void retrieve(String name, String version, FilePath target, Run<?, ?> run, TaskListener listener) throws Exception {
        SCMSourceRetriever.doRetrieve(scm, target, run, listener);
    }

    @Override public FormValidation validateVersion(String name, String version) {
        if (!Items.XSTREAM2.toXML(scm).contains("${library." + name + ".version}")) {
            return FormValidation.warningWithMarkup("When using <b>" + getDescriptor().getDisplayName() + "</b>, you will need to include <code>${library." + Util.escape(name) + ".version}</code> in the SCM configuration somewhere.");
        }
        return FormValidation.okWithMarkup("Cannot validate default version with legacy SCM plugins via <b>" + getDescriptor().getDisplayName() + "</b>. Use " +
            Jenkins.getActiveInstance().getDescriptorByType(SCMSourceRetriever.DescriptorImpl.class).getDisplayName() + " if available.");
    }

    @Extension(ordinal=-100) public static class DescriptorImpl extends LibraryRetrieverDescriptor {
        
        @Override public String getDisplayName() {
            return "Legacy SCM";
        }

        public List<SCMDescriptor<?>> getSCMDescriptors() {
            List<SCMDescriptor<?>> descriptors = new ArrayList<>();
            for (SCMDescriptor<?> d : SCM.all()) {
                // TODO SCM._for cannot be used here since it requires an actual Job, where we want to check for applicability to Job.class
                // (the best we could do is to check whether SCM.checkout(Run, …) is overridden)
                if (d.clazz != NullSCM.class) {
                    descriptors.add(d);
                }
            }
            return descriptors;
        }
    }

    @Extension public static class Hider extends DescriptorVisibilityFilter {

        @SuppressWarnings("rawtypes")
        @Override public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof DescriptorImpl) {
                return !((DescriptorImpl) descriptor).getSCMDescriptors().isEmpty();
            } else {
                return true;
            }
        }

    }

}
