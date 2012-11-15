package com.googlesource.gerrit.plugins.multimaster.pubsub;

public interface EventCallback {
  
  public void onEvent(String topic, Object event);
}
