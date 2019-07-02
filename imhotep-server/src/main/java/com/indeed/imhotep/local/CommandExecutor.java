package com.indeed.imhotep.local;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.indeed.imhotep.AbstractImhotepMultiSession;
import com.indeed.imhotep.api.ImhotepCommand;
import com.indeed.imhotep.api.ImhotepOutOfMemoryException;
import com.indeed.imhotep.scheduling.ImhotepTask;
import com.indeed.imhotep.scheduling.SilentCloseable;
import com.indeed.imhotep.scheduling.TaskScheduler;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Executes the Commands. Execution Order is decided based on def-use graph of named input groups and output groups.
 */
public class CommandExecutor<T> {

    static final Logger log = Logger.getLogger(CommandExecutor.class);

    private final AbstractImhotepMultiSession imhotepMultiSession;
    private final ListeningExecutorService executorService;
    private final ImhotepLocalSession imhotepLocalSession;
    private final List<ImhotepCommand> firstCommands;
    private final ImhotepCommand<T> lastCommand;

    public CommandExecutor(final AbstractImhotepMultiSession imhotepMultiSession, final ListeningExecutorService executorService, final ImhotepLocalSession imhotepLocalSession, final List<ImhotepCommand> firstCommands, final ImhotepCommand<T> lastCommand) {
        this.imhotepMultiSession = imhotepMultiSession;
        this.executorService = executorService;
        this.imhotepLocalSession = imhotepLocalSession;
        this.firstCommands = firstCommands;
        this.lastCommand = lastCommand;
    }

    private T applyCommand(final ImhotepCommand<T> imhotepCommand) {
        try (final SilentCloseable ignored = TaskScheduler.CPUScheduler.lockSlot()) {
            ImhotepTask.setup(imhotepMultiSession, imhotepCommand.getResultClass());
            try {
                return imhotepCommand.apply(imhotepLocalSession);
            } catch (final ImhotepOutOfMemoryException e) {
                Throwables.propagate(e);
            }
        }
        return null;
    }

    private ListenableFuture<Object> processCommand(final ImhotepCommand imhotepCommand, final DefUseManager defUseManager) {
        final List<ListenableFuture<Object>> dependentFutures = defUseManager.getDependentFutures(imhotepCommand.getInputGroups(), imhotepCommand.getOutputGroups());
        final ListenableFuture<Object> commandFuture = Futures.transform(Futures.allAsList(dependentFutures), (final List<Object> ignored) -> applyCommand(imhotepCommand), executorService);

        defUseManager.addUses(imhotepCommand.getInputGroups(), commandFuture);
        defUseManager.addDefinition(imhotepCommand.getOutputGroups(), commandFuture);

        return commandFuture;
    }

    T processCommands(final DefUseManager defUseManager) throws ExecutionException, InterruptedException {
        for (final ImhotepCommand imhotepCommand : firstCommands) {
            processCommand(imhotepCommand, defUseManager);
        }
        final ListenableFuture<Object> lastCommandFuture = processCommand(lastCommand, defUseManager);

        final List<ListenableFuture<Object>> allFutures = defUseManager.getAllFutures(lastCommandFuture);
        return (T) Futures.transform(Futures.allAsList(allFutures), (final List<Object> inputs) -> inputs.get(0), executorService).get();
    }
}
