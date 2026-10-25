package ar.aurora.phone;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

final class ContentHash {
    static String sha256(InputStream stream, AtomicBoolean cancelled) throws IOException {
        if (stream==null) throw new IOException("Archivo no disponible");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[128*1024];
            int n;
            while ((n=stream.read(buffer))!=-1) {
                if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelado");
                digest.update(buffer,0,n);
            }
            if (cancelled.get()) throw new InterruptedIOException("Cancelado");
            StringBuilder result=new StringBuilder(64);
            for (byte b:digest.digest()) result.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private ContentHash() { }
}
