package com.czqwq.wikisearch;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Wraps the JVM default {@link SSLSocketFactory} and reorders cipher suites to closely match
 * Chrome 124's TLS ClientHello. Cloudflare uses the JA3 fingerprint (derived from the cipher-suite
 * list, extensions, and elliptic-curve list in the ClientHello) to detect non-browser clients. By
 * putting Chrome's preferred suites first we produce a JA3 hash that is much closer to a real
 * browser, greatly reducing the chance of a Cloudflare 403.
 */
public class ChromeLikeSSLSocketFactory extends SSLSocketFactory {

    /** Chrome 124 cipher preference order (suites not supported by the JVM are skipped). */
    private static final String[] PREFERRED_CIPHERS = {
        // TLS 1.3
        "TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256",
        // TLS 1.2 – ECDHE/ECDSA
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
        // TLS 1.2 – ECDHE/RSA
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        // TLS 1.2 – RSA
        "TLS_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_AES_256_GCM_SHA384", "TLS_RSA_WITH_AES_128_CBC_SHA",
        "TLS_RSA_WITH_AES_256_CBC_SHA", };

    private final SSLSocketFactory delegate;

    public ChromeLikeSSLSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    private static String[] reorder(String[] available) {
        List<String> supported = Arrays.asList(available);
        List<String> ordered = new ArrayList<>();
        for (String c : PREFERRED_CIPHERS) {
            if (supported.contains(c)) {
                ordered.add(c);
            }
        }
        for (String c : available) {
            if (!ordered.contains(c)) {
                ordered.add(c);
            }
        }
        return ordered.toArray(new String[0]);
    }

    private static Socket configure(Socket socket) {
        if (socket instanceof SSLSocket ssl) {
            ssl.setEnabledCipherSuites(reorder(ssl.getSupportedCipherSuites()));
            ssl.setEnabledProtocols(new String[] { "TLSv1.2", "TLSv1.3" });
        }
        return socket;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return configure(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return configure(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return configure(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return configure(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
        throws IOException {
        return configure(delegate.createSocket(address, port, localAddress, localPort));
    }
}
