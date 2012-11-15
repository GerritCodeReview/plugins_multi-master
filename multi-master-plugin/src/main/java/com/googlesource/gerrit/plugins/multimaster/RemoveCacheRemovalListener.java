package com.googlesource.gerrit.plugins.multimaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multimaster.pubsub.EventCallback;

@Singleton
public class RemoveCacheRemovalListener implements EventCallback {
  private static final Logger log = LoggerFactory
      .getLogger(RemoveCacheRemovalListener.class);

  @Override
  public void onEvent(String topic, Object event) {
    log.info("Received remote cache removal event: " + event);
  }

}
