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

package com.googlesource.gerrit.plugins.multimaster;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.internal.UniqueAnnotations;

import com.googlesource.gerrit.plugins.multimaster.cluster.GitRefPeerRegistry;
import com.googlesource.gerrit.plugins.multimaster.cluster.MembershipLogFactory;
import com.googlesource.gerrit.plugins.multimaster.cluster.PeerRegistry;
import com.googlesource.gerrit.plugins.multimaster.cluster.SelfId;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class MultiMasterModule extends AbstractModule {
  private static final Logger log = LoggerFactory
      .getLogger(MultiMasterModule.class);

  @Inject
  private SitePaths site;

  @Inject
  @GerritServerConfig
  Config config;

  @Override
  protected void configure() {
    String peerId = getPeerId();
    if (peerId == null) {
      log.warn("Cannot contruct peer ID from httpd.listenURL"
          + "; do not use \"localhost\" or \"*\" as hostname."
          + " Membership log usage will be disabled.");
    } else {
      bind(String.class).annotatedWith(SelfId.class).toInstance(peerId);

      install(new FactoryModuleBuilder().build(MembershipLogFactory.class));
      bind(AllProjectsName.class).toProvider(AllProjectsNameProvider.class).in(
          Scopes.SINGLETON);
      bind(PeerRegistry.class).to(GitRefPeerRegistry.class);

      bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create())
          .to(StateManager.class);
    }
    bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create()).to(
        CacheFlusher.class);
  }

  private String getPeerId() {
    String url = config.getString("httpd", null, "listenUrl");
    if (url != null) {
      URI u;
      try {
        u = new URI(url);
      } catch (URISyntaxException e) {
        return null;
      }
      if (u.getHost() == null || u.getHost().equals("localhost")) {
        String hostname = SystemReader.getInstance().getHostname();
        if (hostname.equals("localhost")) {
          return null;
        }
        String oldHostname = "";
        if (u.getAuthority().equals("*") || u.getAuthority().startsWith("*:")) {
          oldHostname = "*";
        } else if (u.getHost().equals("localhost")) {
          oldHostname = "localhost";
        } else {
          return null;
        }
        final int s = url.indexOf(oldHostname);
        url =
            url.substring(0, s) + hostname
                + url.substring(s + oldHostname.length());
      }
    }
    return url;
  }

  @Provides
  @MultiMasterConfig
  @Nullable
  public Config provideMultiMasterConfig() {
    File cfgPath = new File(site.etc_dir, "multimaster.config");
    if (!cfgPath.exists()) {
      log.warn(cfgPath.getAbsolutePath() + " not found");
      return null;
    }

    FileBasedConfig cfg = new FileBasedConfig(cfgPath, FS.DETECTED);

    try {
      cfg.load();
    } catch (ConfigInvalidException e) {
      log.warn(
          String.format("Config file %s is invalid: %s", cfgPath,
              e.getMessage()), e);
      return null;
    } catch (IOException e) {
      log.warn(String.format("Cannot read %s: %s", cfgPath, e.getMessage()), e);
      return null;
    }
    return cfg;
  }
}
