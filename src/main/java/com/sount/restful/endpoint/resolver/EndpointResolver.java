package com.sount.restful.endpoint.resolver;

import com.sount.restful.endpoint.navigation.RestServiceItem;

import java.util.List;

/**
 * 端点解析策略，定义模块范围和项目范围两种解析入口。
 */
public interface EndpointResolver {

    /**
     * 解析当前策略支持的模块范围端点。
     */
    List<RestServiceItem> resolveModuleEndpoints();

    /**
     * 解析当前策略支持的项目范围端点。
     */
    List<RestServiceItem> resolveProjectEndpoints();
}
