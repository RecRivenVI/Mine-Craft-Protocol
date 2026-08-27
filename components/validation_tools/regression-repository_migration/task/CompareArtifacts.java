import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/** Migration-only byte comparison; strips line numbers, not method code or other debug attributes. */
class CompareArtifacts {
    static Map<String, byte[]> entries(Path jar) throws Exception {
        Map<String, byte[]> result = new TreeMap<>();
        try (InputStream in = Files.newInputStream(jar)) { unpack(in, result, true); }
        return result;
    }

    static void unpack(InputStream in, Map<String, byte[]> out, boolean nested) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] value = zip.readAllBytes();
                String name = entry.getName();
                if (nested && name.endsWith(".jar") && (name.startsWith("META-INF/jarjar/") || name.startsWith("META-INF/jars/"))) {
                    out.put(name, value);
                    Map<String, byte[]> inner = new TreeMap<>();
                    unpack(new ByteArrayInputStream(value), inner, false);
                    inner.forEach((key, bytes) -> { if (key.endsWith(".class")) out.put(key, bytes); });
                } else out.put(name, value);
            }
        }
    }

    static byte[] withoutLines(byte[] value) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(value).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, sig, exceptions)) {
                    @Override public void visitLineNumber(int line, Label start) {}
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    static byte[] withoutDebug(byte[] value) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(value).accept(writer, ClassReader.SKIP_DEBUG);
        return writer.toByteArray();
    }

    // Secondary diagnostic only: never substitutes for the line-number-only comparison.
    static byte[] withoutDebugAndAnnotations(byte[] value) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(value).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return null; }
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, sig, exceptions)) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return null; }
                };
            }
        }, ClassReader.SKIP_DEBUG);
        return writer.toByteArray();
    }
    static String array(List<String> values) { return "[" + String.join(",", values.stream().map(CompareArtifacts::quote).toList()) + "]"; }

    public static void main(String[] args) throws Exception {
        Path oldRoot = Path.of(args[0]), newRoot = Path.of(args[1]);
        List<String> rows = new ArrayList<>();
        for (String target : List.of("1.20.1-forge", "1.21.1-neoforge", "26.1.2-neoforge", "26.3-neoforge", "26.3-fabric")) {
            Path relative = Path.of("versions", target, "build", "libs");
            Map<String, byte[]> before = entries(oldRoot.resolve(relative).resolve("minecraft_protocol_probe-0.0.1-phase8.jar"));
            Map<String, byte[]> after = entries(newRoot.resolve(relative).resolve("minecraft_protocol_probe-" + target + "-0.0.1-phase8.jar"));
            List<String> classes = new ArrayList<>(), resources = new ArrayList<>(), added = new ArrayList<>(), removed = new ArrayList<>();
            List<String> debugOnly = new ArrayList<>();
            List<String> annotationOrDebugOnly = new ArrayList<>();
            int identicalClasses = 0, identicalResources = 0;
            for (var entry : before.entrySet()) {
                String name = entry.getKey();
                byte[] other = after.get(name);
                if (other == null) { removed.add(name); continue; }
                if (name.endsWith(".class")) {
                    if (Arrays.equals(withoutLines(entry.getValue()), withoutLines(other))) identicalClasses++;
                    else {
                        classes.add(name);
                        if (Arrays.equals(withoutDebug(entry.getValue()), withoutDebug(other))) debugOnly.add(name);
                        if (Arrays.equals(withoutDebugAndAnnotations(entry.getValue()), withoutDebugAndAnnotations(other))) annotationOrDebugOnly.add(name);
                    }
                } else if (Arrays.equals(entry.getValue(), other)) identicalResources++;
                else resources.add(name);
            }
            for (String name : after.keySet()) if (!before.containsKey(name)) added.add(name);
            rows.add("{\"target\":" + quote(target) + ",\"identicalClassesIgnoringOnlyLineNumbers\":" + identicalClasses
                    + ",\"changedClasses\":" + array(classes) + ",\"changedOnlyInOtherDebugAttributes\":" + array(debugOnly) + ",\"identicalResources\":" + identicalResources
                    + ",\"changedOnlyInAnnotationsOrDebugAttributes\":" + array(annotationOrDebugOnly)
                    + ",\"changedResources\":" + array(resources) + ",\"added\":" + array(added) + ",\"removed\":" + array(removed) + "}");
        }
        System.out.println("{\"baselineCommit\":\"1f754ab52d8c2d7d64e26d72fa489f2c2a471527\",\"candidate\":\"UNCOMMITTED_WORKTREE\",\"rows\":[" + String.join(",", rows) + "]}");
    }
}
