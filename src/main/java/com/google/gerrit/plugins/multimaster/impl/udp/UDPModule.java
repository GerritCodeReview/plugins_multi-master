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

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.plugins.multimaster.ConfigProvider;
import com.google.gerrit.plugins.multimaster.MultiMasterConfig;
import com.google.gerrit.plugins.multimaster.cache.Evictor;
import com.google.gerrit.plugins.multimaster.impl.udp.config.BaseRetryTime;
import com.google.gerrit.plugins.multimaster.impl.udp.config.MaxMessageSize;
import com.google.gerrit.plugins.multimaster.impl.udp.config.MaxSenders;
import com.google.gerrit.plugins.multimaster.impl.udp.config.MaxTimeout;
import com.google.gerrit.plugins.multimaster.impl.udp.config.ReceiveHost;
import com.google.gerrit.plugins.multimaster.impl.udp.config.ReceivePort;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class UDPModule extends AbstractModule {
  private static final Logger log = LoggerFactory.getLogger(UDPModule.class);

  private static final String CONFIG_TITLE = "UDPEvictor";

  @Override
  protected void configure() {
    bind(UDPMessageSender.class).in(Scopes.SINGLETON);
    bind(UDPMessageReceiver.class);
    bind(Evictor.class).to(UDPEvictor.class).in(Scopes.SINGLETON);
  }

  @Provides
  @MaxSenders
  public int getMaxSenders(@MultiMasterConfig final Config config,
      ConfigProvider factory) {
    final String[] want =
        config.getStringList(CONFIG_TITLE, null, "maxSenders");

    if (want != null && want.length == 1) {
      if (want[0].equals("*")) {
        return Runtime.getRuntime().availableProcessors();
      } else {
        return Integer.parseInt(want[0]);
      }
    }

    log.error("Misconfigured number of senders");
    System.exit(1);
    return -1;
  }

  @Provides
  @ReceiveHost
  public String getReceiveHost(@MultiMasterConfig final Config config) {
    final String[] want =
        config.getStringList(CONFIG_TITLE, null, "receiveHost");

    if (want != null && want.length == 1) {
      return want[0];
    }

    log.error("Misconfigured host.");
    System.exit(1);
    return null;
  }

  @Provides
  @ReceivePort
  public int getReceivePort(@MultiMasterConfig final Config config) {
    final String[] want =
        config.getStringList(CONFIG_TITLE, null, "receivePort");

    if (want != null && want.length == 1) {
      if (want[0].equals("*")) {
        return 0;
      } else {
        return Integer.parseInt(want[0]);
      }
    }

    log.error("Misconfigured port");
    System.exit(1);
    return -1;
  }

  @Provides
  @BaseRetryTime
  public long getBaseRetryTime(@MultiMasterConfig final Config config) {
    final String[] want =
        config.getStringList(CONFIG_TITLE, null, "baseRetryTime");

    if (want != null && want.length == 1) {
      return Long.parseLong(want[0]);
    }

    return 500L;
  }

  @Provides
  @MaxTimeout
  public long getMaxTimeout(@MultiMasterConfig final Config config) {
    final String[] want =
        config.getStringList(CONFIG_TITLE, null, "maxTimeout");

    if (want != null && want.length == 1) {
      return Long.parseLong(want[0]);
    }

    return 10000L;
  }

  @Provides
  @MaxMessageSize
  public int getMaxMessageSize(@MultiMasterConfig final Config config) {
    final String[] want =
        config.getStringList(CONFIG_TITLE, null, "maxMessageSize");

    if (want != null && want.length == 1) {
      return Integer.parseInt(want[0]);
    }

    return 1400;
  }
}
