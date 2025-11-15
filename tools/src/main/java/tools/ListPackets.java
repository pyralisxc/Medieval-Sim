package tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight reflection-based utility to list classes in the built mod that extend
 * necesse.engine.network.Packet. Run with a classpath that includes Necesse.jar and
 * the built classes directory (e.g. build/mod).
 *
 * Usage:
 *   java -cp tools/out;"<path to Necesse.jar>";"<path to build/mod>" tools.ListPackets <build/mod>
 */
public class ListPackets {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ListPackets <path-to-built-classes-dir>");
            System.exit(1);
        }

        File classesDir = new File(args[0]);
        if (!classesDir.exists() || !classesDir.isDirectory()) {
            System.err.println("Class directory not found: " + classesDir.getAbsolutePath());
            System.exit(2);
        }

        List<String> classNames = new ArrayList<>();
        collectClassFiles(classesDir, classesDir, classNames);

        try {
            Class<?> packetBase = Class.forName("necesse.engine.network.Packet");

            for (String name : classNames) {
                try {
                    Class<?> cls = Class.forName(name, false, Thread.currentThread().getContextClassLoader());
                    if (cls != null && packetBase.isAssignableFrom(cls) && !packetBase.equals(cls)) {
                        System.out.println(name);
                    }
                } catch (ClassNotFoundException e) {
                    // ignore classes that can't be loaded
                } catch (NoClassDefFoundError e) {
                    // missing deps - ignore
                } catch (Throwable t) {
                    System.err.println("Error loading class " + name + ": " + t.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Could not find engine Packet base class on classpath: necesse.engine.network.Packet");
            System.exit(3);
        }
    }

    private static void collectClassFiles(File root, File dir, List<String> out) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                collectClassFiles(root, f, out);
            } else if (f.isFile() && f.getName().endsWith(".class")) {
                String rel = f.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                String className = rel.replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                out.add(className);
            }
        }
    }
}
