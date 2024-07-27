package diecup;

public class Logger {
    private boolean verbose;

    public Logger(boolean verbose) {
        this.verbose = verbose;
    }

    public void info(String string) {
        if (verbose) {
            System.out.println(string);
        }
    }

    public void info(String string, int leadingLines) {
        newLine(leadingLines);
        info(string);
    }

    public void log(String string) {
        System.out.println(string);
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
    }
}
