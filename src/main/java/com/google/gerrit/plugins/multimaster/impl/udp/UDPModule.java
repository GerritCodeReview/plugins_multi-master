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

import java.util.concurrent.TimeUnit;

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
import com.google.gerrit.server.config.ConfigUtil;
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
    return ConfigUtil.getTimeUnit(config, CONFIG_TITLE, null, "baseRetryTime",
        500L, TimeUnit.MILLISECONDS);
  }

  @Provides
  @MaxTimeout
  public long getMaxTimeout(@MultiMasterConfig final Config config) {
    return ConfigUtil.getTimeUnit(config, CONFIG_TITLE, null, "maxTimeout",
        10000L, TimeUnit.MILLISECONDS);
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
