// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.multimaster.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class CacheManager {
  private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

  private DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  public CacheManager(DynamicMap<Cache<?, ?>> cacheMap, Evictor evictor,
      PeerRegistry peerRegistry, Gson gson) {
    this.cacheMap = cacheMap;
    CacheRemovalListenerImpl.setIgnored(Thread.currentThread());
  }

  /**
   * Flushes all caches
   */
  public void flushAll() {
    CacheRemovalListenerImpl.setIgnored(Thread.currentThread());

    log.info("Flushing all caches");

    for (String pluginName : cacheMap.plugins()) {
      for (Provider<Cache<?, ?>> provider : cacheMap.byPlugin(pluginName)
          .values()) {
        provider.get().invalidateAll();
      }
    }
  }

  /**
   * Flushes a specific cache
   *
   * @param pluginName
   * @param cacheName
   */
  public void flush(String pluginName, String cacheName) {
    CacheRemovalListenerImpl.setIgnored(Thread.currentThread());

    cacheMap.get(pluginName, cacheName).invalidateAll();
  }

  /**
   * Invalidates a specific cache entry
   *
   * @param pluginName
   * @param cacheName
   * @param key
   */
  public void invalidate(String pluginName, String cacheName, Object key) {
    CacheRemovalListenerImpl.setIgnored(Thread.currentThread());

    cacheMap.get(pluginName, cacheName).invalidate(key);
  }
}
