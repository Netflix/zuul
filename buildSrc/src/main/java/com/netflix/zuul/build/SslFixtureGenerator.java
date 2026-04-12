package com.netflix.zuul.build;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Generates short-lived SSL fixtures for local sample and test builds.
 */
public final class SslFixtureGenerator {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration CERT_VALIDITY = Duration.ofDays(2);
    private static final int RSA_KEY_SIZE = 2048;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private SslFixtureGenerator() {}

    public static void generate(Path outputDirectory) {
        char[] trustStorePassword = randomPassword().toCharArray();

        try {
            Path sslDirectory = outputDirectory.resolve("ssl");
            Files.createDirectories(sslDirectory);

            KeyPair serverKeyPair = generateKeyPair();
            X509Certificate serverCertificate = generateCertificate(
                    "CN=localhost, OU=Zuul Sample, O=Netflix",
                    serverKeyPair,
                    KeyPurposeId.id_kp_serverAuth,
                    true);

            KeyPair clientKeyPair = generateKeyPair();
            X509Certificate clientCertificate = generateCertificate(
                    "CN=zuul-sample-client, OU=Zuul Sample, O=Netflix",
                    clientKeyPair,
                    KeyPurposeId.id_kp_clientAuth,
                    false);

            writePem(sslDirectory.resolve("server.key"), serverKeyPair.getPrivate());
            writePem(sslDirectory.resolve("server.cert"), serverCertificate);
            writePem(sslDirectory.resolve("client.key"), clientKeyPair.getPrivate());
            writePem(sslDirectory.resolve("client.cert"), clientCertificate);
            writeTrustStore(sslDirectory.resolve("truststore.jks"), trustStorePassword, clientCertificate);
            Files.writeString(
                    sslDirectory.resolve("truststore.key"),
                    new String(trustStorePassword) + System.lineSeparator(),
                    StandardCharsets.US_ASCII);
        } catch (IOException | GeneralSecurityException | OperatorCreationException e) {
            throw new IllegalStateException("Unable to generate local SSL fixtures in " + outputDirectory, e);
        } finally {
            java.util.Arrays.fill(trustStorePassword, '\0');
        }
    }

    private static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(RSA_KEY_SIZE, SECURE_RANDOM);
        return keyPairGenerator.generateKeyPair();
    }

    private static X509Certificate generateCertificate(
            String distinguishedName,
            KeyPair keyPair,
            KeyPurposeId extendedKeyUsage,
            boolean includeLocalhostSubjectAlternativeNames)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(Duration.ofMinutes(5)));
        Date notAfter = Date.from(now.plus(CERT_VALIDITY));
        X500Name subject = new X500Name(distinguishedName);

        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                subject, randomSerialNumber(), notBefore, notAfter, subject, keyPair.getPublic());
        certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certificateBuilder.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        certificateBuilder.addExtension(
                Extension.extendedKeyUsage, false, new ExtendedKeyUsage(extendedKeyUsage));

        if (includeLocalhostSubjectAlternativeNames) {
            certificateBuilder.addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    new GeneralNames(new GeneralName[] {
                        new GeneralName(GeneralName.dNSName, "localhost"),
                        new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                        new GeneralName(GeneralName.iPAddress, "::1"),
                    }));
        }

        ContentSigner signer =
                new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder certificateHolder = certificateBuilder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certificateHolder);
        certificate.checkValidity(new Date());
        certificate.verify(keyPair.getPublic());
        return certificate;
    }

    private static void writeTrustStore(Path trustStorePath, char[] password, X509Certificate certificate)
            throws IOException, GeneralSecurityException {
        java.security.KeyStore trustStore = java.security.KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("zuul-sample-client", certificate);
        try (OutputStream outputStream = Files.newOutputStream(trustStorePath)) {
            trustStore.store(outputStream, password);
        }
    }

    private static void writePem(Path file, Object value) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.US_ASCII);
                JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(value);
        }
    }

    private static BigInteger randomSerialNumber() {
        return new BigInteger(160, SECURE_RANDOM).abs();
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
