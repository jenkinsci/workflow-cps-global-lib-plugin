package org.jenkinsci.plugins.workflow.libs;

import hudson.AbortException;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import jenkins.branch.Branch;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;

import java.io.IOException;

@OptionalExtension(requirePlugins={"workflow-multibranch"})
public class MultibranchScmRevisionVerifier implements SCMSourceRetrieverVerifier {

    /**
     * Abort library retrieval if the specified build is from a Multibranch Pipeline configured to build the library's SCM and the revision being built is untrusted.
     * Comparable to the defenses against untrusted users in {@code SCMBinder}, but here we care about the library rather than the Jenkinsfile.
     * @throws AbortException if the specified build is from a Multibranch Pipeline configured to build the library's SCM and the revision being built is untrusted
     */
    @Override
    public void verify(Run<?, ?> run, TaskListener listener, SCM libraryScm, String name) throws IOException, InterruptedException {
        // Adapted from ReadTrustedStep
        Job<?, ?> job = run.getParent();
        BranchJobProperty property = job.getProperty(BranchJobProperty.class);
        if (property == null || !(job.getParent() instanceof SCMSourceOwner)) {
            // Not a multibranch project, so we do not care.
            // It is possible to use legacySCM(scm) from a non-multibranch Pipeline that uses CpsScmFlowDefinition,
            // but in that case we implicitly trust the changes because only a user with Item/Configure permission can select which branches to build.
            return;
        }
        Branch pipelineBranch = property.getBranch();
        SCMSource pipelineScmSource = ((SCMSourceOwner)job.getParent()).getSCMSource(pipelineBranch.getSourceId());
        if (pipelineScmSource == null) {
            throw new IllegalStateException(pipelineBranch.getSourceId() + " not found");
        }
        SCMHead head = pipelineBranch.getHead();
        SCMRevision headRevision;
        SCMRevisionAction action = run.getAction(SCMRevisionAction.class);
        if (action != null) {
            headRevision = action.getRevision();
        } else {
            headRevision = pipelineScmSource.fetch(head, listener);
            if (headRevision == null) {
                throw new AbortException("Could not determine exact tip revision of " + pipelineBranch.getName());
            }
            run.addAction(new SCMRevisionAction(pipelineScmSource, headRevision));
        }
        SCMRevision trustedRevision = pipelineScmSource.getTrustedRevision(headRevision, listener);
        if (!headRevision.equals(trustedRevision) && libraryScm.getKey().equals(pipelineScmSource.build(head, headRevision).getKey())) {
            throw new AbortException("Library '" + name + "' has been modified in an untrusted revision");
        }
    }
}
