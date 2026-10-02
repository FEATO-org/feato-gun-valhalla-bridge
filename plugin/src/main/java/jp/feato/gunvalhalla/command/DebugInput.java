package jp.feato.gunvalhalla.command;

/** Bounded administrator test input; no command concatenation/execution. */
public final class DebugInput {
    private DebugInput() { }
    public static double experience(String value) {
        double amount;
        try { amount = Double.parseDouble(value); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("EXP must be a number"); }
        if (!Double.isFinite(amount) || amount <= 0 || amount > 1_000_000)
            throw new IllegalArgumentException("EXP must be finite and in (0, 1000000]");
        return amount;
    }
}
