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

/**
 * Adds groovy files in the workspace
 * of the repository loaded to the classpath of the build
 */
public class WorkspaceLibraryAdder extends ClasspathAdder {

    /**
     * @param execution a running build (possibly newly started, possibly resumed)
     * @param libraries aggregated entries from all encountered {@link Library#value} (will be empty if {@link Library} is never used at all); an implementation should remove entries it “claims”
     * @param changelogs not used when adding directories
     * @return Returns a list of directories that were added to the classpath
     * @throws Exception
     */
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

    /**
     * @param execution provides job URL in order to build expected workspace path
     * @param library relative path specified in {@link Library} of groovy files
     * @return absolute path to groovy files
     * @throws IOException
     */
    private String getWorkspaceLibraryPath(CpsFlowExecution execution, String library) throws IOException {
        String url = execution.getUrl(); // e.g. "job/Directory/job/JobName/34/execution/"
        String[] parts = url.substring(4).split("/job/");
        parts[parts.length - 1] = parts[parts.length - 1].split("/")[0];
        String partialPath = StringUtils.join(parts, "/");
        return String.format("%s/workspace/%s@script/%s", System.getenv("JENKINS_HOME"), partialPath, library);
    }
}
