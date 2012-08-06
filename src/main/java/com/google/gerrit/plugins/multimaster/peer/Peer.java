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

package com.google.gerrit.plugins.multimaster.peer;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.Hashing;
import com.google.gerrit.plugins.multimaster.Json;


public class Peer {
  private static final Logger log = LoggerFactory.getLogger(Peer.class);

  public static class Id {
    public String string;

    public Id() {
      // TODO generate proper unique ID. This still has a chance to create
      // colliding IDs
      String str =
          Long.toString(System.currentTimeMillis())
              + Double.toString(Math.random());
      this.string = Hashing.sha1().hashString(str).toString();
    }

    @Override
    public String toString() {
      return string;
    }

    @Override
    public int hashCode() {
      return string.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Id) {
        return ((Id) o).string.equals(string);
      }

      return false;
    }
  }

  private Id id;
  private Map<String, Json> properties;

  public Peer() {
    this.id = new Id();
    this.properties = new HashMap<String, Json>();
  }

  public Peer(Peer.Id id, Map<String, Json> properties) {
    this.id = id;
    this.properties = new HashMap<String, Json>(properties);
  }

  public Id getId() {
    return id;
  }

  public void setProperty(String key, Json value) {
    log.info("[" + id + "] Property \"" + key + "\" set to \"" + value.string
        + "\"");

    properties.put(key, value);
  }

  public Json getProperty(String key) {
    return properties.get(key);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
