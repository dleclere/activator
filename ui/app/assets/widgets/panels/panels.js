/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'widgets/layout/layoutManager',
  'commons/settings',
  "text!./panels.html",
  "css!./panels"
], function(
  layoutManager,
  settings,
  template
){

  // Find the plugins who have panels
  // var panels = plugins.plugins.reduce(function(list, group) {
  //   return list.concat(group.links.filter(function(it) {
  //     return it.panel;
  //   }));
  // }, []);
  var panels = ['plugins/tutorial/tutorialPanel'];

  var currentPanel = ko.observable();

  var switchPanel = function(panel) {
    require([panel], function(p) {
      $("#panelWrapper").replaceWith(p.render());
      currentPanel(panel);
    });
  }

  // Resizing
  function startResize(m,e){
    e.preventDefault();
    layoutManager.resizing(true);
    window.addEventListener("mousemove", resize);
    window.addEventListener("mouseup", stopResize);
  }
  function resize(e){
    e.preventDefault();
    e.stopPropagation();
    if (layoutManager.panelShape()[0] === 'r'){
      var newWidth = (window.innerWidth - e.pageX);
      if (newWidth > 300 && newWidth < 700){
        layoutManager.panelWidth(Math.round(newWidth/10)*10);
      }
    } else {
      var newHeight = (window.innerHeight - e.pageY);
      if (newHeight > 200 && newHeight < 600){
        layoutManager.panelHeight(Math.round(newHeight/10)*10);
      }
    }
  }
  function stopResize(e){
    e.preventDefault();
    layoutManager.resizing(false);
    window.removeEventListener("mousemove", resize);
    window.removeEventListener("mouseup", stopResize);
  }


  var PanelState = {
    panelWidth: layoutManager.panelWidth,
    panels: panels,
    currentPanel: currentPanel,
    switchPanel: switchPanel,
    startResize: startResize
  };

  // Default panel:
  setTimeout(function() {
    PanelState.switchPanel(panels[0]);
  },100);

  return {
    render: function() {
      return ko.bindhtml(template, PanelState)
    },
    state: PanelState
  }

});
