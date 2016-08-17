package org.jenkinsci.plugins.workflow.cps.global.loader;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author philipplang
 */
public class LoadUrl implements Loader, Parser {
    private URL url;
    
    public static LoadUrl fromString(String line) {
        try {
            LoadUrl instance = new LoadUrl();
            instance.url = new URL(line.split(" ")[1]);
            return instance;
        } catch (MalformedURLException ex) {
            Logger.getLogger(LoadUrl.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("invalid url");
        }
    }
    
    public URL load(File storageDir) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<Loader> parse(String script) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
