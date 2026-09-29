package com.softwaredebebidas.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.*;

public class LoggingConfig {

    private static boolean configured = false;

    static void reset() {
        configured = false;
    }

    public static synchronized void init(String logDir) {
        if (configured) return;

        try {
            Path dir = Paths.get(logDir);
            Files.createDirectories(dir);

            Logger root = Logger.getLogger("");

            FileHandler fileHandler = new FileHandler(
                    dir.resolve("softwaredebebidas-%g.log").toString(),
                    10 * 1024 * 1024,
                    5,
                    true
            );

            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord record) {
                    String msg = String.format("%1$td/%1$tm/%1$tY %1$tH:%1$tM:%1$tS %2$s %3$s: %4$s%n",
                            new java.util.Date(record.getMillis()),
                            record.getLevel().getName(),
                            record.getLoggerName() != null ? record.getLoggerName() : "",
                            record.getMessage());
                    if (record.getThrown() != null) {
                        java.io.StringWriter sw = new java.io.StringWriter();
                        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                        record.getThrown().printStackTrace(pw);
                        pw.close();
                        msg += sw.toString() + "\n";
                    }
                    return msg;
                }
            });

            fileHandler.setLevel(Level.ALL);
            root.addHandler(fileHandler);

            for (Handler handler : root.getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setLevel(Level.INFO);
                }
            }

            configured = true;

        } catch (IOException e) {
            // Decision (audit Fase 3): keep System.err HERE on purpose.
            // This catch only fires when the file handler FAILED to initialize, so the
            // JUL pipeline may have no handler attached yet — logging via a Logger at this
            // point could be silently swallowed. System.err is the only sink guaranteed to
            // surface the misconfiguration, and we still want it visible if the log dir is
            // broken. Everywhere AFTER a successful init() the unified LOGGER is used.
            System.err.println("Failed to initialize file logging: " + e.getMessage());
        }
    }
}
