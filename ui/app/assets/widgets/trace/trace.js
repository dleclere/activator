define([
  'services/sbt/app',
  'commons/format'
],function(
  app,
  format
){

  // TRACE GENERATION
  function _n(name){
    return function(a,b,c) {
      var cls, attrs = {}, children;
      if (typeof a === "string" && c){
        cls = a;
        attrs = b;
        children = c;
      } else if (typeof a === "string" && b) {
        cls = a;
        children = b;
      } else if (a && b) {
        attrs = a;
        children = b;
      } else {
        children = a;
      }
      if (cls){
        attrs.css = attrs.css || {};
        attrs.css[cls] = true;
      }
      return ko.tpl(name, attrs, children);
    }
  }
  var _div = _n("div"), _span = _n("span");


  function renderTree(root) {
    function renderEventTrace(data) {
      var children = data.children;
      data = data.event;
      var highlight = root.traceEvent.type === data.type;
      var show = ko.computed(function() {
        return (highlight || !isSystemEvent(data) || app.deviationPrefs.showSystemMessages());
      })

      return _div('event',{css: {'error': isFailureEvent(data.type)}} ,[
        _div("event-desc", {visible: show, css: {'highlight': highlight}}, [
          _div("type", data.type),
          _div([
            _span("label","Time"),
            _span("value", format.formatTime(data.timestamp))
          ]),
          _div({ visible: app.deviationPrefs.showNanoSeconds }, [
            _span("label","Nano"),
            _span("value", data.nanoTime + "")
          ]),
          _div({ visible: app.deviationPrefs.showActorSystems }, [
            _span("label","Actor System"),
            _span("value", data.actorSystem)
          ]),
          _div({ visible: app.deviationPrefs.showTraceInformation }, [
            _div([
              _span("label","Id"),
              _span("value", data.id)
            ]),
            _div([
              _span("label","Trace"),
              _span("value", extractTrace(data.trace))
            ])
          ]),
          _div({ visible: isActorEvent(data) }, [
            _span("label","Actor"),
            _span("value", extractActorPath(data.annotation))
          ]),
          (data.annotation && data.annotation.message !== undefined) && _div([
            _span("label","Message"),
            _span("value", extractMessage(data.annotation.message,data.annotation.sysMsgType))
          ]),
          (data.annotation && data.annotation.reason !== undefined) && _div([
            _span("label","Reason"),
            _span("value", data.annotation.reason)
          ])
        ]),
        (children && children.length) && _div("children", children.map(renderEventTrace))
      ])
    }

    return renderEventTrace(root.traceTree);
  }


  function recreateMessage(message,prefix) { return prefix+"("+message+")"; }

  function extractMessage(message, sysMsgType) {
    var msgPrefix = sysMsgType || "[Unknown]";

    var result;
    if (message !== undefined) {
      if (typeof message === "string"){
        result = message;
      }
      else if (message.cause   !== undefined) {
        result = recreateMessage(message.cause,msgPrefix);
      }
      else if (message.child   !== undefined && message.cause !== undefined) {
        result = recreateMessage(extractActorInfo(message.child)+", "+message.cause,msgPrefix);
      }
      else if (message.child   !== undefined) {
        result = recreateMessage(extractActorInfo(message.child),msgPrefix);
      }
      else if (message.subject !== undefined) {
        result = recreateMessage(extractActorInfo(message.subject),msgPrefix);
      }
      else if (message.watchee !== undefined && message.watcher !== undefined) {
        result = recreateMessage(extractActorInfo(message.watchee)+", "+extractActorInfo(message.watcher),msgPrefix);
      }
      else if (message.watched !== undefined && message.existenceConfirmed !== undefined && message.addressTerminated !== undefined) {
        result = recreateMessage(extractActorInfo(message.watched)+", "+message.existenceConfirmed+", "+message.addressTerminated,msgPrefix);
      }
      else {
        result = "[unknown message]";
      }
    } else result = "";

    return result;
  }

  // -- SYSTEM
  var systemEvents = ["EventStreamError","EventStreamWarning","EventStreamDeadLetter","EventStreamUnhandledMessage",
            "TopLevelActorRequested","TopLevelActorCreated","SysMsgDispatched","SysMsgReceived","SysMsgCompleted"];

  function isSystemEvent(event) {
    var index = $.inArray(event.type,systemEvents);
    return index >= 0;
  }

  // -- ACTORS
  var actorEvents = ["SysMsgDispatched","SysMsgReceived","SysMsgCompleted","ActorRequested","ActorCreated","ActorTold",
             "ActorAutoReceived","ActorAutoCompleted","ActorReceived","ActorCompleted","ActorAsked","ActorFailed","TempActorCreated",
             "TempActorStopped","ActorSelectionTold","RemoteMessageSent","RemoteMessageReceived","RemoteMessageCompleted",
             "EventStreamDeadLetter","EventStreamUnhandledMessage"];

  function isActorEvent(event) {
    var index = $.inArray(event.type, actorEvents);
    return index >= 0;
  }

  function extractTrace(trace) {
    return (trace === undefined) ? "N/A" : trace.substring(trace.lastIndexOf("/") + 1);
  }

  // -- ERRORS
  var failureEvents = ["ActorFailed","EventStreamError","EventStreamWarning","EventStreamDeadLetter",
             "EventStreamUnhandledMessage", "DeadlockedThreads"];

  function isFailureEvent(type) {
    return ($.inArray(type,failureEvents) >= 0);
  }

  function extractActorInfo(info) {
    var result = "N/A";
    if (info !== undefined) result = info.actorPath;
    return result;
  }

  function extractActorPath(annotation) {
    var result = "N/A";
    if (annotation !== undefined) result = extractActorInfo(annotation.actorInfo);
    return result;
  }

  return renderTree;
});
