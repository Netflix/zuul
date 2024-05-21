package com.netflix.zuul.netty.server.psk;

public class PskCreationFailureException extends Exception {

    public enum TlsAlertMessage {
        /**
         * The server does not recognize the (client) PSK identity
         */
        unknown_psk_identity,
        /**
         * The (client) PSK identity existed but the key was incorrect
         */
        decrypt_error,
    }

    private final TlsAlertMessage tlsAlertMessage;

    public PskCreationFailureException(TlsAlertMessage tlsAlertMessage, String message) {
        super(message);
        this.tlsAlertMessage = tlsAlertMessage;
    }

    public PskCreationFailureException(TlsAlertMessage tlsAlertMessage, String message, Throwable cause) {
        super(message, cause);
        this.tlsAlertMessage = tlsAlertMessage;
    }

    public TlsAlertMessage getTlsAlertMessage() {
        return tlsAlertMessage;
    }
}
