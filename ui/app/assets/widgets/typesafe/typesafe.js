/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'text!./typesafe.html',
  'css!./typesafe'
],function(
  tpl
){

  return ko.bindhtml(tpl,{});

});
