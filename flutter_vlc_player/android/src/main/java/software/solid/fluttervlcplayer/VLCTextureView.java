package software.solid.fluttervlcplayer;

import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.View;
import android.util.AttributeSet;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import io.flutter.view.TextureRegistry;

public class VLCTextureView extends TextureView implements
        TextureView.SurfaceTextureListener,
        View.OnLayoutChangeListener,
        IVLCVout.OnNewVideoLayoutListener {

    private MediaPlayer mMediaPlayer = null;
    private TextureRegistry.SurfaceTextureEntry mTextureEntry = null;
    private SurfaceTexture mSurfaceTexture = null;
    private boolean wasPlaying = false;

    public VLCTextureView(final Context context) {
        this(context, null, 0);
    }

    public VLCTextureView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VLCTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setFocusable(false);
        setSurfaceTextureListener(this);
        addOnLayoutChangeListener(this);
    }

    public void dispose() {
        setSurfaceTextureListener(null);
        removeOnLayoutChangeListener(this);

        if (mSurfaceTexture != null) {
            if (!mSurfaceTexture.isReleased()) {
                mSurfaceTexture.release();
            }
            mSurfaceTexture = null;
        }

        mTextureEntry = null;
        mMediaPlayer = null;
    }


    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        if (mediaPlayer == null) {
            mMediaPlayer.getVLCVout().detachViews();
        }

        mMediaPlayer = mediaPlayer;

        if (mMediaPlayer != null) {
            mMediaPlayer.getVLCVout().attachViews(this);
        }
    }

    public void setTextureEntry(TextureRegistry.SurfaceTextureEntry textureEntry) {
        mTextureEntry = textureEntry;
        updateSurfaceTexture();
    }

    private void updateSurfaceTexture() {
        if (mTextureEntry != null) {
            final SurfaceTexture texture = mTextureEntry.surfaceTexture();
            if (!texture.isReleased() && (getSurfaceTexture() != texture)) {
                setSurfaceTexture(texture);
            }
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (mSurfaceTexture == null || mSurfaceTexture.isReleased()) {
            mSurfaceTexture = surface;

            if (mMediaPlayer != null) {
                final IVLCVout vOut = mMediaPlayer.getVLCVout();
                vOut.setWindowSize(width, height);

                if (!vOut.areViewsAttached()) {
                    vOut.setVideoSurface(mSurfaceTexture);

                    if (!vOut.areViewsAttached()) {
                        vOut.attachViews(this);
                    }

                    mMediaPlayer.setVideoTrackEnabled(true);

                    if (wasPlaying) {
                        mMediaPlayer.play();
                    }
                }
            }

            wasPlaying = false;
        } else {
            if (getSurfaceTexture() != mSurfaceTexture) {
                setSurfaceTexture(mSurfaceTexture);
            }
        }

    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        setSize(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        if (mMediaPlayer != null) {
            wasPlaying = mMediaPlayer.isPlaying();
        }

        if (mSurfaceTexture != surface) {
            if (mSurfaceTexture != null) {
                if (!mSurfaceTexture.isReleased()) {
                    mSurfaceTexture.release();
                }
            }
            mSurfaceTexture = surface;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    @Override
    public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        if (width * height != 0) {
            setSize(width, height);
        }
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateLayoutSize(view);
        }
    }

    public void updateLayoutSize(View view) {
        if (mMediaPlayer != null) {
            mMediaPlayer.getVLCVout().setWindowSize(view.getWidth(), view.getHeight());
            updateSurfaceTexture();
        }
    }

    private void setSize(int width, int height) {
        int mVideoWidth = 0;
        int mVideoHeight = 0;
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth * mVideoHeight <= 1) return;

        // Screen size
        int w = this.getWidth();
        int h = this.getHeight();

        // Size
        if (w > h && w < h) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) mVideoWidth / (float) mVideoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR) {
            h = (int) (w / videoAR);
        } else {
            w = (int) (h * videoAR);
        }

        // Layout fit
        ViewGroup.LayoutParams lp = this.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = h;
        this.setLayoutParams(lp);
        this.invalidate();
    }

}