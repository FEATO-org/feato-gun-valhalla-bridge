package jp.feato.gunvalhalla.integration.guncore;

/** A player command cannot manufacture a server-created marker with the current session score. */
public final class TransportGuard {
    private TransportGuard() {}
    public static boolean accepts(boolean marker, boolean recordTag, Integer recordSession, int session) {
        return marker && recordTag && recordSession != null && session > 0 && recordSession == session;
    }
}
