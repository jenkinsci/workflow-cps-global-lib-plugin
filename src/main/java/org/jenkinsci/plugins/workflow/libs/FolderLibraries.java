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

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Like {@link GlobalLibraries} but scoped to a folder.
 */
public class FolderLibraries extends AbstractFolderProperty<AbstractFolder<?>> {

    private final List<LibraryConfiguration> libraries;

    @DataBoundConstructor public FolderLibraries(List<LibraryConfiguration> libraries) {
        this.libraries = libraries;
    }

    public List<LibraryConfiguration> getLibraries() {
        return libraries;
    }

    @Extension public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @Override public AbstractFolderProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            FolderLibraries prop = (FolderLibraries) super.newInstance(req, formData);
            return prop.libraries.isEmpty() ? null : prop;
        }

    }

    @Extension(ordinal=100) public static class ForJob extends LibraryResolver {

        @Override public boolean isTrusted() {
            return false;
        }

        private Collection<LibraryConfiguration> forGroup(@CheckForNull ItemGroup<?> group, boolean checkPermission) {
            List<LibraryConfiguration> libraries = new ArrayList<>();
            for (ItemGroup<?> g = group; g instanceof AbstractFolder; g = ((AbstractFolder) g).getParent()) {
                AbstractFolder<?> f = (AbstractFolder<?>) g;
                if (!checkPermission || f.hasPermission(Item.CONFIGURE)) {
                    FolderLibraries prop = f.getProperties().get(FolderLibraries.class);
                    if (prop != null) {
                        String source = FolderLibraries.ForJob.class.getName() + " " + f.getFullName();
                        for (LibraryConfiguration library : prop.getLibraries()) {
                            libraries.add(new ResolvedLibraryConfiguration(library, source));
                        }
                    }
                }
            }
            return libraries;
        }

        @Override public Collection<LibraryConfiguration> forJob(Job<?,?> job, Map<String,String> libraryVersions) {
            return forGroup(job.getParent(), false);
        }

        @Override public Collection<LibraryConfiguration> fromConfiguration(StaplerRequest request) {
            return forGroup(request.findAncestorObject(AbstractFolder.class), true);
        }

        @Override public Collection<LibraryConfiguration> suggestedConfigurations(ItemGroup<?> group) {
            return forGroup(group, false);
        }

    }

}
