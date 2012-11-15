package com.googlesource.gerrit.plugins.multimaster.pubsub.impl.zeromq;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.jeromq.ZMQ;

import com.googlesource.gerrit.plugins.multimaster.pubsub.TopicNotifier;

public class TopicNotifierImpl implements TopicNotifier {
  private final ZMQ.Context context;
  private final ZMQ.Socket publisher;


  public TopicNotifierImpl() {
    context = ZMQ.context(1);
    publisher = context.socket(ZMQ.PUB);
    // TODO read from config
    publisher.bind("tcp://*:29428");
  }

  @Override
  public void sendEvent(String topic, Object event) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutput out = null;
    try {
      out = new ObjectOutputStream(bos);
      out.writeObject(event);
      publisher.send(topic.getBytes(), ZMQ.SNDMORE);
      publisher.send(bos.toByteArray(), 0);
    } catch (IOException e) {
    } finally {
      try {
        out.close();
        bos.close();
      } catch (IOException e) {
      }
    }
  }
}
