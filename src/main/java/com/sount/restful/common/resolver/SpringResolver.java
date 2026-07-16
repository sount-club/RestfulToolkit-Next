package com.sount.restful.common.resolver;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sount.restful.annotations.PathMappingAnnotation;
import com.sount.restful.annotations.SpringControllerAnnotation;
import com.sount.restful.common.spring.RequestMappingAnnotationHelper;
import com.sount.restful.method.RequestPath;
import com.sount.restful.method.PropertiesHandler;
import com.sount.restful.navigation.RestServiceItem;
import org.jetbrains.kotlin.psi.KtClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpringResolver extends BaseServiceResolver {
    static final Logger LOG = Logger.getInstance(SpringResolver.class);

    PropertiesHandler propertiesHandler;

    public SpringResolver(Module module) {
        myModule = module;
        propertiesHandler = new PropertiesHandler(module);
    }

    public SpringResolver(Project project) {
        myProject = project;
    }

    @Override
    public List<RestServiceItem> getRestServiceItemList(Project project, GlobalSearchScope globalSearchScope) {
        List<RestServiceItem> itemList = new ArrayList<>();
        Set<PsiClass> processedJavaClasses = new HashSet<>();
        Set<KtClass> processedKtClasses = new HashSet<>();
        SpringJavaEndpointResolver javaResolver = new SpringJavaEndpointResolver(this);
        SpringKotlinEndpointResolver kotlinResolver = new SpringKotlinEndpointResolver(this);
        SpringWebFluxRouterFunctionResolver webFluxRouterFunctionResolver = new SpringWebFluxRouterFunctionResolver(this);

        SpringControllerAnnotation[] supportedAnnotations = SpringControllerAnnotation.values();
        for (PathMappingAnnotation controllerAnnotation : supportedAnnotations) {
            itemList.addAll(javaResolver.collect(controllerAnnotation, project, globalSearchScope, processedJavaClasses));
            itemList.addAll(kotlinResolver.collect(controllerAnnotation, project, globalSearchScope, processedKtClasses));
        }
        itemList.addAll(webFluxRouterFunctionResolver.collect(project, globalSearchScope));

        LOG.info("SpringResolver found " + itemList.size() + " endpoints");
        return itemList;
    }

    protected List<RestServiceItem> getServiceItemList(PsiClass psiClass) {

        List<RestServiceItem> itemList = new ArrayList<>();
        List<RequestPath> classRequestPaths;
        try {
            classRequestPaths = RequestMappingAnnotationHelper.getRequestPaths(psiClass);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            LOG.debug("Failed to resolve class request mappings", e);
            return itemList;
        }
        if (classRequestPaths == null || classRequestPaths.isEmpty()) {
            return itemList;
        }

        for (PsiMethod psiMethod : getClassMethodsIncludingParents(psiClass)) {
            RequestPath[] methodRequestPaths;
            try {
                methodRequestPaths = RequestMappingAnnotationHelper.getRequestPaths(psiMethod);
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Throwable e) {
                LOG.debug("Failed to resolve method request mappings", e);
                continue;
            }
            if (isEmpty(methodRequestPaths)) {
                continue;
            }

            for (RequestPath classRequestPath : classRequestPaths) {
                for (RequestPath methodRequestPath : methodRequestPaths) {
                    String path = classRequestPath.getPath();
//                String path = tryReplacePlaceholderValueInPath( classRequestPath.getPath() );

                    RestServiceItem item = createRestServiceItem(psiMethod, path, methodRequestPath);
                    itemList.add(item);
                }
            }

        }
        return itemList;
    }

    private boolean isEmpty(RequestPath[] requestPaths) {
        return requestPaths == null || requestPaths.length == 0;
    }

    static Collection<PsiAnnotation> findAnnotationsByShortName(
            String shortName, Project project, GlobalSearchScope scope) {
        // Skip index query in dumb mode — stub index may be inconsistent during indexing.
        // This avoids triggering StubProcessingHelper.retrieveStubIdList errors on files
        // whose stub trees haven't been built yet (actual stub count = 0).
        if (DumbService.isDumb(project)) {
            LOG.debug("Skipping @" + shortName + " annotation search — project is in dumb mode");
            return new ArrayList<>();
        }
        try {
            // Use JavaAnnotationIndex.getAnnotations() (non-deprecated) instead of get()
            return JavaAnnotationIndex.getInstance().getAnnotations(shortName, project, scope);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            throw new EndpointResolutionException(
                    "Failed to query @" + shortName + " annotations from the project index", e);
        }
    }

}
