package utils

import scala.annotation.tailrec

object ListUtils {

  @tailrec
  def myLength[A](list: List[A], acc: Int = 0): Int = list match {
    case Nil       => acc
    case _ :: tail => myLength(tail, acc + 1)
  }

  @tailrec
  def myReverse[A](list: List[A], acc: List[A] = Nil): List[A] = list match {
    case Nil       => acc
    case h :: tail => myReverse(tail, h :: acc)
  }

  /** Concatenate two lists without using ++ or ::: */
  def myConcat[A](left: List[A], right: List[A]): List[A] = {
    @tailrec
    def go(remaining: List[A], acc: List[A]): List[A] = remaining match {
      case Nil       => acc
      case h :: tail => go(tail, h :: acc)
    }
    go(myReverse(left), right)
  }

  def myFilter[A](list: List[A], pred: A => Boolean): List[A] = {
    @tailrec
    def go(remaining: List[A], acc: List[A]): List[A] = remaining match {
      case Nil       => myReverse(acc)
      case h :: tail => if (pred(h)) go(tail, h :: acc) else go(tail, acc)
    }
    go(list, Nil)
  }

  def myMap[A, B](list: List[A], f: A => B): List[B] = {
    @tailrec
    def go(remaining: List[A], acc: List[B]): List[B] = remaining match {
      case Nil       => myReverse(acc)
      case h :: tail => go(tail, f(h) :: acc)
    }
    go(list, Nil)
  }

  def myAppendOne[A](list: List[A], elem: A): List[A] =
    myReverse(elem :: myReverse(list))

  /**
   * Increments the count for `key` in the association list.
   * If `key` is not found, a new entry (key, 1) is added at the end.
   * Tail-recursive.
   */
  @tailrec
  def incrementCount(
    counts: List[(String, Int)],
    key:    String,
    acc:    List[(String, Int)] = Nil
  ): List[(String, Int)] = counts match {
    case Nil =>
      myReverse((key, 1) :: acc)
    case (k, v) :: tail =>
      if (k == key) myConcat(myReverse(acc), (k, v + 1) :: tail)
      else          incrementCount(tail, key, (k, v) :: acc)
  }

  @tailrec
  def lookupCount(counts: List[(String, Int)], key: String): Int = counts match {
    case Nil                       => 0
    case (k, v) :: _ if k == key  => v
    case _ :: tail                 => lookupCount(tail, key)
  }
}
