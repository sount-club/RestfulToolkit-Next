package com.sount.restful.common;


import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import java.util.List;

/**
 * KtFunction处理类
 */
public class KtFunctionHelper extends PsiMethodHelper {
    KtNamedFunction ktNamedFunction;
    Project myProject;
    Module myModule;

    public static KtFunctionHelper create(@NotNull KtNamedFunction psiMethod) {
        return new KtFunctionHelper(psiMethod);
    }

    public KtFunctionHelper withModule(Module module) {
        this.myModule = module;
        return this;
    }

    protected KtFunctionHelper(@NotNull KtNamedFunction ktNamedFunction) {
        super(null);
        List<PsiMethod> psiMethods = LightClassUtilsKt.toLightMethods(ktNamedFunction);
        if (psiMethods.isEmpty()) {
            throw new IllegalStateException("No light methods found for Kotlin function: " + ktNamedFunction.getName());
        }
        super.psiMethod = psiMethods.getFirst();
        this.ktNamedFunction = ktNamedFunction;
    }

    @Override
    @NotNull
    protected Project getProject() {
        myProject = psiMethod.getProject();
        return myProject;
    }

}
