package com.sount.restful.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

// 处理 实体自关联，第二层自关联字段
public class PsiClassHelper {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new GsonBuilder().create();

    PsiClass psiClass;

    private int autoCorrelationCount = 0; //标记实体递归
    private int listIterateCount = 0; //标记List递归
    private Module myModule;

    protected PsiClassHelper(@NotNull PsiClass psiClass) {
        this.psiClass = psiClass;
    }

    public static PsiClassHelper create(@NotNull PsiClass psiClass) {
        return new PsiClassHelper(psiClass);
    }

    @NotNull
    protected Project getProject() {
        return psiClass.getProject();
    }


    public String convertClassToJSON(String className, Project project) {
        if (className.contains("List<")) {
            String entityName = className.substring(className.indexOf("<") + 1, className.lastIndexOf(">"));
            Map<String, Object> jsonMap = assembleClassToMap(entityName, project);
            List<Map<String, Object>> jsonList = new ArrayList<>();
            jsonList.add(jsonMap);
            return GSON_PRETTY.toJson(jsonList);
        } else {
            return convertPojoEntityToJSON(className, project);
        }
    }

    private String convertPojoEntityToJSON(String className, Project project) {
        Map<String, Object> jsonMap = assembleClassToMap(className, project);
        return GSON_PRETTY.toJson(jsonMap);
    }


    public String convertClassToJSON(Project project, boolean prettyFormat) {
        Gson gson = prettyFormat ? GSON_PRETTY : GSON_COMPACT;
        Map<String, Object> jsonMap = psiClass != null ? assembleClassToMap(psiClass, project) : new HashMap<>();
        return gson.toJson(jsonMap);
    }


    @Nullable
    public static Object getJavaBaseTypeDefaultValue(String paramType) {
        Object paramValue = null;
//        todo: using map later
        switch (paramType.toLowerCase()) {
            case "byte": paramValue = Byte.valueOf("1");break;
            case "char": paramValue = Character.valueOf('Z');break;
            case "character": paramValue = Character.valueOf('Z');break;
            case "boolean": paramValue = Boolean.TRUE;break;
            case "int": paramValue = Integer.valueOf(1);break;
            case "integer": paramValue = Integer.valueOf(1);break;
            case "double": paramValue = Double.valueOf(1);break;
            case "float": paramValue = Float.valueOf(1.0F);break;
            case "long": paramValue = Long.valueOf(1L);break;
            case "short": paramValue = Short.valueOf("1");break;
            case "bigdecimal": return BigDecimal.ONE;
            case "string": paramValue = "demoData";break;
            case "date": paramValue = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());break; // todo: format date
            case "localdatetime": paramValue = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());break; // todo: format date
//            default: paramValue = paramType;
        }
        return paramValue;
    }


    private boolean isJavaBaseType(String typeName) {
        return getJavaBaseTypeDefaultValue(typeName) != null;
    }

    private Object setFieldDefaultValue(PsiType psiFieldType, Project project) {
        String typeName = psiFieldType.getPresentableText();
        Object baseTypeDefaultValue = getJavaBaseTypeDefaultValue(typeName);
        if (baseTypeDefaultValue != null) {
            return baseTypeDefaultValue;
        }

        if (psiFieldType instanceof PsiClassReferenceType) {
            String className = ((PsiClassReferenceType) psiFieldType).getClassName();
            if (className.equalsIgnoreCase("List") || className.equalsIgnoreCase("ArrayList")) {
                return handleListParam(psiFieldType, project);
            }

            ((PsiClassReferenceType) psiFieldType).resolve().getFields(); // if is Enum

            String fullName = psiFieldType.getCanonicalText();
            PsiClass fieldClass = findOnePsiClassByClassName(fullName, project);

            // 处理递归
            if (fieldClass != null) {
//                todo: 处理递归问题 autoCorrelationCount
                if(autoCorrelationCount > 0) return new HashMap();
                if(fullName.equals(fieldClass.getQualifiedName())){
                    autoCorrelationCount ++;
                }

                return assembleClassToMap(fieldClass, project);
            }
        }

        // 处理自关联 List<T> = T 两级，只处理一次, break

        return typeName;
    }


    @Nullable
    public PsiClass findOnePsiClassByClassName(String qualifiedClassName, Project project) {
        return JavaPsiFacade.getInstance(project).findClass(qualifiedClassName, GlobalSearchScope.allScope(project));
    }

    public Collection<PsiClass> tryDetectPsiClassByShortClassName(Project project, String shortClassName) {
        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(project).getClassesByName(shortClassName, GlobalSearchScope.projectScope(project));

        if (psiClasses.length > 0) {
            return Arrays.asList(psiClasses);
        }

        if (myModule != null) {
            psiClasses = PsiShortNamesCache.getInstance(project).getClassesByName(shortClassName, GlobalSearchScope.allScope(project));
            return Arrays.asList(psiClasses);
        }

        return Collections.emptyList();
    }


    @Nullable
    public PsiClass findOnePsiClassByClassName2(String className, Project project) {
        String shortClassName = className.substring(className.lastIndexOf(".") + 1);

        PsiClass[] psiClasses = tryDetectPsiClassByShortClassName2(shortClassName, project);
        if (psiClasses.length == 0) {
            return null;
        }
        if (psiClasses.length == 1) {
            return psiClasses[0];
        }

        return Arrays.stream(psiClasses)
                .filter(tempPsiClass -> tempPsiClass.getQualifiedName().equals(className))
                .findFirst()
                .orElse(null);
    }
    public PsiClass[] tryDetectPsiClassByShortClassName2(String shortClassName,Project project) {

        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(project).getClassesByName(shortClassName, GlobalSearchScope.allScope(project));// 所有的

        if (psiClasses != null && psiClasses.length > 0) {
            return psiClasses;
        }

        if(myModule != null) {
            psiClasses = PsiShortNamesCache.getInstance(project).getClassesByName(shortClassName, GlobalSearchScope.allScope(project));// 所有的
            if (psiClasses != null && psiClasses.length > 0) {
                return psiClasses;
            }
        }

        return new PsiClass[0];
    }


    public Map<String, Object> assembleClassToMap(String className, Project project) {
        PsiClass psiClass = findOnePsiClassByClassName(className, project);

        Map<String, Object> jsonMap = new HashMap<>();
        if(psiClass != null){
            jsonMap = assembleClassToMap(psiClass, project);
        }
        return jsonMap;
    }

    public Map<String, Object> assembleClassToMap(PsiClass psiClass, Project project) {
        int defaultRecursiveCount = 1;
        return assembleClassToMap(psiClass, project, defaultRecursiveCount);
    }

    public Map<String, Object> assembleClassToMap(PsiClass psiClass, Project project,int recursiveCount) {

        Map<String, Object> map = new LinkedHashMap<>();
        PsiField[] fields = psiClass.getFields();
        for (PsiField field : fields) {
            PsiType psiFieldType = field.getType();
            String typeName = psiFieldType.getPresentableText();

            String fieldName = field.getName();

//            common base type
            if (isJavaBaseType(typeName)) {
                map.put(fieldName, getJavaBaseTypeDefaultValue(typeName));
                continue;
            }

            if (psiFieldType instanceof PsiArrayType) {
                PsiType psiType = ((PsiArrayType) psiFieldType).getComponentType();

                Object baseTypeDefaultValue = getJavaBaseTypeDefaultValue( psiType.getPresentableText() );
                if (baseTypeDefaultValue != null) {
                    List<Object> objects = new ArrayList<>();
                    objects.add(baseTypeDefaultValue);
                    map.put(fieldName, objects );
                }

                continue;
            }

            PsiClass resolveClass = ((PsiClassReferenceType) psiFieldType).resolve();
            if (isEnum(psiFieldType)) {
                PsiField psiField = resolveClass.getFields()[0];
                map.put(fieldName, psiField.getName());
                continue;
            }

//            self recursion
            if (resolveClass.getQualifiedName().equals(psiClass.getQualifiedName())) {
                if (recursiveCount > 0) {
                    Map<String, Object> objectMap = assembleClassToMap(resolveClass, project,0);
                    map.put(fieldName, objectMap);
                    continue;
                }
            }

//            recursive

            if (isListFieldType(psiFieldType)) {
                PsiType[] parameters = ((PsiClassReferenceType) psiFieldType).getParameters();
                if (parameters != null && parameters.length > 0) {
                    PsiType parameter = parameters[0];
// 自关联
                    if (recursiveCount <= 0 )  {
                        continue;
                    }

                    if (parameter.getPresentableText().equals(psiClass.getName())) {
                        Map<String, Object> objectMap = assembleClassToMap(psiClass, project, 0);
                        map.put(fieldName, objectMap);
                        continue;
                    }

                    Object baseTypeDefaultValue = getJavaBaseTypeDefaultValue( parameter.getPresentableText() );
                    if (baseTypeDefaultValue != null) {
                        List<Object> objects = new ArrayList<>();
                        objects.add(baseTypeDefaultValue);
                        map.put(fieldName, objects );
                        continue;
                    }

                    // TODO TODO TODO .................
                    if (parameter instanceof PsiClassReferenceType) {
                        if (parameter.getPresentableText().contains("<")) {
                            continue;
                        }
                        PsiClass onePsiClassByClassName = findOnePsiClassByClassName(parameter.getCanonicalText(), project);

                        Map<String, Object> objectMap = assembleClassToMap(onePsiClassByClassName, project, 0);
                        map.put(fieldName, objectMap);
                        continue;
                    }
                }

            }
        }

        return map;
    }


    private static boolean isListFieldType(PsiType psiFieldType) {
        if (!(psiFieldType instanceof PsiClassReferenceType)) {
            return false;
        }

        PsiClass resolvePsiClass = ((PsiClassReferenceType) psiFieldType).resolve();
        if (resolvePsiClass.getQualifiedName().equals("java.util.List")) {
            return true;
        }

        for (PsiType psiType : ((PsiClassReferenceType) psiFieldType).rawType().getSuperTypes()) {
            if (psiType.getCanonicalText().equals("java.util.List")) {
                return true;
            }
        }

        return false;
    }

    /* 字段是否为List 类型*/
    private static boolean isEnum(PsiType psiFieldType) {
        if (! (psiFieldType instanceof PsiClassReferenceType)) {
            return false;
        }
        return ((PsiClassReferenceType) psiFieldType).resolve().isEnum();
    }


    private Object handleListParam(PsiType psiType, Project project) {
        List<Object> list = new ArrayList<>();
        PsiClassType classType = (PsiClassType) psiType;
        PsiType[] subTypes = classType.getParameters();
        if (subTypes.length > 0) {
            list.add(setFieldDefaultValue(subTypes[0], project));
        }
        return list;
    }


    public PsiClassHelper withModule(Module module) {
        this.myModule = module;
        return this;
    }
}
