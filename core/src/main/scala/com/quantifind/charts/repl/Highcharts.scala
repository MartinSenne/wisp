package com.qf.charts.repl

import com.qf.charts.highcharts.SeriesType


/**
* User: austin
* Date: 12/2/14
*
* Highcharts implementation of plotting functionality. Includes several highcharts specific plots
*
* I rely on the fact that an implicit method defined in an object takes precedence over one
* defined in a trait to have Iterable[T] with PartialFunction[Int, T] resolve to this method
*/

object Highcharts extends IterablePairLowerPriorityImplicits with MatlabStyleHighcharts {

  implicit def mkIterablePair[A: Numeric, B: Numeric](ab: (Iterable[A], Iterable[B])) = new IterableIterable(ab._1, ab._2)

  def area[A, B, C: Numeric, D: Numeric](xy: IterablePair[A, B, C, D]/*, format: String = "r"*/) = {
    val (xr, yr) = xy.toIterables
    xyToSeries(xr, yr, SeriesType.area, "r")
  }

  def areaspline[A, B, C: Numeric, D: Numeric](xy: IterablePair[A, B, C, D]/*, format: String = "r"*/) = {
    val (xr, yr) = xy.toIterables
    xyToSeries(xr, yr, SeriesType.areaspline, "r")
  }

  def bar[A, B, C: Numeric, D: Numeric](xy: IterablePair[A, B, C, D]/*, format: String = "r"*/) = {
    val (xr, yr) = xy.toIterables
    xyToSeries(xr, yr, SeriesType.bar, "r")
  }

  def column[A, B, C: Numeric, D: Numeric](xy: IterablePair[A, B, C, D]/*, format: String = "r"*/) = {
    val (xr, yr) = xy.toIterables
    xyToSeries(xr, yr, SeriesType.column, "r")
  }

  def line[A, B, C: Numeric, D: Numeric](xy: IterablePair[A, B, C, D]/*, format: String = "r"*/) = {
    val (xr, yr) = xy.toIterables
    xyToSeries(xr, yr, SeriesType.line, "r")
  }

  def pie[A, B, C: Numeric, D: Numeric](xy: IterablePair[A, B, C, D]/*, format: String = "r"*/) = {
    val (xr, yr) = xy.toIterables
    xyToSeries(xr, yr, SeriesType.pie, "r")
  }

  def scatter[A, B, C: Numeric, D: Numeric](xy: IterablePair[A, B, C, D]/*, format: String = "r"*/) = {
    val (xr, yr) = xy.toIterables
    xyToSeries(xr, yr, SeriesType.scatter, "r")
  }

  def spline[A, B, C: Numeric, D: Numeric](xy: IterablePair[A, B, C, D]/*, format: String = "r"*/) = {
    val (xr, yr) = xy.toIterables
    xyToSeries(xr, yr, SeriesType.spline, "r")
  }
}
