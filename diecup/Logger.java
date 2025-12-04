package diecup;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private boolean verbose;
    private PrintWriter fileWriter;
    private String logFilePath;

    public Logger(boolean verbose) {
        this.verbose = verbose;
        this.fileWriter = null;
        this.logFilePath = null;
    }

    public Logger(boolean verbose, String logFilePath) {
        this.verbose = verbose;
        this.logFilePath = logFilePath;
        try {
            this.fileWriter = new PrintWriter(new FileWriter(logFilePath, true));
        } catch (IOException e) {
            System.err.println("Failed to open log file: " + logFilePath);
            this.fileWriter = null;
        }
    }

    public void info(String string) {
        if (verbose) {
            System.out.println(string);
        }
        writeToFile(string);
    }

    public void info(String string, int leadingLines) {
        newLine(leadingLines);
        info(string);
    }

    public void log(String string) {
        System.out.println(string);
        writeToFile(string);
    }

    public void log(String string, int leadingLines) {
        newLine(leadingLines);
        log(string);
    }

    public void newLine(int lines) {
        if (verbose) {
            for (int i = 0; i < lines; i++) {
                System.out.println();
            }
        }
        if (fileWriter != null) {
            for (int i = 0; i < lines; i++) {
                fileWriter.println();
            }
        }
    }

    private void writeToFile(String string) {
        if (fileWriter != null) {
            fileWriter.println(string);
            fileWriter.flush();
        }
    }

    public void close() {
        if (fileWriter != null) {
            fileWriter.close();
            fileWriter = null;
        }
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public static String generateLogFileName(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        return "logs/" + prefix + "_" + now.format(formatter) + ".txt";
    }
}
