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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class GitRefPeerRegistry implements PeerRegistry {
  private static final Logger log = LoggerFactory
      .getLogger(GitRefPeerRegistry.class);

  private final String REF_NAME = "refs/meta/cluster";
  private final String MEMBERS_FILE_NAME = "members";

  private final Repository repo;
  private final PersonIdent gerritIdent;
  private final MembershipLogFactory membershipLogFactory;

  @Inject
  public GitRefPeerRegistry(AllProjectsName allProjectsName,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent gerritIdent,
      MembershipLogFactory membershipLogFactory) {
    this.gerritIdent = gerritIdent;
    this.membershipLogFactory = membershipLogFactory;
    try {
      repo = repoManager.openRepository(allProjectsName);
    } catch (RepositoryNotFoundException e) {
      throw new ProvisionException("Cannot open repo " + allProjectsName, e);
    } catch (IOException e) {
      throw new ProvisionException("Cannot open repo " + allProjectsName, e);
    }
  }

  public void register() throws IOException {
    Config members = getMembersFile();
    if (members == null) {
      members = new Config();
    }
    MembershipLog membershipLog = membershipLogFactory.create(members);
    members = membershipLog.newJoinEvent();

    RevCommit prevCommit = null;
    Ref ref = repo.getRef(REF_NAME);
    if (ref != null) {
      RevWalk revWalk = new RevWalk(repo);
      prevCommit = revWalk.parseCommit(ref.getObjectId());
    }

    ObjectInserter inserter = repo.newObjectInserter();
    ObjectId blobId =
        inserter.insert(Constants.OBJ_BLOB, members.toText().getBytes());
    TreeFormatter treeFormatter = new TreeFormatter();
    treeFormatter.append(MEMBERS_FILE_NAME, FileMode.REGULAR_FILE, blobId);
    ObjectId treeId = treeFormatter.insertTo(inserter);

    CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(gerritIdent);
    commitBuilder.setCommitter(gerritIdent);
    commitBuilder.setMessage("Peer joining cluster");
    commitBuilder.setTreeId(treeId);
    if (prevCommit != null) {
      commitBuilder.setParentId(prevCommit.getId());
    }

    byte[] commitBytes = commitBuilder.build();
    ObjectId commitId = inserter.insert(Constants.OBJ_COMMIT, commitBytes);

    inserter.flush();

    RefUpdate refUpdate = repo.updateRef(REF_NAME);
    refUpdate.setNewObjectId(commitId);
    RefUpdate.Result updateResult = refUpdate.forceUpdate();

    while (updateResult == RefUpdate.Result.LOCK_FAILURE) {
      updateResult = refUpdate.forceUpdate();
    }

    if (updateResult == RefUpdate.Result.REJECTED) {
      register();
    } else if (updateResult == RefUpdate.Result.IO_FAILURE
        || updateResult == RefUpdate.Result.NOT_ATTEMPTED) {
      log.error("Failed to update ref");
    }
  }

  /**
   * @return the members file from refs/meta/cluster, or null if not found
   * @throws IOException
   */
  @Nullable
  private Config getMembersFile() throws IOException {
    Ref ref = repo.getRef(REF_NAME);
    if (ref == null) {
      return null;
    }

    RevWalk revWalk = new RevWalk(repo);
    RevCommit commit = revWalk.parseCommit(ref.getObjectId());
    RevTree tree = commit.getTree();

    TreeWalk treeWalk = new TreeWalk(repo);
    treeWalk.addTree(tree);
    treeWalk.setRecursive(true);
    treeWalk.setFilter(PathFilter.create(MEMBERS_FILE_NAME));

    if (treeWalk.next()) {
      ObjectId objectId = treeWalk.getObjectId(0);
      ObjectLoader objLoader = repo.open(objectId);
      byte[] configData = objLoader.getBytes();

      try {
        Config cfg = new Config();
        cfg.fromText(new String(configData));
        return cfg;
      } catch (ConfigInvalidException e) {
        log.warn("Invalid file \"" + MEMBERS_FILE_NAME + "\" in ref "
            + REF_NAME);
      }
    }
    return null;
  }
}
