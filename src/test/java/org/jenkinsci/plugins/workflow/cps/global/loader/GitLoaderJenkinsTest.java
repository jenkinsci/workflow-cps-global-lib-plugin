
package org.jenkinsci.plugins.workflow.cps.global.loader;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariableList;
import org.jenkinsci.plugins.workflow.cps.global.WorkflowLibRepository;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 *
 * @author Philipp Lang
 */
public class GitLoaderJenkinsTest {
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @javax.inject.Inject
    Jenkins jenkins;

    @javax.inject.Inject
    WorkflowLibRepository repo;

    @Test
    public void hello() {
    }
}
