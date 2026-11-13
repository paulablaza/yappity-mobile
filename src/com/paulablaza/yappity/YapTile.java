package com.paulablaza.yappity;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

// Quick Settings tile: tap it to start or stop a yap.
public class YapTile extends TileService {
    @Override public void onStartListening() { refresh(); }

    @Override public void onClick() {
        if (YapService.running()) {
            YapService.start(this, YapService.ACTION_TOGGLE);
            refresh();
            return;
        }
        // Yappity isn't running yet. Android only lets the mic start from the app, so open it.
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        int s = YapService.currentState();
        t.setState(s == YapService.LISTENING ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(s == YapService.LISTENING ? "Done yapping" : s == YapService.WORKING ? "Writing..." : "Yap");
        t.updateTile();
    }
}
