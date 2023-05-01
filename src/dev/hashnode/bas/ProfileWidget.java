// Copyright 2020 Bas Leijdekkers Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package dev.hashnode.bas;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSettingListener;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurableProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ClickListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;

/**
 * @author Bas Leijdekkers
 */
public class ProfileWidget extends TextPanel.WithIconAndArrows implements CustomStatusBarWidget {
    @NotNull
    private final Project project;
    private StatusBar statusBar;
    private boolean disposed = false;

    public ProfileWidget(Project project) {
        this.project = project;
        new ClickListener() {
            @Override
            public boolean onClick(@NotNull MouseEvent event, int clickCount) {
                final Configurable provider =
                        ConfigurableExtensionPointUtil.createProjectConfigurableForProvider(project,
                                ErrorsConfigurableProvider.class);
                if (provider != null) {
                    ShowSettingsUtil.getInstance().editConfigurable(project, provider);
                }
                return true;
            }
        }.installOn(this, true);
        final MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                updateStatus();
            }

            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                updateStatus();
            }
        });
        connection.subscribe(FileHighlightingSettingListener.SETTING_CHANGE, (r, s) -> updateStatus());
        connection.subscribe(PowerSaveMode.TOPIC, this::updateStatus);
        connection.subscribe(ProfileChangeAdapter.TOPIC, new ProfileChangeAdapter() {
            @Override
            public void profileActivated(@Nullable InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
                updateStatus();
            }

            @Override
            public void profileChanged(@Nullable InspectionProfile profile) {
                updateStatus();
            }
        });
        connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
            @Override
            public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
                updateStatus();
            }
        });

        updateStatus();
    }

    private void updateStatus() {
        updateStatus(getCurrentFile());
    }

    @Nullable
    private PsiFile getCurrentFile() {
        final Editor editor = StatusBarUtil.getCurrentTextEditor(statusBar);
        if (editor == null) {
            return null;
        }
        final Document document = editor.getDocument();
        return PsiDocumentManager.getInstance(project).getPsiFile(document);
    }

    private void updateStatus(@Nullable PsiFile file) {
        if (disposed) {
            return;
        }
        final ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(project);
        final InspectionProfileImpl profile = profileManager.getCurrentProfile();
        setText(profile.getDisplayName());
        final boolean highlighting =
                file != null && DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file);
        final Icon icon;
        final String text;
        if (highlighting) {
            if (PowerSaveMode.isEnabled()) {
                icon = IconLoader.getDisabledIcon(AllIcons.Ide.HectorOff);
                text = "Highlighting level: none (power save mode)";
            }
            else if (HighlightingLevelManager.getInstance(project).shouldInspect(file)) {
                icon = AllIcons.Ide.HectorOn;
                text = "Highlighting level: all problems";
            }
            else if (HighlightingLevelManager.getInstance(project).shouldHighlight(file)) {
                icon = AllIcons.Ide.HectorSyntax;
                text = "Highlighting level: syntax";
            }
            else {
                icon = AllIcons.Ide.HectorOff;
                text = "Highlighting level: none";
            }
        }
        else {
            icon = IconLoader.getDisabledIcon(AllIcons.Ide.HectorOff);
            text = "No highlighting";
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            setIcon(icon);
            setToolTipText(text);
        });
    }

    @Override
    public void setIcon(@Nullable Icon icon) {
        super.setIcon(icon);
        repaint();
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public @NotNull String ID() {
        return "HectorTheInspectorWidget";
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Override
    public void dispose() {
        disposed = true;
        statusBar = null;
    }
}
