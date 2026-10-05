package com.zz.douyin.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlaybackStateTest {
    @Test
    public void confirmedResumeRequiresSamePlayingEngine() {
        Object engine = new Object();

        assertTrue(PlaybackState.isConfirmedUserResume(
                engine,
                engine,
                engine,
                1
        ));
        assertFalse(PlaybackState.isConfirmedUserResume(
                engine,
                engine,
                engine,
                2
        ));
        assertFalse(PlaybackState.isConfirmedUserResume(
                engine,
                engine,
                new Object(),
                1
        ));
        assertFalse(PlaybackState.isConfirmedUserResume(
                new Object(),
                engine,
                engine,
                1
        ));
    }

    @Test
    public void loopBoundaryRequiresPlaybackToReachTheRealEnd() {
        assertTrue(PlaybackState.isCompletedLoopBoundary(
                9_800L,
                120L,
                10_000L
        ));
        assertFalse(PlaybackState.isCompletedLoopBoundary(
                8_900L,
                120L,
                10_000L
        ));
        assertFalse(PlaybackState.isCompletedLoopBoundary(
                9_800L,
                900L,
                10_000L
        ));
        assertFalse(PlaybackState.isCompletedLoopBoundary(
                9_800L,
                120L,
                900L
        ));
    }

    @Test
    public void contentChangeRequiresTwoDifferentKnownAids() {
        assertTrue(PlaybackState.isDifferentKnownContent(
                "paused-aid",
                "next-aid"
        ));
        assertFalse(PlaybackState.isDifferentKnownContent(
                "paused-aid",
                "paused-aid"
        ));
        assertFalse(PlaybackState.isDifferentKnownContent(
                null,
                "next-aid"
        ));
        assertFalse(PlaybackState.isDifferentKnownContent(
                "paused-aid",
                null
        ));
        assertFalse(PlaybackState.isDifferentKnownContent(
                "unknown",
                "next-aid"
        ));
    }

    @Test
    public void pendingSwitchCandidateMustBeRecentPlayingAndDifferent() {
        Object pausedPlayer = new Object();
        Object nextPlayer = new Object();

        assertTrue(PlaybackState.isRecentSwitchCandidate(
                nextPlayer,
                pausedPlayer,
                1,
                1_000L,
                1_900L
        ));
        assertFalse(PlaybackState.isRecentSwitchCandidate(
                pausedPlayer,
                pausedPlayer,
                1,
                1_000L,
                1_900L
        ));
        assertFalse(PlaybackState.isRecentSwitchCandidate(
                nextPlayer,
                pausedPlayer,
                2,
                1_000L,
                1_900L
        ));
        assertFalse(PlaybackState.isRecentSwitchCandidate(
                nextPlayer,
                pausedPlayer,
                1,
                1_000L,
                2_001L
        ));
    }

    @Test
    public void pauseCallbackCorroboratesWithoutAdoptedIdentity() {
        assertTrue(PlaybackState.isPauseCorroborated(true, false, 0, -1));
        assertTrue(PlaybackState.isPauseCorroborated(true, true, 1, 2));
    }

    @Test
    public void stateReadCorroboratesOnlyWithIdentityAndTransition() {
        assertTrue(PlaybackState.isPauseCorroborated(false, true, 1, 2));
        assertFalse(PlaybackState.isPauseCorroborated(false, false, 1, 2));
        assertFalse(PlaybackState.isPauseCorroborated(false, true, 1, 1));
        assertFalse(PlaybackState.isPauseCorroborated(false, true, 0, 2));
        assertFalse(PlaybackState.isPauseCorroborated(false, false, 0, -1));
    }

    @Test
    public void pauseSignalCannotConfirmPlayingStoppedOrFailedEngine() {
        assertFalse(PlaybackState.isPauseCorroborated(true, false, 1, 1));
        assertFalse(PlaybackState.isPauseCorroborated(true, false, 1, 0));
        assertFalse(PlaybackState.isPauseCorroborated(true, false, 1, 3));
        assertFalse(PlaybackState.isPauseCorroborated(true, false, 1, 4));
    }

    @Test
    public void missingEngineDoesNotCreateUserPause() {
        assertFalse(PlaybackState.confirmUserPaused(null, 100L, 1, "video-aid"));
    }

    @Test
    public void unrelatedPlayersCannotOverwriteTappedEngineSignal() {
        Object tapped = new Object();
        Object stale = new Object();
        PlaybackState.beginUserPauseIntent(tapped);
        PlaybackState.recordPauseSignal(tapped, true, 200L);
        PlaybackState.recordPauseSignal(stale, true, 210L);
        PlaybackState.recordPauseSignal(stale, false, 220L);
        assertTrue(PlaybackState.hasUserPauseSignal(tapped, 100L));
        assertFalse(PlaybackState.hasUserPauseSignal(stale, 100L));
    }

    @Test
    public void stopReleaseAndResumeInvalidateTappedEngineSignal() {
        Object tapped = new Object();
        PlaybackState.beginUserPauseIntent(tapped);
        PlaybackState.recordPauseSignal(tapped, false, 200L);
        assertFalse(PlaybackState.hasUserPauseSignal(tapped, 100L));
        PlaybackState.recordPauseSignal(tapped, true, 210L);
        PlaybackState.recordPauseSignal(tapped, false, 220L);
        assertFalse(PlaybackState.hasUserPauseSignal(tapped, 100L));
    }

    @Test
    public void newTapCannotReusePreviousPauseSignal() {
        Object tapped = new Object();
        PlaybackState.beginUserPauseIntent(tapped);
        PlaybackState.recordPauseSignal(tapped, true, 200L);
        assertFalse(PlaybackState.hasUserPauseSignal(tapped, 201L));
        PlaybackState.beginUserPauseIntent(tapped);
        assertFalse(PlaybackState.hasUserPauseSignal(tapped, 100L));
    }

    @Test
    public void photoHoldRejectsUnknownOrChangedContent() {
        assertFalse(PlaybackState.confirmPhotoPaused(null, "photo-aid"));
        assertFalse(PlaybackState.confirmPhotoPaused("unknown", "unknown"));
        assertFalse(PlaybackState.confirmPhotoPaused("", ""));
        assertFalse(PlaybackState.confirmPhotoPaused("photo-aid", "next-aid"));
    }
}
