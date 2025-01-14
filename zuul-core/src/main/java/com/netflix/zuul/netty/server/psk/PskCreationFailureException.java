/*
 * Copyright 2024 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
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
