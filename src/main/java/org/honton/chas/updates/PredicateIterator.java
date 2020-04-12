package org.honton.chas.updates;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Filter an iterable with a Predicate
 *
 * @param <T> The type of element being iterated
 */
public class PredicateIterator<T> implements Iterator<T> {
  final Iterator<T> inner;
  final Predicate<? super T> predicate;

  // three states: null, next has not been set; TRUE, next has been set from inner iterable; FALSE,
  // inner iterable is exhausted
  private Boolean hasNext;
  // the cached value from the inner iterable
  private T next;

  /**
   * Create an iterator the filters the content of an Iterable as iteration happens.
   *
   * @param inner The Iterable being filtered
   * @param predicate The predicate that filters the elements
   */
  public PredicateIterator(Iterable<T> inner, Predicate<? super T> predicate) {
    this.inner = inner.iterator();
    this.predicate = predicate;
  }

  public final boolean hasNext() {
    if (hasNext == null) {
      hasNext = Boolean.FALSE;
      while (inner.hasNext()) {
        next = inner.next();
        if (predicate.test(next)) {
          hasNext = Boolean.TRUE;
          break;
        }
      }
    }
    return hasNext.booleanValue();
  }

  public final T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    hasNext = null;
    return next;
  }
}
