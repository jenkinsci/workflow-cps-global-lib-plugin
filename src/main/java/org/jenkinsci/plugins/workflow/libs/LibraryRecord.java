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

package org.jenkinsci.plugins.workflow.libs;

import java.util.Set;
import java.util.TreeSet;

/**
 * Record of a library being used in a particular build.
 */
final class LibraryRecord {

    final String name;
    final String version;
    final Set<String> variables = new TreeSet<>();
    final boolean trusted;
    final boolean changelog;
    final LibraryCachingConfiguration cachingConfiguration;

    LibraryRecord(String name, String version, boolean trusted, boolean changelog, LibraryCachingConfiguration cachingConfiguration) {
        this.name = name;
        this.version = version;
        this.trusted = trusted;
        this.changelog = changelog;
        this.cachingConfiguration = cachingConfiguration;
    }

    @Override public String toString() {
        return "LibraryRecord{name=" + name + ", version=" + version + ", variables=" + variables + ", trusted=" + trusted + ", changelog=" + changelog + ", cachingConfiguration=" + cachingConfiguration.toString() + '}';
    }

}
