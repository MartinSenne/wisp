package com.qf.charts.highcharts

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
//import com.qf.data.mongo.LinkedHashMapToKeyValues
//import com.qf.data.mongo.QProduct
//import com.qf.data.mongo.QProductSerializer

import scala.collection._
import com.fasterxml.jackson.databind._

/**
 * User: austin
 * Date: 9/9/13
 * Some ideas from : https://github.com/tedeling/HighCharts-with-Scala
 *
 * Tries to closely follow : api.highcharts.com/highcharts
 *
 */
object Highchart {
  implicit def traversableToTraversableData[X: Numeric, Y: Numeric](seq: Traversable[(X, Y)]) = seq.map{case(x, y) => Data(x, y)}
  implicit def traversableToTraversableSeries[X: Numeric, Y: Numeric](seq: Traversable[(X, Y)]) = seriesToTraversableSeries(traversableToSeries(seq))
  implicit def traversableToSeries[X: Numeric, Y: Numeric](seq: Traversable[(X, Y)]) = Series(traversableToTraversableData(seq))
  implicit def seriesToTraversableSeries(series: Series) = Seq(series)
  implicit def traversableToSomeArray(t: Traversable[Any]) = Some(t.toArray) // for axes
  implicit def axisTitleOptionToArrayAxes(axisTitle: Option[AxisTitle]) = Some(Array(Axis(axisTitle)))
  implicit def axisToArrayAxes(axis: Axis) = Some(Array(axis))
  implicit def axesSeqToSomeAxesArray(axes: Seq[Axis]) = Some(axes.toArray)
  implicit def stringToAxisTitle(s: String) = Some(AxisTitle(s))
  implicit def stringToAxis(s: String): Option[Array[Axis]] = axisTitleOptionToArrayAxes(stringToAxisTitle(s))
  implicit def colorToSomeColorArray(c: Color.Type) = Some(Array(c))
  implicit def stringToExporting(s: String) = Some(Exporting(s))
  implicit def stringToTitle(s: String) = Some(Title(text = s))

  implicit def optionWrap[T](value: T): Option[T] = Option(value)

  // TODO MOVE TODO WHAT DO WE NEED
  // jackson for json processing - TODO include quantifind defaults ?
  @transient var objectMapper: ObjectMapper = new ObjectMapper()
  // Don't write null map values
  objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)

  objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

  // Don't fail on serialization when there are null fields in the class
  objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

  // When there are unknown properties in the JSON (some unused fields), don't fail
  objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  //when there is a JSON_STRING instead of JSON_ARRAY and we want an array, just put it in an array
  objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)

  // Scala specific. Register the scala module with the asl mapper
  objectMapper.registerModule(DefaultScalaModule)

  val qfmodule = new SimpleModule("QfModule", new Version(1, 0, 0, null, "com.qf", "json"));
//  qfmodule.addSerializer(classOf[mutable.LinkedHashMap[String, Seq[String]]], new LinkedHashMapToKeyValues())
//  qfmodule.addSerializer(classOf[QProduct], new QProductSerializer())
//  mapper.registerModule(qfmodule)

  objectMapper.registerModule(new JodaModule())
}

abstract class HighchartKey(var _name: String) {
  def toServiceFormat: Map[String, Any]
}

//Internal functions for mapping to service format (which gets parsed to json)
object HighchartKey {
  def highchartKeyToServiceFormat(hck: HighchartKey): Map[String, Any] = Map(hck._name -> hck.toServiceFormat)

  def optionToServiceFormat(o: Option[HighchartKey]): Map[String, Any] = o match {
    case None => Map()
    case Some(s) => highchartKeyToServiceFormat(s)
  }

  def optionArrayAxisToServiceFormat(o: Option[Array[Axis]]): Map[String, Any] = o match {
    case None => Map()
    case Some(s) => Map(s.head.__name -> s.map(_.toServiceFormat))
  }

  def optionArrayColorToServiceFormat(o: Option[Array[Color.Type]]): Map[String, Any] = o match {
    case None => Map()
    case Some(a) => Map("colors" -> a)
  }

  def axisToTitleId(a: Axis) = a.id

  def someAxisToTitleId(oa: Option[Axis]) = oa match {
    case None => "0"
    case Some(a) => a.id
  }

  def hckTraversableToServiceFormat(t: Traversable[HighchartKey]): Map[String, Any] = {
    if(t.isEmpty) Map()
    else Map(t.head._name -> t.map(_.toServiceFormat))
  }

  def flatten(o: (String, Option[Any])) = o._2 match {
    case None => None
    case Some(v) => Some(o._1, v)
  }

  def someStyleToServiceFormat(style: Option[CSSObject]) =
  {if (style != None) Map("style" -> style.get.toString()) else Map()}
}

// Not going to implement: loading
// Not done yet:
//  css object wrappers limited support
//  navigation :: (styling for exporting)
//  pane: for guages (where are guages?)
//
case class Highchart(
                      series: Traversable[Series],
                      title: Option[Title] = Some(Title()),
                      chart: Option[Chart] = None,
                      colors: Option[Array[Color.Type]] = None,
                      credits: Option[Credits] = Some(Credits()),
                      exporting: Option[Exporting] = Some(Exporting()),
                      legend: Option[Legend] = None,
                      // plotOptions
                      subtitle: Option[Title] = None,
                      setTurboThreshold: Boolean = true,
                      tooltip: Option[ToolTip] = None,
                      xAxis: Option[Array[Axis]] = None,
                      yAxis: Option[Array[Axis]] = Some(Array(Axis()))
                      ) {

  import HighchartKey._

  def toJson = Highchart.objectMapper.writeValueAsString(jsonMap)

  def jsonMap: Map[String, Any] = {
    if(series.size == 0) System.err.println("Tried to create a chart with no series")

    val turboOutput =
      if(setTurboThreshold) {
        val allPlotsTurbo = {Seq("line") ++ series.flatMap(_.chart)}.map { s =>
          s -> Map("turboThreshold" -> "Infinity--")
        }.toMap

        Map("plotOptions" -> allPlotsTurbo)
      } else Map.empty[String, Any]

    val colorWrapper = (colors, yAxis) match {
      case (Some(c), _) if c.size > 0 => colors
      case (_, Some(y)) => {
        val styleColors = y.flatMap(_.title).flatMap(_.style).flatMap(_.color)
        if(styleColors.size == series.size) Some(styleColors)
        else None
      }
      case _ => None
    }

    (yAxis, series) match {
      case (Some(y), s) => if(y.size > 1 && y.size == s.size) y.zip(s.toSeq).zipWithIndex.foreach{case((axis, ser), index) =>
        if(axis.id.isEmpty) axis.id = Some(index.toString())
        if(ser.yAxis.isEmpty) ser.yAxis = axis.id
      }
      case _ =>
    }

    (xAxis, series) match {
      case (Some(x), s) => if(x.size > 1 && x.size == s.size) x.zip(s.toSeq).zipWithIndex.foreach{case((axis, ser), index) =>
        if(axis.id.isEmpty) axis.id = Some(index.toString())
        if(ser.xAxis.isEmpty) ser.xAxis = axis.id
      }
      case _ =>
    }

    // Axis defaults to yAxis, rename xAxes
    xAxis.map(_.foreach(_.__name = "xAxis"))

      turboOutput ++
        hckTraversableToServiceFormat(series) ++
        Seq(xAxis, yAxis).flatMap(optionArrayAxisToServiceFormat) ++
        optionArrayColorToServiceFormat(colorWrapper) ++
        Seq(chart, title, exporting, credits, legend, tooltip, subtitle).flatMap(optionToServiceFormat)
  }

  def toServiceFormat: (String, Map[String, Any]) = {
     "highcharts" -> jsonMap
  }
}

// can we do better than specifying every field manually? (probably...)
// but I was not happy with Enumeration returning type Value instead of type String
// I need to look into jerkson or something similar for case class -> json conversion
case class Title(
                  text: String = "", // Override default "Chart title"
                  align: Option[Alignment.Type] = None,
                  floating: Option[Boolean] = None,
                  style: Option[CSSObject] = None,
                  useHTML: Option[Boolean] = None,
                  verticalAlign: Option[VerticalAlignment.Type] = None,
                  x: Option[Int] = None,
                  y: Option[Int] = None,
                  var __name: String = "title"
                  ) extends HighchartKey(__name) {
  def toServiceFormat =
    Map("text" -> text) ++
      Map(
        "align" -> align,
        "floating" -> floating,
        "useHTML" -> useHTML,
        "verticalAlign" -> verticalAlign,
        "x" -> x,
        "y" -> y
      ).flatMap(HighchartKey.flatten)  ++
      HighchartKey.someStyleToServiceFormat(style)
}

case class Chart(
                  // todo, many other chart options
                  zoomType: Option[Zoom.Type] = None
                  ) extends HighchartKey("chart") {
  def toServiceFormat = Map(
    "zoomType" -> zoomType
  ).flatMap(HighchartKey.flatten)
}

case class Credits(
                    enabled: Option[Boolean] = None,
                    href: String = "", // Override default Highcharts
                    position: Option[Position] = None,
                    style: Option[CSSObject] = None,
                    text: String = "" // Override default Highcharts
                    ) extends HighchartKey("credits") {
  def toServiceFormat = Map(
    "href" -> href,
    "text" -> text
  ) ++
    Map("style" -> style, "enabled" -> enabled).flatMap(HighchartKey.flatten) ++
    HighchartKey.optionToServiceFormat(position) ++
    HighchartKey.someStyleToServiceFormat(style)}

case class Exporting(
                      //buttons
                      //chartOptions
                      filename: String = "chart",
                      scale: Option[Int] = None,
                      sourceHeight: Option[Int] = None,
                      sourceWidth: Option[Int] = None,
                      _type: Option[String] = None,
                      url: Option[String] = None,
                      width: Option[Int] = None
                      ) extends HighchartKey("exporting") {

  def toServiceFormat =
    Map("filename" -> filename) ++
      Map(
        "scale" -> scale,
        "type" -> _type,
        "url" -> url,
        "sourceHeight" -> sourceHeight,
        "sourceWidth" -> sourceWidth,
        "width" -> width
      ).flatMap(HighchartKey.flatten)
}

case class Position(
                     align: Option[Alignment.Type] = None,
                     x: Option[Int] = None,
                     verticalAlign: Option[VerticalAlignment.Type] = None,
                     y: Option[Int] = None
                     ) extends HighchartKey("position") {
  def toServiceFormat = Map(
    "align" -> align,
    "x" -> x,
    "verticalAlign" -> verticalAlign,
    "y" -> y
  ).flatMap(HighchartKey.flatten)
}

case class ToolTip(
                    animation: Option[Boolean] = None,
                    backgroundColor: Option[Color.Type] = None,
                    borderColor: Option[Color.Type] = None,
                    borderRadius: Option[Int] = None,
                    borderWidth: Option[Int] = None,
                    // crosshairs
                    dateTimeLabelFormats: Option[DateTimeFormats] = None, // this has different defaults than the Axis
                    enabled: Option[Boolean] = None,
                    followPointer: Option[Boolean] = None,
                    followTouchMove: Option[Boolean] = None,
                    footerFormat: Option[String] = None,
                    //formatter
                    //headerFormat
                    hideDelay: Option[Int] = None,
                    //pointFormat
                    //positioner
                    shadow: Option[Boolean] = None,
                    shared: Option[Boolean] = None,
                    snap: Option[Int] = None,
                    //style
                    useHTML: Option[Boolean] = None,
                    valueDecimals: Option[Int] = None,
                    valuePrefix: Option[String] = None,
                    valueSuffix: Option[String] = None,
                    xDateFormat: Option[String] = None
                    ) extends HighchartKey("ToolTip") {

  def toServiceFormat =
    Map(
      "animation" -> animation,
      "backgroundColor" -> backgroundColor,
      "borderColor" -> borderColor,
      "borderRadius" -> borderRadius,
      "borderWidth" -> borderWidth,
      "enabled" -> enabled,
      "followPointer" -> followPointer,
      "followTouchMove" -> followTouchMove,
      "footerFormat" -> footerFormat,
      "hideDelay" -> hideDelay,
      "shadow" -> shadow,
      "shared" -> shared,
      "snap" -> snap,
      "useHTML" -> useHTML,
      "valueDecimals" -> valueDecimals,
      "valuePrefix" -> valuePrefix,
      "valueSuffix" -> valueSuffix,
      "xDateFormat" -> xDateFormat
    ).flatMap(HighchartKey.flatten) ++
      HighchartKey.optionToServiceFormat(dateTimeLabelFormats)

}

case class Series(
                    data: Traversable[Data[_, _]],
                    index: Option[Int] = None,
                    legendIndex: Option[Int] = None,
                    name: Option[String] = None,
                    chart: Option[SeriesType.Type] = None,
                    visible: Option[Boolean] = None,
                    color: Option[Color.Type] = None,
                    var xAxis: Option[String] = None,
                    var yAxis: Option[String] = None,
                    __name: String = "series"
) extends HighchartKey(__name) {

  def toServiceFormat: Map[String, Any] = {
    if (data.size == 0) System.err.println("Tried to create a series with no data")
    Map("data" -> data.map(_.toServiceFormat).toSeq) ++
    Map("xAxis" -> xAxis, "yAxis" -> yAxis, "type" -> chart, "color" -> color, "visible" -> visible, " index" -> index, "legendIndex" -> legendIndex, "name" -> name).flatMap{HighchartKey.flatten}
  }
}

case class Data[X: Numeric, Y: Numeric](
                                         x: X,
                                         y: Y,
                                         color: Option[Color.Type] = None,
                                         //dataLabels
                                         //events
                                         // id
                                         name: Option[String] = None
                                         ) {

  def toServiceFormat = {
    Map("x" -> x, "y" -> y) ++
      Map("color" -> color, "name" -> name).flatMap{HighchartKey.flatten}
  }
}

// TODO PieData for legendIndex, slice

// No more than 22 members in a case class TODO
case class Legend(
                   align: Option[Alignment.Type] = None,
                   backgroundColor: Option[Color.Type] = None,
                   borderColor: Option[Color.Type] = None,
                   //  borderRadius: Int = 5,
                   //  borderWidth: Int = 2,
                   enabled: Option[Boolean] = Some(false), // override default
                   floating: Option[Boolean] = None,
                   itemDistance: Option[Int] = None,
                   //itemHiddenStyle
                   //itemHoverStyle
                   itemMarginBottom: Option[Int] = None,
                   itemMarginTop: Option[Int] = None,
                   //itemStyle
                   itemWidth: Option[Int] = None,
                   labelFormat: Option[String] = None, // TODO - format string helpers
                   //labelFormatter
                   layout: Option[Layout.Type] = None,
                   margin: Option[Int] = None,
                   maxHeight: Option[Int] = None,
                   //navigation
                   padding: Option[Int] = None,
                   reversed: Option[Boolean] = None,
                   rtl: Option[Boolean] = None, // right-to-left
                   //shadow
                   //style
                   //  symbolPadding: Int = 5,
                   //  symbolWidth: Int = 30,
                   title: Option[String] = None, // todo - css title
                   //  useHTML: Boolean = false,
                   verticalAlign: Option[VerticalAlignment.Type] = None,
                   width: Option[Int] = None,
                   x: Option[Int] = None,
                   y: Option[Int] = None
                   ) extends HighchartKey("legend") {

  def toServiceFormat =
    Map(
      "align" -> align,
      "backgroundColor" -> backgroundColor,
      "borderColor" -> borderColor,
      //      "borderRadius" -> borderRadius,
      //      "borderWidth" -> borderWidth,
      "enabled" -> enabled,
      "floating" -> floating,
      "itemDistance" -> itemDistance,
      "itemMarginBottom" -> itemMarginBottom,
      "itemMarginTop" -> itemMarginTop,
      "labelFormat" -> labelFormat,
      "layout" -> layout,
      "margin" -> margin,
      "padding" -> padding,
      "reversed" -> reversed,
      "rtl" -> rtl,
      //      "symbolPadding" -> symbolPadding,
      //      "symbolWidth" -> symbolWidth,
      //      "useHTML" -> useHTML,
      "verticalAlign" -> verticalAlign,
      "x" -> x,
      "y" -> y,
      "itemWidth" -> itemWidth,
      "maxHeight" -> maxHeight,
      "title" -> title,
      "width" -> width
    ).flatMap{case(s, a) => HighchartKey.flatten(s, a)}
}

case class Axis(
                 title: Option[AxisTitle] = Some(AxisTitle()),
                 allowDecimals: Option[Boolean] = None,
                 alternateGridColor: Option[Color.Type] = None,
                 categories: Option[Array[String]] = None,
                 dateTimeLabelFormats: Option[DateTimeFormats] = None,
                 endOnTick: Option[Boolean] = None,
                 //events
                 //  gridLineColor: Color.Type = "#C0C0C0",
                 //  gridLineDashStyle: String = "Solid",       // TODO Bundle
                 //  gridLineWidth: Int = 2,
                 var id: Option[String] = None,
                 labels: Option[AxisLabel] = None,
                 lineColor: Option[Color.Type] = None,
                 lineWidth: Option[Int] = None,
                 //linkedTo
                 max: Option[Int] = None,
                 //  maxPadding: Double = 0.01,
                 min: Option[Int] = None,
                 //  minPadding: Double = 0.01,
                 minRange: Option[Int] = None,
                 minTickInterval: Option[Int] = None,
                 //minor
                 offset: Option[Int] = None,
                 opposite: Option[Boolean] = None, // opposite side of chart, ie left / right for y-axis
                 //plotBands
                 //plotLines // TODO Kevin wants these
                 reversed: Option[Boolean] = None,
                 //  showEmpty: Boolean = false,
                 showFirstLabel: Option[Boolean] = None,
                 showLastLabel: Option[Boolean] = None,
                 //startOfWeek
                 startOnTick: Option[Boolean] = None,
                 //  tickColor: Color.Type = "#C0D0E0",
                 // TICK STUFF TODO
                 axisType: Option[AxisType.Type] = None,
                 var __name: String = "yAxis"
                 ) extends HighchartKey(__name) {

  def toServiceFormat: Map[String, Any] =
    Map(
      "allowDecimals" -> allowDecimals,
      "categories" -> categories,
      "endOnTick" -> endOnTick,
      "lineColor" -> lineColor,
      "lineWidth" -> lineWidth,
      //    "maxPadding" -> maxPadding,
      //    "minPadding" -> minPadding,
      "offset" -> offset,
      "opposite" -> opposite,
      "reversed" -> reversed,
      "showFirstLabel" -> showFirstLabel,
      "showLastLabel" -> showLastLabel,
      "startOnTick" -> startOnTick,
      "type" -> axisType,
      "title" -> title,
      "id" -> id
    ).flatMap(HighchartKey.flatten) ++
      HighchartKey.optionToServiceFormat(dateTimeLabelFormats) ++
      HighchartKey.optionToServiceFormat(labels)
}

case class AxisLabel(
                      align: Option[String] = None,
                      enabled: Option[Boolean] = None,
                      format: Option[String] = None,
                      //                            formatter
                      maxStaggerLines: Option[Int] = None,
                      overflow: Option[Overflow.Type] = None,
                      rotation: Option[Int] = None,
                      step: Option[Int] = None,
                      style: Option[CSSObject] = None,
                      useHTML: Option[Boolean] = None,
                      x: Option[Int] = None,
                      y: Option[Int] = None,
                      zIndex: Option[Int] = None
                      ) extends HighchartKey("labels") {
  def toServiceFormat =
    Map(
      "align" -> align,
      "enabled" -> enabled,
      "format" -> format,
      "maxStaggerLines" -> maxStaggerLines,
      "overflow" -> overflow,
      "rotation" -> rotation,
      "step" -> step,
      "useHTML" -> useHTML,
      "x" -> x,
      "y" -> y,
      "zIndex" -> zIndex
    ).flatMap(HighchartKey.flatten) ++
      HighchartKey.someStyleToServiceFormat(style)
}

case class DateTimeFormats(
                            millisecond: String = "%H:%M:%S.%L",
                            second: String = "%H:%M:%S",
                            minute: String = "%H:%M",
                            hour: String = "%H:%M",
                            day: String = "%e. %b",
                            week: String = "%e. b",
                            month: String = "%b \\ %y",
                            year: String = "%Y"
                            ) extends HighchartKey("dateTimeLabelFormats") {

  def toServiceFormat = Map("dateTimeLabelFormats" ->
    Map(
      "millisecond" -> millisecond,
      "second" -> second,
      "minute" -> minute,
      "hour" -> hour,
      "day" -> day,
      "week" -> week,
      "month" -> month,
      "year" -> year
    )
  )
}

// Must supply text, others default to align=middle, maring=(x=0, y=10), offset=(relative), rotation=0
case class AxisTitle(
                      text: String = "", // Override default y-axis "value"
                      align: Option[AxisAlignment.Type] = None,
                      margin: Option[Int] = None,
                      offset: Option[Int] = None,
                      rotation: Option[Int] = None,
                      style: Option[CSSObject] = None
                      ) {
  def toServiceFormat =
    Map("text" -> text) ++
      Map("align" -> align, "margin" -> margin, "offset" -> offset, "rotation" -> rotation).flatMap(HighchartKey.flatten) ++
      HighchartKey.someStyleToServiceFormat(style)
}

object AxisTitle {
  def apply(text: String, color: Color.Type) =
    new AxisTitle(text, style = Some(CSSObject(Some(color))))

  def apply(text: String, color: Color.Type, rotation: Option[Int]) =
    new AxisTitle(text, rotation = rotation, style = Some(CSSObject(Some(color))))
}
