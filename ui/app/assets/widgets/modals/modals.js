/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'text!./modal.html',
  'css!./modals'
], function(tpl){
  var modal = $('<div id="modal"></div>').hide();

  function modalKeyboard(e){
    switch(e.keyCode){
      case 13:
        var main = $("button.main",modal);
        if (main.is(":visible")){
          main.trigger("click");
        }
        break;
      case 27:
        var light = $("button.light",modal);
        if (light.is(":visible")){
          light.trigger("click");
        } else {
          $("button.main",modal).trigger("click");
        }
        break;
    }
  }

  function show(){
    modal.fadeIn("fast");
    $(document.body).on("keydown", modalKeyboard);
  }

  function hide(after){
    $(document.body).off("keydown", modalKeyboard);
    modal.fadeOut("fast", function(){
      modal.html("");
      if (after) {
        after();
      }
    });
  }

  return {
    render: function(){
      return modal[0];
    },


    // MODAL API:
    // {
    //   title:   String,
    //   text:    String,
    //   body:    DOM Element,
    //   ok:      String (default = "OK"),
    //   cancel:  String (default = "Cancel"),
    //   okEnabled:      ko.observable (default = true),
    //   cancelEnabled:  ko.observable (default = true),
    //   callback: Function, <- returns true
    //   onCancel: Function, <- returns false
    // }

    // To show a button, provide either a callback or a label, eg:
    // bodies.ok = "Text" or bodies.callback = f()
    // bodies.cancel = "Text" or bodies.onCancel = f()
    show: function(bodies) {
      // Default values
      bodies.text = bodies.text || false;
      bodies.body = bodies.body || false;
      bodies.ok =  bodies.callback ? bodies.ok || "OK": bodies.ok || false;
      bodies.cancel = bodies.onCancel ? bodies.cancel || "Cancel" : bodies.cancel || false;
      bodies.okEnabled = bodies.okEnabled || true;
      bodies.cancelEnabled = bodies.cancelEnabled || true;
      bodies.shape = bodies.shape || "normal";

      bodies.clickOk = function() {
        hide(bodies.callback ? function() {bodies.callback(true);} : null);
      };
      bodies.clickCancel = function() {
        hide(bodies.onCancel ? function() {bodies.onCancel(false);} : null);
      };

      modal.html("").append(ko.bindhtml(tpl, bodies));
      show();
    },

    alert: function(bodies) {
      modal.html("").append(ko.bindhtml(tpl, bodies));
      show();
    },

    confirm: function(bodies) {
      modal.html("").append(ko.bindhtml(tpl, bodies));
      show();
    },

    hideModal: function() {
      hide(null);
    }

  };
});
