(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  var chart = echarts.init(document.getElementById('chart-radar'), null, { renderer: 'svg' });
  chart.setOption({
    animation: false,
    tooltip: {
      appendToBody: true,
      trigger: 'item'
    },
    legend: {
      data: ['ai-goofish-monitor', 'xianyu-auto-reply-fix', 'goofish-cli', 'xianyu-pilot'],
      bottom: 0,
      textStyle: { color: muted, fontSize: 12 },
      itemWidth: 14,
      itemHeight: 14
    },
    radar: {
      indicator: [
        { name: '商品监控', max: 3 },
        { name: 'AI分析', max: 3 },
        { name: 'AI客服', max: 3 },
        { name: '自动发货', max: 3 },
        { name: '商品管理', max: 3 },
        { name: 'CLI/MCP', max: 3 },
        { name: '风控', max: 3 },
        { name: 'Web界面', max: 3 },
        { name: '部署便捷', max: 3 }
      ],
      center: ['50%', '48%'],
      radius: '62%',
      axisName: {
        color: ink,
        fontSize: 12
      },
      splitLine: {
        lineStyle: { color: rule }
      },
      splitArea: {
        areaStyle: { color: [bg2, 'transparent'] }
      },
      axisLine: {
        lineStyle: { color: rule }
      }
    },
    series: [{
      type: 'radar',
      data: [
        {
          value: [3, 3, 0, 0, 0, 0, 1, 3, 3],
          name: 'ai-goofish-monitor',
          areaStyle: { color: accent + '20' },
          lineStyle: { color: accent, width: 2 },
          itemStyle: { color: accent }
        },
        {
          value: [0, 1, 3, 3, 0, 0, 0, 3, 2],
          name: 'xianyu-auto-reply-fix',
          areaStyle: { color: accent2 + '20' },
          lineStyle: { color: accent2, width: 2 },
          itemStyle: { color: accent2 }
        },
        {
          value: [1, 1, 2, 0, 3, 3, 3, 0, 2],
          name: 'goofish-cli',
          areaStyle: { color: '#8b5cf620' },
          lineStyle: { color: '#8b5cf6', width: 2 },
          itemStyle: { color: '#8b5cf6' }
        },
        {
          value: [0, 0, 3, 3, 2, 0, 0, 3, 3],
          name: 'xianyu-pilot',
          areaStyle: { color: '#f59e0b20' },
          lineStyle: { color: '#f59e0b', width: 2 },
          itemStyle: { color: '#f59e0b' }
        }
      ]
    }]
  });
  window.addEventListener('resize', function() { chart.resize(); });
})();
