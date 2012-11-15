package com.googlesource.gerrit.plugins.multimaster;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.RemovalNotification;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multimaster.pubsub.TopicNotifier;

@Singleton
public class LocalCacheRemovalListenerImpl<K, V> implements
    CacheRemovalListener<K, V> {
  private static final Logger log = LoggerFactory
      .getLogger(LocalCacheRemovalListenerImpl.class);
  private final TopicNotifier notifier;

  @Inject
  public LocalCacheRemovalListenerImpl(final TopicNotifier notifier) {
    this.notifier = notifier;
  }

  @Override
  public void onRemoval(String pluginName, String cacheName,
      RemovalNotification<K, V> notification) {
    log.info("Entry " + notification.getKey() + " evicted from cache "
        + cacheName);
    try {
      notifier.sendEvent(Constants.ROOT + "/" + cacheName,
          notification.getKey());
    } catch (IOException e) {
      log.error("Cannot notify cache eviction for %s on key %s", cacheName,
          notification.getKey());
    }
  }

}
