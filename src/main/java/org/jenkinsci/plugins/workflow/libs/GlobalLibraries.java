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
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Manages libraries available to any job in the system.
 */
@Extension public class GlobalLibraries extends GlobalConfiguration {

    public static @Nonnull GlobalLibraries get() {
        GlobalLibraries instance = GlobalConfiguration.all().get(GlobalLibraries.class);
        if (instance == null) { // TODO would be useful to have an ExtensionList.getOrFail
            throw new IllegalStateException();
        }
        return instance;
    }

    private List<LibraryConfiguration> libraries = new ArrayList<>();

    public GlobalLibraries() {
        load();
    }

    public List<LibraryConfiguration> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<LibraryConfiguration> libraries) {
        this.libraries = libraries;
        save();
    }

    @Override public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        if (Jenkins.getActiveInstance().hasPermission(Jenkins.RUN_SCRIPTS)) {
            setLibraries(Collections.<LibraryConfiguration>emptyList()); // allow last library to be deleted
            // TODO 2.19+ super.configure(req, json)
            req.bindJSON(this, json);
            return true;
        } else {
            // For anyone with ADMINISTER but not RUN_SCRIPTS, we do not display the configuration section and we do not allow it to be modified.
            return true;
        }
    }

    @Extension(ordinal=0) public static class ForJob extends LibraryResolver {

        @Override public boolean isTrusted() {
            return true;
        }

        @Override public Collection<LibraryConfiguration> forJob(Job<?,?> job, Map<String,String> libraryVersions) {
            return GlobalLibraries.get().getLibraries();
        }

        @Override public Collection<LibraryConfiguration> fromConfiguration(StaplerRequest request) {
            if (Jenkins.getInstance().hasPermission(Jenkins.RUN_SCRIPTS)) {
                return GlobalLibraries.get().getLibraries();
            }
            return Collections.emptySet();
        }

        @Override public Collection<LibraryConfiguration> suggestedConfigurations(ItemGroup<?> group) {
            return GlobalLibraries.get().getLibraries();
        }

    }

}
