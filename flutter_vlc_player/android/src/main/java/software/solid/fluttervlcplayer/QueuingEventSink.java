package software.solid.fluttervlcplayer;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.flutter.plugin.common.EventChannel;

/**
 * And implementation of {@link EventChannel.EventSink} which can wrap an underlying sink.
 *
 * <p>It delivers messages immediately when downstream is available, but it queues messages before
 * the delegate event sink is set with setDelegate.
 *
 * <p>This class is not thread-safe. All calls must be done on the same thread or synchronized
 * externally.
 */
final class QueuingEventSink implements EventChannel.EventSink {
    @Nullable
    private EventChannel.EventSink delegate;
    private final List<Object> eventQueue = Collections.synchronizedList(new ArrayList<>());
    private boolean done = false;

    public void setDelegate(EventChannel.EventSink delegate) {
        this.delegate = delegate;
        maybeFlush();
    }

    @Override
    public void endOfStream() {
        enqueue(new EndOfStreamEvent());
        maybeFlush();
        done = true;
    }

    @Override
    public void error(String code, String message, Object details) {
        enqueue(new ErrorEvent(code, message, details));
        maybeFlush();
    }

    @Override
    public void success(Object event) {
        enqueue(event);
        maybeFlush();
    }

    private void enqueue(Object event) {
        if (!done) {
            eventQueue.add(event);
        }
    }

    private void maybeFlush() {
        if (delegate != null) {
            for (int i = 0; i < eventQueue.size(); i++) {
                Object event = eventQueue.get(i);
                if (event instanceof EndOfStreamEvent) {
                    delegate.endOfStream();
                } else if (event instanceof ErrorEvent) {
                    ErrorEvent errorEvent = (ErrorEvent) event;
                    delegate.error(errorEvent.code, errorEvent.message, errorEvent.details);
                } else {
                    delegate.success(event);
                }
            }
            eventQueue.clear();
        }
    }

    private static class EndOfStreamEvent {
    }

    private static class ErrorEvent {
        String code;
        String message;
        Object details;

        ErrorEvent(String code, String message, Object details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }
    }
}