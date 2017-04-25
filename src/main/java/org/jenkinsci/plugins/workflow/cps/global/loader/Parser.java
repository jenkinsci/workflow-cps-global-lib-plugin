package org.jenkinsci.plugins.workflow.cps.global.loader;

import java.util.List;

/**
 * Parse the given groovy pipeline Script for Loaders.
 * 
 * @author Philipp Lang
 */
public interface Parser {
    /**
     * Parse the script and detect loaders.
     * 
     * @param script - groovy pipeline script
     * @return list of loaders for extending the classpath
     */
    List<Loader> parse(String script);
}
