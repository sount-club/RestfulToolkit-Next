package com.sount.restful.endpoint.resolver;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.endpoint.navigation.RestServiceItem;

import java.util.List;

/**
 * 验证组合解析器的策略顺序和去重契约。
 */
public class CompositeEndpointResolverTest extends BasePlatformTestCase {

    public void testProjectStrategiesKeepFirstDuplicateAndPreserveOrder() {
        PsiMethod method = createMethod();
        RestServiceItem firstDuplicate = new RestServiceItem(method, "GET", "/users");
        RestServiceItem secondDuplicate = new RestServiceItem(method, "GET", "/users");
        RestServiceItem unique = new RestServiceItem(method, "POST", "/users");

        CompositeEndpointResolver resolver = new CompositeEndpointResolver(List.of(
                new StubResolver(List.of(firstDuplicate)),
                new StubResolver(List.of(secondDuplicate, unique))));

        List<RestServiceItem> results = resolver.resolveProjectEndpoints();

        assertEquals(2, results.size());
        assertSame(firstDuplicate, results.get(0));
        assertSame(unique, results.get(1));
    }

    /**
     * 创建用于 selection key 的稳定 PSI 方法。
     */
    private PsiMethod createMethod() {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("UserController.java", """
                package demo;

                public class UserController {
                    public void users() {}
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];
        return psiClass.findMethodsByName("users", false)[0];
    }

    /**
     * 为组合行为测试提供固定结果的端点解析策略。
     */
    private record StubResolver(List<RestServiceItem> items) implements EndpointResolver {
        @Override
        public List<RestServiceItem> resolveModuleEndpoints() {
            return items;
        }

        @Override
        public List<RestServiceItem> resolveProjectEndpoints() {
            return items;
        }
    }
}
