package com.googlesource.gerrit.plugins.multimaster.pubsub;

import java.io.IOException;

public interface TopicNotifier{
  
  public void sendEvent(String topic, Object event) throws IOException;
}
