/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'text!./help.html',
  'css!./help'
], function(
  tpl
){

  return ko.bindhtml(tpl, {});

})
