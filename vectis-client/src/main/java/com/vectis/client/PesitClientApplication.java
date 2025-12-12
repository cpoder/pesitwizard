package com.vectis.client;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * PeSIT Client Application
 * Command-line interface for PeSIT file transfers
 */
@SpringBootApplication(exclude = {
        // Exclude OTLP auto-configuration - enable via OTEL_METRICS_ENABLED=true
        OtlpMetricsExportAutoConfiguration.class
})
@EnableScheduling
public class PesitClientApplication implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private final PesitClientCommand clientCommand;
    private int exitCode;

    public PesitClientApplication(IFactory factory, PesitClientCommand clientCommand) {
        this.factory = factory;
        this.clientCommand = clientCommand;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(PesitClientApplication.class, args)));
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(clientCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
