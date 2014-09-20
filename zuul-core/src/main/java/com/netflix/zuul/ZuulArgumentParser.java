package com.netflix.zuul;

/**
 * A utility runner for parsing standard program arguments used to create a zuul server from command line.
 *
 * @author Nitesh Kant
 */
public class ZuulArgumentParser {

    private String[] packagePrefix;
    private int port;

    public ZuulArgumentParser(String[] programArgs) {
        this(8888, new String[]{"com.netflix.zuul.filter.example"}, programArgs);
    }

    public ZuulArgumentParser(int portDefault, String[] packagePrefixDefault, String[] programArgs) {
        this(portDefault, packagePrefixDefault, ZuulArgumentParser.class.getCanonicalName(), programArgs);
    }

    public ZuulArgumentParser(int portDefault, String[] packagePrefixDefault, String mainRunnerClassName,
                              String[] programArgs) {
        port = portDefault;
        packagePrefix = packagePrefixDefault;

        if (programArgs.length > 0) {
            try {
                port = Integer.parseInt(programArgs[0]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: java " + mainRunnerClassName + " [<port>] [<comma separated package prefixes for filters>]");
            }
        }

        if (programArgs.length > 1) {
            packagePrefix = programArgs[1].split(",");
        }
    }

    public String[] getPackagePrefix() {
        return packagePrefix;
    }

    public int getPort() {
        return port;
    }
}
