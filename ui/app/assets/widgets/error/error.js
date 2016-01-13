/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'widgets/modals/modals',
  'text!./error.html',
  'css!./error'
], function(
  modal,
  tpl
){

  return function(title, message, retry, cancel){
    var errorForm = ko.bindhtml(tpl, {});

    var State = {
      text: message,
      title: title,
      body: errorForm
    };

    if (retry) {
      State = $.extend(State, {
        ok: "Retry",
        cancel: "Cancel",
        callback: retry,
        onCancel: cancel
      });
    } else {
      State = $.extend(State, {
        ok: "Ok"
      });
    }
    modal.show(State);
  }

});
