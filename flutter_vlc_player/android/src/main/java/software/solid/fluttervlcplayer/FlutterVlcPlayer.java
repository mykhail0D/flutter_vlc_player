package software.solid.fluttervlcplayer;

import org.videolan.BuildConfig;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.RendererDiscoverer;
import org.videolan.libvlc.RendererItem;
import org.videolan.libvlc.interfaces.IMedia;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.view.TextureRegistry;
import software.solid.fluttervlcplayer.Enums.HwAcc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FlutterVlcPlayer implements PlatformView {

    private static final String TAG = "FlutterVlcPlayer";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String VIDEO_EVENTS_CHANNEL = "flutter_video_plugin/getVideoEvents_";
    private static final String VIDEO_RENDER_CHANNEL = "flutter_video_plugin/getRendererEvents_";
    //
    private final Context context;
    private final VLCTextureView textureView;
    private final TextureRegistry.SurfaceTextureEntry textureEntry;
    //
    private final QueuingEventSink mediaEventSink = new QueuingEventSink();
    private final EventChannel mediaEventChannel;
    //
    private final QueuingEventSink rendererEventSink = new QueuingEventSink();
    private final EventChannel rendererEventChannel;
    //
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private List<String> options;
    private List<RendererDiscoverer> rendererDiscoverers = new ArrayList<>();
    private List<RendererItem> rendererItems = new ArrayList<>();
    private boolean isDisposed = false;

    private final HandlerThread bgThread = new HandlerThread("MP-BG_Thread");
    private final Handler bgHandler;

    // Platform view
    @Override
    public View getView() {
        return textureView;
    }

    @Override
    public void dispose() {
        if (isDisposed) {
            return;
        }

        Map<String, Object> eventObject = new HashMap<>();
        eventObject.put("event", "stopped");
        mediaEventSink.success(eventObject);
        mediaEventSink.setDelegate(null);
        rendererEventSink.setDelegate(null);

        textureView.dispose();
        textureEntry.release();
        mediaEventChannel.setStreamHandler(null);
        rendererEventChannel.setStreamHandler(null);

        if (mediaPlayer != null) {
            final MediaPlayer mediaPlayer = this.mediaPlayer;
            bgHandler.removeCallbacksAndMessages(null);
            bgHandler.post(() -> {
                mediaPlayer.stop();
                mediaPlayer.getVLCVout().detachViews();
                mediaPlayer.release();
                bgHandler.removeCallbacksAndMessages(null);
                bgThread.quit();
            });
            this.mediaPlayer = null;
        }

        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }

        isDisposed = true;
    }

    // VLC Player
    FlutterVlcPlayer(int viewId, Context context, BinaryMessenger binaryMessenger, TextureRegistry textureRegistry) {
        this.context = context;
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());

        mediaEventChannel = new EventChannel(binaryMessenger, VIDEO_EVENTS_CHANNEL + viewId);
        mediaEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, EventChannel.EventSink sink) {
                mediaEventSink.setDelegate(sink);
            }

            @Override
            public void onCancel(Object o) {
                mediaEventSink.setDelegate(null);
            }
        });

        rendererEventChannel = new EventChannel(binaryMessenger, VIDEO_RENDER_CHANNEL + viewId);
        rendererEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, EventChannel.EventSink sink) {
                rendererEventSink.setDelegate(sink);
            }

            @Override
            public void onCancel(Object o) {
                rendererEventSink.setDelegate(null);
            }
        });

        textureEntry = textureRegistry.createSurfaceTexture();
        textureView = new VLCTextureView(context);
        textureView.setSurfaceTexture(textureEntry.surfaceTexture());
        textureView.forceLayout();
        textureView.setFitsSystemWindows(true);
    }

    public void initialize(List<String> options) {
        this.options = options;
        libVLC = new LibVLC(context, options);
        mediaPlayer = new MediaPlayer(libVLC);
        setupVlcMediaPlayer();
    }

    private void setupVlcMediaPlayer() {
        mediaPlayer.getVLCVout().setWindowSize(textureView.getWidth(), textureView.getHeight());
        mediaPlayer.getVLCVout().setVideoSurface(textureView.getSurfaceTexture());

        textureView.setTextureEntry(textureEntry);
        textureView.setMediaPlayer(mediaPlayer);
        mediaPlayer.setVideoTrackEnabled(true);
        //
        mediaPlayer.setEventListener(event -> {
            if (mediaPlayer == null) {
                return;
            }

            HashMap<String, Object> eventObject = new HashMap<>();
            //
            // Current video track is only available when the media is playing
            int height = 0;
            int width = 0;
            final Media.VideoTrack currentVideoTrack = mediaPlayer.getCurrentVideoTrack();

            if (currentVideoTrack != null) {
                height = currentVideoTrack.height;
                width = currentVideoTrack.width;
            }

            switch (event.type) {
                case MediaPlayer.Event.Opening:
                    eventObject.put("event", "opening");
                    mediaEventSink.success(eventObject);
                    break;
                case MediaPlayer.Event.Paused:
                    eventObject.put("event", "paused");
                    mediaEventSink.success(eventObject);
                    break;
                case MediaPlayer.Event.Stopped:
                    eventObject.put("event", "stopped");
                    mediaEventSink.success(eventObject);
                    break;
                case MediaPlayer.Event.Playing:
                    eventObject.put("event", "playing");
                    eventObject.put("height", height);
                    eventObject.put("width", width);
                    eventObject.put("speed", mediaPlayer.getRate());
                    eventObject.put("duration", mediaPlayer.getLength());
                    eventObject.put("audioTracksCount", mediaPlayer.getAudioTracksCount());
                    eventObject.put("activeAudioTrack", mediaPlayer.getAudioTrack());
                    eventObject.put("spuTracksCount", mediaPlayer.getSpuTracksCount());
                    eventObject.put("activeSpuTrack", mediaPlayer.getSpuTrack());
                    mediaEventSink.success(eventObject.clone());
                    break;
                case MediaPlayer.Event.Vout:
                    //  mediaPlayer.getVLCVout().setWindowSize(textureView.getWidth(), textureView.getHeight());
                    break;
                case MediaPlayer.Event.EndReached:
                    eventObject.put("event", "ended");
                    eventObject.put("position", mediaPlayer.getTime());
                    mediaEventSink.success(eventObject);
                    break;
                case MediaPlayer.Event.Buffering:
                case MediaPlayer.Event.TimeChanged:
                    eventObject.put("event", "timeChanged");
                    eventObject.put("height", height);
                    eventObject.put("width", width);
                    eventObject.put("speed", mediaPlayer.getRate());
                    eventObject.put("position", mediaPlayer.getTime());
                    eventObject.put("duration", mediaPlayer.getLength());
                    eventObject.put("buffer", event.getBuffering());
                    eventObject.put("audioTracksCount", mediaPlayer.getAudioTracksCount());
                    eventObject.put("activeAudioTrack", mediaPlayer.getAudioTrack());
                    eventObject.put("spuTracksCount", mediaPlayer.getSpuTracksCount());
                    eventObject.put("activeSpuTrack", mediaPlayer.getSpuTrack());
                    eventObject.put("isPlaying", mediaPlayer.isPlaying());
                    mediaEventSink.success(eventObject);
                    break;
                case MediaPlayer.Event.EncounteredError:
                    //mediaEventSink.error("500", "Player State got an error.", null);
                    eventObject.put("event", "error");
                    mediaEventSink.success(eventObject);
                    break;
                case MediaPlayer.Event.RecordChanged:
                    eventObject.put("event", "recording");
                    eventObject.put("isRecording", event.getRecording());
                    eventObject.put("recordPath", event.getRecordPath());
                    mediaEventSink.success(eventObject);
                    break;
                case MediaPlayer.Event.LengthChanged:
                case MediaPlayer.Event.MediaChanged:
                case MediaPlayer.Event.ESAdded:
                case MediaPlayer.Event.ESDeleted:
                case MediaPlayer.Event.ESSelected:
                case MediaPlayer.Event.PausableChanged:
                case MediaPlayer.Event.SeekableChanged:
                case MediaPlayer.Event.PositionChanged:
                default:
                    break;
            }
        });
    }

    void play() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    void stop() {
        if (mediaPlayer != null) {
            bgHandler.post(() -> mediaPlayer.stop());
        }
    }

    boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    boolean isSeekable() {
        return mediaPlayer != null && mediaPlayer.isSeekable();
    }

    void setStreamUrl(String url, boolean isAssetUrl, boolean autoPlay, long hwAcc) {
        if (mediaPlayer != null && !isDisposed) {
            bgHandler.removeCallbacksAndMessages(null);
            bgHandler.post(() -> setStreamUrlAsync(url, isAssetUrl, autoPlay, hwAcc));
        }
    }

    private void setStreamUrlAsync(String url, boolean isAssetUrl, boolean autoPlay, long hwAcc) {
        if (mediaPlayer == null || isDisposed) {
            return;
        }

        try {
            mediaPlayer.stop();

            final Media media = isAssetUrl ?
                    new Media(libVLC, context.getAssets().openFd(url)) :
                    new Media(libVLC, Uri.parse(url));

            final HwAcc hwAccValue = HwAcc.values()[(int) hwAcc];
            switch (hwAccValue) {
                case DISABLED:
                    media.setHWDecoderEnabled(false, false);
                    break;
                case DECODING:
                case FULL:
                    media.setHWDecoderEnabled(true, true);
                    break;
            }

            if (hwAccValue == HwAcc.DECODING) {
                media.addOption(":no-mediacodec-dr");
                media.addOption(":no-omxil-dr");
            }

            if (options != null) {
                for (String option : options) {
                    media.addOption(option);
                }
            }

            mediaPlayer.setMedia(media);
            media.release();

            if (autoPlay) {
                mediaPlayer.play();
            }

        } catch (IOException e) {
            log(e.getMessage());
        }
    }

    void setLooping(boolean value) {
        if (mediaPlayer != null) {
            final IMedia media = mediaPlayer.getMedia();
            if (media != null) {
                media.addOption(value ? "--loop" : "--no-loop");
            }
        }
    }

    void setVolume(long value) {
        if (mediaPlayer != null) {
            long bracketedValue = Math.max(0, Math.min(100, value));
            mediaPlayer.setVolume((int) bracketedValue);
        }
    }

    int getVolume() {
        return mediaPlayer == null ? -1 : mediaPlayer.getVolume();
    }

    void setPlaybackSpeed(double value) {
        if (mediaPlayer != null) {
            mediaPlayer.setRate((float) value);
        }
    }

    float getPlaybackSpeed() {
        return mediaPlayer == null ? -1.0f : mediaPlayer.getRate();
    }

    void seekTo(int location) {
        if (mediaPlayer != null) {
            mediaPlayer.setTime(location);
        }
    }

    long getPosition() {
        return mediaPlayer == null ? -1 : mediaPlayer.getTime();
    }

    long getDuration() {
        return mediaPlayer == null ? -1 : mediaPlayer.getLength();
    }

    int getSpuTracksCount() {
        return mediaPlayer == null ? -1 : mediaPlayer.getSpuTracksCount();
    }

    Map<Integer, String> getSpuTracks() {
        if (mediaPlayer != null) {
            MediaPlayer.TrackDescription[] spuTracks = mediaPlayer.getSpuTracks();

            if (spuTracks != null) {
                Map<Integer, String> subtitles = new HashMap<>();

                for (MediaPlayer.TrackDescription trackDescription : spuTracks) {
                    if (trackDescription.id >= 0) {
                        subtitles.put(trackDescription.id, trackDescription.name);
                    }
                }

                return subtitles;
            }

        }

        return Collections.emptyMap();
    }

    void setSpuTrack(int index) {
        if (mediaPlayer != null) {
            mediaPlayer.setSpuTrack(index);
        }
    }

    int getSpuTrack() {
        return mediaPlayer == null ? -1 : mediaPlayer.getSpuTrack();
    }

    void setSpuDelay(long delay) {
        if (mediaPlayer != null) {
            mediaPlayer.setSpuDelay(delay);
        }
    }

    long getSpuDelay() {
        return mediaPlayer == null ? -1 : mediaPlayer.getSpuDelay();
    }

    void addSubtitleTrack(String url, boolean isSelected) {
        if (mediaPlayer != null) {
            mediaPlayer.addSlave(Media.Slave.Type.Subtitle, Uri.parse(url), isSelected);
        }
    }

    int getAudioTracksCount() {
        return mediaPlayer == null ? -1 : mediaPlayer.getAudioTracksCount();
    }

    Map<Integer, String> getAudioTracks() {
        if (mediaPlayer != null) {
            MediaPlayer.TrackDescription[] audioTracks = mediaPlayer.getAudioTracks();

            if (audioTracks != null) {
                Map<Integer, String> audios = new HashMap<>();

                for (MediaPlayer.TrackDescription trackDescription : audioTracks) {
                    if (trackDescription.id >= 0) {
                        audios.put(trackDescription.id, trackDescription.name);
                    }
                }

                return audios;
            }
        }

        return Collections.emptyMap();
    }

    void setAudioTrack(int index) {
        if (mediaPlayer != null) {
            mediaPlayer.setAudioTrack(index);
        }
    }

    int getAudioTrack() {
        return mediaPlayer == null ? -1 : mediaPlayer.getAudioTrack();
    }

    void setAudioDelay(long delay) {
        if (mediaPlayer != null) {
            mediaPlayer.setAudioDelay(delay);
        }
    }

    long getAudioDelay() {
        return mediaPlayer == null ? -1 : mediaPlayer.getAudioDelay();
    }

    void addAudioTrack(String url, boolean isSelected) {
        if (mediaPlayer != null) {
            mediaPlayer.addSlave(Media.Slave.Type.Audio, Uri.parse(url), isSelected);
        }
    }

    int getVideoTracksCount() {
        return mediaPlayer == null ? -1 : mediaPlayer.getVideoTracksCount();
    }

    Map<Integer, String> getVideoTracks() {
        if (mediaPlayer != null) {
            MediaPlayer.TrackDescription[] videoTracks = mediaPlayer.getVideoTracks();
            if (videoTracks != null) {
                Map<Integer, String> videos = new HashMap<>();

                for (MediaPlayer.TrackDescription trackDescription : videoTracks) {
                    if (trackDescription.id >= 0) {
                        videos.put(trackDescription.id, trackDescription.name);
                    }
                }

                return videos;
            }
        }

        return Collections.emptyMap();
    }

    void setVideoTrack(int index) {
        if (mediaPlayer != null) {
            mediaPlayer.setVideoTrack(index);
        }
    }

    int getVideoTrack() {
        return mediaPlayer == null ? -1 : mediaPlayer.getVideoTrack();
    }

    void setVideoScale(float scale) {
        if (mediaPlayer != null) {
            mediaPlayer.setScale(scale);
        }
    }

    float getVideoScale() {
        return mediaPlayer == null ? -1.0f : mediaPlayer.getScale();
    }

    void setVideoAspectRatio(String aspectRatio) {
        if (mediaPlayer != null) {
            mediaPlayer.setAspectRatio(aspectRatio);
        }
    }

    String getVideoAspectRatio() {
        return mediaPlayer == null ? "" : mediaPlayer.getAspectRatio();
    }

    void startRendererScanning(String rendererService) {
        if (libVLC == null) {
            return;
        }

        //
        //  android -> chromecast -> "microdns"
        //  ios -> chromecast -> "Bonjour_renderer"
        //
        rendererDiscoverers = new ArrayList<>();
        rendererItems = new ArrayList<>();
        //
        //todo: check for duplicates
        RendererDiscoverer.Description[] renderers = RendererDiscoverer.list(libVLC);
        for (RendererDiscoverer.Description renderer : renderers) {
            RendererDiscoverer rendererDiscoverer = new RendererDiscoverer(libVLC, renderer.name);
            try {
                rendererDiscoverer.setEventListener(event -> {
                    Map<String, Object> eventObject = new HashMap<>();
                    RendererItem item = event.getItem();
                    switch (event.type) {
                        case RendererDiscoverer.Event.ItemAdded:
                            rendererItems.add(item);
                            eventObject.put("event", "attached");
                            eventObject.put("id", item.name);
                            eventObject.put("name", item.displayName);
                            rendererEventSink.success(eventObject);
                            break;
                        case RendererDiscoverer.Event.ItemDeleted:
                            rendererItems.remove(item);
                            eventObject.put("event", "detached");
                            eventObject.put("id", item.name);
                            eventObject.put("name", item.displayName);
                            rendererEventSink.success(eventObject);
                            break;
                        default:
                            break;
                    }
                });
                rendererDiscoverer.start();
                rendererDiscoverers.add(rendererDiscoverer);
            } catch (Exception ex) {
                rendererDiscoverer.setEventListener(null);
            }
        }
    }

    void stopRendererScanning() {
        if (mediaPlayer == null || isDisposed) {
            return;
        }

        for (RendererDiscoverer rendererDiscoverer : rendererDiscoverers) {
            rendererDiscoverer.stop();
            rendererDiscoverer.setEventListener(null);
        }
        rendererDiscoverers.clear();
        rendererItems.clear();

        // return back to default output
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            mediaPlayer.setRenderer(null);
            mediaPlayer.play();
        }
    }

    List<String> getAvailableRendererServices() {
        if (libVLC != null) {
            RendererDiscoverer.Description[] renderers = RendererDiscoverer.list(libVLC);
            List<String> availableRendererServices = new ArrayList<>();

            for (RendererDiscoverer.Description renderer : renderers) {
                availableRendererServices.add(renderer.name);
            }

            return availableRendererServices;
        }

        return Collections.emptyList();
    }

    Map<String, String> getRendererDevices() {
        if (rendererItems != null) {
            Map<String, String> renderers = new HashMap<>();

            for (RendererItem rendererItem : rendererItems) {
                renderers.put(rendererItem.name, rendererItem.displayName);
            }

            return renderers;
        }

        return Collections.emptyMap();
    }

    void castToRenderer(String rendererDevice) {
        if (mediaPlayer == null || isDisposed) {
            return;
        }

        boolean isPlaying = mediaPlayer.isPlaying();
        if (isPlaying) {
            mediaPlayer.pause();
        }

        // if you set it to null, it will start to render normally (i.e. locally) again
        RendererItem rendererItem = null;
        for (RendererItem item : rendererItems) {
            if (item.name.equals(rendererDevice)) {
                rendererItem = item;
                break;
            }
        }

        mediaPlayer.setRenderer(rendererItem);

        // start the playback
        mediaPlayer.play();
    }

    String getSnapshot() {
        if (textureView != null) {
            Bitmap bitmap = textureView.getBitmap();
            if (bitmap != null) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
            }
        }

        return "";
    }

    Boolean startRecording(String directory) {
        return mediaPlayer.record(directory);
    }

    Boolean stopRecording() {
        return mediaPlayer.record(null);
    }

    private void log(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

}
