/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "services/tutorial",
  "text!./tutorial-panel.html",
  "css!./tutorial",
], function(
  TutorialState,
  tpl
){

  return {
    render: function(){
      return ko.bindhtml(tpl, TutorialState);
    }
  }

});
