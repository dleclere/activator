/*
 Copyright (C) 2015 Typesafe, Inc <http://typesafe.com>
 */
define([
  'widgets/modals/modals',
  'css!./help'
], function(modals) {

  var shortcuts = {
    "?": "Show shortcuts",
    "T": "Search anything, or run a command",
    "Arrows": "Navigate in tutorial and code",
    "W": "Close current file in code",
    "Ctrl/Command+S": "Save current file in code",
    "Esc.": "Blur"
  }

  var shortcutsView = ko.tpl("ul", { css: { 'shortcuts': 1 }}, Object.keys(shortcuts).map(function(key){
    return ko.tpl("li", {}, [
      ko.tpl("span", { css: { 'key': 1 }}, key),
      ko.tpl("span", { css: { 'action': 1 }}, shortcuts[key])
    ])
  }));

  return function(){
    modals.show({
      title: "Keyboard shortcuts",
      body: shortcutsView,
      cancel: "close"
    });
  };

});
