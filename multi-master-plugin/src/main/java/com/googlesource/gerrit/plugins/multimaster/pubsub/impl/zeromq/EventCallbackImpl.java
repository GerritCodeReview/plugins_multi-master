package com.googlesource.gerrit.plugins.multimaster.pubsub.impl.zeromq;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multimaster.pubsub.EventCallback;
import com.googlesource.gerrit.plugins.multimaster.pubsub.TopicNotifier;

@Singleton
public class EventCallbackImpl implements EventCallback {
  private static final Logger log = LoggerFactory
      .getLogger(EventCallbackImpl.class);

  private TopicNotifier notifier;

  @Inject
  public EventCallbackImpl(final TopicNotifier notifier) {
    this.notifier = notifier;
  }

  @Override
  public void onEvent(String topic, Object event) {
    try {
      notifier.sendEvent(topic, event);
    } catch (IOException e) {
      log.error("Unable to notify event " + event + " on topic " + topic);
    }
  }

}
