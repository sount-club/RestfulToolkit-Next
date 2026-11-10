package com.sount.restful.common

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.javaInterop.KaElementJavaView
import org.jetbrains.kotlin.analysis.api.javaInterop.asPsiClass
import org.jetbrains.kotlin.analysis.api.javaInterop.asPsiMethods
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Kotlin 声明 -> Java(light) PSI 的桥接层。
 *
 * 2026.3 起 `org.jetbrains.kotlin.asJava.LightClassUtil` 与 `LightClassUtilsKt` 里的
 * `toLightClass` / `toLightMethods` / `getUnwrapped` 被废弃（KT-85385），官方替代是
 * Analysis API 的 `asPsiClass` / `asPsiMethods` 以及 `KaSymbolJavaView.kotlinOrigin`。
 * 这些入口都要求一个 `KaSession`，故统一收敛到这里，对上层暴露普通的 Java 可调用方法。
 */
@OptIn(KaExperimentalApi::class)
object KotlinAsJavaBridge {

    /** 将 Kotlin 类/对象转换为其 Java(light) 视图的 [PsiClass]，失败返回 null。 */
    @JvmStatic
    fun toPsiClass(element: KtClassOrObject?): PsiClass? {
        val target = element ?: return null
        return try {
            analyze(target) {
                (target.symbol as? KaClassSymbol)?.asPsiClass()
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** 将 Kotlin 函数转换为其 Java(light) 视图的方法列表，失败返回空列表。 */
    @JvmStatic
    fun toPsiMethods(function: KtNamedFunction?): List<PsiMethod> {
        val target = function ?: return emptyList()
        return try {
            analyze(target) {
                target.symbol.asPsiMethods()
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** 取 light PSI 元素对应的 Kotlin 源元素（替代废弃的 `getUnwrapped`）。 */
    @JvmStatic
    fun unwrap(element: PsiElement?): PsiElement? {
        val target = element ?: return null
        return (target as? KaElementJavaView)?.kotlinOrigin ?: target
    }
}
