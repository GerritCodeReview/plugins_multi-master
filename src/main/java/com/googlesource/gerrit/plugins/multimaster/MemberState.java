package com.googlesource.gerrit.plugins.multimaster;

import com.google.inject.Singleton;

@Singleton
public class MemberState {
  public boolean isDegraded() {
    return true;
  }
}
