package com.sount.restful.annotations;


public enum JaxrsRequestAnnotation {

    PATH("Path", "javax.ws.rs.Path", null);

    JaxrsRequestAnnotation(String shortName, String qualifiedName, String methodName) {
        this.shortName = shortName;
        this.qualifiedName = qualifiedName;
        this.methodName = methodName;
    }

    private final String shortName;
    private final String qualifiedName;
    private final String methodName;

    public String methodName() {
        return this.methodName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getShortName() {
        return shortName;
    }

}
