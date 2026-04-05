package com.sount.restful.common;

import com.sount.utils.JsonUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class RequestHelper {

    public record RequestResult(String body,
                                int statusCode,
                                String statusText,
                                long elapsedMillis,
                                long responseBytes,
                                boolean success) {
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
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        switch (method.toUpperCase()) {
            case "GET": return get(url, headers);
            case "POST": return post(url, headers);
            case "PUT":  return put(url, headers);
            case "PATCH": return patch(url, headers);
            case "DELETE": return delete(url, headers);
            default:return failedResult("not supported method : " + method + ".", 0L);
        }

    }

    public static RequestResult requestWithJsonBodyForResult(String url, String method, String json) {
        return requestWithJsonBodyForResult(url, method, json, Collections.emptyMap());
    }

    public static RequestResult requestWithJsonBodyForResult(String url, String method, String json, Map<String, String> headers) {
        if (method == null) {
            return failedResult("method is null", 0L);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
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
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        switch (method.toUpperCase()) {
            case "POST": return postFormForResult(url, formData, headers);
            case "PUT": return putFormForResult(url, formData, headers);
            case "PATCH": return patchFormForResult(url, formData, headers);
            default: return requestForResult(url, method, headers);
        }
    }

    public static RequestResult get(String url, Map<String, String> headers) {
        CloseableHttpResponse response = null;
        CloseableHttpClient  httpClient = HttpClients.createDefault();
        String completedUrl = completed(url);
        HttpGet httpMethod = new HttpGet(completedUrl);
        applyHeaders(httpMethod, headers);
        long start = System.nanoTime();
        try {
            response = httpClient.execute(httpMethod);
            return buildResult(response, elapsedMillis(start));
        } catch (UnsupportedEncodingException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } catch (ClientProtocolException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } finally {
            release(response, httpClient);
        }
    }

    public static RequestResult post(String url, Map<String, String> headers) {
        CloseableHttpResponse response = null;
        CloseableHttpClient  httpClient = HttpClients.createDefault();
        String completedUrl = completed(url);
        long start = System.nanoTime();
        try {
            HttpPost httpMethod = new HttpPost(completedUrl);
            applyHeaders(httpMethod, headers);
            response = httpClient.execute(httpMethod);
            return buildResult(response, elapsedMillis(start));
        } catch (UnsupportedEncodingException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } catch (ClientProtocolException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } finally {
            release(response, httpClient);
        }
    }


    public static RequestResult put(String url, Map<String, String> headers) {
        CloseableHttpResponse response = null;
        CloseableHttpClient  httpClient = HttpClients.createDefault();
        String completedUrl = completed(url);
        long start = System.nanoTime();
        try {
            HttpPut httpMethod = new HttpPut(completedUrl);
            applyHeaders(httpMethod, headers);
            response = httpClient.execute(httpMethod);
            return buildResult(response, elapsedMillis(start));
        } catch (UnsupportedEncodingException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } catch (ClientProtocolException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } finally {
            release(response, httpClient);
        }
    }


    public static RequestResult patch(String url, Map<String, String> headers) {
        CloseableHttpResponse response = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String completedUrl = completed(url);
        long start = System.nanoTime();
        try {
            HttpPatch httpMethod = new HttpPatch(completedUrl);
            applyHeaders(httpMethod, headers);
            response = httpClient.execute(httpMethod);
            return buildResult(response, elapsedMillis(start));
        } catch (UnsupportedEncodingException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } catch (ClientProtocolException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + completedUrl + "\n\n" + e, elapsedMillis(start));
        } finally {
            release(response, httpClient);
        }
    }


    public static RequestResult delete(String url, Map<String, String> headers) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        CloseableHttpResponse response = null;
        CloseableHttpClient  httpClient = HttpClients.createDefault();
        long start = System.nanoTime();
        try {
            HttpDelete httpMethod = new HttpDelete(url);
            applyHeaders(httpMethod, headers);
            response = httpClient.execute(httpMethod);
            return buildResult(response, elapsedMillis(start));
        } catch (UnsupportedEncodingException e) {
            return failedResult("There was an error accessing to URL: " + url + "\n\n" + e, elapsedMillis(start));
        } catch (ClientProtocolException e) {
            return failedResult("There was an error accessing to URL: " + url + "\n\n" + e, elapsedMillis(start));
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + url + "\n\n" + e, elapsedMillis(start));
        } finally {
            release(response, httpClient);
        }
    }


    public static String postRequestBodyWithJson(String url, String json) {
        return postRequestBodyWithJsonForResult(url, json).body();
    }

    public static RequestResult postRequestBodyWithJsonForResult(String url, String json) {
        return postRequestBodyWithJsonForResult(url, json, Collections.emptyMap());
    }

    public static RequestResult postRequestBodyWithJsonForResult(String url, String json, Map<String, String> headers) {
        return executeJsonEntityRequest(new HttpPost(completed(url)), json, headers);
    }

    public static RequestResult putRequestBodyWithJsonForResult(String url, String json) {
        return putRequestBodyWithJsonForResult(url, json, Collections.emptyMap());
    }

    public static RequestResult putRequestBodyWithJsonForResult(String url, String json, Map<String, String> headers) {
        return executeJsonEntityRequest(new HttpPut(completed(url)), json, headers);
    }

    public static RequestResult patchRequestBodyWithJsonForResult(String url, String json) {
        return patchRequestBodyWithJsonForResult(url, json, Collections.emptyMap());
    }

    public static RequestResult patchRequestBodyWithJsonForResult(String url, String json, Map<String, String> headers) {
        return executeJsonEntityRequest(new HttpPatch(completed(url)), json, headers);
    }

    public static RequestResult postFormForResult(String url, Map<String, String> formData, Map<String, String> headers) {
        return executeFormEntityRequest(new HttpPost(completed(url)), formData, headers);
    }

    public static RequestResult putFormForResult(String url, Map<String, String> formData, Map<String, String> headers) {
        return executeFormEntityRequest(new HttpPut(completed(url)), formData, headers);
    }

    public static RequestResult patchFormForResult(String url, Map<String, String> formData, Map<String, String> headers) {
        return executeFormEntityRequest(new HttpPatch(completed(url)), formData, headers);
    }

    private static RequestResult executeJsonEntityRequest(HttpEntityEnclosingRequestBase request, String json, Map<String, String> headers) {
        CloseableHttpResponse response = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        long start = System.nanoTime();
        try {
            StringEntity httpEntity = new StringEntity(json);
            httpEntity.setContentType("application/json");
            httpEntity.setContentEncoding("UTF-8");
            applyHeaders(request, headers);
            if (!request.containsHeader("Content-type")) {
                request.addHeader("Content-type", "application/json; charset=utf-8");
            }
            if (!request.containsHeader("Accept")) {
                request.setHeader("Accept", "application/json");
            }
            request.setEntity(httpEntity);
            response = httpClient.execute(request);
            return buildResult(response, elapsedMillis(start));
        } catch (UnsupportedEncodingException e) {
            return failedResult("There was an error accessing to URL: " + request.getURI() + "\n\n" + e, elapsedMillis(start));
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + request.getURI() + "\n\n" + e, elapsedMillis(start));
        } finally {
            release(response, httpClient);
        }
    }

    private static RequestResult executeFormEntityRequest(HttpEntityEnclosingRequestBase request, Map<String, String> formData, Map<String, String> headers) {
        CloseableHttpResponse response = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        long start = System.nanoTime();
        try {
            List<BasicNameValuePair> params = new ArrayList<>();
            if (formData != null) {
                formData.forEach((key, value) -> params.add(new BasicNameValuePair(key, value)));
            }
            request.setEntity(new UrlEncodedFormEntity(params, Charset.forName("UTF-8")));
            applyHeaders(request, headers);
            if (!request.containsHeader("Content-type")) {
                request.addHeader("Content-type", "application/x-www-form-urlencoded; charset=utf-8");
            }
            response = httpClient.execute(request);
            return buildResult(response, elapsedMillis(start));
        } catch (UnsupportedEncodingException e) {
            return failedResult("There was an error accessing to URL: " + request.getURI() + "\n\n" + e, elapsedMillis(start));
        } catch (IOException e) {
            return failedResult("There was an error accessing to URL: " + request.getURI() + "\n\n" + e, elapsedMillis(start));
        } finally {
            release(response, httpClient);
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

    private static void release(CloseableHttpResponse response, CloseableHttpClient httpClient) {
        if (response != null) {
            try {
                response.close();
            } catch (IOException e) { }
        }
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) { }
        }
    }

    private static String completed(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return url;
    }

    @NotNull
    private static String toString(HttpEntity entity) {
        if (entity == null) {
            return "";
        }
        String result = null;
        try {
            result = EntityUtils.toString(entity, Charset.forName("UTF-8"));

        } catch (IOException e) {
            e.printStackTrace();
        }

        if(result != null && JsonUtils.isValidJson(result))
            return JsonUtils.format(result);

        return result != null ? result : "";
    }

    private static RequestResult buildResult(CloseableHttpResponse response, long elapsedMillis) {
        String body = toString(response.getEntity());
        int statusCode = response.getStatusLine().getStatusCode();
        String statusText = response.getStatusLine().getReasonPhrase();
        long responseBytes = body.getBytes(Charset.forName("UTF-8")).length;
        boolean success = statusCode >= 200 && statusCode < 400;
        return new RequestResult(body, statusCode, statusText, elapsedMillis, responseBytes, success);
    }

    private static RequestResult failedResult(String body, long elapsedMillis) {
        return new RequestResult(body, 0, "Request Failed", elapsedMillis,
                body == null ? 0 : body.getBytes(Charset.forName("UTF-8")).length, false);
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }


}
