package com.github.ilittleangel.notifier.utils

import scala.collection._

final class FixedList[A] private (val capacity: Int, val length: Int, val offset: Int, elems: Array[Any])
  extends immutable.Iterable[A] with IterableOps[A, FixedList, FixedList[A]] { self =>

  def this(capacity: Int) = this(capacity, length = 0, offset = 0, elems = Array.ofDim(capacity))

  def appended[B >: A](elem: B): FixedList[B] = {
    val newElems = Array.ofDim[Any](capacity)
    Array.copy(elems, 0, newElems, 0, capacity)
    val (newOffset, newLength) =
      if (length == capacity) {
        newElems(offset) = elem
        ((offset + 1) % capacity, length)
      } else {
        newElems(length) = elem
        (offset, length + 1)
      }

    new FixedList[B](capacity, newLength, newOffset, newElems)
  }

  @`inline` def :+ [B >: A](elem: B): FixedList[B] = appended(elem)

  def apply(i: Int): A = elems((i + offset) % capacity).asInstanceOf[A]

  override def iterator: Iterator[A] = new AbstractIterator[A] {
    private var current = 0
    override def hasNext: Boolean = current < self.length
    override def next(): A = {
      val elem = self(current)
      current += 1
      elem
    }
  }

  override def className: String = "FixedList"
  override val iterableFactory: IterableFactory[FixedList] = new FixedListFactory(capacity)
  override protected def fromSpecific(coll: IterableOnce[A]): FixedList[A] = iterableFactory.from(coll)
  override protected def newSpecificBuilder: mutable.Builder[A, FixedList[A]] = iterableFactory.newBuilder
  override def empty: FixedList[A] = iterableFactory.empty

}

class FixedListFactory(capacity: Int) extends IterableFactory[FixedList] {
  override def from[A](source: IterableOnce[A]): FixedList[A] = (newBuilder[A] ++= source).result()
  override def empty[A]: FixedList[A] = new FixedList[A](capacity)
  override def newBuilder[A]: mutable.Builder[A, FixedList[A]] =
    new mutable.ImmutableBuilder[A, FixedList[A]](empty) {
      override def addOne(elem: A): this.type = { elems = elems :+ elem; this}
    }
}
