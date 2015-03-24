package org.jsproxy;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

public final class JSProxyBinder {

    private static Map<String, Object> proxies = new HashMap<String, Object>();

    private static final String ROOT_FRAME_ID = "rootFrame";

    private JSProxyBinder() {}

    private static void loadScript(String scriptPath, String waitingVar, WebDriver driver) {
        String baseUrl = getRootFrameBaseURL(driver);
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        long expireTime = 10000;// 10 seconds
        if (jsExecutor.executeScript("return window." + waitingVar) == null) {
            StringBuilder script = new StringBuilder();
            script.append("var fileref=document.createElement('script');");
            script.append("fileref.setAttribute('type','text/javascript');");
            script.append("fileref.setAttribute('src', arguments[0]);");
            script.append("document.getElementsByTagName('head')[0].appendChild(fileref);");

            long start = System.currentTimeMillis();
            String fullPath = baseUrl + "/" + scriptPath;
            jsExecutor.executeScript(script.toString(), fullPath + "?v=" + start);

            while (jsExecutor.executeScript("return window." + waitingVar) == null) {
                long now = System.currentTimeMillis();

                if (now - start > expireTime) {
                    throw new TimeoutException("Script " + fullPath + " took more than 10 seconds to load! Aborting loading");
                }
            }
        }

    }

    private static String getRootFrameBaseURL(WebDriver driver) {
        String script = "var root = window.parent; while(root != root.parent) { root = window.parent; } var context = root.location.pathname; var idx = context.lastIndexOf('/'); if (idx > 0) { context = context.substring(0, idx); } return root.location.origin + context;";
        Object result = ((JavascriptExecutor) driver).executeScript(script);
        return result.toString();
    }

    private static String getFrameId(WebDriver driver) {
        String script = "var frame = window.frameElement; if (frame) { return frame.id; } else { return '" + ROOT_FRAME_ID + "'; }";
        Object result = ((JavascriptExecutor) driver).executeScript(script);
        return result.toString();
    }

    private static String getKey(Class<?> clazz, WebDriver driver) {
        String frameId = getFrameId(driver);
        return clazz.getSimpleName() + "(" + driver.getWindowHandle() + ":" + frameId + ")";
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> clazz, WebDriver driver) {
        return get(clazz, driver, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> clazz, WebDriver driver, JSProxyBinderCallback<T> callback) {
        String key = getKey(clazz, driver);

        T proxy = (T) proxies.get(key);
        if (proxy != null) {
            return proxy;
        }

        JSProxy jsproxy = clazz.getAnnotation(JSProxy.class);
        if (jsproxy == null) {
            throw new IllegalArgumentException("JSProxy annotation not found!");
        }

        Class<?>[] dependencies = jsproxy.dependencies();
        for (Class<?> dep : dependencies) {
            get(dep, driver);// load dependency
        }

        loadScript(jsproxy.value(), clazz.getSimpleName(), driver);

        JSProxyInvocationHandler<T> handler = new JSProxyInvocationHandler<T>(clazz, driver);
        proxy = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, handler);
        proxies.put(key, proxy);

        if (callback != null) {
            callback.onJSProxyBinded(proxy);
        }

        return proxy;
    }

    public static void unbindAll() {
        proxies.clear();
    }

    public static <T> T unbind(Class<T> clazz, WebDriver driver) {
        String key = getKey(clazz, driver);
        return (T)proxies.remove(key);
    }

}
