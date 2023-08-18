package software.solid.fluttervlcplayer;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.AbstractVLCEvent;
import org.videolan.libvlc.interfaces.ILibVLC;

class VLCMediaPlayer extends MediaPlayer {

    VLCMediaPlayer(@NonNull ILibVLC libVLC) {
        super(libVLC);
    }

    @Override
    public synchronized void setEventListener(
            @Nullable AbstractVLCEvent.Listener<Event> listener,
            @Nullable Handler handler
    ) {
        super.setEventListener(listener, handler);
    }
}
