/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'commons/stream',
  'commons/types',
  'widgets/modals/modals'
], function(
  Stream,
  Types,
  modals
) {

  var WS = ('MozWebSocket' in window) ? window.MozWebSocket : window.WebSocket;
  var websocket = null;
  var isOpened = ko.observable(false);

  var withWebSocket = function (wsFunc) {
    if (isOpened() && websocket) {
      wsFunc(websocket);
    }
  };

  var SocketStream = Stream().map(function(evt) {
    return JSON.parse(evt.data);
  });

  // Pattern checking (optional), eg:
  // subscribe({ type: 'Log', subtype: String })
  // See commons/type.js -> is()
  function subscribe(pattern, value) {
    if (value && pattern)
      return SocketStream.fork().matchOnAttribute(pattern, value);
    else if (pattern)
      return SocketStream.fork().match(pattern);
    else
      return SocketStream.fork();
  }

  function send(msg) {
    withWebSocket(function (ws) {
      var smsg = JSON.stringify(msg);
      debug && console.debug("Sending:", smsg);
      ws.send(smsg);
    });
  }

  function onOpen(event) {
    debug && console.info("WS opened: ", event);
    isOpened(true);
    Ping();
  }

  function onError(event) {
    debug && console.error("WS error: ", event);
    isOpened(false);
    modals.show({
      title: "Lost connection to Activator",
      text: "Click OK to try to reconnect. You may have to refresh this page in your browser.",
      ok: "OK",
      callback: reconnect,
      cancel: "Hide"
    });
  }

  // ---------------------------
  // Keeping the websocket alive
  // ---------------------------
  var Ping = (function(){
    var pendingPing;
    function randomShort() {
      return Math.floor(Math.random() * 65536);
    }
    // We used to check if the cookie we receive is the one we expect
    // But since we didn't do anything about it, I just removed it
    function ping() {
      if (!isOpened()) return;
      pendingPing = { request: 'Ping', cookie: randomShort().toString() };
      send(pendingPing);
      setTimeout(ping, 1000*25); // IE11 needs < 30s ping time, see: https://projects.tigase.org/boards/15/topics/1982?r=1985
    }

    return ping;
  }());

  function connect() {
    isOpened(false);
    debug && console.info("WS opening: " + window.wsUrl);
    websocket = new WS(window.wsUrl);
    websocket.addEventListener('open', onOpen);
    websocket.addEventListener('close', onError);
    websocket.addEventListener('error', onError);
    websocket.addEventListener("message", SocketStream.push.bind(SocketStream));
  }
  function reconnect() {
    setTimeout(connect, 200);
  }

  // Avoid "lost connection" popup flashing, when leaving the page
  window.onbeforeunload = function() {
    websocket.removeEventListener("close", onError);
  };


  return {
    isOpened: isOpened,
    connect: connect,
    send: send,
    subscribe: subscribe
  }

});
