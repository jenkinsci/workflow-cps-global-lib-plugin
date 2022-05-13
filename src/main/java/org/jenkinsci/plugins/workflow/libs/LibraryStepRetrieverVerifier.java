package org.jenkinsci.plugins.workflow.libs;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;

@Restricted(NoExternalUse.class)
public interface LibraryStepRetrieverVerifier extends ExtensionPoint {

    void verify(Run<?, ?> run, TaskListener listener, SCM scm, String name) throws IOException, InterruptedException;

    static ExtensionList<LibraryStepRetrieverVerifier> all() {
        return ExtensionList.lookup(LibraryStepRetrieverVerifier.class);
    }
}
