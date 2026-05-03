package com.sount.restful.common;

import com.intellij.openapi.diagnostic.Logger;
import com.sount.utils.JsonUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class RequestHelper {

    private static final Logger LOG = Logger.getInstance(RequestHelper.class);
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int TIMEOUT_MS = 30_000;

    private static final PoolingHttpClientConnectionManager CONNECTION_MANAGER = new PoolingHttpClientConnectionManager();

    static {
        CONNECTION_MANAGER.setMaxTotal(20);
        CONNECTION_MANAGER.setDefaultMaxPerRoute(10);
    }

    private static final CloseableHttpClient SHARED_CLIENT = HttpClients.custom()
            .setConnectionManager(CONNECTION_MANAGER)
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(TIMEOUT_MS)
                    .setSocketTimeout(TIMEOUT_MS)
                    .setConnectionRequestTimeout(TIMEOUT_MS)
                    .build())
            .build();

    public record RequestResult(String body,
                                int statusCode,
                                String statusText,
                                long elapsedMillis,
                                long responseBytes,
                                boolean success) {
    }

    public static void shutdown() {
        try {
            SHARED_CLIENT.close();
        } catch (IOException e) {
            LOG.warn("Failed to close HTTP client", e);
        }
        CONNECTION_MANAGER.close();
    }

    public static String request(String url, String method) {
        return requestForResult(url, method).body();
    }

    public static RequestResult requestForResult(String url, String method) {
        return requestForResult(url, method, Collections.emptyMap());
    }

    public static RequestResult requestForResult(String url, String method, Map<String, String> headers) {
        if (method == null) {
            return failedResult("method is null", 0L);
        }

        switch (method.toUpperCase()) {
            case "GET": return execute(new HttpGet(ensureHttp(url)), headers);
            case "POST": return execute(new HttpPost(ensureHttp(url)), headers);
            case "PUT": return execute(new HttpPut(ensureHttp(url)), headers);
            case "PATCH": return execute(new HttpPatch(ensureHttp(url)), headers);
            case "DELETE": return execute(new HttpDelete(ensureHttp(url)), headers);
            default: return failedResult("not supported method : " + method + ".", 0L);
        }
    }

    public static RequestResult requestWithJsonBodyForResult(String url, String method, String json) {
        return requestWithJsonBodyForResult(url, method, json, Collections.emptyMap());
    }

    public static RequestResult requestWithJsonBodyForResult(String url, String method, String json, Map<String, String> headers) {
        if (method == null) {
            return failedResult("method is null", 0L);
        }

        switch (method.toUpperCase()) {
            case "POST": return postRequestBodyWithJsonForResult(url, json, headers);
            case "PUT": return putRequestBodyWithJsonForResult(url, json, headers);
            case "PATCH": return patchRequestBodyWithJsonForResult(url, json, headers);
            default: return requestForResult(url, method, headers);
        }
    }

    public static RequestResult requestWithFormBodyForResult(String url, String method, Map<String, String> formData, Map<String, String> headers) {
        if (method == null) {
            return failedResult("method is null", 0L);
        }

        switch (method.toUpperCase()) {
            case "POST": return postFormForResult(url, formData, headers);
            case "PUT": return putFormForResult(url, formData, headers);
            case "PATCH": return patchFormForResult(url, formData, headers);
            default: return requestForResult(url, method, headers);
        }
    }

    public static RequestResult postRequestBodyWithJsonForResult(String url, String json) {
        return postRequestBodyWithJsonForResult(url, json, Collections.emptyMap());
    }

    public static RequestResult postRequestBodyWithJsonForResult(String url, String json, Map<String, String> headers) {
        return executeJsonEntityRequest(new HttpPost(ensureHttp(url)), json, headers);
    }

    public static RequestResult putRequestBodyWithJsonForResult(String url, String json) {
        return putRequestBodyWithJsonForResult(url, json, Collections.emptyMap());
    }

    public static RequestResult putRequestBodyWithJsonForResult(String url, String json, Map<String, String> headers) {
        return executeJsonEntityRequest(new HttpPut(ensureHttp(url)), json, headers);
    }

    public static RequestResult patchRequestBodyWithJsonForResult(String url, String json) {
        return patchRequestBodyWithJsonForResult(url, json, Collections.emptyMap());
    }

    public static RequestResult patchRequestBodyWithJsonForResult(String url, String json, Map<String, String> headers) {
        return executeJsonEntityRequest(new HttpPatch(ensureHttp(url)), json, headers);
    }

    public static RequestResult postFormForResult(String url, Map<String, String> formData, Map<String, String> headers) {
        return executeFormEntityRequest(new HttpPost(ensureHttp(url)), formData, headers);
    }

    public static RequestResult putFormForResult(String url, Map<String, String> formData, Map<String, String> headers) {
        return executeFormEntityRequest(new HttpPut(ensureHttp(url)), formData, headers);
    }

    public static RequestResult patchFormForResult(String url, Map<String, String> formData, Map<String, String> headers) {
        return executeFormEntityRequest(new HttpPatch(ensureHttp(url)), formData, headers);
    }

    private static RequestResult execute(HttpRequestBase request, Map<String, String> headers) {
        applyHeaders(request, headers);
        long start = System.nanoTime();
        try (CloseableHttpResponse response = SHARED_CLIENT.execute(request)) {
            return buildResult(response, elapsedMillis(start));
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + request.getURI() + "\n\n" + e, elapsedMillis(start));
        }
    }

    private static RequestResult executeJsonEntityRequest(HttpEntityEnclosingRequestBase request, String json, Map<String, String> headers) {
        long start = System.nanoTime();
        try {
            StringEntity httpEntity = new StringEntity(json, UTF8);
            httpEntity.setContentType("application/json");
            request.setEntity(httpEntity);
            applyHeaders(request, headers);
            if (!request.containsHeader("Content-type")) {
                request.addHeader("Content-type", "application/json; charset=utf-8");
            }
            if (!request.containsHeader("Accept")) {
                request.setHeader("Accept", "application/json");
            }
            try (CloseableHttpResponse response = SHARED_CLIENT.execute(request)) {
                return buildResult(response, elapsedMillis(start));
            }
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + request.getURI() + "\n\n" + e, elapsedMillis(start));
        }
    }

    private static RequestResult executeFormEntityRequest(HttpEntityEnclosingRequestBase request, Map<String, String> formData, Map<String, String> headers) {
        long start = System.nanoTime();
        try {
            List<BasicNameValuePair> params = new ArrayList<>();
            if (formData != null) {
                formData.forEach((key, value) -> params.add(new BasicNameValuePair(key, value)));
            }
            request.setEntity(new UrlEncodedFormEntity(params, UTF8));
            applyHeaders(request, headers);
            if (!request.containsHeader("Content-type")) {
                request.addHeader("Content-type", "application/x-www-form-urlencoded; charset=utf-8");
            }
            try (CloseableHttpResponse response = SHARED_CLIENT.execute(request)) {
                return buildResult(response, elapsedMillis(start));
            }
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + request.getURI() + "\n\n" + e, elapsedMillis(start));
        }
    }

    private static void applyHeaders(HttpRequestBase request, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                request.setHeader(key.trim(), value.trim());
            }
        });
    }

    @NotNull
    private static String toString(HttpEntity entity) {
        if (entity == null) {
            return "";
        }
        try {
            String result = EntityUtils.toString(entity, UTF8);
            if (result != null && JsonUtils.isValidJson(result)) {
                return JsonUtils.format(result);
            }
            return result != null ? result : "";
        } catch (IOException e) {
            LOG.warn("Failed to read HTTP response entity", e);
            return "";
        }
    }

    private static RequestResult buildResult(CloseableHttpResponse response, long elapsedMillis) {
        String body = toString(response.getEntity());
        int statusCode = response.getStatusLine().getStatusCode();
        String statusText = response.getStatusLine().getReasonPhrase();
        long responseBytes = body.getBytes(UTF8).length;
        boolean success = statusCode >= 200 && statusCode < 400;
        return new RequestResult(body, statusCode, statusText, elapsedMillis, responseBytes, success);
    }

    private static RequestResult failedResult(String body, long elapsedMillis) {
        return new RequestResult(body, 0, "Request Failed", elapsedMillis,
                body == null ? 0 : body.getBytes(UTF8).length, false);
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String ensureHttp(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "http://" + url;
        }
        return url;
    }
}
