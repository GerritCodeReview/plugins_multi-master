// Copyright (c) 2012, Code Aurora Forum. All rights reserved.
// // Copyright (C) 2012 The Android Open Source Project
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
