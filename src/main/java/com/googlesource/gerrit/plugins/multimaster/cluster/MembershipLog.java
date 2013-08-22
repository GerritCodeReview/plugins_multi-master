// Copyright (c) 2013, The Linux Foundation. All rights reserved.
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
// * Neither the name of The Linux Foundation nor the names of its
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

package com.googlesource.gerrit.plugins.multimaster.cluster;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TreeSet;

public class MembershipLog {
  private static final String MEMBER = "member";
  private static final String EVENT = "event";
  private static final String JOIN = "join";
  private static final String JOINED = "joined";
  private static final String LEAVE = "leave";

  private static final Logger log = LoggerFactory
      .getLogger(MembershipLog.class);

  private Config logFile;
  private Set<String> members = new TreeSet<String>();
  private long currentGeneration = 0;
  private long lastId = 0;
  private final String selfId;

  @Inject
  public MembershipLog(@Assisted Config logFile, @SelfId String selfId) {
    this.logFile = logFile;
    this.selfId = selfId;

    for (String eventId : logFile.getSubsections(EVENT)) {
      String[] terms = eventId.split("\\.");
      if (terms.length != 2) {
        log.warn("Invalid event ID: " + eventId);
        continue;
      }
      long gen = Long.parseLong(terms[0]);
      long id = Long.parseLong(terms[1]);
      if (lastId < id && currentGeneration == gen) {
        lastId = id;
      }
      if (currentGeneration < gen || currentGeneration == 0) {
        currentGeneration = gen;
        lastId = id;
      }
      // remove expired events
      logFile.unsetSection(EVENT, eventId);
    }

    for (String memberId : logFile.getSubsections(MEMBER)) {
      members.add(memberId);
    }
  }

  public Config newJoinEvent() {
    if (members.isEmpty()) {
      currentGeneration++;
      lastId = 0;
    }
    String eventId = String.format("%d.%d", currentGeneration, lastId + 1);

    Config newEventSection = new Config();
    addEvent(newEventSection, eventId, JOIN);
    try {
      logFile.fromText(newEventSection.toText() + logFile.toText());
    } catch (ConfigInvalidException e) {
      log.warn("Could not place sections in order", e);
      addEvent(logFile, eventId, JOIN);
    }
    logFile.setString(MEMBER, selfId, JOINED, eventId);

    lastId++;
    return logFile;
  }

  public Config newLeaveEvent() {
    String eventId = String.format("%d.%d", currentGeneration, lastId + 1);

    Config newEventSection = new Config();
    addEvent(newEventSection, eventId, LEAVE);
    try {
      logFile.fromText(newEventSection.toText() + logFile.toText());
    } catch (ConfigInvalidException e) {
      log.warn("Could not place sections in order", e);
      addEvent(logFile, eventId, LEAVE);
    }
    logFile.unsetSection(MEMBER, selfId);

    lastId++;
    return logFile;
  }

  private void addEvent(Config cfg, String eventId, String eventType) {
    cfg.setString(EVENT, eventId, MEMBER, selfId);
    cfg.setString(EVENT, eventId, eventType, members.toString());
  }

  public int getMembership() {
    return members.size();
  }
}
