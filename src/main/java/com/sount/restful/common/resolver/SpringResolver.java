package com.sount.restful.common.resolver;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sount.restful.annotations.PathMappingAnnotation;
import com.sount.restful.annotations.SpringControllerAnnotation;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.spring.RequestMappingAnnotationHelper;
import com.sount.restful.method.RequestPath;
import com.sount.restful.method.action.PropertiesHandler;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.kotlin.asJava.LightClassUtil;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtModifierList;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtValueArgument;
import org.jetbrains.kotlin.psi.KtValueArgumentList;
import org.jetbrains.kotlin.psi.KtValueArgumentName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpringResolver extends BaseServiceResolver {
    private static final Logger LOG = Logger.getInstance(SpringResolver.class);

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

        // TODO: 这种实现的局限了其他方式实现的url映射（xml（类似struts），webflux routers）
        SpringControllerAnnotation[] supportedAnnotations = SpringControllerAnnotation.values();
        for (PathMappingAnnotation controllerAnnotation : supportedAnnotations) {

            // java: 标注了 (Rest)Controller 注解的类，即 Controller 类
            Collection<PsiAnnotation> psiAnnotations = findAnnotationsByShortName(
                    controllerAnnotation.getShortName(), project, globalSearchScope);
            LOG.debug("Found " + psiAnnotations.size() + " @" + controllerAnnotation.getShortName() + " annotations");
            for (PsiAnnotation psiAnnotation : psiAnnotations) {
                if (!(psiAnnotation.getParent() instanceof PsiModifierList psiModifierList)) {
                    continue;
                }
                PsiElement psiElement = psiModifierList.getParent();

                if (!(psiElement instanceof PsiClass psiClass)) {
                    continue;
                }
                // Deduplicate: same class may have multiple controller annotations
                if (!processedJavaClasses.add(psiClass)) continue;

                List<RestServiceItem> serviceItemList = getServiceItemList(psiClass);
                itemList.addAll(serviceItemList);
            }



            // kotlin:
            try {
                Collection<KtAnnotationEntry> ktAnnotationEntries = KotlinAnnotationsIndex.Helper.get(controllerAnnotation.getShortName(), project, globalSearchScope);
                LOG.debug("Found " + ktAnnotationEntries.size() + " Kotlin @" + controllerAnnotation.getShortName() + " annotations");
                for (KtAnnotationEntry ktAnnotationEntry : ktAnnotationEntries) {
                    PsiElement modifierList = ktAnnotationEntry.getParent();
                    if (modifierList == null || !(modifierList.getParent() instanceof KtClass ktClass)) {
                        continue;
                    }

                    // Deduplicate: same class may have multiple controller annotations
                    if (!processedKtClasses.add(ktClass)) continue;

                    List<RequestPath> classRequestPaths = getRequestPaths(ktClass);
                    if (classRequestPaths.isEmpty()) {
                        continue;
                    }

                    Set<String> processedKtMethodNames = new HashSet<>();
                    for (KtNamedFunction fun : getKtNamedFunctions(ktClass)) {
                        processedKtMethodNames.add(fun.getName());
                        List<RequestPath> methodRequestPaths = getRequestPaths(fun);
                        if (methodRequestPaths.isEmpty()) {
                            continue;
                        }

                        for (RequestPath classRequestPath : classRequestPaths) {
                            for (RequestPath methodRequestPath : methodRequestPaths) {
                                RestServiceItem item = createRestServiceItem(fun, classRequestPath.getPath(), methodRequestPath);
                                itemList.add(item);
                            }
                        }
                    }

                    // Use Light Class bridge to add inherited Kotlin/Java methods.
                    List<PsiMethod> ktMethods = getAllKtMethods(ktClass);
                    for (PsiMethod method : ktMethods) {
                        if (processedKtMethodNames.contains(method.getName())) {
                            continue;
                        }
                        RequestPath[] methodRequestPaths = RequestMappingAnnotationHelper.getRequestPaths(method);
                        if (isEmpty(methodRequestPaths)) {
                            continue;
                        }

                        for (RequestPath classRequestPath : classRequestPaths) {
                            for (RequestPath methodRequestPath : methodRequestPaths) {
                                String path = classRequestPath.getPath();
                                RestServiceItem item = createRestServiceItem(getKtMethodSourceElement(method), path, methodRequestPath);
                                itemList.add(item);
                            }
                        }
                    }
                }
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Throwable e) {
                // Kotlin plugin may not be installed or index not available
                LOG.debug("Kotlin annotation index not available for @" + controllerAnnotation.getShortName(), e);
            }

        }

        LOG.info("SpringResolver found " + itemList.size() + " endpoints");
        return itemList;
    }

    protected List<RestServiceItem> getServiceItemList(PsiClass psiClass) {

        List<RestServiceItem> itemList = new ArrayList<>();
        List<RequestPath> classRequestPaths = RequestMappingAnnotationHelper.getRequestPaths(psiClass);
        if (classRequestPaths == null || classRequestPaths.isEmpty()) {
            return itemList;
        }

        for (PsiMethod psiMethod : getClassMethodsIncludingParents(psiClass)) {
            RequestPath[] methodRequestPaths = RequestMappingAnnotationHelper.getRequestPaths(psiMethod);
            if (isEmpty(methodRequestPaths)) {
                continue;
            }

            for (RequestPath classRequestPath : classRequestPaths) {
                for (RequestPath methodRequestPath : methodRequestPaths) {
                    String path =  classRequestPath.getPath();
//                String path = tryReplacePlaceholderValueInPath( classRequestPath.getPath() );

                    RestServiceItem item = createRestServiceItem(psiMethod, path, methodRequestPath);
                    itemList.add(item);
                }
            }

        }
        return itemList;
    }

    private List<KtNamedFunction> getKtNamedFunctions(KtClass ktClass) {
        List<KtNamedFunction> ktNamedFunctions = new ArrayList<>();
        List<KtDeclaration> declarations = ktClass.getDeclarations();

        for (KtDeclaration declaration : declarations) {
            if (declaration instanceof KtNamedFunction) {
                KtNamedFunction fun = (KtNamedFunction) declaration;
                ktNamedFunctions.add(fun);

            }
        }
        return ktNamedFunctions;
    }

    /**
     * Get all methods from a Kotlin class including inherited ones via Light Class bridge.
     * Manually traverses class hierarchy to collect methods from parent classes.
     * Filters out java.lang.Object methods.
     */
    private List<PsiMethod> getAllKtMethods(KtClass ktClass) {
        List<PsiMethod> methods = new ArrayList<>();
        try {
            if (LightClassUtil.INSTANCE.canGenerateLightClass(ktClass)) {
                PsiClass lightClass = LightClassUtilsKt.toLightClass(ktClass);
                if (lightClass != null) {
                    methods.addAll(getClassMethodsIncludingParents(lightClass));
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to get Kotlin light class methods", e);
        }
        return methods;
    }

    private PsiElement getKtMethodSourceElement(PsiMethod method) {
        try {
            PsiElement unwrapped = LightClassUtilsKt.getUnwrapped(method);
            if (unwrapped instanceof KtNamedFunction) {
                return unwrapped;
            }
        } catch (Exception e) {
            LOG.debug("Failed to unwrap Kotlin light method", e);
        }
        return method;
    }

    private boolean isEmpty(RequestPath[] requestPaths) {
        return requestPaths == null || requestPaths.length == 0;
    }

    private List<RequestPath> getRequestPaths(KtClass ktClass) {
        String defaultPath = "/";
        //方法注解
        KtModifierList modifierList = ktClass.getModifierList();
        if (modifierList == null) {
            return new ArrayList<>();
        }
        List<KtAnnotationEntry> annotationEntries = modifierList.getAnnotationEntries();

        List<RequestPath>  requestPaths = getRequestMappings( defaultPath, annotationEntries);
        return requestPaths;
    }

    private List<RequestPath> getRequestPaths(KtNamedFunction fun) {
//        String methodBody = fun.getBodyExpression().getText();// 方法体
//        String defaultPath = fun.getName();
        String defaultPath = "/";
        //方法注解
        KtModifierList modifierList = fun.getModifierList();
        if (modifierList == null) {
            return new ArrayList<>();
        }
        List<KtAnnotationEntry> annotationEntries = modifierList.getAnnotationEntries();
        List<RequestPath> requestPaths = getRequestMappings( defaultPath, annotationEntries);
        return requestPaths;
    }

    private List<RequestPath>  getRequestMappings(String defaultPath, List<KtAnnotationEntry> annotationEntries) {
        List<RequestPath> requestPaths = new ArrayList<>();
        for (KtAnnotationEntry entry : annotationEntries) {
//            List<RequestPath> requestMappings = getRequestMappings(defaultPath, entry);
            List<RequestPath> requestMappings = getRequestMappings(defaultPath, entry);
            requestPaths.addAll(requestMappings);
        }
        return requestPaths;
    }

   /* private List<RequestPath> getRequestMappings(String defaultPath, KtAnnotationEntry entry) {
        List<RequestPath> requestPaths = new ArrayList<>();
        List<String> methodList = new ArrayList<>();
        List<String> pathList = new ArrayList<>();

        String annotationName = entry.getCalleeExpression().getText();
        SpringRequestMethodAnnotation requestMethodAnnotation = SpringRequestMethodAnnotation.getByShortName(annotationName);
        if (requestMethodAnnotation == null) {
            return new ArrayList<>();
        }

        if (requestMethodAnnotation.methodName() != null) {
            methodList.add(requestMethodAnnotation.methodName());
        } else {
            // 下面循环获取
        }

        //注解参数值
        KtValueArgumentList valueArgumentList = entry.getValueArgumentList();
        // 只有注解，没有参数
        if (valueArgumentList != null) {
            List<KtValueArgument> arguments = valueArgumentList.getArguments();


            for (int i = 0; i < arguments.size(); i++) {
                KtValueArgument ktValueArgument = arguments.get(i);
                KtValueArgumentName argumentName = ktValueArgument.getArgumentName();

                KtExpression argumentExpression = ktValueArgument.getArgumentExpression();
                if (argumentName == null || argumentName.getText().equals("value") || argumentName.getText().equals("path")) {
                    // array, kotlin 1.1-
                    if (argumentExpression.getText().startsWith("arrayOf")) {
                        List<KtValueArgument> pathValueArguments = ((KtCallExpression) argumentExpression).getValueArguments();
                        for (KtValueArgument pathValueArgument : pathValueArguments) {
                            pathList.add(pathValueArgument.getText().replace("\"", ""));
                        }
                        // array, kotlin 1.2+
                    } else if (argumentExpression.getText().startsWith("[")) {
                        List<KtExpression> innerExpressions = ((KtCollectionLiteralExpression) argumentExpression).getInnerExpressions();
                        for (KtExpression ktExpression : innerExpressions) {
                            pathList.add(ktExpression.getText().replace("\"", ""));
                        }
                    } else {
                        // 有且仅有一个value
                        PsiElement[] paths = ktValueArgument.getArgumentExpression().getChildren();
//                            Arrays.stream(paths).forEach(p -> pathList.add(p.getText()));
                        pathList.add(paths.length==0? "" : paths[0].getText());
                    }
                    //TODO
                    continue;
                }

                String attribute = "method";
                if (argumentName.getText().equals(attribute)) {

                    // array, kotlin 1.1-
                    if (argumentExpression.getText().startsWith("arrayOf")) {
                        List<KtValueArgument> pathValueArguments = ((KtCallExpression) argumentExpression).getValueArguments();
                        for (KtValueArgument pathValueArgument : pathValueArguments) {
                            methodList.add(pathValueArgument.getText().replace("\"", ""));
                        }
                        // array, kotlin 1.2+
                    } else if (argumentExpression.getText().startsWith("[")) {
                        List<KtExpression> innerExpressions = ((KtCollectionLiteralExpression) argumentExpression).getInnerExpressions();
                        for (KtExpression ktExpression : innerExpressions) {
                            methodList.add(ktExpression.getText().replace("\"", ""));
                        }
                    } else {
                        // 有且仅有一个value
                        PsiElement[] paths = ktValueArgument.getArgumentExpression().getChildren();
//                            Arrays.stream(paths).forEach(p -> methodList.add(p.getText()));
                        methodList.add(paths.length==0? "" : paths[0].getText());
                    }

                }
            }
        } else {
            pathList.add(defaultPath);
            //method = "GET";
        }

        if (methodList.size() > 0) {
            for (String method : methodList) {
                for (String path : pathList) {
                    requestPaths.add(new RequestPath(path, method));
                }
            }
        } else {
            for (String path : pathList) {
                requestPaths.add(new RequestPath(path, null));
            }
        }

        return requestPaths;
    }*/



    private List<RequestPath> getRequestMappings(String defaultPath, KtAnnotationEntry entry) {
        List<RequestPath> requestPaths = new ArrayList<>();
        List<String> methodList = new ArrayList<>();
        List<String> pathList = new ArrayList<>();

        String annotationName = entry.getCalleeExpression().getText();
        SpringRequestMethodAnnotation requestMethodAnnotation = SpringRequestMethodAnnotation.getByShortName(annotationName);
        if (requestMethodAnnotation == null) {
            return new ArrayList<>();
        }

        if (requestMethodAnnotation.methodName() != null) { // GetMapping PostMapping ...
            methodList.add(requestMethodAnnotation.methodName());
        } else {
            methodList.addAll(getAttributeValues( entry, "method") ); // RequestMapping
        }

        //注解参数值
//        KtValueArgumentList valueArgumentList = entry.getValueArgumentList();
        if (entry.getValueArgumentList() != null) {
            List<String> mappingValues = getAttributeValues(entry, null);
            if(!mappingValues.isEmpty() )
                pathList.addAll(mappingValues);
            else
                pathList.addAll(getAttributeValues(entry, "value")); // path

            pathList.addAll(getAttributeValues(entry, "path")); // path
        }

        if(pathList.isEmpty()) pathList.add(defaultPath); //没指定参数

        if (methodList.size() > 0) {
            for (String method : methodList) {
                for (String path : pathList) {
                    requestPaths.add(new RequestPath(path, method));
                }
            }
        } else {
            for (String path : pathList) {
                requestPaths.add(new RequestPath(path, null));
            }
        }

        return requestPaths;
    }

    private List<String> getAttributeValues(KtAnnotationEntry entry, String attribute) {
        KtValueArgumentList valueArgumentList = entry.getValueArgumentList();

        if(valueArgumentList == null) return new ArrayList<>();

        List<KtValueArgument> arguments = valueArgumentList.getArguments();

        for (int i = 0; i < arguments.size(); i++) {
            KtValueArgument ktValueArgument = arguments.get(i);
            KtValueArgumentName argumentName = ktValueArgument.getArgumentName();

            KtExpression argumentExpression = ktValueArgument.getArgumentExpression();

            if (( argumentName == null && attribute == null ) || (argumentName != null && argumentName.getText().equals(attribute) ) ) {
                List<String> methodList = new ArrayList<>();
                // array, kotlin 1.1-
                if (argumentExpression.getText().startsWith("arrayOf")) {
                    List<KtValueArgument> pathValueArguments = ((KtCallExpression) argumentExpression).getValueArguments();
                    for (KtValueArgument pathValueArgument : pathValueArguments) {
                        methodList.add(pathValueArgument.getText().replace("\"", ""));
                    }
                    // array, kotlin 1.2+
                } else if (argumentExpression.getText().startsWith("[")) {
                    List<KtExpression> innerExpressions = ((KtCollectionLiteralExpression) argumentExpression).getInnerExpressions();
                    for (KtExpression ktExpression : innerExpressions) {
                        methodList.add(ktExpression.getText().replace("\"", ""));
                    }
                } else {
                    // 有且仅有一个value
                    PsiElement[] paths = ktValueArgument.getArgumentExpression().getChildren();
//                            Arrays.stream(paths).forEach(p -> methodList.add(p.getText()));
                    methodList.add(paths.length==0? "" : paths[0].getText());
                }

                return methodList;
            }
        }

        return new ArrayList<>();
    }

    private static Collection<PsiAnnotation> findAnnotationsByShortName(
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
            // Handle index inconsistency gracefully — return empty collection.
            // This can happen when the stub index references a file whose stub tree is missing
            // (e.g. index cache corrupted, concurrent file changes during indexing).
            // Note: IntelliJ platform logs this at ERROR level internally via
            // StubProcessingHelper.retrieveStubIdList before our catch runs, so we log at
            // debug level here to avoid duplicate noise.
            LOG.debug("Failed to find @" + shortName + " annotations (stub index may be inconsistent)", e);
            return new ArrayList<>();
        }
    }

}
