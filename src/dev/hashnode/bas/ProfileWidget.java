// Copyright 2020 Bas Leijdekkers Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package dev.hashnode.bas;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSettingListener;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.util.concurrency.AppExecutorUtil;
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
        if (isDisposed()) {
            return;
        }
        final ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(project);
        final InspectionProfileImpl profile = profileManager.getCurrentProfile();
        setText(profile.getDisplayName());
        ReadAction.nonBlocking(() -> {
                    final PsiFile file = getCurrentFile();
                    final boolean highlighting = file != null &&
                            DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file);
                    if (highlighting) {
                        if (PowerSaveMode.isEnabled()) {
                            return new IconText(IconLoader.getDisabledIcon(AllIcons.Ide.HectorOff),
                                                "Highlighting level: none (power save mode)");
                        } else if (HighlightingLevelManager.getInstance(project).shouldInspect(file)) {
                            return new IconText(AllIcons.Ide.HectorOn, "Highlighting level: all problems");
                        } else if (HighlightingLevelManager.getInstance(project).shouldHighlight(file)) {
                            return new IconText(AllIcons.Ide.HectorSyntax, "Highlighting level: syntax");
                        } else {
                            return new IconText(AllIcons.Ide.HectorOff, "Highlighting level: none");
                        }
                    } else {
                        return new IconText(IconLoader.getDisabledIcon(AllIcons.Ide.HectorOff), "No highlighting");
                    }
                })
                .finishOnUiThread(ModalityState.any(), iconText -> {
                    if (isDisposed()) {
                        return;
                    }
                    setIcon(iconText.icon);
                    setToolTipText(iconText.text);
                })
                .expireWith(this)
                .submit(AppExecutorUtil.getAppExecutorService());
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

    record IconText(Icon icon, String text) {}

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

    private boolean isDisposed() {
        return statusBar == null;
    }

    @Override
    public void dispose() {
        statusBar = null;
    }
}
