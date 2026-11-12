package com.abhishek.ame.music.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How the page area changes from one page to another. */
enum class PageTransition {
    /** Swap with nothing to animate. */
    None,

    /** The new page slides in from the right over the old one, which recedes. */
    Push,

    /** The page leaves to the right (or left) and the one under it is revealed. */
    Pop,

    /** Two pages of one level: the new one fades in over the old. */
    Fade,
}

private enum class GesturePhase { None, Dragging, Cancelling, Committing }

/**
 * Every change of page in the app, and the interactive back gesture, as one
 * piece of state.
 *
 * Two pages are on screen at most: [settled], which was there, and [incoming],
 * which is being moved to. [progress] says how far, from 0 (only [settled])
 * to 1 (only [incoming]) — and it is the *only* thing that moves. A push, a
 * pop from the bar's arrow, a fade between tabs and a finger dragging a page
 * away off the screen's edge are all just something changing [progress], so
 * there is no moment at which one animation hands over to another: letting go
 * continues from exactly where the finger was, and a committed pop ends with
 * the revealed page already composed, in its own slot, where it stays.
 *
 * Both pages are real, keyed compositions inside one container — see
 * [PageStackHost] — so what is revealed under a dragged page is the page
 * itself, with its scroll position and everything, not a copy of it.
 *
 * Gesture calls carry the token [beginGesture] returned, so a late call from
 * a gesture that has been replaced is ignored. The gesture's springs and the
 * pop that follows a commit run on [scope], not on the system callback's
 * coroutine, which is cancelled by exactly the events (a second swipe, a
 * cancel) that must not stop them.
 */
@Stable
class PageStackState(initial: AppDestination, private val scope: CoroutineScope) {

    /** The page that was on screen, and is while nothing is changing. */
    var settled by mutableStateOf(initial)
        private set

    /** The page being moved to, or revealed by a gesture; null at rest. */
    var incoming by mutableStateOf<AppDestination?>(null)
        private set

    var kind by mutableStateOf(PageTransition.None)
        private set

    /** 0 with only [settled] showing, 1 with only [incoming]. For [PageTransition.Pop], how far the page has gone. */
    var progress by mutableFloatStateOf(0f)
        private set

    private var phase by mutableStateOf(GesturePhase.None)
    // Plain, not state: it is read in composition right after [sync] bumps it.
    private var runId = 0
    private var generation = 0
    private var awaitingPop = false
    private var gestureJob: Job? = null

    private var lastValue = 0f
    private var lastNanos = 0L
    private var velocity = 0f

    /** A commit is in flight: further back presses are swallowed rather than stacked on it. */
    val isBusy: Boolean get() = phase == GesturePhase.Committing


    // ---- Following the app's navigation ----

    /**
     * Brings the stack in line with the page the app is now on. Called from
     * composition, ahead of anything that reads the state, so a page that
     * arrives is drawn for the first time already at its starting offset
     * rather than for a frame where it is not.
     */
    internal fun sync(
        target: AppDestination,
        classify: (from: AppDestination, to: AppDestination) -> PageTransition,
    ) {
        Snapshot.withoutReadObservation { syncUntracked(target, classify) }
    }

    private fun syncUntracked(
        target: AppDestination,
        classify: (from: AppDestination, to: AppDestination) -> PageTransition,
    ) {
        val to = incoming
        if (to == null) {
            if (target.key == settled.key) {
                if (target != settled) settled = target
            } else {
                begin(target, classify)
            }
            return
        }
        when (target.key) {
            to.key -> {
                if (target != to) incoming = target
                // The pop a committed gesture was waiting for has happened: the
                // revealed page is already in place, so there is nothing to animate.
                if (awaitingPop || phase != GesturePhase.None) complete()
            }
            settled.key -> {
                if (target != settled) settled = target
                // A page changing its mind mid-flight: back where it started.
                if (phase == GesturePhase.None) begin(target, classify)
            }
            else -> begin(target, classify)
        }
    }

    private fun begin(
        target: AppDestination,
        classify: (from: AppDestination, to: AppDestination) -> PageTransition,
    ) {
        // A change arriving mid-flight starts from where the last one was going,
        // except a drag that never committed, which never left [settled].
        val base = if (phase != GesturePhase.None && !awaitingPop) settled else incoming ?: settled
        gestureJob?.cancel()
        gestureJob = null
        phase = GesturePhase.None
        awaitingPop = false
        val k = classify(base, target)
        if (k == PageTransition.None) {
            settled = target
            incoming = null
            kind = PageTransition.None
            progress = 0f
            runId++
            return
        }
        settled = base
        incoming = target
        kind = k
        progress = 0f
        runId++
    }

    /** Runs the animation [sync] queued. Keyed on [runId] by the host, so a newer change cancels it. */
    internal suspend fun runQueued(id: Int) {
        if (incoming == null || phase != GesturePhase.None) return
        when (kind) {
            PageTransition.None -> Unit
            PageTransition.Fade -> animate(progress, 1f, animationSpec = FadeSpec) { v, _ -> progress = v }
            else -> animate(progress, 1f, animationSpec = SlideSpec) { v, _ -> progress = v }
        }
        if (id == runId && phase == GesturePhase.None) complete()
    }

    internal val queuedRun: Int get() = runId

    /** Back to rest on [settled]: the incoming page is dropped, as after a cancelled drag. */
    private fun abort() {
        gestureJob = null
        incoming = null
        kind = PageTransition.None
        progress = 0f
        phase = GesturePhase.None
        awaitingPop = false
        velocity = 0f
        lastNanos = 0L
        runId++
    }

    private fun complete() {
        gestureJob?.cancel()
        gestureJob = null
        settled = incoming ?: settled
        incoming = null
        kind = PageTransition.None
        progress = 0f
        phase = GesturePhase.None
        awaitingPop = false
        velocity = 0f
        lastNanos = 0L
        runId++
    }

    // ---- The back gesture ----

    /**
     * START. [revealed] is the page the pop will land on; it is composed
     * straight away, under the one the finger moves. Returns the token the
     * rest of the gesture carries, or [REJECTED] while a commit is in flight.
     *
     * The page always leaves to the right, whichever edge the swipe began on:
     * a pop is the push played backwards, and a push always arrives from the right.
     */
    fun beginGesture(revealed: AppDestination): Int {
        if (phase == GesturePhase.Committing) return REJECTED
        if (phase == GesturePhase.None && incoming != null) {
            // Mid-push or mid-fade: take it to its end rather than peel half a page.
            complete()
        }
        gestureJob?.cancel()
        gestureJob = null
        generation++
        incoming = revealed
        kind = PageTransition.Pop
        if (phase == GesturePhase.None) progress = 0f
        phase = GesturePhase.Dragging
        lastNanos = 0L
        velocity = 0f
        runId++
        return generation
    }

    /** PROGRESS: [value] is the system's 0..1, however far the finger has come. */
    fun dragGesture(token: Int, value: Float) {
        if (token != generation || phase != GesturePhase.Dragging) return
        val visual = DragTravel * value.coerceIn(0f, 1f)
        val now = System.nanoTime()
        if (lastNanos != 0L) {
            val dt = (now - lastNanos) / 1_000_000_000f
            if (dt > 0.001f) velocity = 0.5f * velocity + 0.5f * ((visual - lastValue) / dt)
        }
        lastValue = visual
        lastNanos = now
        progress = visual
    }

    /** CANCEL: springs the page home from wherever it is, then clears the stack of the revealed page. */
    fun cancelGesture(token: Int) {
        if (token != generation || phase != GesturePhase.Dragging) return
        phase = GesturePhase.Cancelling
        val startVelocity = velocity.coerceIn(-4f, 0f)
        gestureJob = scope.launch {
            animate(progress, 0f, initialVelocity = startVelocity, animationSpec = CancelSpec) { v, _ ->
                progress = v
            }
            if (token == generation && phase == GesturePhase.Cancelling) abort()
        }
    }

    /**
     * COMMIT: carries the page the rest of the way off from where it is —
     * velocity and all — then [pop]s. The revealed page is already the one
     * the pop lands on, so the state change finds it in place and only drops
     * the page that has gone.
     */
    fun commitGesture(token: Int, pop: () -> Unit) {
        if (token != generation || phase != GesturePhase.Dragging) return
        phase = GesturePhase.Committing
        val startVelocity = velocity.coerceIn(0f, 6f)
        gestureJob = scope.launch {
            try {
                animate(progress, 1f, initialVelocity = startVelocity, animationSpec = CommitSpec) { v, _ ->
                    progress = v
                }
            } finally {
                // The pop is what was asked for, whatever became of the animation.
                withContext(NonCancellable) {
                    if (token != generation || phase != GesturePhase.Committing) return@withContext
                    progress = 1f
                    awaitingPop = true
                    pop()
                    // Normally the next composition sees the new page and completes.
                    // If the pop changed nothing, do not leave the page gone.
                    repeat(3) { withFrameNanos { } }
                    if (awaitingPop) complete()
                }
            }
        }
    }

    // ---- Drawing ----

    /** The transform of one of the two pages, read in the draw phase so a frame never recomposes. */
    fun applyToLayer(layer: GraphicsLayerScope, isIncoming: Boolean) = with(layer) {
        val p = progress.coerceIn(0f, 1f)
        val width = size.width
        when (kind) {
            PageTransition.None -> Unit
            PageTransition.Push ->
                if (isIncoming) {
                    translationX = width * (1f - p)
                } else {
                    translationX = -width * ParallaxFraction * p
                    alpha = 1f - 0.30f * p
                }
            PageTransition.Pop -> {
                // The page is revealed over the first stretch of its way out and
                // stays put after, so the rest of the slide is just it leaving.
                val reveal = (p / DragTravel).coerceAtMost(1f)
                if (isIncoming) {
                    translationX = -width * ParallaxFraction * (1f - reveal)
                    val scale = 0.94f + 0.06f * reveal
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.70f + 0.30f * reveal
                } else {
                    translationX = width * p
                    val scale = 1f - ShrinkWhenRevealed * reveal
                    scaleX = scale
                    scaleY = scale
                    clip = true
                    shape = RoundedCornerShape(MaxCorner.toPx() * reveal)
                }
            }
            PageTransition.Fade -> if (isIncoming) alpha = p
        }
    }

    companion object {
        const val REJECTED = -1

        /** How far a full drag moves the page, as a fraction of the width. */
        private const val DragTravel = 0.40f
        private const val ShrinkWhenRevealed = 0.08f
        private const val ParallaxFraction = 0.28f
        private val MaxCorner = 24.dp

        private val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
        private val FadeSpec = tween<Float>(durationMillis = 220, easing = Ease)

        // The pushes and pops the app has always had.
        private val SlideSpec = spring<Float>(dampingRatio = 0.88f, stiffness = 380f, visibilityThreshold = 0.002f)

        // Progress units, not pixels: the default threshold is far too coarse for 0..1.
        private val CancelSpec = spring<Float>(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.001f,
        )

        // Critically damped: the page leaves, it does not bounce off the edge.
        private val CommitSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 450f,
            visibilityThreshold = 0.002f,
        )
    }
}

/**
 * The page area: [state]'s pages, each in a layer of its own, inside one
 * container whose [modifier] carries whatever samples the pages (the glass
 * backdrop, haze, scroll connections).
 *
 * That container is also what makes a revealed page show: whatever paints the
 * backdrop paints it opaque across the whole container, and anything laid
 * *under* the container is behind it. Pages must be inside.
 *
 * @param target the page the app is on now
 * @param classify how to get from one page to another
 */
@Composable
fun PageStackHost(
    state: PageStackState,
    target: AppDestination,
    classify: (from: AppDestination, to: AppDestination) -> PageTransition,
    modifier: Modifier = Modifier,
    content: @Composable (AppDestination) -> Unit,
) {
    state.sync(target, classify)
    val run = state.queuedRun
    LaunchedEffect(run) { state.runQueued(run) }

    Box(
        modifier = modifier,
    ) {
        val settled = state.settled
        val incoming = state.incoming
        // Bottom first. One loop over keyed layers, so a page keeps its
        // composition as it goes from being "incoming" to being the only one.
        val layers = when {
            incoming == null -> listOf(settled to false)
            state.kind == PageTransition.Pop -> listOf(incoming to true, settled to false)
            else -> listOf(settled to false, incoming to true)
        }
        for ((page, isIncoming) in layers) {
            key(page.key) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { state.applyToLayer(this, isIncoming) },
                ) {
                    content(page)
                }
            }
        }
    }
}
