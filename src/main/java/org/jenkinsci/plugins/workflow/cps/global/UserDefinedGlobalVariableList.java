package org.jenkinsci.plugins.workflow.cps.global;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Run;
import hudson.util.CopyOnWriteList;
import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.GlobalVariableSet;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Keeps {@link UserDefinedGlobalVariable}s in {@link ExtensionList} up-to-date
 * from {@code $JENKINS_HOME/workflow-libs/vars/*.groovy}.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class UserDefinedGlobalVariableList extends GlobalVariableSet {

    /*package*/ static final String PREFIX = "vars";

    private @Inject WorkflowLibRepository repo;
    
    private volatile CopyOnWriteList<GlobalVariable> ours;

    /**
     * Rebuilds the list of {@link UserDefinedGlobalVariable}s and update {@link ExtensionList} accordingly.
     */
    public synchronized void rebuild() {
        File[] children = new File(repo.workspace, PREFIX).listFiles();
        if (children==null) children = new File[0];

        List<GlobalVariable> list = new ArrayList<GlobalVariable>();

        for (File child : children) {
            if (!child.getName().endsWith(".groovy") || child.isDirectory())
                continue;

            String name = FilenameUtils.getBaseName(child.getName());
            UserDefinedGlobalVariable uv = new UserDefinedGlobalVariable(name, new File(repo.workspace, PREFIX + "/" + name + ".txt"));
            list.add(uv);
        }

        // first time, build the initial list
        if (ours==null)
            ours = new CopyOnWriteList<GlobalVariable>();
        ours.replaceBy(list);
    }

    @Override public Collection<GlobalVariable> forRun(Run<?, ?> run) {
        if (ours==null) {
            rebuild();
        }
        return ours.getView();
    }
}
