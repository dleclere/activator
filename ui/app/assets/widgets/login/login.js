/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'widgets/modals/modals',
  'text!./login.html',
  'css!./login'
], function(
  modal,
  tpl
){
  return function(callback, cancel, error){
    var State = {
      username: ko.observable(""),
      password: ko.observable("")
    };

    var loginForm = ko.bindhtml(tpl, State);

    modal.show({
      text: error,
      title: "Log into your Typesafe account",
      body: loginForm,
      ok: "Login",
      cancel: "cancel",
      callback: function(){
        callback(State.username(),State.password());
      },
      onCancel: cancel
    });
  }

});
