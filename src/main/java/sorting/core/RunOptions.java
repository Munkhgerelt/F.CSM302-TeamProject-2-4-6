package sorting.core;

import sorting.trace.TraceRecorder;

import java.util.function.Consumer;

/**
 * Optional extras for one run of {@link MultithreadedSorter}:
 * an event recorder, a lockstep gate and a callback that is told about every
 * worker thread right after it is created (used to read Thread.getState()).
 * All fields may be null.
 */
public final class RunOptions {
    public final TraceRecorder trace;
    public final StepGate gate;
    public final Consumer<Thread> onThreadCreated;

    public RunOptions(TraceRecorder trace, StepGate gate, Consumer<Thread> onThreadCreated) {
        this.trace = trace;
        this.gate = gate;
        this.onThreadCreated = onThreadCreated;
    }

    public static RunOptions none() {
        return new RunOptions(null, null, null);
    }

    public static RunOptions traced(TraceRecorder trace) {
        return new RunOptions(trace, null, null);
    }
}
