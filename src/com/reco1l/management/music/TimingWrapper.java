package com.reco1l.management.music;

// Created by Reco1l on 21/9/22 00:26

import android.util.Log;

import com.reco1l.Game;
import com.reco1l.framework.execution.Async;

import com.rian.difficultycalculator.beatmap.timings.EffectControlPoint;
import com.rian.difficultycalculator.beatmap.timings.EffectControlPointManager;
import com.rian.difficultycalculator.beatmap.timings.TimingControlPoint;
import com.rian.difficultycalculator.beatmap.timings.TimingControlPointManager;

import java.util.LinkedList;

import main.osu.TrackInfo;
import main.osu.beatmap.BeatmapData;
import main.osu.beatmap.parser.BeatmapParser;

public class TimingWrapper implements IMusicObserver {

    public static final TimingWrapper instance = new TimingWrapper();
    private static final int defaultBeatLength = 1000;

    private TimingControlPoint
            mLastTimingPoint,
            mFirstTimingPoint,
            mCurrentTimingPoint;

    private EffectControlPoint mCurrentEffectPoint;

    private final LinkedList<TimingControlPoint> mTimingPoints;
    private final LinkedList<EffectControlPoint> mEffectPoints;

    private short
            mBeat,
            mMaxBeat;

    private float
            mOffset,
            mBeatLength,
            mElapsedTime,
            mLastBeatLength,
            mLastElapsedTime;

    private boolean
            mIsNextBeat = false,
            mIsKiaiSection = false;

    //--------------------------------------------------------------------------------------------//

    public TimingWrapper() {
        mBeatLength = defaultBeatLength;
        mTimingPoints = new LinkedList<>();
        mEffectPoints = new LinkedList<>();
        Game.musicManager.bindMusicObserver(this);
    }

    //--------------------------------------------------------------------------------------------//

    private void clear() {
        mTimingPoints.clear();
        mEffectPoints.clear();
        mCurrentTimingPoint = null;
        mIsKiaiSection = false;
    }

    private void loadPointsFrom(TrackInfo track) {
        Async.run(() -> {
            BeatmapParser parser = new BeatmapParser(track.getFilename());

            if (parser.openFile()) {
                Log.i("TimingWrapper", "Parsed points from: " + track.getPublicName());
                parsePoints(parser.parse(false));
            }
        });
    }

    public void parsePoints(BeatmapData data) {
        if (data == null) {
            Log.i("TimingWrapper", "Data is null!");
            return;
        }

        TimingControlPointManager timingManager = data.timingPoints.timing;
        EffectControlPointManager effectManager = data.timingPoints.effect;

        mTimingPoints.addAll(timingManager.getControlPoints());
        mEffectPoints.addAll(effectManager.getControlPoints());

        Log.i("TimingWrapper", "Timing points found: " + mTimingPoints.size());
        Log.i("TimingWrapper", "Effect points found: " + mEffectPoints.size());

        mFirstTimingPoint = timingManager.controlPointAt(0);
        mCurrentTimingPoint = mFirstTimingPoint;
        mLastTimingPoint = mCurrentTimingPoint;
        mBeatLength = (float) mFirstTimingPoint.msPerBeat;
        mMaxBeat = (short) (mFirstTimingPoint.timeSignature - 1);

        mCurrentEffectPoint = effectManager.controlPointAt(0);
        mIsKiaiSection = mCurrentEffectPoint.isKiai;

        if (computeFirstBpmLength()) {
            computeOffset();
        }
    }

    //--------------------------------------------------------------------------------------------//

    public void setBeatLength(float length) {
        mLastBeatLength = mBeatLength;
        mBeatLength = length;
    }

    public void restoreBPMLength() {
        mBeatLength = mLastBeatLength;
    }

    //--------------------------------------------------------------------------------------------//

    private void computeCurrentBpmLength() {
        if (mCurrentTimingPoint != null) {
            mBeatLength = (float) mCurrentTimingPoint.msPerBeat;
        }
    }

    private boolean computeFirstBpmLength() {
        if (mFirstTimingPoint != null) {
            mBeatLength = (float) mFirstTimingPoint.msPerBeat;
            return true;
        }
        return false;
    }

    private void computeOffset() {
        if (mLastTimingPoint != null) {
            mOffset = mLastTimingPoint.time % mBeatLength;
        }
    }

    private void computeOffsetAtPosition(int position) {
        if (mLastTimingPoint != null) {
            mOffset = (position - mLastTimingPoint.time) % mBeatLength;
        }
    }

    //--------------------------------------------------------------------------------------------//

    public boolean isKiai() {
        return mIsKiaiSection;
    }

    public boolean isNextBeat() {
        return mIsNextBeat;
    }

    public short getBeat() {
        return mBeat;
    }

    public float getBeatLength() {
        return mBeatLength;
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    public void onMusicChange(TrackInfo newTrack, boolean isSameAudio) {
        clear();

        if (newTrack != null) {
            loadPointsFrom(newTrack);
        }
    }

    @Override
    public void onMusicPlay() {
        restoreBPMLength();
        computeOffsetAtPosition(Game.songService.getPosition());
    }

    @Override
    public void onMusicPause() {
        setBeatLength(defaultBeatLength);
    }

    @Override
    public void onMusicStop() {
        setBeatLength(defaultBeatLength);
    }

    public void sync() {
        if (Game.musicManager.isPlaying()) {
            computeOffsetAtPosition(Game.songService.getPosition());
        }
    }

    //--------------------------------------------------------------------------------------------//

    public void onUpdate(float elapsed) {
        if (Game.musicManager.isPlaying()) {
            update(elapsed, Game.songService.getPosition());
            return;
        }
        update(elapsed, -1);
    }

    private void update(float elapsed, int position) {
        mElapsedTime += elapsed * 1000f;

        if (mElapsedTime - mLastElapsedTime >= mBeatLength - mOffset) {
            mLastElapsedTime = mElapsedTime;
            mOffset = 0;

            mBeat++;
            if (mBeat > mMaxBeat) {
                mBeat = 0;
            }

            mIsNextBeat = true;
        } else {
            mIsNextBeat = false;
        }

        if (mCurrentTimingPoint != null && position > mCurrentTimingPoint.time) {
            mMaxBeat = (short) (mCurrentTimingPoint.timeSignature - 1);
            mCurrentTimingPoint = !mTimingPoints.isEmpty() ? mTimingPoints.removeFirst() : null;

            if (mCurrentTimingPoint != null) {
                mLastTimingPoint = mCurrentTimingPoint;
                computeCurrentBpmLength();
                computeOffset();
            }
        }

        if (mCurrentEffectPoint != null && position > mCurrentEffectPoint.time) {
            mIsKiaiSection = mCurrentEffectPoint.isKiai;
            mCurrentEffectPoint = !mEffectPoints.isEmpty() ? mEffectPoints.removeFirst() : null;
        }
    }
}
