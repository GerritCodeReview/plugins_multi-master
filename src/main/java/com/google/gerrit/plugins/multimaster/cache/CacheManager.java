// Copyright (c) 2012, Code Aurora Forum. All rights reserved.
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
// * Neither the name of Code Aurora Forum, Inc. nor the names of its
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
