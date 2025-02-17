// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

interface Subproject {
  val workspace: Project
  val name: @Nls String
  val projectPath: String

  fun getModules(): List<Module>
  fun removeSubproject()
}