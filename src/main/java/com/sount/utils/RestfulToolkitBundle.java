package com.sount.utils;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author zhaow
 */
public class RestfulToolkitBundle extends DynamicBundle {

  @NonNls private static final String BUNDLE = "RestfulToolkitBundle";
  private static final RestfulToolkitBundle INSTANCE = new RestfulToolkitBundle();

  private RestfulToolkitBundle() {
    super(BUNDLE);
  }

  public static @Nls String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
