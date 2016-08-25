package org.jenkinsci.plugins.workflow.cps.global.loader;

import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link GitLoader}.
 * 
 * @author Philipp Lang
 */
public class GitLoaderTest {

    private final GitLoader instance = new GitLoader();

    /**
     * Test script parsing with one url
     */
    @Test
    public void testParseOneUrl() {

        String script = "//#git http://localhost:8080/workflowLibs.git branch\n"
                + "node {}";
        
        List<Loader> loaders = instance.parse(script);
        
        Assert.assertEquals(1, loaders.size());
        Assert.assertEquals("http://localhost:8080/workflowLibs.git", ((GitLoader)loaders.get(0)).repositoryUrl);
        Assert.assertEquals("branch", ((GitLoader)loaders.get(0)).branch);
    }
}
