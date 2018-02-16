package com.indeed.flamdex.dynamic;

import com.indeed.flamdex.api.IntTermIterator;
import com.indeed.util.core.io.Closeables2;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongHeapSemiIndirectPriorityQueue;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * {@link IntTermIterator} that merges several {@link IntTermIterator}.
 *
 * @author michihiko
 */
class MergedIntTermIterator implements MergedTermIterator, IntTermIterator {
    private static final Logger LOG = Logger.getLogger(MergedIntTermIterator.class);

    private final List<IntTermIterator> intTermIterators;
    // We have to store iterators which have 'currentTerm' as that status to be able to do MergedDocIdStream#reset(MergedTermIterator).
    // this is the indices which satisfies currentTerms[i] == currentTerm, which is removed from priority queue until the next call of next().
    private final IntArrayList currentMinimums;
    private long currentTerm;
    private int currentTermFreq;
    private final long[] currentTerms;
    private final LongHeapSemiIndirectPriorityQueue priorityQueue;

    MergedIntTermIterator(@Nonnull final List<IntTermIterator> intTermIterators) {
        this.intTermIterators = intTermIterators;
        this.currentMinimums = new IntArrayList(this.intTermIterators.size());
        this.currentTerms = new long[intTermIterators.size()];
        this.priorityQueue = new LongHeapSemiIndirectPriorityQueue(this.currentTerms);
        innerReset();
    }

    /**
     * For given {@code intTermIterators} which are invalid until next call of {@link IntTermIterator#next()},
     * reset current status to match those iterators.
     * This iterator is invalid until next call of {@link MergedIntTermIterator#next()}
     */
    private void innerReset() {
        priorityQueue.clear();
        // All iterators must be in currentMinimums since they're waiting for call of next().
        currentMinimums.clear();
        for (int i = 0; i < intTermIterators.size(); ++i) {
            currentMinimums.add(i);
        }
        // We reset other states (current{Term, TermFreq, Terms}) in the next call of next().
    }

    @Nonnull
    @Override
    public IntTermIterator getInnerTermIterator(final int idx) {
        return intTermIterators.get(idx);
    }

    @Nonnull
    @Override
    public IntList getCurrentMinimums() {
        return currentMinimums;
    }

    @Override
    public void reset(final long term) {
        for (final IntTermIterator iterator : intTermIterators) {
            iterator.reset(term);
        }
        innerReset();
    }

    @Override
    public long term() {
        return currentTerm;
    }

    @Override
    public boolean next() {
        for (final int i : currentMinimums) {
            final IntTermIterator iterator = intTermIterators.get(i);
            if (iterator.next()) {
                currentTerms[i] = iterator.term();
                priorityQueue.enqueue(i);
            }
        }
        currentMinimums.clear();
        currentTermFreq = 0;
        if (!priorityQueue.isEmpty()) {
            currentTerm = currentTerms[priorityQueue.first()];
            while (!priorityQueue.isEmpty() && (currentTerm == currentTerms[priorityQueue.first()])) {
                final int i = priorityQueue.dequeue();
                currentTermFreq += intTermIterators.get(i).docFreq();
                currentMinimums.add(i);
            }
        }
        IntArrays.quickSort(currentMinimums.elements(), 0, currentMinimums.size());
        return !currentMinimums.isEmpty();
    }

    @Override
    public int docFreq() {
        return currentTermFreq;
    }

    @Override
    public void close() {
        Closeables2.closeAll(intTermIterators, LOG);
    }
}