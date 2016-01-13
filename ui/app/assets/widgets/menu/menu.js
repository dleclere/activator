/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'text!./menu.html',
  'widgets/lists/list',
  'css!widgets/buttons/button',
  'css!./menu'
], function(tpl) {

  return function(elements,state) {
    state.__elements = elements;
    return ko.bindhtml(tpl, state);
  }

});
