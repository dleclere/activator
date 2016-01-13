/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'css!./contextmenu'
], function() {

  ko.bindingHandlers.contextmenu = {
    init: function(element, valueAccessor, allBindings, viewModel, bindingContext) {
      var links = valueAccessor();

      element.addEventListener('contextmenu',function(event) {
        event.stopPropagation();
        event.preventDefault();

        if (event.target.className === 'contextmenu') return;

        var dom = $("<div class='contextmenu'></div>");
        dom.css({
          left: (event.clientX - 5)+"px" ,
          top: (event.clientY + 10)+"px"
        })
        dom.click(function(e) {
          e.preventDefault();
          e.stopPropagation();
          dom.remove();
        })
        $.each(links, function(name, callback) {
          $("<a/>").text(name).click(callback).appendTo(dom);
        })
        dom.appendTo(event.currentTarget);
      });
    }
  }

})
