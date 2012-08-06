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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.RemovalNotification;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gson.Gson;

/**
 * RemovalListener that notifies its listeners that a cache has been invalidated.
 *
 * @param <K>
 * @param <V>
 */
public class CacheRemovalListenerImpl<K, V> implements
    CacheRemovalListener<K, V> {
  private static final Logger log = LoggerFactory
      .getLogger(CacheRemovalListenerImpl.class);

  /*
   * Listeners
   */
  public interface Listener {
    public void onRemoval(EvictNotice notice);
  }

  private static List<Listener> listeners = new LinkedList<Listener>();

  public static void addListener(Listener listener) {
    listeners.add(listener);
  }

  private static Set<Thread> ignoredThreads = Collections
      .synchronizedSet(new HashSet<Thread>());

  /**
   * Sets the thread to be ignored
   *
   * @param thread to be ignored
   */
  public static void setIgnored(Thread thread) {
    ignoredThreads.add(thread);
  }

  /**
   * Determines whether to ignore the thread or not.
   *
   * @param thread to query about
   * @return true if the thread should be ignored
   */
  public static boolean isIgnored(Thread thread) {
    return ignoredThreads.contains(thread);
  }

  @Override
  public void onRemoval(String pluginName, String cacheName,
      RemovalNotification<K, V> notification) {
    if (isIgnored(Thread.currentThread())) {
      return;
    }

    if (notification.wasEvicted()) {
      return;
    }

    Gson gson = new Gson();

    String keyJson = gson.toJson(notification.getKey());

    for (Listener listener : listeners) {
      listener.onRemoval(new EvictNotice(pluginName, cacheName, keyJson,
          notification.getKey().getClass().getName()));
    }
  }
}
