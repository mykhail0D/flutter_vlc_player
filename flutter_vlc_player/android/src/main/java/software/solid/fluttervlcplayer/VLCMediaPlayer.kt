@file:JvmName("VLCMediaPlayer")

package software.solid.fluttervlcplayer

import android.os.Handler
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.AbstractVLCEvent
import org.videolan.libvlc.interfaces.ILibVLC

class VLCMediaPlayer(libVLC: ILibVLC) : MediaPlayer(libVLC) {
    public override fun setEventListener(
        listener: AbstractVLCEvent.Listener<Event>?, handler: Handler?
    ) = super.setEventListener(listener, handler)
}