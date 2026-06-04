package lumis.obf;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Body-level bytecode obfuscation applied AFTER the renaming pass, only to our own
 * "normal" classes (mixins / JNA structs / interfaces / annotations are skipped — their
 * bodies are resolved or rewritten by other tooling at runtime).
 *
 * Three transforms:
 *   1. String encryption — every {@code LDC "..."} is replaced by {@code LDC <enc>}, a
 *      per-call-site {@code salt}, and a call to an injected per-class decrypt method that
 *      returns the EXACT original string. The keystream mixes a per-CLASS key (derived from
 *      the renamed class name, so reversing one class's decryptor reveals nothing about any
 *      other), the char index, the string length and the call-site salt — so two identical
 *      string literals at different sites encrypt to different bytes. The runtime value is
 *      identical, so reflection / enum names / string switches keep working.
 *   2. Number encryption — {@code BIPUSH}/{@code SIPUSH} and {@code LDC int}/{@code LDC long}
 *      literals are replaced by an arithmetic expression {@code (enc + opaque) ^ key} that
 *      evaluates to the original value. {@code opaque} is a never-written static field that is
 *      always 0, but a decompiler cannot prove that, so it cannot constant-fold the literal
 *      back. Tiny {@code ICONST_*}/{@code LCONST_*} pushes are left alone (high volume, low value).
 *   3. Junk control flow — each concrete method gets an opaque predicate at entry that always
 *      falls through to the real body but leaves a dead {@code ACONST_NULL; ATHROW} branch that
 *      decompilers (JD, CFR, Procyon) cannot fold away.
 *
 * Frame safety: original stack-map frames are passed through unchanged (reader flag 0, no
 * EXPAND_FRAMES). String/number transforms only insert straight-line code with the same net
 * stack delta as the instruction they replace, so they never create or invalidate a frame.
 * The junk inserts exactly one {@code F_SAME} frame (same locals as method entry, empty stack)
 * at the real-body label. The decrypt method carries hand-authored {@code F_FULL} frames.
 * Max stack/locals are recomputed with COMPUTE_MAXS (no classpath needed); frames are never
 * recomputed, so MC supertypes are never resolved.
 */
public final class BytecodeTransformer extends ClassVisitor {

    private static final String DECRYPT_DESC = "(Ljava/lang/String;I)Ljava/lang/String;";

    private final long seed;
    private final String decryptName;
    private final String opaqueField;

    // Per-class keystream parameters, derived from the class name in visit() so every class
    // has an independent string/number cipher.
    private int key;       // base XOR key for strings
    private int step;      // per-index multiplier (odd)
    private int saltMul;   // call-site salt multiplier (odd)
    private int lenMul;    // string-length multiplier (odd)
    private int numKey;    // XOR key for int literals
    private long numKeyL;  // XOR key for long literals

    private String owner;
    private boolean eligible;
    private boolean stringsUsed;
    private boolean opaqueUsed;   // true once anything reads the opaque field (junk OR numbers)
    private int saltCounter;      // deterministic per-call-site salt source

    public BytecodeTransformer(ClassVisitor next, long seed, NameGenerator gen) {
        super(Opcodes.ASM9, next);
        this.seed = seed;
        this.decryptName = "d" + gen.next();
        this.opaqueField = "o" + gen.next();
    }

    @Override
    public void visit(int version, int access, String name, String sig, String superName, String[] itf) {
        this.owner = name;
        boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        boolean isAnnotation = (access & Opcodes.ACC_ANNOTATION) != 0;
        boolean isModule = (access & Opcodes.ACC_MODULE) != 0;
        this.eligible = KeepRules.isOurs(name)
                && !KeepRules.keepAllMembers(name)
                && !isInterface && !isAnnotation && !isModule;

        // Mix the (already-renamed) class name into the seed: every class gets its own cipher.
        long cs = seed ^ (((long) name.hashCode()) * 0x9E3779B97F4A7C15L);
        this.key     = (int) (cs * 0x9E3779B1L);
        this.step    = ((int) (cs >>> 16)) | 1;
        this.saltMul = ((int) (cs >>> 21)) | 1;
        this.lenMul  = ((int) (cs >>> 11)) | 1;
        this.numKey  = ((int) (cs * 0x85EBCA6BL)) ^ 0x27D4EB2F;
        this.numKeyL = (cs * 0xC2B2AE3D27D4EB4FL) ^ 0x165667B19E3779F9L;

        super.visit(version, access, name, sig, superName, itf);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
        MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
        if (!eligible || mv == null) return mv;
        boolean abstractOrNative = (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0;
        if (abstractOrNative) return mv;
        boolean ctor = name.equals("<init>") || name.equals("<clinit>");
        return new BodyVisitor(mv, ctor);
    }

    @Override
    public void visitEnd() {
        if (eligible && opaqueUsed) {
            // private static int <opaque>;  (defaults to 0, never written -> predicate always true)
            super.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    opaqueField, "I", null, null).visitEnd();
        }
        if (eligible && stringsUsed) {
            emitDecryptMethod();
        }
        super.visitEnd();
    }

    /** Build-time counterpart of the runtime decrypt: exact round-trip via I2C truncation. */
    private String encrypt(String s, int salt) {
        char[] c = s.toCharArray();
        int len = c.length;
        for (int i = 0; i < len; i++) {
            c[i] = (char) (c[i] ^ key ^ (i * step) ^ (salt * saltMul) ^ (len * lenMul));
        }
        return new String(c);
    }

    private final class BodyVisitor extends MethodVisitor {
        private final boolean ctor;

        BodyVisitor(MethodVisitor mv, boolean ctor) {
            super(Opcodes.ASM9, mv);
            this.ctor = ctor;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            // Junk is skipped in constructors: the verifier tracks uninitializedThis at entry,
            // which would make the inserted frame awkward. String/number encryption still apply.
            if (ctor) return;
            opaqueUsed = true;
            Label real = new Label();
            super.visitFieldInsn(Opcodes.GETSTATIC, owner, opaqueField, "I"); // always 0
            super.visitJumpInsn(Opcodes.IFEQ, real);                          // -> real body
            super.visitInsn(Opcodes.ACONST_NULL);                             // dead branch
            super.visitInsn(Opcodes.ATHROW);
            super.visitLabel(real);
            super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);              // same locals, empty stack
        }

        @Override
        public void visitLdcInsn(Object cst) {
            if (cst instanceof String str) {
                stringsUsed = true;
                int salt = saltCounter++ & 0xFFFF;
                super.visitLdcInsn(encrypt(str, salt));
                pushInt(salt);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, decryptName, DECRYPT_DESC, false);
                return;
            }
            if (cst instanceof Integer i) {
                encryptInt(i);
                return;
            }
            if (cst instanceof Long l) {
                encryptLong(l);
                return;
            }
            super.visitLdcInsn(cst);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            // Only BIPUSH/SIPUSH push an integer constant; NEWARRAY's operand is a type code.
            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                encryptInt(operand);
                return;
            }
            super.visitIntInsn(opcode, operand);
        }

        /** Replace an int literal v with {@code (enc + opaque) ^ numKey == v}. */
        private void encryptInt(int v) {
            opaqueUsed = true;
            pushInt(v ^ numKey);                                       // enc
            super.visitFieldInsn(Opcodes.GETSTATIC, owner, opaqueField, "I"); // 0
            super.visitInsn(Opcodes.IADD);                             // enc + 0
            pushInt(numKey);
            super.visitInsn(Opcodes.IXOR);                            // (enc) ^ numKey = v
        }

        /** Replace a long literal v with {@code (enc + (long) opaque) ^ numKeyL == v}. */
        private void encryptLong(long v) {
            opaqueUsed = true;
            super.visitLdcInsn(v ^ numKeyL);                          // enc (long)
            super.visitFieldInsn(Opcodes.GETSTATIC, owner, opaqueField, "I"); // 0
            super.visitInsn(Opcodes.I2L);                             // 0L
            super.visitInsn(Opcodes.LADD);                            // enc + 0
            super.visitLdcInsn(numKeyL);
            super.visitInsn(Opcodes.LXOR);                           // (enc) ^ numKeyL = v
        }

        /** Smallest encoding for an int constant push. */
        private void pushInt(int v) {
            if (v >= -1 && v <= 5) {
                super.visitInsn(Opcodes.ICONST_0 + v);
            } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
                super.visitIntInsn(Opcodes.BIPUSH, v);
            } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
                super.visitIntInsn(Opcodes.SIPUSH, v);
            } else {
                super.visitLdcInsn(v);
            }
        }
    }

    /**
     * Emits: {@code private static String <decryptName>(String s, int salt)} reversing
     * {@link #encrypt}. Locals: 0=s, 1=salt, 2=char[] c, 3=int i. Hand-authored F_FULL frames
     * at the two jump targets. {@code c.length} is re-read each iteration (ARRAYLENGTH).
     */
    private void emitDecryptMethod() {
        MethodVisitor mv = super.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                decryptName, DECRYPT_DESC, null, null);
        mv.visitCode();
        Object[] locals = { "java/lang/String", Opcodes.INTEGER, "[C", Opcodes.INTEGER };

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ISTORE, 3);

        Label cond = new Label();
        Label end = new Label();
        mv.visitLabel(cond);
        mv.visitFrame(Opcodes.F_FULL, locals.length, locals, 0, new Object[0]);
        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, end);

        // c[i] = (char)(c[i] ^ key ^ (i*step) ^ (salt*saltMul) ^ (len*lenMul));
        mv.visitVarInsn(Opcodes.ALOAD, 2);   // array ref for CASTORE
        mv.visitVarInsn(Opcodes.ILOAD, 3);   // index for CASTORE
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitInsn(Opcodes.CALOAD);        // c[i]
        mv.visitLdcInsn(key);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitVarInsn(Opcodes.ILOAD, 3);   // i * step
        mv.visitLdcInsn(step);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitVarInsn(Opcodes.ILOAD, 1);   // salt * saltMul
        mv.visitLdcInsn(saltMul);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitVarInsn(Opcodes.ALOAD, 2);   // c.length * lenMul
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitLdcInsn(lenMul);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitInsn(Opcodes.I2C);
        mv.visitInsn(Opcodes.CASTORE);
        mv.visitIincInsn(3, 1);
        mv.visitJumpInsn(Opcodes.GOTO, cond);

        mv.visitLabel(end);
        mv.visitFrame(Opcodes.F_FULL, locals.length, locals, 0, new Object[0]);
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0); // recomputed by COMPUTE_MAXS
        mv.visitEnd();
    }
}
