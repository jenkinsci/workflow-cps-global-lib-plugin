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
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.impl.mock.MockSCMRevision;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

public class FailingSCMSource extends SCMSource {

    @Override
    protected SCMRevision retrieve(@NonNull final String thingName, @NonNull final TaskListener listener) throws IOException, InterruptedException {
        final SCMHead head = new SCMHead(thingName);
        return new MockSCMRevision(head, "not important");
    }

    @Override
    protected void retrieve(final SCMSourceCriteria criteria, @NonNull final SCMHeadObserver observer, final SCMHeadEvent<?> event, @NonNull final TaskListener listener) throws IOException, InterruptedException {
        throw new AbortException("Failing 'retrieve' on purpose!");
    }

    @NonNull
    @Override
    public SCM build(@NonNull final SCMHead head, final SCMRevision revision) {
        return new SCM() {
            @Override
            public ChangeLogParser createChangeLogParser() {
                return null;
            }

            @Override
            public void checkout(@Nonnull final Run<?, ?> build, @Nonnull final Launcher launcher, @Nonnull final FilePath workspace, @Nonnull final TaskListener listener, @CheckForNull final File changelogFile, @CheckForNull final SCMRevisionState baseline) throws IOException, InterruptedException {
                throw new AbortException("Failing 'checkout' on purpose!");
            }
        };
    }

}
