// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.multimaster.impl.udp;

import com.google.gerrit.plugins.multimaster.Json;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class UDPMessage {
  public enum Type {
    EVICT_NOTICE, PULSE, ACK
  }

  /**
   * Create a new PULSE message
   *
   * @param sourceId
   * @param targetId
   * @return PULSE message
   * @throws ClassNotFoundException 
   * @throws JsonSyntaxException 
   */
  public static UDPMessage newPulse(Peer source, Peer target) {
    return new UDPMessage(source, target, Type.PULSE, null, false);
  }

  /**
   * Create a new ACK message with the same id
   *
   * TODO It may be better to give this a different id and pass the id that is
   * being ACKed in a separate container.
   *
   * @param peerId
   * @param targetId
   * @param id to ACK
   * @return ACK message
   * @throws ClassNotFoundException
   * @throws JsonSyntaxException
   */
  public static UDPMessage newAck(Peer source, Peer target, int id) {
    UDPMessage msg = new UDPMessage(target, target, Type.ACK, null, false);
    msg.id = id;
    return msg;
  }

  /**
   * Copy the message and increment numTries on the copy.
   *
   * @return copied message
   */
  public static UDPMessage newRetry(UDPMessage msg) {
    UDPMessage copy = new UDPMessage(msg);
    copy.numTries++;
    return copy;
  }

  public Peer source;
  public Peer target;

  public int id;
  public Type type;
  public Json json;
  public long timestamp;
  public boolean ackRequired;
  public int numTries;

  public UDPMessage(Peer source, Peer target, Type type, Json json,
      boolean ackRequired) {
    this.source = source;
    this.target = target;
    this.type = type;
    this.json = json;
    this.timestamp = System.currentTimeMillis();
    this.ackRequired = ackRequired;
    this.numTries = 0;

    this.id = hashCode();
  }

  public UDPMessage(final UDPMessage msg) {
    this.source = msg.source;
    this.target = msg.target;
    this.type = msg.type;
    this.json = msg.json;
    this.timestamp = System.currentTimeMillis();
    this.ackRequired = msg.ackRequired;
    this.numTries = msg.numTries;

    this.id = msg.id;
  }

  /**
   * Serialize to JSON
   *
   * @return
   */
  public String toJson() {
    return (new Gson()).toJson(this);
  }
}
