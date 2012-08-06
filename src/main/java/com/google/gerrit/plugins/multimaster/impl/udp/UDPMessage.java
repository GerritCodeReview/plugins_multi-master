// Copyright (c) 2012, Code Aurora Forum. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following
// disclaimer in the documentation and/or other materials provided
// with the distribution.
// * Neither the name of Code Aurora Forum, Inc. nor the names of its
// contributors may be used to endorse or promote products derived
// from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
// BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
// WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
// OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
// IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

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
