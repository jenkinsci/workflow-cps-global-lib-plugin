/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.global;

import groovy.grape.Grape;
import groovy.grape.GrapeEngine;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.MaskingClassLoader;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GrapeHack {

    private static final Logger LOGGER = Logger.getLogger(GrapeHack.class.getName());

    @Initializer(after=InitMilestone.PLUGINS_PREPARED, fatal=false)
    public static void hack() throws Exception {
        String grapeIvyName = "groovy.grape.GrapeIvy";
        URL groovyJar = Grape.class.getProtectionDomain().getCodeSource().getLocation();
        LOGGER.log(Level.FINE, "using {0}", groovyJar);
        ClassLoader l = new URLClassLoader(new URL[] {groovyJar}, new MaskingClassLoader(GrapeHack.class.getClassLoader(), grapeIvyName));
        Class<?> c = Class.forName(grapeIvyName, false, l);
        Field instance = Grape.class.getDeclaredField("instance");
        instance.setAccessible(true);
        if (instance.get(null) == null) {
            instance.set(null, c.newInstance());
            GrapeEngine engine = Grape.getInstance();
            LOGGER.log(Level.FINE, "successfully loaded {0}", engine);
            LOGGER.log(Level.FINE, "linked to {0}", engine.getClass().getClassLoader().loadClass("org.apache.ivy.core.settings.IvySettings").getProtectionDomain().getCodeSource().getLocation());
        } else {
            LOGGER.fine("instance was already set");
        }
    }

    private GrapeHack() {}

}
