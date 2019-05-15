package com.indeed.imhotep.scheduling;

import com.indeed.imhotep.AbstractImhotepMultiSession;
import com.indeed.imhotep.AbstractImhotepSession;
import com.indeed.imhotep.RequestContext;
import com.indeed.imhotep.api.ImhotepSession;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Class to hold all the fields of ImhotepTask to be reported in the Task Servlet
 */

public class TaskSnapshot {

    public final long taskID;
    public final String sessionID;
    @Nullable public final String shardPath;
    @Nullable public final RequestContext requestContext;
    @Nullable public final Long numDocs;
    @Nullable public final Map<String, Integer> numGroups;
    @Nullable public final Integer numStats;
    public final Duration timeSinceCreation;
    @Nullable public final String userName;
    @Nullable public final String clientName;
    @Nullable public final String dataset;
    @Nullable public final String shardName;
    public final Duration timeSinceLastExecutionStart;
    public final Duration timeSinceLastWaitStart;
    public final long totalExecutionTimeMillis;
    @Nullable
    public final StackTraceElement[] stackTrace;

    public TaskSnapshot(
            final long taskID,
            @Nullable final AbstractImhotepMultiSession session,
            @Nullable final AbstractImhotepSession innerSession,
            @Nullable final RequestContext requestContext,
            final long creationTime,
            @Nullable final String userName,
            @Nullable final String clientName,
            @Nullable final String dataset,
            @Nullable final String shardName,
            @Nullable final Integer numDocs,
            final long lastExecutionStartTime,
            final long lastWaitStartTime,
            final long totalExecutionTime,
            @Nullable final StackTraceElement[] stackTrace
    ) {
        this.taskID = taskID;
        this.sessionID = (session == null) ? "null" : session.getSessionId();
        this.requestContext = requestContext;
        this.timeSinceCreation = Duration.ZERO.plusNanos(System.nanoTime() - creationTime);
        this.userName = userName;
        this.clientName = clientName;
        this.dataset = dataset;
        this.shardName = shardName;
        this.timeSinceLastExecutionStart = Duration.ZERO.plusNanos(System.nanoTime() - lastExecutionStartTime);
        this.timeSinceLastWaitStart = Duration.ZERO.plusNanos(System.nanoTime() - lastWaitStartTime);
        this.totalExecutionTimeMillis = TimeUnit.MILLISECONDS.convert((totalExecutionTime + System.nanoTime() - lastExecutionStartTime), TimeUnit.NANOSECONDS);
        this.stackTrace = stackTrace;

        // innerSession access is dangerous
        // It must be ensured that any methods that are called from here are
        // non-synchronized methods
        this.shardPath = Optional.ofNullable(innerSession)
                .map(AbstractImhotepSession::getShardPath)
                .map(Object::toString)
                .orElse(null);
        this.numDocs = (innerSession == null) ? ((numDocs != null) ? (long)(int)numDocs : null) : (Long)innerSession.getNumDocs();
        this.numStats = (innerSession == null) ? null : innerSession.weakGetNumStats();
        // TODO: is this good? maybe Map<String, Integer>?
        this.numGroups = (innerSession == null) ? null : innerSession.weakGetNumGroups(ImhotepSession.DEFAULT_GROUPS);
        // end innerSession access
    }

    public String getTimeSinceCreation() {
        return this.timeSinceCreation.toString();
    }

    public String getTimeSinceLastExecutionStart() {
        return this.timeSinceLastExecutionStart.toString();
    }

    public String getTimeSinceLastWaitStart() {
        return this.timeSinceLastWaitStart.toString();
    }

    @Nullable
    public List<String> getStackTrace() {
        if (stackTrace == null) {
            return  null;
        }
        return Arrays.stream(stackTrace)
                        .map(StackTraceElement::toString)
                        .collect(Collectors.toList());
    }

}
