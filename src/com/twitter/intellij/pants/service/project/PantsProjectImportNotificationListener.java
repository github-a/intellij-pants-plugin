// Copyright 2016 Pants project contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package com.twitter.intellij.pants.service.project;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.Project;
import com.twitter.intellij.pants.components.impl.PantsMetrics;
import com.twitter.intellij.pants.util.PantsUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This class overrides the methods that will be called before/after project import/refresh.
 */
public class PantsProjectImportNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  @Override
  public void onEnd(@NotNull ExternalSystemTaskId id) {
    Project project = id.findProject();
    if (project == null) {
      return;
    }
    PantsMetrics.markResolveEnd();
    PantsMetrics.timeNextIndexing(project);
    // Sync files as generated sources may have changed after `pants export` called
    // due to import and refresh.
    PantsUtil.synchronizeFiles();
    super.onEnd(id);
  }

  @Override
  public void onQueued(@NotNull ExternalSystemTaskId id, String workingDir) {
    super.onQueued(id, workingDir);
    if (id.findProject() == null) {
      return;
    }
    PantsMetrics.markResolveStart();
  }
}
