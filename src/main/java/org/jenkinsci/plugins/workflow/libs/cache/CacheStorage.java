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

package org.jenkinsci.plugins.workflow.libs.cache;

import java.util.Collection;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.libs.cache.impl.FileBasedCacheStorage;

public interface CacheStorage {

    <T> CacheStorageResult<T> tryWrite(String key, CacheStorageWriteAction<T> action) throws Exception;

    <T> CacheStorageResult<T> tryRead(String key, CacheStorageReadAction<T> action) throws Exception;

    Collection<String> getKeys() throws Exception;

    void forceDelete(String key) throws Exception;

    static CacheStorage get() {
        String clazz = System.getProperty("jenkins.workflow-libs.cacheStorage.implementation", FileBasedCacheStorage.class.getName());
        try {
            return (CacheStorage) Jenkins.get().getPluginManager().uberClassLoader.loadClass(clazz).newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | IllegalStateException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
