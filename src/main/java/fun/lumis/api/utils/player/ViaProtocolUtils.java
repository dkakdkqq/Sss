package fun.lumis.api.utils.player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ViaProtocolUtils {
    private static final int MC_1_19_PROTOCOL = 759;
    private static final long CACHE_TIME_MS = 1500L;
    private static final Pattern VERSION_PATTERN = Pattern.compile("1\\.(\\d+)");

    private static long nextRefreshAt;
    private static boolean belowOneNineteen;

    private ViaProtocolUtils() {
    }

    public static boolean isTargetProtocolBelowOneNineteen() {
        long now = System.currentTimeMillis();
        if (now < nextRefreshAt) {
            return belowOneNineteen;
        }

        belowOneNineteen = resolveBelowOneNineteen();
        nextRefreshAt = now + CACHE_TIME_MS;
        return belowOneNineteen;
    }

    private static boolean resolveBelowOneNineteen() {
        try {
            Class<?> viaFabricPlusClass = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            Object impl = viaFabricPlusClass.getMethod("getImpl").invoke(null);
            if (impl == null) {
                return false;
            }

            Object targetVersion = invokeNoArg(impl, "getTargetVersion");
            if (targetVersion == null) {
                return false;
            }

            Integer protocolId = readProtocolId(targetVersion);
            return protocolId != null && protocolId < MC_1_19_PROTOCOL;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeNoArg(Object instance, String methodName) {
        try {
            Method method = instance.getClass().getMethod(methodName);
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
                return null;
            }
            return method.invoke(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer readProtocolId(Object targetVersion) {
        try {
            Method getVersion = targetVersion.getClass().getMethod("getVersion");
            Object value = getVersion.invoke(targetVersion);
            if (value instanceof Number number) {
                return number.intValue();
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Method method : targetVersion.getClass().getMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (returnType != int.class && returnType != Integer.class) {
                    continue;
                }

                String name = method.getName().toLowerCase();
                if (!name.contains("version") && !name.contains("protocol") && !name.contains("id")) {
                    continue;
                }

                Object value = method.invoke(targetVersion);
                if (value instanceof Number number) {
                    return number.intValue();
                }
            }
        } catch (Throwable ignored) {
        }

        Matcher matcher = VERSION_PATTERN.matcher(String.valueOf(targetVersion));
        if (matcher.find()) {
            int minor = Integer.parseInt(matcher.group(1));
            return minor >= 19 ? MC_1_19_PROTOCOL : MC_1_19_PROTOCOL - 1;
        }

        return null;
    }
}
