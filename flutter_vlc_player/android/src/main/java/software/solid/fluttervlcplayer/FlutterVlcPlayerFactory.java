package software.solid.fluttervlcplayer;

import android.content.Context;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;
import io.flutter.view.TextureRegistry;

final class FlutterVlcPlayerFactory extends PlatformViewFactory {

    public interface KeyForAssetFn {
        String get(String asset);
    }

    public interface KeyForAssetAndPackageName {
        String get(String asset, String packageName);
    }

    private final BinaryMessenger messenger;
    private final TextureRegistry textureRegistry;
    private final KeyForAssetFn keyForAsset;
    private final KeyForAssetAndPackageName keyForAssetAndPackageName;
    private final FlutterVlcPlayerBuilder flutterVlcPlayerBuilder = new FlutterVlcPlayerBuilder();
    private final HandlerThread bgThread = new HandlerThread("VLCBackgroundThread");

    FlutterVlcPlayerFactory(
            @NonNull BinaryMessenger messenger,
            @NonNull TextureRegistry textureRegistry,
            @NonNull KeyForAssetFn keyForAsset,
            @NonNull KeyForAssetAndPackageName keyForAssetAndPackageName
    ) {
        super(StandardMessageCodec.INSTANCE);
        this.messenger = messenger;
        this.textureRegistry = textureRegistry;
        this.keyForAsset = keyForAsset;
        this.keyForAssetAndPackageName = keyForAssetAndPackageName;
        bgThread.start();
    }

    @NonNull
    @Override
    public PlatformView create(@NonNull Context context, int viewId, @Nullable Object args) {
        return flutterVlcPlayerBuilder.build(
                viewId,
                context,
                bgThread.getLooper(),
                messenger,
                textureRegistry,
                keyForAsset,
                keyForAssetAndPackageName
        );
    }

    public void startListening() {
        flutterVlcPlayerBuilder.startListening(messenger);
    }

    public void stopListening() {
        flutterVlcPlayerBuilder.stopListening(messenger);
    }
}
