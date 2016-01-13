/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "main/router",
  "text!./breadcrumb.html",
  "css!./breadcrumb"
], function(
  router,
  tpl
){

  return ko.bindhtml(tpl, router);

});
