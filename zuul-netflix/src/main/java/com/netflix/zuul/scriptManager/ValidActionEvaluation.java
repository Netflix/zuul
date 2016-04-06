package com.netflix.zuul.scriptManager;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.unmodifiableSet;

class ValidActionEvaluation {

    private static final Set<String> VALID_GET_ACTIONS = unmodifiableSet(newHashSet("LIST", "DOWNLOAD"));
    private static final Set<String> VALID_PUT_ACTIONS =
            unmodifiableSet(newHashSet("UPLOAD", "ACTIVATE", "DEACTIVATE", "RUN", "CANARY"));

    private final boolean result;

    ValidActionEvaluation(HttpServletRequest request, HttpServletResponse response) {
        this.result = resultOfEvaluation(request, response);
    }

    private boolean resultOfEvaluation(HttpServletRequest request, HttpServletResponse response) {
        String action = request.getParameter("action").trim().toUpperCase();
        if (VALID_GET_ACTIONS.contains(action)) {
            return validateGetAction(request, response);
        }

        if (VALID_PUT_ACTIONS.contains(action)) {
            return validatePutAction(request, response);
        }

        // wrong action
        new UsageError(400, "ERROR: Unknown action type.").setOn(response);
        return false;
    }

    private boolean validatePutAction(HttpServletRequest request, HttpServletResponse response) {
        if ((request.getMethod().equals("PUT") || request.getMethod().equals("POST"))) {
            return true;
        }
        addValidActionWrongMethodError(response);
        return false;
    }

    private void addValidActionWrongMethodError(HttpServletResponse response) {
        new UsageError(405, "ERROR: Invalid HTTP method for action type.").setOn(response);
    }

    private boolean validateGetAction(HttpServletRequest request, HttpServletResponse response) {
        if (request.getMethod().equals("GET")) {
            return true;
        }
        addValidActionWrongMethodError(response);
        return false;
    }

    public boolean isValid() {
        return result;
    }
}
