package com.netflix.zuul.scriptManager;

final class UsageDoc {

    String get() {
        StringBuilder s = new StringBuilder();
        s.append("Usage: /scriptManager?action=<ACTION_TYPE>&<ARGS>").append("\n");
        s.append("       Actions:").append("\n");
        s.append("          LIST: List all endpoints with scripts or all scripts for a given endpoint.").append("\n");
        s.append("              Arguments:").append("\n");
        s.append("                  endpoint: [Optional (Default: All endpoints)] The endpoint of script revisions to list.").append("\n");
        s.append("              Examples:").append("\n");
        s.append("                GET /scriptManager?action=LIST").append("\n");
        s.append("                GET /scriptManager?action=LIST&endpoint=/ps3/home").append("\n");
        s.append("\n");

        s.append("          DOWNLOAD: Download a given script.").append("\n");
        s.append("              Arguments:").append("\n");
        s.append("                  endpoint: [Required] The endpoint of script to download.").append("\n");
        s.append("                  revision: [Optional (Default: last revision)] The revision to download.")
             .append("\n");
        s.append("              Examples:").append("\n");
        s.append("                GET /scriptManager?action=DOWNLOAD&endpoint=/ps3/home").append("\n");
        s.append("                GET /scriptManager?action=DOWNLOAD&endpoint=/ps3/home&revision=23").append("\n");
        s.append("\n");

        s.append("          UPLOAD: Upload a script for a given endpoint.").append("\n");
        s.append("              Arguments:").append("\n");
        s.append("                  endpoint: [Required] The endpoint to associated the script with. If it doesn't exist it will be created.").append("\n");
        s.append("                  userAuthenticationRequired: [Optional (Default: true)] Whether the script requires an authenticated user to execute.").append("\n");
        s.append("              Example:").append("\n");
        s.append("                POST /scriptManager?action=UPLOAD&endpoint=/ps3/home").append("\n");
        s.append("                POST /scriptManager?action=UPLOAD&endpoint=/ps3/home&userAuthenticationRequired=false").append("\n");
        s.append("\n");

        s.append("          ACTIVATE: Mark a particular script revision as active for production.").append("\n");
        s.append("              Arguments:").append("\n");
        s.append("                  endpoint: [Required] The endpoint for which a script revision should be activated.").append("\n");
        s.append("                  revision: [Required] The script revision to activate.").append("\n");
        s.append("              Example:").append("\n");
        s.append("                PUT /scriptManager?action=ACTIVATE&endpoint=/ps3/home&revision=22").append("\n");
        return s.toString();
    }
}
