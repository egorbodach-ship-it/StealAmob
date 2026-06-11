package Polfg.Polfg;

/**
 * Callback the brainrot-auction plugin registers so BrainrotBases can notify it
 * when a listed mob disappears for reasons outside the auction (e.g. it was
 * stolen by another player). The auction plugin should then drop that listing.
 */
public interface AuctionHook {
    void onListedMobRemoved(String base, String mobPoint);
}
