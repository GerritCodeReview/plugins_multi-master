// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.multimaster;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Scopes;

import com.ericsson.gerrit.plugins.multimaster.event.EventModule;
import com.ericsson.gerrit.plugins.multimaster.forwarder.rest.RestForwarderModule;
import com.ericsson.gerrit.plugins.multimaster.index.IndexModule;

class Module extends LifecycleModule {

  @Override
  protected void configure() {
    bind(Configuration.class).in(Scopes.SINGLETON);
    install(new RestForwarderModule());
    install(new EventModule());
    install(new IndexModule());
  }
}