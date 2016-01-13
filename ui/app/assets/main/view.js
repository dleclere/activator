/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'widgets/layout/layout',
  'css!./main'
], function(
  layout
) {

  return {
    render: function() {
      layout.render();
    }
  }

});
