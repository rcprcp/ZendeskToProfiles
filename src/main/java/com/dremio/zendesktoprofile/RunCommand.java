package com.dremio.zendesktoprofile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;

public class RunCommand {
    private static final Logger LOG = LogManager.getLogger(ZendeskToProfile.class);
    private String stdout = "";
    private String stderr = "";

    public RunCommand(String... command) {
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(command);
        } catch (IOException ex) {
            LOG.error("IOException RunTime.exec: {}" + ex.getMessage(), ex);
            System.exit(48);
        }

        // deal with the process's stdout. which is an input stream for us.
        stdout = gatherStream(proc.getInputStream());
        stderr = gatherStream(proc.getInputStream());

        try {
            proc.waitFor();
        } catch (InterruptedException ex) {
            LOG.error("InterruptedException proc.waitFor(): {}" + ex.getMessage(), ex);
            System.exit(48);
        }

    }

    private String gatherStream(InputStream stream) {
        String output = "";
        Scanner scanner = new Scanner(stream, "UTF-8");
        while (scanner.hasNextLine()) {
            output += scanner.nextLine();
        }
        scanner.close();
        return output;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }
}
