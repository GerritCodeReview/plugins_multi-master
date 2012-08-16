package com.google.gerrit.plugins.multimaster.peer;

import com.google.gerrit.plugins.multimaster.Json;


public class ClusterProperty {
  public interface Listener {
    public void onDoesNotExist(ClusterProperty property);

    public void onUpdate(ClusterProperty property);

    public void onOutdated(ClusterProperty property);
  }

  private Peer.Id author;

  private String name;

  private Json value;

  public ClusterProperty(String name, Json value, Peer.Id author) {
    this.name = name;
    this.value = value;
    this.author = author;
  }

  public String getName() {
    return name;
  }

  public void setValue(Json value, Peer.Id author) {
    this.value = value;
    this.author = author;
  }

  public Json getValue() {
    return value;
  }

  public Peer.Id getAuthor() {
    return author;
  }
}
