package lumis.obf;

import org.objectweb.asm.Opcodes;

/**
 * Static, name-based keep rules. Hierarchy/override-based keeps live in {@link Obfuscator}.
 *
 * A "kept" class keeps its own name but its body is still rewritten so references to
 * renamed classes are patched. A "kept" member keeps its name.
 */
public final class KeepRules {

    public static final String ROOT = "fun/lumis/";
    private static final String MIXIN_PKG = "fun/lumis/mixin/";
    private static final String RPC_UTILS_PKG = "fun/lumis/api/utils/rpc/utils/";
    private static final String ENTRYPOINT = "fun/lumis/Lumis";

    private KeepRules() {}

    /** Is this one of our classes (candidate for processing/renaming)? */
    public static boolean isOurs(String internalName) {
        return internalName.startsWith(ROOT);
    }

    /** Keep the class's own name (still rewrite references inside it). */
    public static boolean keepClassName(String internalName) {
        return internalName.equals(ENTRYPOINT)
                || internalName.startsWith(MIXIN_PKG)
                || internalName.startsWith(RPC_UTILS_PKG);
    }

    /**
     * Keep every member (method+field) of this class regardless of other analysis.
     * - Mixins: @Shadow/@Accessor/@Invoker names map to targets by convention; injected
     *   handler names may be referenced by the refmap. Keep them all.
     * - JNA rpc/utils: native method names (Discord_*) and Structure field names must match
     *   the native layout / getFieldOrder() strings.
     */
    public static boolean keepAllMembers(String owner) {
        return owner.startsWith(MIXIN_PKG) || owner.startsWith(RPC_UTILS_PKG);
    }

    /** Static method-name keeps that don't depend on the inheritance hierarchy. */
    public static boolean keepMethodName(String owner, String name, String desc, int classAccess) {
        if (name.equals("<init>") || name.equals("<clinit>")) return true;
        if (keepAllMembers(owner)) return true;
        // Enum contract methods are looked up by name.
        if (isEnum(classAccess) && (name.equals("values") || name.equals("valueOf") || name.equals("$values"))) {
            return true;
        }
        // Annotation element methods (e.g. EventLink.priority()) are read reflectively by name.
        if (isAnnotation(classAccess)) return true;
        // Keep interface methods to avoid breaking lambda/SAM (invokedynamic) wiring.
        if (isInterface(classAccess)) return true;
        return false;
    }

    /** Static field-name keeps that don't depend on the inheritance hierarchy. */
    public static boolean keepFieldName(String owner, String name, int fieldAccess, int classAccess) {
        if (keepAllMembers(owner)) return true;
        if ((fieldAccess & Opcodes.ACC_ENUM) != 0) return true;     // enum constants -> name()/valueOf()
        if (name.equals("$VALUES")) return true;                    // enum backing array
        if (name.equals("serialVersionUID")) return true;
        if (name.startsWith("$") || name.startsWith("this$") || name.startsWith("val$")) return true; // synthetic
        if (isAnnotation(classAccess)) return true;
        return false;
    }

    public static boolean isEnum(int access)       { return (access & Opcodes.ACC_ENUM) != 0; }
    public static boolean isAnnotation(int access) { return (access & Opcodes.ACC_ANNOTATION) != 0; }
    public static boolean isInterface(int access)  { return (access & Opcodes.ACC_INTERFACE) != 0; }
}
