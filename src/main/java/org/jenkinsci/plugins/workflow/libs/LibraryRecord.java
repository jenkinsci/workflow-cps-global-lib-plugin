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
    final String libBasePath;
    final Set<String> variables = new TreeSet<>();
    final boolean trusted;
    final boolean changelog;

    LibraryRecord(String name, String version, String libBasePath, boolean trusted, boolean changelog) {
        this.name = name;
        this.version = version;
        this.libBasePath = libBasePath;
        this.trusted = trusted;
        this.changelog = changelog;
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (changelog ? 1231 : 1237);
		result = prime * result + ((libBasePath == null) ? 0 : libBasePath.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + (trusted ? 1231 : 1237);
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LibraryRecord other = (LibraryRecord) obj;
		if (changelog != other.changelog)
			return false;
		if (libBasePath == null) {
			if (other.libBasePath != null)
				return false;
		} else if (!libBasePath.equals(other.libBasePath))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (trusted != other.trusted)
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}



	@Override public String toString() {
        return "LibraryRecord{name=" + name + ", version=" + version + ", libBasePath=" + libBasePath + ", variables=" + variables + ", trusted=" + trusted + ", changelog=" + changelog + '}';
    }

}
