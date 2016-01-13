/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'services/sbt',
  'commons/websocket',
  'services/ajax',
  'text!./notifications.html',
  'css!widgets/buttons/dropdown',
  'css!./notifications',
  'css!widgets/stickers/stickers'
], function(
  sbt,
  websocket,
  fs,
  tpl
){

  var State = {
    appStatus: sbt.events.appStatus,
    unreadBuildErrors: sbt.events.unreadBuildErrors,
    notifications: sbt.events.notifications,
    notificationsReadCount: ko.computed(function() {
      return sbt.events.notifications().filter(function(n) {
        return !n.read();
      }).length;
    }),
    markAsRead: function() {
      sbt.events.notifications().forEach(function(n) {
        n.read(true);
      });
    }
  };

  sbt.tasks.taskCompleteEvent.subscribe(function(e) {
    var el = $("#notifications ."+(e.succeded?"success":"error")).removeClass('animate');
    setTimeout(function(){
      el.addClass('animate');
    },50);
  });

  return ko.bindhtml(tpl, State);

});
