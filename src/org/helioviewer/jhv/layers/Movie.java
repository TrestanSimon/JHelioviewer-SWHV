package org.helioviewer.jhv.layers;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.swing.Timer;

import org.helioviewer.jhv.display.Display;
import org.helioviewer.jhv.gui.components.MoviePanel;
import org.helioviewer.jhv.time.JHVTime;
import org.helioviewer.jhv.time.TimeListener;
import org.helioviewer.jhv.time.TimeUtils;
import org.helioviewer.jhv.view.View;

public class Movie {

    public enum AdvanceMode {
        Loop, Stop, Swing, SwingDown
    }

    public interface Listener {
        void frameChanged(int frame, boolean last);
    }

    public static final int FPS_RELATIVE_DEFAULT = 20;
    public static final int FPS_ABSOLUTE = 30;

    @Nullable
    private static JHVTime nextTime(AdvanceMode mode, JHVTime time,
                                    Supplier<JHVTime> firstTime, Supplier<JHVTime> lastTime,
                                    Function<JHVTime, JHVTime> lowerTime, Function<JHVTime, JHVTime> higherTime) {
        JHVTime next = mode == AdvanceMode.SwingDown ? lowerTime.apply(time) : higherTime.apply(time);
        if (next.milli == time.milli) { // already at the edges
            switch (mode) {
                case Stop:
                    if (next.milli == lastTime.get().milli) {
                        return null;
                    }
                    break;
                case Swing:
                    if (next.milli == lastTime.get().milli) {
                        setAdvanceMode(AdvanceMode.SwingDown);
                        return lowerTime.apply(next);
                    }
                    break;
                case SwingDown:
                    if (next.milli == firstTime.get().milli) {
                        setAdvanceMode(AdvanceMode.Swing);
                        return higherTime.apply(next);
                    }
                    break;
                default: // Loop
                    if (next.milli == lastTime.get().milli) {
                        return firstTime.get();
                    }
            }
        }
        return next;
    }

    public static void setMaster(ImageLayer layer) {
        if (layer == null) {
            pause();
            MoviePanel.unsetMovie();
        } else
            MoviePanel.setMovie(layer.getView().getMaximumFrameNumber());
        timeRangeChanged();
    }

    public static long getStartTime() {
        return movieStart;
    }

    public static long getEndTime() {
        return movieEnd;
    }

    private static long getMovieStart() {
        ImageLayer layer = Layers.getActiveImageLayer();
        return layer == null ? lastTimestamp.milli : layer.getStartTime();
    }

    private static long getMovieEnd() {
        ImageLayer layer = Layers.getActiveImageLayer();
        return layer == null ? lastTimestamp.milli : layer.getEndTime();
    }

    static void timeRangeChanged() {
        movieStart = getMovieStart();
        movieEnd = getMovieEnd();
        timeRangeListeners.forEach(listener -> listener.timeRangeChanged(movieStart, movieEnd));
    }

    private static int deltaT;

    private static void relativeTimeAdvance() {
        ImageLayer layer = Layers.getActiveImageLayer();
        if (layer != null) {
            View view = layer.getView();
            JHVTime next = nextTime(advanceMode, lastTimestamp,
                    view::getFirstTime, view::getLastTime,
                    view::getLowerTime, view::getHigherTime);

            if (next == null)
                pause();
            else
                setTime(next);
        }
    }

    private static void absoluteTimeAdvance() {
        ImageLayer layer = Layers.getActiveImageLayer();
        if (layer != null) {
            View view = layer.getView();
            JHVTime first = view.getFirstTime();
            JHVTime last = view.getLastTime();
            JHVTime next = nextTime(advanceMode, lastTimestamp,
                    () -> first, () -> last,
                    time -> new JHVTime(Math.max(first.milli, time.milli - deltaT)),
                    time -> new JHVTime(Math.min(last.milli, time.milli + deltaT)));

            if (next == null)
                pause();
            else
                syncTime(next);
        }
    }

    private static class RelativeTimeAdvanceListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            relativeTimeAdvance();
        }
    }

    private static class AbsoluteTimeAdvanceListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            absoluteTimeAdvance();
        }
    }

    private static final RelativeTimeAdvanceListener relativeTimeAdvanceListener = new RelativeTimeAdvanceListener();
    private static final AbsoluteTimeAdvanceListener absoluteTimeAdvanceListener = new AbsoluteTimeAdvanceListener();
    private static final Timer movieTimer = new Timer(1000 / FPS_RELATIVE_DEFAULT, relativeTimeAdvanceListener);

    public static boolean isPlaying() {
        return movieTimer.isRunning();
    }

    public static void play() {
        ImageLayer layer = Layers.getActiveImageLayer();
        if (layer != null && layer.getView().isMultiFrame()) {
            movieTimer.restart();
            MoviePanel.setPlayState(true);
        }
    }

    public static void pause() {
        movieTimer.stop();
        MoviePanel.setPlayState(false);
        MovieDisplay.render(1); /* ! force update for on the fly resolution change */
    }

    public static void toggle() {
        if (isPlaying())
            pause();
        else
            play();
    }

    public static void setTime(JHVTime dateTime) {
        ImageLayer layer = Layers.getActiveImageLayer();
        if (layer != null) {
            syncTime(layer.getView().getNearestTime(dateTime));
        }
    }

    public static void setFrame(int frame) {
        ImageLayer layer = Layers.getActiveImageLayer();
        if (layer != null) {
            syncTime(layer.getView().getFrameTime(frame));
        }
    }

    public static void nextFrame() {
        ImageLayer layer = Layers.getActiveImageLayer();
        if (layer != null) {
            syncTime(layer.getView().getHigherTime(lastTimestamp));
        }
    }

    public static void previousFrame() {
        ImageLayer layer = Layers.getActiveImageLayer();
        if (layer != null) {
            syncTime(layer.getView().getLowerTime(lastTimestamp));
        }
    }

    private static JHVTime lastTimestamp = TimeUtils.START;
    private static long movieStart = TimeUtils.START.milli;
    private static long movieEnd = TimeUtils.START.milli;

    public static JHVTime getTime() {
        return lastTimestamp;
    }

    private static void syncTime(JHVTime dateTime) {
        if (recording && notDone)
            return;

        lastTimestamp = dateTime;
        Display.getCamera().timeChanged(dateTime);

        Layers.setImageLayersNearestFrame(dateTime);
        MovieDisplay.render(1);

        timeListeners.forEach(listener -> listener.timeChanged(lastTimestamp.milli));

        View view = Layers.getActiveImageLayer().getView(); // should be not null
        int activeFrame = view.getCurrentFrameNumber();
        boolean last = activeFrame == view.getMaximumFrameNumber();

        frameListeners.forEach(listener -> listener.frameChanged(activeFrame, last));

        MoviePanel.setFrameSlider(activeFrame);

        if (recording)
            notDone = true;
    }

    private static final ArrayList<Listener> frameListeners = new ArrayList<>();
    private static final ArrayList<TimeListener.Change> timeListeners = new ArrayList<>();
    private static final ArrayList<TimeListener.Range> timeRangeListeners = new ArrayList<>();

    public static void addFrameListener(Listener listener) {
        if (!frameListeners.contains(listener))
            frameListeners.add(listener);
    }

    public static void removeFrameListener(Listener listener) {
        frameListeners.remove(listener);
    }

    public static void addTimeListener(TimeListener.Change listener) {
        if (!timeListeners.contains(listener)) {
            timeListeners.add(listener);
            listener.timeChanged(lastTimestamp.milli);
        }
    }

    public static void removeTimeListener(TimeListener.Change listener) {
        timeListeners.remove(listener);
    }

    public static void addTimeRangeListener(TimeListener.Range listener) {
        if (!timeRangeListeners.contains(listener)) {
            timeRangeListeners.add(listener);
            listener.timeRangeChanged(movieStart, movieEnd);
        }
    }

    public static void removeTimeRangeListener(TimeListener.Range listener) {
        timeRangeListeners.remove(listener);
    }

    public static void setDesiredRelativeSpeed(int fps) {
        for (ActionListener listener : movieTimer.getActionListeners())
            movieTimer.removeActionListener(listener);
        movieTimer.addActionListener(relativeTimeAdvanceListener);
        movieTimer.setDelay(1000 / fps);
        deltaT = 0;
    }

    public static void setDesiredAbsoluteSpeed(int sec) {
        for (ActionListener listener : movieTimer.getActionListeners())
            movieTimer.removeActionListener(listener);
        movieTimer.addActionListener(absoluteTimeAdvanceListener);
        movieTimer.setDelay(1000 / FPS_ABSOLUTE);
        deltaT = 1000 / FPS_ABSOLUTE * sec;
    }

    private static AdvanceMode advanceMode = AdvanceMode.Loop;

    public static void setAdvanceMode(AdvanceMode mode) {
        advanceMode = mode;
    }

    private static boolean recording;
    private static boolean notDone;

    public static void grabDone() {
        notDone = false;
    }

    public static void startRecording() {
        recording = true;
    }

    public static void stopRecording() {
        recording = false;
    }

    public static boolean isRecording() {
        return recording;
    }

}
