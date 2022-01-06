package org.jenkinsci.plugins.workflow.libs;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import jenkins.scm.api.*;
import jenkins.scm.impl.mock.MockSCMRevision;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;

public class FailingSCMSourceDuringFetch extends FailingSCMSource {

    @Override
    protected SCMRevision retrieve(@NonNull final String thingName, @NonNull final TaskListener listener) throws IOException, InterruptedException {
        throw new AbortException("Failing 'fetch' on purpose!");
    }
}
