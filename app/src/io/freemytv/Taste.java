package io.freemytv;

import java.util.ArrayList;
import java.util.List;

/**
 * [custom] The owner's addition (see ENHANCED.md phase 4): "Your taste", a picks source from TMDB alone. Loved titles
 * (hold OK on a pick → Love it) bring TMDB's recommendations for them ("Because you loved …" under the card); the
 * genres chosen in Home Settings → Picks → Your taste bring the popular titles of those genres on the TV's services
 * (no label); "Not for me" titles never come back. Recs
 * puts these first when Home Settings → Picks → Suggestions is "Your taste" (Recs.tasteLists).
 */
final class Taste {
    private Taste() {}

    /**
     * The genres offered, by English name (Ui.tr translates it), with TMDB's movie and TV genre ids. TV has no Horror,
     * Thriller, Romance or History genre: those use the TV genres TMDB files such series under (horror series are
     * Mystery or Sci-Fi & Fantasy there), so the Shows tab isn't empty. Music has none: movies only.
     */
    static final String[][] GENRES = {
            {"Action", "28", "10759"}, {"Adventure", "12", "10759"}, {"Animation", "16", "16"}, {"Comedy", "35", "35"},
            {"Crime", "80", "80"}, {"Documentary", "99", "99"}, {"Drama", "18", "18"}, {"Family", "10751", "10751"},
            {"Fantasy", "14", "10765"}, {"History", "36", "10768|99"}, {"Horror", "27", "9648|10765"}, {"Music", "10402", ""},
            {"Mystery", "9648", "9648"}, {"Romance", "10749", "18"}, {"Science Fiction", "878", "10765"},
            {"Thriller", "53", "9648|80"}, {"War", "10752", "10768"}, {"Western", "37", "37"},
    };

    /** TMDB's genre ids for these names and kind ("movie" or "tv"), joined with "|" (any of them); "" for none. */
    static String ids(List<String> names, String kind) {
        List<String> out = new ArrayList<>();
        for (String[] g : GENRES) {
            if (!names.contains(g[0])) continue;
            String ids = kind.equals("movie") ? g[1] : g[2];
            for (String id : ids.split("\\|")) if (!id.isEmpty() && !out.contains(id)) out.add(id);
        }
        return String.join("|", out);
    }

    /** Raised when the taste lists are made differently: the picks are fetched again once, after the update. */
    private static final int VERSION = 6;

    /** Whether picks come from the owner's taste first. */
    static boolean on() {
        return "taste".equals(Prefs.picksSource());
    }

    /** Changes when the taste does: Recs fetches the list again (its source stamp). "Not for me" counts in both modes. */
    static long stamp() {
        long no = Prefs.tasteNo().hashCode();
        if (!on()) return no;
        return ((Prefs.tasteGenres().hashCode() * 31L + Prefs.tasteLoved().hashCode()) * 31L + no) * 31L + VERSION;
    }
}
