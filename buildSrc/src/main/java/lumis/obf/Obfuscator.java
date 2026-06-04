package lumis.obf;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Standalone ASM obfuscator: renames our own classes/methods/fields ({@code fun/lumis/**})
 * to short identifiers while keeping everything that the runtime resolves by name
 * (mixins, entrypoint, JNA structs, enum constants, and any override of an external API).
 *
 * Designed to run on the named (yarn-mapped) jar BEFORE loom's remapJar, so the reference
 * classpath used for override detection shares the same names as the jar being processed.
 */
public final class Obfuscator {

    private static final int ASM = Opcodes.ASM9;

    /** Lightweight view of a class needed for hierarchy/override resolution. */
    private static final class ClassMeta {
        String name;
        String superName;
        String[] interfaces = new String[0];
        int access;
        final Set<String> methods = new HashSet<>();   // "name desc"
        final Set<String> fields = new HashSet<>();     // field name
        boolean ours;
    }

    private final Map<String, ClassMeta> hierarchy = new HashMap<>();
    private final Map<String, byte[]> ourClasses = new LinkedHashMap<>();
    private final Map<String, Integer> ourClassAccess = new HashMap<>();

    private final Map<String, String> classNewNames = new HashMap<>();
    private final Map<String, String> methodNewNames = new HashMap<>(); // key: owner#name desc
    private final Map<String, String> fieldNewNames = new HashMap<>();  // key: owner#name

    // Random package-tree shape used to scatter renamed classes across many nested
    // directories instead of a single flat package (harder to reverse-engineer).
    private static final int MIN_PKG_DEPTH = 2;
    private static final int MAX_PKG_DEPTH = 5;
    private static final int CLASSES_PER_PKG = 4;

    private final NameGenerator classGen;
    private final NameGenerator memberGen;
    private final NameGenerator pkgGen;
    private final long bytecodeSeed;

    private int renamedClasses, renamedMethods, renamedFields;

    public Obfuscator(long seed) {
        this.classGen = new NameGenerator(seed);
        this.memberGen = new NameGenerator(seed ^ 0x5DEECE66DL);
        this.pkgGen = new NameGenerator(seed ^ 0x1F123BB5L);
        this.bytecodeSeed = seed;
    }

    /** Entry point. Reads {@code inputJar}, writes obfuscated {@code outputJar}. */
    public void run(File inputJar, File outputJar, List<File> classpath) throws IOException {
        loadClasspath(classpath);
        loadInputJar(inputJar);
        log("loaded " + ourClasses.size() + " own classes, " + hierarchy.size() + " hierarchy entries");

        buildClassNames();
        buildMemberNames();
        log("renaming: " + renamedClasses + " classes, " + renamedMethods + " methods, " + renamedFields + " fields");

        writeOutput(inputJar, outputJar);
    }

    // ---------------------------------------------------------------- loading

    private void loadClasspath(List<File> classpath) throws IOException {
        for (File f : classpath) {
            if (!f.exists()) continue;
            if (f.isDirectory()) continue; // dev class dirs are not our concern for the jar artifact
            if (!f.getName().endsWith(".jar") && !f.getName().endsWith(".zip")) continue;
            try (ZipFile zip = new ZipFile(f)) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                    try (InputStream in = zip.getInputStream(e)) {
                        ClassMeta meta = readMeta(in.readAllBytes(), false);
                        if (meta != null) hierarchy.putIfAbsent(meta.name, meta);
                    } catch (Exception ignored) {
                        // unreadable/odd entry: skip, hierarchy resolution degrades gracefully
                    }
                }
            }
        }
    }

    private void loadInputJar(File inputJar) throws IOException {
        try (ZipFile zip = new ZipFile(inputJar)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                byte[] bytes;
                try (InputStream in = zip.getInputStream(e)) {
                    bytes = in.readAllBytes();
                }
                String internal = e.getName().substring(0, e.getName().length() - ".class".length());
                ClassMeta meta = readMeta(bytes, KeepRules.isOurs(internal));
                if (meta != null) hierarchy.put(meta.name, meta); // ours overrides classpath copy
                if (KeepRules.isOurs(internal)) {
                    ourClasses.put(internal, bytes);
                    if (meta != null) ourClassAccess.put(internal, meta.access);
                }
            }
        }
    }

    private ClassMeta readMeta(byte[] bytes, boolean ours) {
        ClassMeta meta = new ClassMeta();
        meta.ours = ours;
        ClassReader cr = new ClassReader(bytes);
        cr.accept(new ClassVisitor(ASM) {
            @Override public void visit(int v, int access, String name, String sig, String superName, String[] itf) {
                meta.name = name;
                meta.access = access;
                meta.superName = superName;
                if (itf != null) meta.interfaces = itf;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                meta.methods.add(name + " " + desc);
                return null;
            }
            @Override public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
                meta.fields.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return meta.name != null ? meta : null;
    }

    /** Resolve a class meta, falling back to JDK reflection for core classes. */
    private ClassMeta resolve(String internal) {
        if (internal == null) return null;
        ClassMeta m = hierarchy.get(internal);
        if (m != null) return m;
        if (internal.startsWith("java/") || internal.startsWith("javax/")
                || internal.startsWith("jdk/") || internal.startsWith("sun/")) {
            m = reflectMeta(internal);
            if (m != null) hierarchy.put(internal, m);
            return m;
        }
        return null;
    }

    private ClassMeta reflectMeta(String internal) {
        try {
            Class<?> c = Class.forName(internal.replace('/', '.'), false, Obfuscator.class.getClassLoader());
            ClassMeta meta = new ClassMeta();
            meta.name = internal;
            meta.access = c.getModifiers();
            meta.superName = c.getSuperclass() != null ? Type.getInternalName(c.getSuperclass()) : null;
            Class<?>[] itf = c.getInterfaces();
            meta.interfaces = new String[itf.length];
            for (int i = 0; i < itf.length; i++) meta.interfaces[i] = Type.getInternalName(itf[i]);
            for (Method method : c.getDeclaredMethods()) {
                meta.methods.add(method.getName() + " " + Type.getMethodDescriptor(method));
            }
            return meta;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ name tables

    private void buildClassNames() {
        // Classes that will actually be renamed (kept ones stay in their original package).
        // Group them by their top-level (nest host) class: the JVM requires every nest member
        // to live in the same package as its nest host, so an inner class and its enclosing
        // class must land in the SAME random package.
        LinkedHashMap<String, List<String>> nests = new LinkedHashMap<>();
        for (String internal : ourClasses.keySet()) {
            if (KeepRules.keepClassName(internal)) continue;
            nests.computeIfAbsent(topLevel(internal), k -> new ArrayList<>()).add(internal);
        }

        // Pool of random nested packages under fun/lumis, sized for ~CLASSES_PER_PKG classes
        // each. Everything is derived from the seed (via pkgGen) so builds stay reproducible.
        int total = renameableCount(nests);
        int poolSize = Math.max(1, (total + CLASSES_PER_PKG - 1) / CLASSES_PER_PKG);
        List<String> packages = buildPackagePool(poolSize);

        // Walk nests in order, filling one package until it holds ~CLASSES_PER_PKG classes,
        // then move to the next. A whole nest always stays together, so it never spills the
        // host/member package invariant even when a single nest is larger than the target.
        int pkgIdx = 0, inCurrent = 0;
        for (List<String> nest : nests.values()) {
            if (inCurrent >= CLASSES_PER_PKG && pkgIdx < packages.size() - 1) {
                pkgIdx++;
                inCurrent = 0;
            }
            String pkg = packages.get(pkgIdx);
            for (String internal : nest) {
                classNewNames.put(internal, pkg + "/" + classGen.next());
                renamedClasses++;
            }
            inCurrent += nest.size();
        }
    }

    private static int renameableCount(Map<String, List<String>> nests) {
        int n = 0;
        for (List<String> nest : nests.values()) n += nest.size();
        return n;
    }

    /** Top-level (nest host) internal name: {@code a/b/C$D$E} -> {@code a/b/C}. */
    private static String topLevel(String internal) {
        int slash = internal.lastIndexOf('/');
        int dollar = internal.indexOf('$', slash + 1);
        return dollar < 0 ? internal : internal.substring(0, dollar);
    }

    /** Generate {@code count} distinct nested packages, e.g. {@code fun/lumis/Qz/a3f/Kx}. */
    private List<String> buildPackagePool(int count) {
        int span = MAX_PKG_DEPTH - MIN_PKG_DEPTH + 1;
        List<String> packages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int depth = MIN_PKG_DEPTH + (i % span); // deterministic 2..MAX depth spread
            StringBuilder sb = new StringBuilder(KeepRules.ROOT.substring(0, KeepRules.ROOT.length() - 1));
            for (int d = 0; d < depth; d++) sb.append('/').append(pkgGen.next());
            packages.add(sb.toString());
        }
        return packages;
    }

    private void buildMemberNames() {
        buildFieldNames();
        buildMethodNames();
    }

    private void buildFieldNames() {
        for (Map.Entry<String, byte[]> e : ourClasses.entrySet()) {
            String owner = e.getKey();
            int classAccess = ourClassAccess.getOrDefault(owner, 0);
            ClassReader cr = new ClassReader(e.getValue());
            cr.accept(new ClassVisitor(ASM) {
                @Override public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
                    if (!KeepRules.keepFieldName(owner, name, access, classAccess)) {
                        fieldNewNames.put(owner + "#" + name, "f" + memberGen.next());
                        renamedFields++;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    /**
     * Methods need override-group consistency: every class in an override group must share
     * the new name, and if any member overrides an external API the whole group is kept.
     */
    private void buildMethodNames() {
        // Collect candidate method nodes (declaring class is ours, not statically kept).
        record MethodKey(String owner, String name, String desc) {}
        List<MethodKey> nodes = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();

        for (Map.Entry<String, byte[]> e : ourClasses.entrySet()) {
            String owner = e.getKey();
            int classAccess = ourClassAccess.getOrDefault(owner, 0);
            ClassReader cr = new ClassReader(e.getValue());
            cr.accept(new ClassVisitor(ASM) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    if (KeepRules.keepMethodName(owner, name, desc, classAccess)) return null;
                    String key = owner + "#" + name + " " + desc;
                    index.putIfAbsent(key, nodes.size());
                    nodes.add(new MethodKey(owner, name, desc));
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        // Union-find over method nodes.
        int n = nodes.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        boolean[] kept = new boolean[n];

        for (int i = 0; i < n; i++) {
            MethodKey mk = nodes.get(i);
            int access = methodAccess(mk.owner(), mk.name(), mk.desc());
            boolean staticOrPrivate = (access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0;
            if (staticOrPrivate) continue; // singleton, never an override

            for (ClassMeta sup : supertypes(mk.owner())) {
                if (!sup.methods.contains(mk.name() + " " + mk.desc())) continue;
                if (sup.ours) {
                    Integer j = index.get(sup.name + "#" + mk.name() + " " + mk.desc());
                    if (j != null) union(parent, i, j);
                } else {
                    kept[i] = true; // overrides external API -> keep
                }
            }
        }

        // Propagate kept flag to whole groups, then assign names.
        boolean[] groupKept = new boolean[n];
        for (int i = 0; i < n; i++) if (kept[i]) groupKept[find(parent, i)] = true;

        Map<Integer, String> groupName = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            if (groupKept[root]) continue;
            String newName = groupName.computeIfAbsent(root, r -> "m" + memberGen.next());
            MethodKey mk = nodes.get(i);
            methodNewNames.put(mk.owner() + "#" + mk.name() + " " + mk.desc(), newName);
            renamedMethods++;
        }
    }

    private int methodAccess(String owner, String name, String desc) {
        byte[] bytes = ourClasses.get(owner);
        if (bytes == null) return 0;
        int[] holder = {0};
        new ClassReader(bytes).accept(new ClassVisitor(ASM) {
            @Override public MethodVisitor visitMethod(int access, String mn, String md, String sig, String[] ex) {
                if (mn.equals(name) && md.equals(desc)) holder[0] = access;
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return holder[0];
    }

    /** All transitive supertypes (super + interfaces), excluding the class itself. */
    private List<ClassMeta> supertypes(String start) {
        List<ClassMeta> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        ClassMeta first = resolve(start);
        if (first != null) {
            if (first.superName != null) queue.add(first.superName);
            for (String i : first.interfaces) queue.add(i);
        }
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (cur == null || !seen.add(cur)) continue;
            ClassMeta m = resolve(cur);
            if (m == null) continue;
            out.add(m);
            if (m.superName != null) queue.add(m.superName);
            for (String i : m.interfaces) queue.add(i);
        }
        return out;
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; }
        return x;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) p[ra] = rb;
    }

    // ----------------------------------------------------------- declarer resolution

    /** Nearest class up from {@code owner} (inclusive) that declares this method. */
    private String resolveMethodDeclarer(String owner, String name, String desc) {
        String sig = name + " " + desc;
        ClassMeta self = resolve(owner);
        if (self != null && self.methods.contains(sig)) return owner;
        for (ClassMeta sup : supertypes(owner)) {
            if (sup.methods.contains(sig)) return sup.name;
        }
        return owner;
    }

    private String resolveFieldDeclarer(String owner, String name) {
        ClassMeta self = resolve(owner);
        if (self != null && self.fields.contains(name)) return owner;
        for (ClassMeta sup : supertypes(owner)) {
            if (sup.fields.contains(name)) return sup.name;
        }
        return owner;
    }

    // ----------------------------------------------------------------- output

    private void writeOutput(File inputJar, File outputJar) throws IOException {
        Remapper remapper = new Remapper() {
            @Override public String map(String internalName) {
                return classNewNames.getOrDefault(internalName, internalName);
            }
            @Override public String mapMethodName(String owner, String name, String desc) {
                if (owner == null || owner.startsWith("[")) return name;
                String decl = resolveMethodDeclarer(owner, name, desc);
                return methodNewNames.getOrDefault(decl + "#" + name + " " + desc, name);
            }
            @Override public String mapFieldName(String owner, String name, String desc) {
                if (owner == null || owner.startsWith("[")) return name;
                String decl = resolveFieldDeclarer(owner, name);
                return fieldNewNames.getOrDefault(decl + "#" + name, name);
            }
            @Override public String mapRecordComponentName(String owner, String name, String desc) {
                return mapFieldName(owner, name, desc);
            }
        };

        File tmp = File.createTempFile("lumis-obf", ".jar");
        // Shared generator for injected member names (decrypt methods, opaque fields).
        NameGenerator bcGen = new NameGenerator(this.bytecodeSeed ^ 0x632BE5ABL);
        try (ZipFile zip = new ZipFile(inputJar);
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp.toPath()))) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                byte[] bytes;
                try (InputStream in = zip.getInputStream(e)) {
                    bytes = in.readAllBytes();
                }

                String name = e.getName();
                if (name.endsWith(".class")) {
                    String internal = name.substring(0, name.length() - ".class".length());
                    if (KeepRules.isOurs(internal)) {
                        ClassReader cr = new ClassReader(bytes);
                        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        // reader -> rename -> body obfuscation (strings + junk) -> writer
                        ClassVisitor sink = new BytecodeTransformer(cw, this.bytecodeSeed, bcGen);
                        cr.accept(new ClassRemapper(sink, remapper), 0);
                        bytes = cw.toByteArray();
                        verify(internal, bytes);
                        name = remapper.map(internal) + ".class";
                    }
                }
                writeEntry(out, name, bytes);
            }
        }
        Files.move(tmp.toPath(), outputJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Structural sanity check on a transformed class; fails the build on malformed bytecode. */
    private void verify(String internal, byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            cr.accept(new org.objectweb.asm.util.CheckClassAdapter(
                    new ClassWriter(0), false), 0);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("[obf] produced invalid bytecode for " + internal, ex);
        }
    }

    private void writeEntry(ZipOutputStream out, String name, byte[] bytes) throws IOException {
        ZipEntry ne = new ZipEntry(name);
        ne.setTime(0L); // deterministic
        out.putNextEntry(ne);
        out.write(bytes);
        out.closeEntry();
    }

    private void log(String msg) {
        System.out.println("[obf] " + msg);
    }
}
