package com.sount.restful.method;

public class RequestPath {
    String path;
    String method;

    public RequestPath(String path, String method) {
        this.path = path;
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void concat(RequestPath classRequestPath) {
        String classUri = normalizeClassPath(classRequestPath.getPath());
        String methodUri = normalizeMethodPath(this.path);

        this.path = classUri.concat(methodUri);
    }

    private static String normalizeClassPath(String classUri) {
        if (!classUri.startsWith("/")) classUri = "/".concat(classUri);
        if (!classUri.endsWith("/")) classUri = classUri.concat("/");
        return classUri;
    }

    private static String normalizeMethodPath(String methodUri) {
        if (methodUri.startsWith("/")) return methodUri.substring(1);
        return methodUri;
    }
}
