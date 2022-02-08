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
import jenkins.security.HMACConfidentialKey;

/**
 * Record of a library being used in a particular build.
 */
final class LibraryRecord {

    private static final HMACConfidentialKey DIRECTORY_NAME_KEY = new HMACConfidentialKey(LibraryRecord.class, "directoryName", 32);
    private static final String ASCII_UNIT_SEPARATOR = String.valueOf((char)31);

    final String name;
    final String version;
    final Set<String> variables = new TreeSet<>();
    final boolean trusted;
    final boolean changelog;
    private String directoryName;

    /**
     * @param name The name of the library, as entered by the user. Not validated or restricted in any way.
     * @param version The version of the library, as entered by the user. Not validated or restricted in any way.
     * @param trusted Whether the library is trusted. Typically determined by {@link LibraryResolver#isTrusted}, but see also {@link LibraryStep}.
     * @param changelog Whether we should include any SCM changes in this library in the build's changelog.
     * @param source A string describing the source of the configuration of this library. Typically the class name of a {@link LibraryResolver}, sometimes with additional data, but see also {@link LibraryStep}.
     */
    LibraryRecord(String name, String version, boolean trusted, boolean changelog, String source) {
        this.name = name;
        this.version = version;
        this.trusted = trusted;
        this.changelog = changelog;
        this.directoryName = directoryNameFor(name, version, String.valueOf(trusted), source);
    }

    /**
     * Returns a partially unique name that can be safely used as a directory name.
     *
     * Uniqueness is based on the library name, version, whether it is trusted, and the source of the library.
     * {@link LibraryRetriever}-specific information such as the SCM is not used to produce this name.
     */
    public String getDirectoryName() {
        return directoryName;
    }

    @Override public String toString() {
        return "LibraryRecord{name=" + name + ", version=" + version + ", variables=" + variables + ", trusted=" + trusted + ", changelog=" + changelog + ", directoryName=" + directoryName + '}';
    }

    private Object readResolve() {
        if (directoryName == null) {
            // Builds started before directoryName was added must continue to use the library name as the directory name.
            directoryName = name;
        }
        return this;
    }

    public static String directoryNameFor(String... data) {
        for (String datum : data) {
            if (datum.contains(ASCII_UNIT_SEPARATOR)) { // Very unlikely to appear in legitimate user-controlled text.
                throw new IllegalStateException("Unable to create directory name due to control character in " + datum);
            }
        }
        return DIRECTORY_NAME_KEY.mac(String.join(ASCII_UNIT_SEPARATOR, data));
    }

}
