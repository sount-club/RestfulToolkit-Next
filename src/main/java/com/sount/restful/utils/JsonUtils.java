package com.sount.restful.utils;

import com.google.gson.*;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author zhaow
 */
public class JsonUtils {

    public static boolean isValidJson(String json) {
        if (StringUtil.isEmptyOrSpaces(json)) {
            return false;
        }

        return isValidJsonObject(json) || isValidJsonArray(json);
    }

    public static String format(String str) {
        if (str == null) {
            return "";
        }
        try {
            JsonElement parse = JsonParser.parseString(str);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(parse);
        } catch (JsonSyntaxException e) {
            // Not valid JSON — return the original input unchanged.
            return str;
        }
    }

    private static boolean isGsonFormat(String targetStr, Class<? extends JsonElement> clazz) {
        try {
            new Gson().fromJson(targetStr, clazz);
            return true;
        } catch(JsonSyntaxException ex) {
            return false;
        }
    }

    public static boolean isValidJsonObject(String targetStr){
        return isGsonFormat(targetStr,JsonObject.class);
    }

    public static boolean isValidJsonArray(String targetStr){
        return isGsonFormat(targetStr,JsonArray.class);
    }

}
