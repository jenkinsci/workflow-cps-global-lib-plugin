package org.jenkinsci.plugins.workflow.cps.global.loader;

import java.io.File;
import java.net.URL;

/**
 * Loaders download some files (for example groovy files) and make them
 * available for the groovy shell classloader.
 *
 * @author Philipp Lang
 */
public interface Loader {

    /**
     * Load the files and save them in the given folder.
     *
     * @param storageDir - Target folder for the loaded files
     * @return URL to a folder with the loaded files
     */
    URL load(File storageDir);
}
