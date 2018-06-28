package org.jenkinsci.plugins.workflow.libs;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class WorkspaceLibraryAdder extends ClasspathAdder {

    @Nonnull
    @Override
    public List<Addition> add(@Nonnull CpsFlowExecution execution, @Nonnull List<String> libraries, @Nonnull HashMap<String, Boolean> changelogs) throws Exception {
        List<Addition> additions = new ArrayList<>();

        Iterator<String> iterator = libraries.iterator();
        while (iterator.hasNext()) {
            String path = getWorkspaceLibraryPath(execution, iterator.next());
            File libraryPath = new File(path);
            if (libraryPath.exists()) {
                iterator.remove();
                Addition addition = new Addition(libraryPath.toURI().toURL(), false);
                addition.addTo(execution);
                additions.add(addition);
            }
        }

        return additions;
    }

    private String getWorkspaceLibraryPath(CpsFlowExecution execution, String library) throws IOException {
        String url = execution.getUrl(); // e.g. "job/Directory/job/JobName/34/execution/"
        String[] parts = url.substring(4).split("/job/");
        parts[parts.length - 1] = parts[parts.length - 1].split("/")[0];
        String partialPath = StringUtils.join(parts, "/");
        return String.format("%s/workspace/%s@script/%s", System.getenv("JENKINS_HOME"), partialPath, library);
    }
}
