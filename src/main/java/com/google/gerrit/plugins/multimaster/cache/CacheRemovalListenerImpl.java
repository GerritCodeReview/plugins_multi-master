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
