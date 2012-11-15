package com.googlesource.gerrit.plugins.multimaster.pubsub;

public interface TopicSubscriber<K> {
  
  public void subscribe(String topicPath, EventCallback eventHandler);
  
  public void unsubscribe(String topicPath);
  
  public void unsubscribeAll();
}
