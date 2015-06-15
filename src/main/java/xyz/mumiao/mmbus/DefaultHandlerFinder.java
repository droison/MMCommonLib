package xyz.mumiao.mmbus;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultHandlerFinder implements HandlerFinder {

    /**
     * Cache event bus subscriber methods for each class.
     */
    private final Map<Class<?>, Map<Class<?>, Set<Method>>> SUBSCRIBERS_CACHE =
            new HashMap<Class<?>, Map<Class<?>, Set<Method>>>();

    private void loadAnnotatedMethods(Class<?> listenerClass) {
        Map<Class<?>, Set<Method>> subscriberMethods = new HashMap<Class<?>, Set<Method>>();

        for (Method method : listenerClass.getDeclaredMethods()) {
            // The compiler sometimes creates synthetic bridge methods as part of the
            // type erasure process. As of JDK8 these methods now include the same
            // annotations as the original declarations. They should be ignored for
            // subscribe/produce.
            if (method.isBridge()) {
                continue;
            }
            if (method.isAnnotationPresent(Override.class)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1) {
                    throw new IllegalArgumentException("Method " + method + " has @Subscribe annotation but requires "
                            + parameterTypes.length + " arguments.  Methods must require a single argument.");
                }

                Class<?> eventType = parameterTypes[0];
                if (eventType.isInterface()) {
                    throw new IllegalArgumentException("Method " + method + " has @Subscribe annotation on " + eventType
                            + " which is an interface.  Subscription must be on a concrete class type.");
                }

                if ((method.getModifiers() & Modifier.PUBLIC) == 0) {
                    throw new IllegalArgumentException("Method " + method + " has @Subscribe annotation on " + eventType
                            + " but is not 'public'.");
                }

                Set<Method> methods = subscriberMethods.get(eventType);
                if (methods == null) {
                    methods = new HashSet<Method>();
                    subscriberMethods.put(eventType, methods);
                }
                methods.add(method);
            }
            SUBSCRIBERS_CACHE.put(listenerClass, subscriberMethods);
        }
    }

    @Override
    public <T> Map<String, EventHandler<T>> findAllSubscribers(Class<T> cls, T listener) {
        Map<String, EventHandler<T>> handlersInMethod = new HashMap<String, EventHandler<T>>();

        if (!SUBSCRIBERS_CACHE.containsKey(cls)) {
            loadAnnotatedMethods(cls);
        }
        Map<Class<?>, Set<Method>> methods = SUBSCRIBERS_CACHE.get(cls);
        if (!methods.isEmpty()) {
            for (Map.Entry<Class<?>, Set<Method>> e : methods.entrySet()) {
                for (Method m : e.getValue()) {
                    handlersInMethod.put(keyFromSubscribers(cls, e.getKey()), new EventHandler<T>(listener, m));
                }
            }
        }
        return handlersInMethod;
    }


    @Override
    public String keyFromSubscribers(Class<?> keyClass, Class<?> eventClass) {
        return keyClass.getName() + "-" + eventClass.getName();
    }
}
