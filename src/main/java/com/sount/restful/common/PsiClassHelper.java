package com.sount.restful.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;

// 处理 实体自关联，第二层自关联字段
public class PsiClassHelper {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new GsonBuilder().create();
    private static final Map<String, Object> JAVA_BASE_TYPE_DEFAULT_VALUES = createJavaBaseTypeDefaultValues();

    PsiClass psiClass;

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
        if (isListType(className)) {
            if (extractListElementType(className) == null) {
                // Malformed/unclosed generic (e.g. "List<" without '>') — fall back to POJO
                // instead of throwing StringIndexOutOfBoundsException.
                return convertPojoEntityToJSON(className, project);
            }
            return GSON_PRETTY.toJson(createJsonValue(className, project));
        } else {
            return convertPojoEntityToJSON(className, project);
        }
    }

    private Object createJsonValue(@NotNull String typeName, @NotNull Project project) {
        if (!isListType(typeName)) {
            return assembleClassToMap(typeName, project);
        }
        String elementType = extractListElementType(typeName);
        if (elementType == null) {
            return assembleClassToMap(typeName, project);
        }
        return Collections.singletonList(createJsonValue(elementType, project));
    }

    private static boolean isListType(@NotNull String typeName) {
        int genericStart = typeName.indexOf('<');
        if (genericStart < 0) {
            return false;
        }
        String rawType = typeName.substring(0, genericStart).trim();
        return "List".equals(rawType) || rawType.endsWith(".List");
    }

    /**
     * Extracts the element type of {@code List<T>} type string for JSON sample generation.
     * Preserves nested generic arguments (e.g. {@code List<List<User>>} yields
     * {@code List<User>}) so callers can recursively construct the sample structure.
     * Returns {@code null} for malformed/unclosed generics so the caller can fall back
     * instead of throwing.
     */
    private static @Nullable String extractListElementType(@NotNull String className) {
        int start = className.indexOf('<');
        int end = className.lastIndexOf('>');
        if (start < 0 || end <= start) {
            return null;
        }
        String inner = className.substring(start + 1, end).trim();
        if (inner.isEmpty()) {
            return null;
        }
        int topLevelComma = findTopLevelComma(inner);
        String first = topLevelComma < 0 ? inner : inner.substring(0, topLevelComma).trim();
        return first.isEmpty() ? null : first;
    }

    private static int findTopLevelComma(@NotNull String typeArgs) {
        int depth = 0;
        for (int i = 0; i < typeArgs.length(); i++) {
            char c = typeArgs.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
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
        if (paramType == null) {
            return null;
        }
        return JAVA_BASE_TYPE_DEFAULT_VALUES.get(toShortTypeName(paramType).toLowerCase(Locale.ROOT));
    }

    private static Map<String, Object> createJavaBaseTypeDefaultValues() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("byte", Byte.valueOf("1"));
        defaults.put("char", 'Z');
        defaults.put("character", 'Z');
        defaults.put("boolean", Boolean.TRUE);
        defaults.put("int", 1);
        defaults.put("integer", 1);
        defaults.put("double", 1.0);
        defaults.put("float", 1.0F);
        defaults.put("long", 1L);
        defaults.put("short", Short.valueOf("1"));
        defaults.put("bigdecimal", BigDecimal.ONE);
        defaults.put("string", "demoData");
        defaults.put("date", "2024-01-01 12:00:00");
        defaults.put("localdate", "2024-01-01");
        defaults.put("localtime", "12:00:00");
        defaults.put("localdatetime", "2024-01-01T12:00:00");
        return Collections.unmodifiableMap(defaults);
    }

    private static String toShortTypeName(String typeName) {
        String rawType = typeName;
        int genericStart = rawType.indexOf('<');
        if (genericStart >= 0) {
            rawType = rawType.substring(0, genericStart);
        }
        return rawType.substring(rawType.lastIndexOf('.') + 1);
    }


    @Nullable
    public PsiClass findOnePsiClassByClassName(String qualifiedClassName, Project project) {
        return JavaPsiFacade.getInstance(project).findClass(qualifiedClassName, GlobalSearchScope.allScope(project));
    }


    public Map<String, Object> assembleClassToMap(String className, Project project) {
        PsiClass psiClass = findOnePsiClassByClassName(className, project);

        Map<String, Object> jsonMap = new HashMap<>();
        if (psiClass != null) {
            jsonMap = assembleClassToMap(psiClass, project);
        }
        return jsonMap;
    }

    public Map<String, Object> assembleClassToMap(PsiClass psiClass, Project project) {
        int defaultRecursiveCount = 1;
        return assembleClassToMap(psiClass, project, defaultRecursiveCount);
    }

    public Map<String, Object> assembleClassToMap(PsiClass psiClass, Project project, int recursiveCount) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (PsiField field : psiClass.getFields()) {
            Object value = buildFieldValue(field.getType(), project, psiClass, recursiveCount);
            map.put(field.getName(), value);
        }
        return map;
    }

    private Object buildFieldValue(PsiType fieldType, Project project, PsiClass ownerClass, int recursiveCount) {
        Object baseTypeDefaultValue = getJavaBaseTypeDefaultValue(fieldType.getPresentableText());
        if (baseTypeDefaultValue != null) {
            return baseTypeDefaultValue;
        }

        if (fieldType instanceof PsiArrayType arrayType) {
            return buildListValue(arrayType.getComponentType(), project, ownerClass, recursiveCount);
        }

        if (!(fieldType instanceof PsiClassReferenceType referenceType)) {
            return fieldType.getPresentableText();
        }

        if (isListFieldType(referenceType)) {
            PsiType[] parameters = referenceType.getParameters();
            return parameters.length > 0
                    ? buildListValue(parameters[0], project, ownerClass, recursiveCount)
                    : new ArrayList<>();
        }

        PsiClass resolvedClass = referenceType.resolve();
        if (resolvedClass == null) {
            return fieldType.getPresentableText();
        }

        if (resolvedClass.isEnum()) {
            PsiField[] enumFields = resolvedClass.getFields();
            return enumFields.length > 0 ? enumFields[0].getName() : fieldType.getPresentableText();
        }

        if (Objects.equals(resolvedClass.getQualifiedName(), ownerClass.getQualifiedName())) {
            if (recursiveCount <= 0) {
                return new LinkedHashMap<>();
            }
            return assembleClassToMap(resolvedClass, project, recursiveCount - 1);
        }

        return assembleClassToMap(resolvedClass, project, Math.max(0, recursiveCount - 1));
    }

    private Object buildListValue(PsiType elementType, Project project, PsiClass ownerClass, int recursiveCount) {
        List<Object> list = new ArrayList<>();
        Object elementValue = buildFieldValue(elementType, project, ownerClass, Math.max(0, recursiveCount - 1));
        list.add(elementValue);
        return list;
    }


    private static boolean isListFieldType(PsiClassReferenceType psiFieldType) {
        String className = psiFieldType.getClassName();
        if ("List".equals(className) || "ArrayList".equals(className)) {
            return true;
        }
        String canonicalText = psiFieldType.rawType().getCanonicalText();
        if ("java.util.List".equals(canonicalText) || "java.util.ArrayList".equals(canonicalText)) {
            return true;
        }

        PsiClass resolvePsiClass = psiFieldType.resolve();
        if (resolvePsiClass == null) {
            return false;
        }
        if (Objects.equals(resolvePsiClass.getQualifiedName(), "java.util.List")) {
            return true;
        }

        for (PsiType psiType : psiFieldType.rawType().getSuperTypes()) {
            if (psiType.getCanonicalText().equals("java.util.List")) {
                return true;
            }
        }

        return false;
    }


    public PsiClassHelper withModule() {
        return this;
    }
}
