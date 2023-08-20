package software.solid.fluttervlcplayer;

import androidx.annotation.NonNull;

import io.flutter.FlutterInjector;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;

public class FlutterVlcPlayerPlugin implements FlutterPlugin, ActivityAware {

    private static FlutterVlcPlayerFactory flutterVlcPlayerFactory;
    private FlutterPluginBinding flutterPluginBinding;

    private static final String VIEW_TYPE = "flutter_video_plugin/getVideoView";

    @SuppressWarnings("deprecation")
    public static void registerWith(PluginRegistry.Registrar registrar) {
        if (flutterVlcPlayerFactory == null) {
            flutterVlcPlayerFactory = new FlutterVlcPlayerFactory(
                    registrar.messenger(),
                    registrar.textures(),
                    registrar::lookupKeyForAsset,
                    registrar::lookupKeyForAsset
            );

            registrar.platformViewRegistry()
                    .registerViewFactory(VIEW_TYPE, flutterVlcPlayerFactory);
        }

        registrar.addViewDestroyListener(view -> {
            stopListening();
            return false;
        });

        startListening();
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        flutterPluginBinding = binding;

        if (flutterVlcPlayerFactory == null) {
            final FlutterInjector injector = FlutterInjector.instance();
            flutterVlcPlayerFactory = new FlutterVlcPlayerFactory(
                    flutterPluginBinding.getBinaryMessenger(),
                    flutterPluginBinding.getTextureRegistry(),
                    injector.flutterLoader()::getLookupKeyForAsset,
                    injector.flutterLoader()::getLookupKeyForAsset
            );
            flutterPluginBinding.getPlatformViewRegistry()
                    .registerViewFactory(VIEW_TYPE, flutterVlcPlayerFactory);
        }
        startListening();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        stopListening();
        flutterPluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    }

    @Override
    public void onDetachedFromActivity() {
    }

    private static void startListening() {
        if (flutterVlcPlayerFactory != null) {
            flutterVlcPlayerFactory.startListening();
        }
    }

    private static void stopListening() {
        if (flutterVlcPlayerFactory != null) {
            flutterVlcPlayerFactory.stopListening();
            flutterVlcPlayerFactory = null;
        }
    }
}
