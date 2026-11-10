package cn.academy.client;

/** Direct state machine from Railgun.scala's client KeyDelegate. */
public final class RailgunDelegate {
    public interface Coin { boolean alive(); double progress(); }
    public enum Press { NONE, CHARGE, FIRE_COIN }
    private Coin coin;
    private int chargeTicks = -1;
    public void informThrow(Coin thrown) {
        if (coin == null || !coin.alive()) { coin = thrown; abort(); }
    }
    public Press down(boolean acceptedItem) {
        if (coin == null) {
            if (acceptedItem) { chargeTicks = cn.academy.data.Balance.i("railgun", "item_charge_ticks"); return Press.CHARGE; }
        } else {
            boolean success = coin.progress() > cn.academy.data.Balance.d("railgun", "coin_success_progress");
            coin = null; // Early and successful judgments both consume the one opportunity.
            if (success) return Press.FIRE_COIN;
        }
        return Press.NONE;
    }
    public boolean tick() {
        if (chargeTicks != -1) { chargeTicks--; return chargeTicks == 0; }
        return false;
    }
    public void abort() { chargeTicks = -1; }
    public int state() {
        if (coin != null && coin.alive()) return coin.progress() < cn.academy.data.Balance.d("railgun", "coin_hint_progress") ? 1 : 2;
        return chargeTicks == -1 ? 0 : 1;
    }
}
