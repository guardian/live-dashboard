$ ->
  # see http://stackoverflow.com/questions/6459630/how-to-write-settimeout-with-params-by-coffeescript
  repeat = (ms, func) -> setInterval func, ms

  params = window.location.search

  if params.indexOf '?' != -1
    params = params.substring 1

  updateGraph = ->
    $.getJSON "/api/pageviews?#{params}&callback=?", (data) ->
      chart.series[0].setData data.lastWeek
      chart.series[1].setData data.today

  Highcharts.setOptions { global: { useUTC: false } }

  chart = new Highcharts.Chart(
    chart:
      type: 'spline'
      renderTo: 'page-views-graph'
      zoomType: 'x'

    colors: [ '#888888', '#FFFF77' ]

    credits:
      text: 'Powered by dashboard.ophan.co.uk'
      href: 'http://dashboard.ophan.co.uk'

    title:
      text: 'www.guardian.co.uk page views per minute'

    subtitle:
      text: if document.ontouchstart == undefined
        'Click and drag in the plot area to zoom in'
      else
        'Drag your finger over the plot to zoom in'

    xAxis:
      type: 'datetime'
      title:
        text: null

    tooltip:
      xDateFormat: '%H:%M'

    yAxis:
      min: 0
      title:
        text: 'Page Views per Minute'

    legend:
      enabled: true

    plotOptions:
      spline:
        marker: { enabled: false }

    series: [{
      type: 'spline',
      name: 'Last Week',
    },
      {
      type: 'spline',
      name: 'Today',
      }]
  )

  updateGraph()

  repeat 10000, -> updateGraph()


