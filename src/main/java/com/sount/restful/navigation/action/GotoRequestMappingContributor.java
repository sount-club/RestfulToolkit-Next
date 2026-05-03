package com.sount.restful.navigation.action;


import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.sount.restful.common.ServiceHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;

//
public class GotoRequestMappingContributor implements ChooseByNameContributor {
    Module myModule;

    private volatile List<RestServiceItem> navItem = List.of();

    public GotoRequestMappingContributor(Module myModule) {
        this.myModule = myModule;
    }

    @NotNull
    @Override
    public String[] getNames(Project project, boolean onlyThisModuleChecked) {
        List<RestServiceItem> itemList;
        if (onlyThisModuleChecked && myModule != null) {
            itemList = ServiceHelper.buildRestServiceItemListUsingResolver(myModule);
        } else {
            itemList = ServiceHelper.buildRestServiceItemListUsingResolver(project);
        }

        navItem = itemList;
        return itemList.stream().map(RestServiceItem::getName).toArray(String[]::new);
    }

    @NotNull
    @Override
    public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean onlyThisModuleChecked) {
        return navItem.stream().filter(item -> item.getName().equals(name)).toArray(NavigationItem[]::new);
    }
}
