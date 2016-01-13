/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "main/plugins",
  "text!./system.html",
  "services/sbt",
  "css!./system",
  "css!widgets/modules/modules",
  "css!widgets/lists/logs"
], function(
  plugins,
  tpl,
  sbt
) {

  function scrollToBottom() {
    $(".logs")[0].scrollTop = 99999;
    State.memoLogsScroll('stick');
  }

  // Cache the dom Logs, for better performances
  // Not very elegant, but much, much, much more efficient.
  var logsView = ko.tpl("ul", {logEach: sbt.logs.stdout }, [
    ko.tpl("li", { attr: { 'data-bind': "text: event.entry.message, attr: { 'data-level': event.entry.level, 'data-type': event.entry['$type'] }"} }, [])
  ]);

  var State = {
    sbt: sbt,
    logsView: logsView,
    clear: function() {
      sbt.logs.stdout.removeAll();
    },
    memoLogsScroll: ko.observable(),
    scrollToBottom: scrollToBottom
  }

  return {
    render: function(){
      return ko.bindhtml(tpl, State)
    }
  }

});
