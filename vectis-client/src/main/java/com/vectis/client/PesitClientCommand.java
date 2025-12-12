package com.vectis.client;

import org.springframework.stereotype.Component;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main CLI command for PeSIT client
 */
@Component
@Command(name = "pesit-client", mixinStandardHelpOptions = true, version = "1.0.0", description = "PeSIT file transfer client", subcommands = {
        SendCommand.class,
        ReceiveCommand.class,
        MessageCommand.class,
        ListCommand.class,
        TestCommand.class,
        ServeCommand.class
})
public class PesitClientCommand implements Runnable {

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output")
    boolean verbose;

    @Override
    public void run() {
        System.out.println("PeSIT Client v1.0.0");
        System.out.println("Use --help for usage information");
    }
}
