/*
 * The MIT License
 *
 * Copyright 2016 philipplang.
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
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 *
 * @author philipplang
 */
public class GitLoader implements Loader, Parser {

    protected String repositoryUrl;
    protected String branch;

    @Override
    public List<Loader> parse(String script) {
        System.out.println("- parsing script " + script);
        List<Loader> result = new ArrayList<>();

        Pattern pattern = Pattern.compile("^//#git.*$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(script);
        if (matcher.lookingAt()) {
            result.add(GitLoader.fromString(matcher.group(0)));
        }
        System.out.println("- parsed loaders: " + result.size());
        return result;
    }

    static GitLoader fromString(String line) {
        String[] splitted = line.split(" ");
        GitLoader loader = new GitLoader();
        loader.repositoryUrl = splitted[1];
        loader.branch = splitted[2];
        return loader;
    }

    @Override
    public URL load() {
        File targetFile = new File(Jenkins.getActiveInstance().root, "global-library-git");
        try {
            FileUtils.deleteDirectory(targetFile);
            Git result = Git
                    .cloneRepository()
                    .setURI(repositoryUrl)
                    .setBranch(branch)
                    .setDirectory(targetFile)
                    .call();
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
