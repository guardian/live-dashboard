$ ->
  # see http://stackoverflow.com/questions/6459630/how-to-write-settimeout-with-params-by-coffeescript
  repeat = (ms, func) -> setInterval func, ms

  Highcharts.setOptions { global: { useUTC: false } }

  chart = new Highcharts.Chart(
    chart:
      type: 'spline'
      renderTo: 'container'
      zoomType: 'x'

    title:
      text: 'Page views'

    subtitle:
      text: if document.ontouchstart == undefined
        'Click and drag in the plot area to zoom in'
      else
        'Drag your finger over the plot to zoom in'

    xAxis:
      type: 'datetime'
      title:
        text: null

    yAxis:
      min: 0
      title:
        text: 'Page Views per Minute'
    #startOnTick: false
    #showFirstLabel: false

#    tooltip:
#      shared: true
#
    legend:
      enabled: false

    plotOptions:
      spline:
        #lineWidth: 1
        marker: { enabled: false }
#      area:
##        fillColor:
##          linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1}
##          stops: [
##            [0, Highcharts.getOptions().colors[0]]
##            [1, 'rgba(2,0,0,0)']
##          ]
##
#        lineWidth: 1
#        marker:
#          enabled: false
#          states:
#            hover:
#              enabled: true
#              radius: 5
#        shadow: false,
#        states:
#          hover:
#            lineWidth: 1

    series: [{
      type: 'spline',
      name: 'Page Views per Minute',
    }]
  )

  repeat 5000, ->
    $.getJSON '/api/pageviews?callback=?', (data) ->
      chart.series[0].setData data

