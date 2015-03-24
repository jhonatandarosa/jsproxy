package org.jsproxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.apache.commons.lang3.ClassUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

class JSProxyInvocationHandler<T> implements InvocationHandler {

    private WebDriver driver;
    private Class<T> clazz;

    public JSProxyInvocationHandler(Class<T> clazz, WebDriver driver) {
        this.clazz = clazz;
        this.driver = driver;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null) {
            args = new Object[0];
        }
        if (method.isVarArgs()) {
            Object[] varargs = (Object[]) args[args.length - 1];
            Object[] newArgs = new Object[args.length + varargs.length - 1];

            System.arraycopy(args, 0, newArgs, 0, args.length - 1);
            System.arraycopy(varargs, 0, newArgs, args.length - 1, varargs.length);
            args = newArgs;
        }

        boolean isGenericReturnTypeDeclaration = method.toGenericString().matches("public abstract <(\\w+)> \\1.*");

        Class<?> returnType = method.getReturnType();

        if (isGenericReturnTypeDeclaration) {
            validateGenericReturnTypeDeclaration(method, args);

            returnType = (Class<?>) args[0];
            Object[] newArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, newArgs, 0, args.length - 1);
            args = newArgs;

        }

        StringBuilder sb = new StringBuilder();

        if (!void.class.isAssignableFrom(returnType)) {
            sb.append("return ");
        }

        sb.append(clazz.getSimpleName());
        sb.append('.');
        sb.append(method.getName());
        sb.append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("arguments[");
            sb.append(i);
            sb.append(']');
        }
        sb.append(");");

        String script = sb.toString();
        JavascriptExecutor executor = (JavascriptExecutor) driver;

        Object result = executor.executeScript(script, args);

        if (result == null) {
            return null;
        }

        if (String.class.isAssignableFrom(returnType) || ClassUtils.isPrimitiveOrWrapper(returnType) || WebElement.class.isAssignableFrom(returnType)) {
            return result;
        }

        if (List.class.isAssignableFrom(returnType)) {
            Type[] typeParameters = ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments();
            Class<?> clazz = (Class<?>) typeParameters[0];
            if (String.class.isAssignableFrom(clazz) || ClassUtils.isPrimitiveOrWrapper(clazz) || WebElement.class.isAssignableFrom(clazz)) {
                return result;
            }
        }

        Gson gson = new Gson();

        if (result instanceof Map) {
            result = gson.toJson(result);
        }

        if (isGenericReturnTypeDeclaration) {
            return gson.fromJson(result.toString(), returnType);
        }

        return gson.fromJson(result.toString(), method.getGenericReturnType());
    }

    private void validateGenericReturnTypeDeclaration(Method method, Object[] args) {
        if (args.length == 0) {
            throw new JSProxyException(
                    "Generic method declaration expects first argument to be a Class matching the return type. No arguments found!");
        }

        Object arg0 = args[0];
        if (arg0 == null) {
            throw new JSProxyException(
                    "Generic method declaration expects first argument to be a Class matching the return type. First argument is null");
        }
        if (!(arg0 instanceof Class)) {
            throw new JSProxyException("Generic method declaration expects first argument to be a Class matching the return type. First argument is "
                    + arg0 + " of " + arg0.getClass());
        }

        String typeName = method.getGenericReturnType().toString();
        String classType = method.getGenericParameterTypes()[0].toString();
        String expected = "java.lang.Class<" + typeName + ">";
        if (!classType.equals(expected)) {
            throw new JSProxyException(
                    "Generic method declaration expects first argument to be a Class matching the return type. First argument type is " + classType
                    + ". Expected to be " + expected + " because the declared return type is " + typeName);
        }
    }
}
