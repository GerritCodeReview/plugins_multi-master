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

package com.googlesource.gerrit.plugins.multimaster;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.inject.AbstractModule;
import com.googlesource.gerrit.plugins.multimaster.pubsub.EventCallback;
import com.googlesource.gerrit.plugins.multimaster.pubsub.TopicNotifier;
import com.googlesource.gerrit.plugins.multimaster.pubsub.TopicSubscriber;
import com.googlesource.gerrit.plugins.multimaster.pubsub.impl.zeromq.EventCallbackImpl;
import com.googlesource.gerrit.plugins.multimaster.pubsub.impl.zeromq.TopicNotifierImpl;
import com.googlesource.gerrit.plugins.multimaster.pubsub.impl.zeromq.TopicSubscriberImpl;

class MultiMasterModule extends AbstractModule {
  @Override
  protected void configure() {

    bind(TopicNotifier.class).to(TopicNotifierImpl.class);
    bind(TopicSubscriber.class).to(TopicSubscriberImpl.class);
    bind(EventCallback.class).to(EventCallbackImpl.class);

    DynamicSet.bind(binder(), CacheRemovalListener.class).to(
        LocalCacheRemovalListenerImpl.class);
    DynamicSet.bind(binder(), LifecycleListener.class).to(
        MasterLifecycleManager.class);

  }
}
