/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "main/plugins",
  "text!./dependencies.html",
  "css!widgets/modules/modules"
], function(
  plugins,
  tpl
) {

  var State = {}

  return {
    render: function(){
      return ko.bindhtml(tpl, State)
    }
  }

});
