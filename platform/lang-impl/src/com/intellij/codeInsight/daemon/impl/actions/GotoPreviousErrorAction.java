
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.codeInsight.daemon.impl.GotoNextErrorUtilsKt;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class GotoPreviousErrorAction extends BaseCodeInsightAction implements DumbAware {

  public GotoPreviousErrorAction() {
    super(false);
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler(@NotNull DataContext dataContext) {
    return new GotoNextErrorHandler(true, GotoNextErrorUtilsKt.getTrafficHighlightSeverity(dataContext));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    GotoNextErrorUtilsKt.reportTrafficHighlightStatistic(e, false);
    super.actionPerformed(e);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file);
  }
}