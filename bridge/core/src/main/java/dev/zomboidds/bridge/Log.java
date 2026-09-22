package dev.zomboidds.bridge;

/** Writes to stdout, which Project Zomboid captures in {@code console.txt}. */
public final class Log {

    private static final String PREFIX = "[ZomboidDS] ";

    public static void info(String message) {
        System.out.println(PREFIX + message);
    }

    public static void warn(String message) {
        System.out.println(PREFIX + "WARN " + message);
    }

    public static void error(String message, Throwable t) {
        System.out.println(PREFIX + "ERROR " + message);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }

    private Log() {
    }
}
