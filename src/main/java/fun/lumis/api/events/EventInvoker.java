package fun.lumis.api.events;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class EventInvoker
{
    private static final ConcurrentHashMap<Class<?>, Object> classRegistry = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<? extends Event>, List<Invocation>> invocationCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> slowHandlerWarnings = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> slowEventWarnings = new ConcurrentHashMap<>();
    private static final boolean PERF_DEBUG = Boolean.parseBoolean(System.getProperty("lumis.perf.debug", "false"));
    private static final long SLOW_HANDLER_NANOS = Long.getLong("lumis.perf.handlerMs", 8L) * 1_000_000L;
    private static final long SLOW_EVENT_NANOS = Long.getLong("lumis.perf.eventMs", 18L) * 1_000_000L;
    private static final long WARN_COOLDOWN_NANOS = Long.getLong("lumis.perf.cooldownMs", 1000L) * 1_000_000L;
    private static volatile boolean cacheDirty = true;

    public static void register(Object obj)
    {
        classRegistry.putIfAbsent(obj.getClass(), obj);
        cacheDirty = true;
    }

    public static void unregister(Object obj)
    {
        classRegistry.remove(obj.getClass());
        cacheDirty = true;
    }

    public static void clean()
    {
        classRegistry.clear();
        invocationCache.clear();
        cacheDirty = false;
    }

    public static void invoke(Event event) throws IllegalAccessException, InvocationTargetException, InstantiationException
    {
        long eventStart = PERF_DEBUG ? System.nanoTime() : 0L;
        if (cacheDirty)
        {
            rebuildCache();
        }

        List<Invocation> invocations = invocationCache.get(event.getClass());
        if (invocations == null || invocations.isEmpty())
        {
            return;
        }

        for (Invocation invocation : invocations)
        {
            if (!classRegistry.containsKey(invocation.listener().getClass()))
            {
                continue;
            }

            Method method = invocation.method();
            method.setAccessible(true);
            long handlerStart = PERF_DEBUG ? System.nanoTime() : 0L;
            try
            {
                method.invoke(invocation.listener(), event);
            }
            finally
            {
                if (PERF_DEBUG)
                {
                    long elapsed = System.nanoTime() - handlerStart;
                    if (elapsed >= SLOW_HANDLER_NANOS)
                    {
                        logSlowHandler(event, invocation, elapsed);
                    }
                }
            }
        }

        if (PERF_DEBUG)
        {
            long elapsed = System.nanoTime() - eventStart;
            if (elapsed >= SLOW_EVENT_NANOS)
            {
                logSlowEvent(event, elapsed, invocations.size());
            }
        }
    }

    public static boolean hasListeners(Class<? extends Event> eventClass)
    {
        if (cacheDirty)
        {
            rebuildCache();
        }

        List<Invocation> invocations = invocationCache.get(eventClass);
        return invocations != null && !invocations.isEmpty();
    }

    private static synchronized void rebuildCache()
    {
        if (!cacheDirty)
        {
            return;
        }

        ConcurrentHashMap<Class<? extends Event>, List<Invocation>> rebuilt = new ConcurrentHashMap<>();
        for (Object listener : classRegistry.values())
        {
            for (Method method : listener.getClass().getDeclaredMethods())
            {
                if (!method.isAnnotationPresent(EventLink.class))
                {
                    continue;
                }

                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 1 || !Event.class.isAssignableFrom(parameters[0]))
                {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) parameters[0];
                method.setAccessible(true);
                rebuilt.computeIfAbsent(eventClass, key -> new ArrayList<>())
                        .add(new Invocation(listener, method, method.getAnnotation(EventLink.class).priority()));
            }
        }

        for (List<Invocation> invocations : rebuilt.values())
        {
            invocations.sort((a, b) -> {
                int priorityCompare = Integer.compare(b.priority(), a.priority());
                if (priorityCompare != 0) return priorityCompare;

                int classCompare = a.listener().getClass().getName().compareTo(b.listener().getClass().getName());
                if (classCompare != 0) return classCompare;

                return a.method().getName().compareTo(b.method().getName());
            });
        }

        invocationCache.clear();
        invocationCache.putAll(rebuilt);
        cacheDirty = false;
    }

    private static void logSlowHandler(Event event, Invocation invocation, long elapsedNanos)
    {
        String listenerName = invocation.listener().getClass().getSimpleName();
        String methodName = invocation.method().getName();
        String eventName = event.getClass().getSimpleName();
        String key = "handler:" + eventName + ':' + listenerName + '#' + methodName;
        if (!canWarn(slowHandlerWarnings, key))
        {
            return;
        }

        System.out.println(String.format(Locale.ROOT,
                "[PerfDebug] Slow handler: %s -> %s#%s took %.2f ms",
                eventName,
                listenerName,
                methodName,
                elapsedNanos / 1_000_000.0D));
    }

    private static void logSlowEvent(Event event, long elapsedNanos, int invocationCount)
    {
        String eventName = event.getClass().getSimpleName();
        String key = "event:" + eventName;
        if (!canWarn(slowEventWarnings, key))
        {
            return;
        }

        System.out.println(String.format(Locale.ROOT,
                "[PerfDebug] Slow event: %s took %.2f ms for %d handlers",
                eventName,
                elapsedNanos / 1_000_000.0D,
                invocationCount));
    }

    private static boolean canWarn(ConcurrentHashMap<String, Long> warnings, String key)
    {
        long now = System.nanoTime();
        Long lastWarn = warnings.get(key);
        if (lastWarn != null && now - lastWarn < WARN_COOLDOWN_NANOS)
        {
            return false;
        }

        warnings.put(key, now);
        return true;
    }

    private record Invocation(Object listener, Method method, int priority) {}
}
