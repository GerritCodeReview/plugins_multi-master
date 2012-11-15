package com.googlesource.gerrit.plugins.multimaster.pubsub.impl.zeromq;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jeromq.ZMQ;

import com.googlesource.gerrit.plugins.multimaster.pubsub.EventCallback;
import com.googlesource.gerrit.plugins.multimaster.pubsub.TopicSubscriber;

public class TopicSubscriberImpl implements TopicSubscriber {

  private final ZMQ.Context context;
  private final ZMQ.Socket subscriber;
  private ConcurrentHashMap<String, Boolean> topics;


  public TopicSubscriberImpl() {
    context = ZMQ.context(1);
    subscriber = context.socket(ZMQ.SUB);
    topics = new ConcurrentHashMap<String, Boolean>();
    // TODO read from config
    subscriber.connect("tcp://localhost:29428");
  }

  @Override
  public void subscribe(String topic, EventCallback eventHandler) {
    if (topics.put(topic, true) == null) {
      subscriber.subscribe(topic);
    }
  }

  @Override
  public void unsubscribe(String topic) {
    if (topics.remove(topic) != null) {
      subscriber.unsubscribe(topic);
    }
  }

  @Override
  public void unsubscribeAll() {
    for (String topic : topics.keySet()) {
      unsubscribe(topic);
    }
  }

}
