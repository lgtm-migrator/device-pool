/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.example;

import picocli.CommandLine;

@CommandLine.Command(
        name = "device-pool-examples",
        version = "1.0.0",
        description = "Local app containing examples to show case device pools.",
        subcommands = { Ec2.class, Lab.class, Local.class }
)
public class ExampleApp {
    @CommandLine.Option(
            names = {"-h", "--help"},
            description = "Displays help usage information",
            scope = CommandLine.ScopeType.INHERIT,
            usageHelp = true
    )
    private boolean help;

    public static void main(String[] args) {
        System.exit(new CommandLine(new ExampleApp()).execute(args));
    }
}
