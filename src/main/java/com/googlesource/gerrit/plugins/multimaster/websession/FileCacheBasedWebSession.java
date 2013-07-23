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

package com.googlesource.gerrit.plugins.multimaster.websession;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.CacheBasedWebSession;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.WebSessionManager;
import com.google.gerrit.httpd.WebSessionManagerFactory;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.IdentifiedUser.RequestFactory;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletScopes;

import com.googlesource.gerrit.plugins.multimaster.MultiMasterConfig;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestScoped
public class FileCacheBasedWebSession extends CacheBasedWebSession {
  public static class Module extends AbstractModule {
    private static final Logger log = LoggerFactory.getLogger(Module.class);

    @Inject
    @MultiMasterConfig
    @Nullable
    private Config cfg;

    @Override
    protected void configure() {
      bindScope(RequestScoped.class, ServletScopes.REQUEST);
      if (cfg == null) {
        log.warn("Config file not found. Using default cache.");
        return;
      }
      if (cfg.getString("cache", WebSessionManager.CACHE_NAME, "directory")
          == null) {
        log.warn("cache." + WebSessionManager.CACHE_NAME
            + ".directory must be specified. Using default cache.");
        return;
      }

      DynamicItem.bind(binder(), WebSession.class)
          .to(FileCacheBasedWebSession.class).in(RequestScoped.class);
    }
  }

  @Inject
  FileCacheBasedWebSession(final Provider<HttpServletRequest> request,
      final Provider<HttpServletResponse> response,
      final WebSessionManagerFactory managerFactory,
      final FileWebSessionCache cache, final AuthConfig authConfig,
      final Provider<AnonymousUser> anonymousProvider,
      final RequestFactory identified) {
    super(request.get(), response.get(), managerFactory.create(cache),
        authConfig, anonymousProvider, identified);
  }
}
