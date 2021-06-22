/*
 * Copyright 2020 Cisco Systems, Inc. and its affiliates
 * Licensed under the MIT License
 *
 * The MIT License
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
package org.jenkinsci.plugins.workflow.cps.global;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractBuild;
import hudson.model.Api;
import hudson.model.Run;
import java.io.Serializable;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import static hudson.Util.fixNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExportedBean(defaultVisibility = 999)
public class UserDefinedGlobalVariableAction implements RunAction2 {

    private transient Run run;
    public List<UserDefinedGlobalVariableData> userGlobalVars = new ArrayList<>();

    public UserDefinedGlobalVariableAction() {
    }

    public UserDefinedGlobalVariableAction(List<UserDefinedGlobalVariableData> globalVars) {
        for(UserDefinedGlobalVariableData v : globalVars) {
            userGlobalVars.add(v);
        }
    }

    public String getDisplayName() {
        return "User Defined Global Variables";
    }

    public String getIconFileName() {
        return "attribute.png";
    }

    public String getUrlName() {
        return "userglobalvars";
    }

    public void addUserDefinedGlobalVariable(UserDefinedGlobalVariable userVar) {
        userGlobalVars.add(new UserDefinedGlobalVariableData(userVar));
    }

    @Exported
    public List<Map<String,String>> apiUserGlobalVars() {
        ArrayList<Map<String,String>> apiData = new ArrayList<>();
        for(UserDefinedGlobalVariableData v : userGlobalVars) {
            Map<String,String> data = new HashMap<>();
            data.put("name", v.getName());
            data.put("library", v.getLibraryName());
            data.put("version", v.getLibraryVersion());
            apiData.add(data);
        }
        return apiData;
    }

    public List<UserDefinedGlobalVariableData> getUserGlobalVars() {
        return userGlobalVars;
    }

    @Override
    public String toString() {
        return super.toString()+"[userGlobalVars="+userGlobalVars+"]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UserDefinedGlobalVariableAction that = (UserDefinedGlobalVariableAction) o;

        return Objects.equals(userGlobalVars, that.userGlobalVars);
    }

    @Override
    public int hashCode() {
        return userGlobalVars.hashCode();
    }

    /* Package protected for easier testing */
    static final Logger LOGGER = Logger.getLogger(UserDefinedGlobalVariableAction.class.getName());

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    public Run getRun() {
        return run;
    }

    public Api getApi() { return new Api(this); }

    public static class UserDefinedGlobalVariableData {
        private String name;
        private String library;
        private String version;

        public UserDefinedGlobalVariableData(UserDefinedGlobalVariable userVar) {
            this.name = userVar.getName();
            this.library = userVar.getLibraryName();
            this.version = userVar.getLibraryVersion();
        }

        public String getName() { return this.name; }
        public String getLibraryName() { return this.library; }
        public String getLibraryVersion() { return this.version; }
    }
}
