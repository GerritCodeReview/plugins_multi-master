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

package com.google.gerrit.plugins.multimaster.impl.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
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

import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GitRefManager {
  private static final Logger log = LoggerFactory
      .getLogger(GitRefManager.class);

  Gson gson;

  private GitRepositoryManager repoManager;
  private AllProjectsName repoName;

  private Repository repo;
  private String refNameBase;
  private String masterRefBase;

  private PersonIdent gerritIdent;

  @Inject
  public GitRefManager(GitRepositoryManager repoManager,
      AllProjectsName repoName, @RefNameBase String refNameBase,
      @GerritPersonIdent final PersonIdent gerritIdent, Gson gson) {
    this.repoManager = repoManager;
    this.gerritIdent = gerritIdent;

    this.repoName = repoName;
    this.refNameBase = refNameBase;

    this.masterRefBase = "masters/";

    this.gson = gson;
  }

  public void open() {
    log.info("Initializing ref manager for repo \"");

    try {
      repo = repoManager.openRepository(repoName);
    } catch (RepositoryNotFoundException e) {
      log.error("Repository not found", e);
      System.exit(1);
    } catch (IOException e) {
      log.error("IOException", e);
      System.exit(1);
    }

    log.info("Successfully initialized repo.");
  }

  public void close() {
    log.info("Stopping ref manager for repo \"" + repoName + "\"");
    repo.close();
  }

  /**
   * Deletes the specific ref
   *
   * @param peerId
   * @throws IOException
   */
  public void delete(final Peer.Id peerId) throws IOException {
    Ref ref = repo.getRef(refNameBase + masterRefBase + peerId);

    if (ref == null) {
      return;
    }

    RefUpdate refUpdate = repo.updateRef(ref.getName());
    refUpdate.setForceUpdate(true);
    refUpdate.disableRefLog();
    switch (refUpdate.delete()) {
      case NEW:
      case FAST_FORWARD:
      case FORCED:
      case NO_CHANGE:
        // Successful deletion.
        break;
      default:
        log.warn("IOException while attempting to delete ref.");
        // TODO: is this a problem?
    }
  }

  /**
   * @return all activity refs
   */
  public List<byte[]> getAll() {
    List<Ref> allMasterRefs = new ArrayList<Ref>();
    for (String refName : repo.getAllRefs().keySet()) {
      if (!refName.startsWith(refNameBase + masterRefBase)) {
        continue;
      }

      try {
        allMasterRefs.add(repo.getRef(refName));
      } catch (IOException e) {
        continue;
      }
    }

    List<byte[]> allActivities = new ArrayList<byte[]>();

    for (Ref ref : allMasterRefs) {
      byte[] activity = get(ref, "self");

      if (activity != null) {
        allActivities.add(activity);
      }
    }

    return allActivities;
  }

  public byte[] get(final String refName, final String path) {
    Ref ref;
    try {
      ref = repo.getRef(refNameBase + refName);
    } catch (IOException e) {
      return null;
    }

    if (ref == null) {
      return null;
    }

    return get(ref, path);
  }

  public byte[] get(final Ref ref, final String path) {
    try {
      RevWalk revWalk = new RevWalk(repo);
      RevCommit commit = revWalk.parseCommit(ref.getObjectId());
      RevTree tree = commit.getTree();

      TreeWalk treeWalk = new TreeWalk(repo);
      treeWalk.addTree(tree);
      treeWalk.setRecursive(true);
      treeWalk.setFilter(PathFilter.create(path));
      if (!treeWalk.next()) {
        return null;
      }

      ObjectId objectId = treeWalk.getObjectId(0);
      ObjectLoader loader = repo.open(objectId);

      return loader.getBytes();
    } catch (MissingObjectException e) {
      e.printStackTrace();
      return null;
    } catch (IncorrectObjectTypeException e) {
      e.printStackTrace();
      return null;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * @param peerId
   * @return the bytes specified by a peer id, null if the ref could not be found
   */
  public byte[] get(final Peer.Id peerId) {
    return get(masterRefBase + peerId, "self");
  }


  /**
   * Set an activity to create a ref
   *
   * @param peerId
   * @param bytes
   * @return success
   * @throws IOException
   */
  public boolean set(final Peer.Id peerId, final byte[] bytes)
      throws IOException {
    if (repo == null) {
      log.warn("Repository is null");
      return false;
    }

    return set("masters/" + peerId, "self", bytes);
  }

  /**
   * Set a ref
   * @param path
   * @param bytes if null will delete existing
   * @return true if successful false if not
   * @throws IOException
   */
  public boolean set(final String refName, final String path, final byte[] bytes)
      throws IOException {
    log.info("Writing to ref " + refNameBase + refName + " at " + path);

    Ref ref = repo.getRef(refNameBase + refName);

    TreeFormatter treeFormatter = new TreeFormatter();
    CommitBuilder commitBuilder = new CommitBuilder();

    ObjectInserter inserter = repo.newObjectInserter();

    ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, bytes);
    treeFormatter.append(path, FileMode.REGULAR_FILE, blobId);

    ObjectId treeId = treeFormatter.insertTo(inserter);

    commitBuilder.setAuthor(gerritIdent);
    commitBuilder.setCommitter(gerritIdent);
    commitBuilder.setMessage("Updating cluster");
    commitBuilder.setTreeId(treeId);

    byte[] commitBytes = commitBuilder.build();
    ObjectId commitId = inserter.insert(Constants.OBJ_COMMIT, commitBytes);

    inserter.flush();

    RefUpdate refUpdate = null;
    refUpdate = repo.updateRef(refNameBase + refName);

    refUpdate.setNewObjectId(commitId);
    RefUpdate.Result updateResult = refUpdate.forceUpdate();

    while (updateResult == RefUpdate.Result.LOCK_FAILURE) {
      updateResult = refUpdate.forceUpdate();
    }

    if (updateResult == RefUpdate.Result.IO_FAILURE
        || updateResult == RefUpdate.Result.REJECTED
        || updateResult == RefUpdate.Result.NOT_ATTEMPTED) {
      log.error("Failed to update ref");
      return false;
    }

    return true;
  }
}
