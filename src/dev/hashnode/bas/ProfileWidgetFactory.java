// Copyright 2020 Bas Leijdekkers Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package dev.hashnode.bas;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ProfileWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull
    String getId() {
        return "HectorTheInspectorWidgetFactory";
    }

    @Override
    public @Nls
    @NotNull
    String getDisplayName() {
        return "Hector the Inspector";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull
    StatusBarWidget createWidget(@NotNull Project project) {
        return new ProfileWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget statusBarWidget) {
        Disposer.dispose(statusBarWidget);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }
}
