// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")

package com.intellij.ide.startup.importSettings.testActions

import com.intellij.ide.startup.importSettings.data.NotificationData
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.ide.startup.importSettings.data.SyncService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.Signal
import javax.swing.JComponent

class ErrorTestDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val settService = SettingsService.getInstance()
    val error = settService.error

    if(error !is Signal) return

    val dialog = object : DialogWrapper(null) {
      init {
        init()
      }

      override fun createCenterPanel(): JComponent {
        val s = "com.intellij.o penapi. project.impl .ProjectManag erImplKt.acc essche ckOl \ndTrusted Sta teAnd Mi grate (ProjectManag erImpl.kt:1)\n" +
                "\tat com.intellij.openap i.project.i mpl.ProjectManagerImplKti nitProje ct.inv okeSuspend(ProjectManagerImpl.kt:1233)"

        val pane = panel {
          row {
            button( "Error With Actions") {
              error.fire(object : NotificationData {
                override val status: NotificationData.NotificationStatus = NotificationData.NotificationStatus.ERROR
                override val message: String = s

                override val customActionList: List<NotificationData.Action> = arrayListOf(
                  NotificationData.Action("Skip") {},
                  NotificationData.Action("Try Again") {}
                )

              })
            }
          }
          row {
            button("Error") {
              error.fire(object : NotificationData {
                override val status: NotificationData.NotificationStatus = NotificationData.NotificationStatus.WARNING
                override val message: String = "ага. тогда сигнал. если решим, что неудобно переделаем"
                override val customActionList: List<NotificationData.Action> = emptyList()

              })
            }
          }
          row {
            button("close dialog") {
              settService.doClose.fire(Unit)
            }
          }
          row {
            val state = SettingsService.getInstance().getSyncService().syncState
            if(state is Property<SyncService.SYNC_STATE>) {

              comboBox(SyncService.SYNC_STATE.entries).onChanged {
                if (it.selectedItem is SyncService.SYNC_STATE) {
                  state.value = it.selectedItem as SyncService.SYNC_STATE
                }
              }.applyToComponent {
                selectedItem = state.value
              }


              val component = textField().component
              state.advise(Lifetime.Eternal) {
                component.text = it.toString()
              }

            }
          }
        }

        return pane
      }
    }

    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()
  }
}