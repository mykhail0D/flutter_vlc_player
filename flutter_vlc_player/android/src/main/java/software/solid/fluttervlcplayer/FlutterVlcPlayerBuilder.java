package software.solid.fluttervlcplayer;

import android.content.Context;
import android.os.Looper;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.view.TextureRegistry;

final class FlutterVlcPlayerBuilder implements Messages.VlcPlayerApi {

    private static final class DataSourceType {
        private static final long ASSET = 0;

        private DataSourceType() {
        }
    }

    private final LongSparseArray<FlutterVlcPlayer> vlcPlayers = new LongSparseArray<>();
    private FlutterVlcPlayerFactory.KeyForAssetFn keyForAsset;
    private FlutterVlcPlayerFactory.KeyForAssetAndPackageName keyForAssetAndPackageName;

    void startListening(@NonNull BinaryMessenger messenger) {
        Messages.VlcPlayerApi.setup(messenger, this);
    }

    void stopListening(@NonNull BinaryMessenger messenger) {
        Messages.VlcPlayerApi.setup(messenger, null);
    }

    @NonNull
    FlutterVlcPlayer build(
            int viewId,
            @NonNull Context context,
            @NonNull Looper looper,
            @NonNull BinaryMessenger binaryMessenger,
            @NonNull TextureRegistry textureRegistry,
            @NonNull FlutterVlcPlayerFactory.KeyForAssetFn keyForAsset,
            @NonNull FlutterVlcPlayerFactory.KeyForAssetAndPackageName keyForAssetAndPackageName
    ) {
        this.keyForAsset = keyForAsset;
        this.keyForAssetAndPackageName = keyForAssetAndPackageName;
        // only create view for player and attach channel events
        FlutterVlcPlayer vlcPlayer = new FlutterVlcPlayer(
                viewId,
                context,
                looper,
                binaryMessenger,
                textureRegistry
        );
        vlcPlayers.append(viewId, vlcPlayer);
        return vlcPlayer;
    }

    private void disposeAllPlayers() {
        for (int i = 0; i < vlcPlayers.size(); i++) {
            vlcPlayers.valueAt(i).dispose();
        }

        vlcPlayers.clear();
    }

    @Override
    public void initialize() {
        //        disposeAllPlayers();
    }

    @Override
    public void create(Messages.CreateMessage arg) {
        List<String> options = arg.getOptions()
                .stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());

        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.initialize(options);
        setupStreamUrl(
                player,
                arg.getUri(),
                arg.getPackageName(),
                arg.getAutoPlay(),
                arg.getType(),
                arg.getHwAcc()
        );
    }

    @Override
    public void dispose(Messages.ViewMessage arg) {
        // the player has been already disposed by platform we just remove it from players list
        vlcPlayers.remove(arg.getViewId());
    }

    @Override
    public void setStreamUrl(Messages.SetMediaMessage arg) {
        setupStreamUrl(
                vlcPlayers.get(arg.getViewId()),
                arg.getUri(),
                arg.getPackageName(),
                arg.getAutoPlay(),
                arg.getType(),
                arg.getHwAcc()
        );
    }

    @Override
    public void play(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.play();
    }

    @Override
    public void pause(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.pause();
    }

    @Override
    public void stop(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.stop();
    }

    @Override
    public Messages.BooleanMessage isPlaying(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.BooleanMessage message = new Messages.BooleanMessage();
        message.setResult(player.isPlaying());
        return message;
    }

    @Override
    public Messages.BooleanMessage isSeekable(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.BooleanMessage message = new Messages.BooleanMessage();
        message.setResult(player.isSeekable());
        return message;
    }

    @Override
    public void setLooping(Messages.LoopingMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setLooping(arg.getIsLooping());
    }

    @Override
    public void seekTo(Messages.PositionMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.seekTo(arg.getPosition().intValue());
    }

    @Override
    public Messages.PositionMessage position(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.PositionMessage message = new Messages.PositionMessage();
        message.setPosition(player.getPosition());
        return message;
    }

    @Override
    public Messages.DurationMessage duration(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.DurationMessage message = new Messages.DurationMessage();
        message.setDuration(player.getDuration());
        return message;
    }

    @Override
    public void setVolume(Messages.VolumeMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setVolume(arg.getVolume());
    }

    @Override
    public Messages.VolumeMessage getVolume(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.VolumeMessage message = new Messages.VolumeMessage();
        message.setVolume((long) player.getVolume());
        return message;
    }

    @Override
    public void setPlaybackSpeed(Messages.PlaybackSpeedMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setPlaybackSpeed(arg.getSpeed());
    }

    @Override
    public Messages.PlaybackSpeedMessage getPlaybackSpeed(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.PlaybackSpeedMessage message = new Messages.PlaybackSpeedMessage();
        message.setSpeed((double) player.getPlaybackSpeed());
        return message;
    }

    @Override
    public Messages.SnapshotMessage takeSnapshot(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.SnapshotMessage message = new Messages.SnapshotMessage();
        message.setSnapshot(player.getSnapshot());
        return message;
    }

    @Override
    public Messages.TrackCountMessage getSpuTracksCount(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.TrackCountMessage message = new Messages.TrackCountMessage();
        message.setCount((long) player.getSpuTracksCount());
        return message;
    }

    @Override
    public Messages.SpuTracksMessage getSpuTracks(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.SpuTracksMessage message = new Messages.SpuTracksMessage();
        message.setSubtitles(player.getSpuTracks());
        return message;
    }

    @Override
    public void setSpuTrack(Messages.SpuTrackMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setSpuTrack(arg.getSpuTrackNumber().intValue());
    }

    @Override
    public Messages.SpuTrackMessage getSpuTrack(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.SpuTrackMessage message = new Messages.SpuTrackMessage();
        message.setSpuTrackNumber((long) player.getSpuTrack());
        return message;
    }

    @Override
    public void setSpuDelay(Messages.DelayMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setSpuDelay(arg.getDelay());
    }

    @Override
    public Messages.DelayMessage getSpuDelay(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.DelayMessage message = new Messages.DelayMessage();
        message.setDelay(player.getSpuDelay());
        return message;
    }

    @Override
    public void addSubtitleTrack(Messages.AddSubtitleMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.addSubtitleTrack(arg.getUri(), arg.getIsSelected());
    }

    @Override
    public Messages.TrackCountMessage getAudioTracksCount(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.TrackCountMessage message = new Messages.TrackCountMessage();
        message.setCount((long) player.getAudioTracksCount());
        return message;
    }

    @Override
    public Messages.AudioTracksMessage getAudioTracks(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.AudioTracksMessage message = new Messages.AudioTracksMessage();
        message.setAudios(player.getAudioTracks());
        return message;
    }

    @Override
    public void setAudioTrack(Messages.AudioTrackMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setAudioTrack(arg.getAudioTrackNumber().intValue());
    }

    @Override
    public Messages.AudioTrackMessage getAudioTrack(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.AudioTrackMessage message = new Messages.AudioTrackMessage();
        message.setAudioTrackNumber((long) player.getAudioTrack());
        return message;
    }

    @Override
    public void setAudioDelay(Messages.DelayMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setAudioDelay(arg.getDelay());
    }

    @Override
    public Messages.DelayMessage getAudioDelay(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.DelayMessage message = new Messages.DelayMessage();
        message.setDelay(player.getAudioDelay());
        return message;
    }

    @Override
    public void addAudioTrack(Messages.AddAudioMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.addAudioTrack(arg.getUri(), arg.getIsSelected());
    }

    @Override
    public Messages.TrackCountMessage getVideoTracksCount(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.TrackCountMessage message = new Messages.TrackCountMessage();
        message.setCount((long) player.getVideoTracksCount());
        return message;
    }

    @Override
    public Messages.VideoTracksMessage getVideoTracks(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.VideoTracksMessage message = new Messages.VideoTracksMessage();
        message.setVideos(player.getVideoTracks());
        return message;
    }

    @Override
    public void setVideoTrack(Messages.VideoTrackMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setVideoTrack(arg.getVideoTrackNumber().intValue());
    }

    @Override
    public Messages.VideoTrackMessage getVideoTrack(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.VideoTrackMessage message = new Messages.VideoTrackMessage();
        message.setVideoTrackNumber((long) player.getVideoTrack());
        return null;
    }

    @Override
    public void setVideoScale(Messages.VideoScaleMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setVideoScale(arg.getScale().floatValue());
    }

    @Override
    public Messages.VideoScaleMessage getVideoScale(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.VideoScaleMessage message = new Messages.VideoScaleMessage();
        message.setScale((double) player.getVideoScale());
        return message;
    }

    @Override
    public void setVideoAspectRatio(Messages.VideoAspectRatioMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.setVideoAspectRatio(arg.getAspectRatio());
    }

    @Override
    public Messages.VideoAspectRatioMessage getVideoAspectRatio(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.VideoAspectRatioMessage message = new Messages.VideoAspectRatioMessage();
        message.setAspectRatio(player.getVideoAspectRatio());
        return message;
    }

    @Override
    public Messages.RendererServicesMessage getAvailableRendererServices(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.RendererServicesMessage message = new Messages.RendererServicesMessage();
        message.setServices(player.getAvailableRendererServices());
        return message;
    }

    @Override
    public void startRendererScanning(Messages.RendererScanningMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.startRendererScanning(arg.getRendererService());
    }

    @Override
    public void stopRendererScanning(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.stopRendererScanning();
    }

    @Override
    public Messages.RendererDevicesMessage getRendererDevices(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        Messages.RendererDevicesMessage message = new Messages.RendererDevicesMessage();
        message.setRendererDevices(player.getRendererDevices());
        return message;
    }

    @Override
    public void castToRenderer(Messages.RenderDeviceMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        player.castToRenderer(arg.getRendererDevice());
    }

    @Override
    public Messages.BooleanMessage startRecording(Messages.RecordMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        boolean result = player.startRecording(arg.getSaveDirectory());
        Messages.BooleanMessage message = new Messages.BooleanMessage();
        message.setResult(result);
        return message;
    }

    @Override
    public Messages.BooleanMessage stopRecording(Messages.ViewMessage arg) {
        FlutterVlcPlayer player = vlcPlayers.get(arg.getViewId());
        boolean result = player.stopRecording();
        Messages.BooleanMessage message = new Messages.BooleanMessage();
        message.setResult(result);
        return message;
    }

    private void setupStreamUrl(
            @NonNull FlutterVlcPlayer player,
            @NonNull String uri,
            @Nullable String packageName,
            boolean autoPlay,
            long dataSourceType,
            long hwAcc
    ) {
        final String mediaUrl;
        final boolean isAssetUrl;

        if (dataSourceType == DataSourceType.ASSET) {
            mediaUrl = packageName != null
                    ? keyForAssetAndPackageName.get(uri, packageName)
                    : keyForAsset.get(uri);
            isAssetUrl = true;
        } else {
            mediaUrl = uri;
            isAssetUrl = false;
        }

        player.setStreamUrl(mediaUrl, isAssetUrl, autoPlay, hwAcc);
    }

}
