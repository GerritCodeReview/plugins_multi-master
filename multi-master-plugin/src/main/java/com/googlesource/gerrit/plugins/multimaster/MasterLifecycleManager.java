package com.googlesource.gerrit.plugins.multimaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multimaster.pubsub.EventCallback;
import com.googlesource.gerrit.plugins.multimaster.pubsub.TopicSubscriber;

@Singleton
public class MasterLifecycleManager<V> implements LifecycleListener {
  private static final Logger log = LoggerFactory
      .getLogger(MasterLifecycleManager.class);
  private TopicSubscriber subscriber;
  private EventCallback callback;

  @Inject
  public MasterLifecycleManager(final TopicSubscriber subscriber,
      final EventCallback callback) {
    this.subscriber = subscriber;
    this.callback = callback;
  }

  @Override
  public void start() {
    log.info("Starting up multi-master");
    subscriber.subscribe(Constants.ROOT, callback);
  }

  @Override
  public void stop() {
    log.info("Shutting down multi-master");
    subscriber.unsubscribeAll();
  }

}
