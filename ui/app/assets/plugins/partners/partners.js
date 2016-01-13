/*
 Copyright (C) 2015 Typesafe, Inc <http://typesafe.com>
 */
define([
  'main/plugins',
  'text!./partners.html',
  "widgets/layout/layout",
  'css!./partners',
  "css!widgets/menu/menu"
], function(
  plugins,
  tpl,
  layout
){

  var State = {
    partnerHtml: ko.observable("<div class='hint'>Loading partner information...</div>")
  };

  var onError = function() {
    State.partnerHtml("<div class='hint'>Failed to load partner information from <a href='https://typesafe.com/partners/activator'>https://typesafe.com/partners/activator</a></div>");
  };

  var onGotHtml = function(data) {
    var domNodes = $.parseHTML(data, null /* current document */, true /* keepScripts */);
    var node = null;
    try {
      for (var i = 0; i < domNodes.length; ++i) {
        if (domNodes[i].id === 'partner-wrapper')
          node = $(domNodes[i]);
      }
    } catch (e) {
      console.log("failed to find partner-wrapper node ", e);
    }
    if (node === null)
      onError();
    else
      State.partnerHtml(node.html());
  };

  $.ajax({
    url: '/api/proxy/partners/activator',
    success: onGotHtml,
    error: onError,
    dataType: 'html'
  });

  return {
    render: function(url){
      layout.renderPlugin(ko.bindhtml(tpl, State));
    },

    route: function(url, breadcrumb){
      var all = [
        ['partners/', "Partners"]
      ];
      if(url.parameters[0]){
        breadcrumb(all);
      } else {
        breadcrumb(all);
      }
    }
  };
});
