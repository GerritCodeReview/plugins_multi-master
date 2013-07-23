// Copyright (c) 2013, The Linux Foundation. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following
// disclaimer in the documentation and/or other materials provided
// with the distribution.
// * Neither the name of The Linux Foundation nor the names of its
// contributors may be used to endorse or promote products derived
// from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
// BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
// WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
// OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
// IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.googlesource.gerrit.plugins.multimaster.websession;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.httpd.WebSessionManager;
import com.google.gerrit.httpd.WebSessionManager.Val;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.googlesource.gerrit.plugins.multimaster.MultiMasterConfig;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

@Singleton
public class FileWebSessionCache implements
    Cache<String, WebSessionManager.Val> {
  private static final Logger log = LoggerFactory
      .getLogger(FileWebSessionCache.class);

  private final File dir;

  @Inject
  public FileWebSessionCache(@MultiMasterConfig Config cfg) {
    String cacheDir =
        cfg.getString("cache", WebSessionManager.CACHE_NAME, "directory");
    String dirName =
        WebSessionManager.CACHE_NAME + "_" + getClass().getSimpleName();
    dir = new File(new File(cacheDir), dirName);
  }

  @Override
  public ConcurrentMap<String, Val> asMap() {
    ConcurrentMap<String, Val> map = new ConcurrentHashMap<String, Val>();
    File[] files = dir.listFiles();
    if (files == null) {
      return map;
    }
    for (File f : files) {
      Val v = readFile(f);
      if (v != null) {
        map.put(f.getName(), v);
      }
    }
    return map;
  }

  @Override
  public void cleanUp() {
    // do nothing
  }

  @Override
  public Val get(String key, Callable<? extends Val> valueLoader)
      throws ExecutionException {
    Val value = getIfPresent(key);
    if (value == null) {
      try {
        value = valueLoader.call();
      } catch (Exception e) {
        throw new ExecutionException(e);
      }
    }
    return value;
  }

  @Override
  public ImmutableMap<String, Val> getAllPresent(Iterable<?> keys) {
    ImmutableMap.Builder<String, Val> mapBuilder =
        new ImmutableMap.Builder<String, Val>();
    for (Object key : keys) {
      Val v = getIfPresent(key);
      if (v != null) {
        mapBuilder.put((String) key, v);
      }
    }
    return mapBuilder.build();
  }

  @Override
  @Nullable
  public Val getIfPresent(Object key) {
    if (key instanceof String) {
      File f = new File(dir, (String) key);
      return readFile(f);
    }
    return null;
  }

  @Override
  public void invalidate(Object key) {
    if (key instanceof String) {
      deleteFile(new File(dir, (String) key));
    }
  }

  @Override
  public void invalidateAll() {
    File[] files = dir.listFiles();
    if (files != null) {
      for (File f : files) {
        deleteFile(f);
      }
    }
  }

  @Override
  public void invalidateAll(Iterable<?> keys) {
    for (Object key : keys) {
      invalidate(key);
    }
  }

  @Override
  public void put(String key, Val value) {
    if (!dir.exists()) {
      dir.mkdir();
    }

    File tempFile = null;
    OutputStream fileStream = null;
    ObjectOutputStream objStream = null;

    try {
      tempFile = File.createTempFile(UUID.randomUUID().toString(), null, dir);
      fileStream = new FileOutputStream(tempFile);

      objStream = new ObjectOutputStream(fileStream);
      objStream.writeObject(value);

      File f = new File(dir, key);
      if (!tempFile.renameTo(f)) {
        log.warn("Cannot put into cache " + dir.getAbsolutePath()
            + "; error renaming temp file");
      }
    } catch (FileNotFoundException e) {
      log.warn("Cannot put into cache " + dir.getAbsolutePath(), e);
    } catch (IOException e) {
      log.warn("Cannot put into cache " + dir.getAbsolutePath(), e);
    } finally {
      if (tempFile != null) {
        deleteFile(tempFile);
      }
      close(fileStream);
      close(objStream);
    }
  }

  @Override
  public void putAll(Map<? extends String, ? extends Val> keys) {
    for (Entry<? extends String, ? extends Val> e : keys.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public long size() {
    String[] files = dir.list();
    if (files == null) {
      return 0;
    }
    return files.length;
  }

  @Override
  public CacheStats stats() {
    log.warn("stats() unimplemented");
    return null;
  }

  private Val readFile(File f) {
    InputStream fileStream = null;
    ObjectInputStream objStream = null;
    try {
      fileStream = new FileInputStream(f);
      objStream = new ObjectInputStream(fileStream);
      try {
        return (Val) objStream.readObject();
      } catch (ClassNotFoundException e) {
        log.warn("Entry " + f.getName() + " in cache " + dir.getAbsolutePath()
            + " has an incompatible class and can't be deserialized. "
            + "Invalidating entry.");
        invalidate(f.getName());
      }
    } catch (FileNotFoundException e) {
    } catch (IOException e) {
      log.warn("Cannot read cache " + dir.getAbsolutePath(), e);
    } finally {
      close(fileStream);
      close(objStream);
    }
    return null;
  }

  private void deleteFile(File f) {
    if (!f.delete()) {
      log.warn("Cannot delete file " + f.getName() + " from cache "
          + dir.getAbsolutePath());
    }
  }

  private void close(Closeable c) {
    if (c != null) {
      try {
        c.close();
      } catch (IOException e) {
        log.warn("Cannot close stream", e);
      }
    }
  }
}
