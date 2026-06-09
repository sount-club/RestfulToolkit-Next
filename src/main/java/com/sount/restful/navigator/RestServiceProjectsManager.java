/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sount.restful.navigator;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.sount.restful.navigation.action.RestServiceItem;
import com.sount.restful.search.EndpointIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service(Service.Level.PROJECT)
@State(name = "RestServiceProjectsManager", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class RestServiceProjectsManager implements PersistentStateComponent<RestServicesNavigatorState>, Disposable {
    protected final Project myProject;

    private RestServicesNavigatorState myState = new RestServicesNavigatorState();

    public static RestServiceProjectsManager getInstance(Project p) {
        return p.getService(RestServiceProjectsManager.class);
    }

    public RestServiceProjectsManager(Project project) {
        myProject = project;
    }

    @Override
    public void dispose() {
    }

    @Nullable
    @Override
    public RestServicesNavigatorState getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull RestServicesNavigatorState state) {
        myState = state;
    }

    public void getServiceProjects(@NotNull java.util.function.Consumer<List<RestServiceProject>> callback) {
        // Use cached index; group items by module into RestServiceProject list
        List<RestServiceItem> items = EndpointIndex.getInstance(myProject).getItems();
        List<RestServiceProject> projects = groupByModule(items);
        callback.accept(projects);
    }

    public void forceUpdateAllProjects() {
        EndpointIndex.getInstance(myProject).refresh();
    }

    private static @NotNull List<RestServiceProject> groupByModule(@NotNull List<RestServiceItem> items) {
        Map<Module, List<RestServiceItem>> grouped = new LinkedHashMap<>();
        for (RestServiceItem item : items) {
            Module module = item.getModule();
            if (module != null) {
                grouped.computeIfAbsent(module, k -> new ArrayList<>()).add(item);
            }
        }
        List<RestServiceProject> projects = new ArrayList<>();
        for (Map.Entry<Module, List<RestServiceItem>> entry : grouped.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                projects.add(new RestServiceProject(entry.getKey(), entry.getValue()));
            }
        }
        return projects;
    }
}
