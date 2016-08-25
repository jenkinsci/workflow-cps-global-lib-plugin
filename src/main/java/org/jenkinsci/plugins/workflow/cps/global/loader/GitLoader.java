package org.jenkinsci.plugins.workflow.cps.global.loader;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Loads the Groovy-Files from a specific git repository.
 *
 * @author Philipp Lang
 */
public class GitLoader implements Loader, Parser {

    protected String repositoryUrl;
    protected String branch;
    protected String clPath;

    @Override
    public List<Loader> parse(String script) {
        List<Loader> result = new ArrayList<>();

        Pattern pattern = Pattern.compile("^//#git.*$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(script);
        if (matcher.lookingAt()) {
            result.add(GitLoader.fromString(matcher.group(0)));
        }
        return result;
    }

    static GitLoader fromString(String line) {
        String[] splitted = line.split(" ");
        GitLoader loader = new GitLoader();
        loader.repositoryUrl = splitted[1];
        loader.branch = splitted[2];
        if (splitted.length > 3) {
            loader.clPath = splitted[3];
        }
        return loader;
    }

    @Override
    public URL load(File storageDir) {
        File targetFile = new File(storageDir, "global-library-git");
        Logger.getLogger(GitLoader.class.getName()).log(Level.INFO, "clone git repo into " + targetFile);
        try {
            FileUtils.deleteDirectory(targetFile);
            Git result = Git
                    .cloneRepository()
                    .setURI(repositoryUrl)
                    .setBranch(branch)
                    .setDirectory(targetFile)
                    .call();
            if (clPath != null) {
                targetFile = new File(targetFile, clPath);
            }
            return targetFile.toURI().toURL();
        } catch (GitAPIException ex) {
            Logger.getLogger(GitLoader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (MalformedURLException ex) {
            Logger.getLogger(GitLoader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(GitLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

}
