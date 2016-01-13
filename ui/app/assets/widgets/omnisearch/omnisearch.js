/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'services/search',
  'text!./omnisearch.html',
  'css!./omnisearch'
],function(
  search,
  tpl
) {

  var searchString = ko.observable("").extend({ throttle: 200 }),
      pendingQueries = ko.observable(0),
      active = ko.observable(false),
      options = ko.observable([]).extend({ notify: 'always' }),
      selected = ko.observable(null),
      empty = ko.computed(function() {
        return searchString().length >= 2 && options().length === 0;
      });

  options.subscribe(function(opts) {
    pendingQueries(pendingQueries()-1);
    if (!selected() || opts.filter(function(a){ return a.subtitle && a.subtitle === selected().subtitle; }).length === 0) {
      selected(opts[1] || null);
    }
  });

  searchString.subscribe(function(keywords) {
    // Don't search until at least two characters are entered and search string isn't the same as last
    pendingQueries(pendingQueries()+1);
    if (keywords.length >= 2) {
      search.combinedSearch(keywords, options);
    } else {
      options([]);
      pendingQueries(0);
    }
  });

  function scrollToSelected(){
    var $omnisearch = $('#omnisearch ul');
    var $selected = $omnisearch.find('li.selected');
    if (typeof $selected.position() !== 'undefined') {
      if ($selected.position().top < 0) {
        $omnisearch.scrollTop($omnisearch.scrollTop() + $selected.position().top);
      } else if ($selected.position().top + $selected.outerHeight() >= $omnisearch.height()) {
        $omnisearch.scrollTop($omnisearch.scrollTop() + $selected.position().top + $selected.outerHeight() - $omnisearch.height());
      }
    }
  }

  var State = {

    options: options,
    pendingQueries: pendingQueries,
    selected: selected,
    active: active,
    empty: empty,
    searchString: searchString,

    keyDown: function repeat(state,e) {
      var index = options().indexOf(selected());
      // Up
      if (e.keyCode === 38) {
        e.preventDefault();
        if (index > 0) {
          selected(options()[index - 1]);
        } else {
          selected(options()[options().length - 1]);
        }
        if (selected() && !selected().subtitle) repeat(state,e)
        scrollToSelected();
        return false;
      }
      // Down
      else if (e.keyCode === 40) {
        e.preventDefault();
        if (index < options().length - 1) {
          selected(options()[index + 1]);
        } else {
          selected(options()[0]);
        }
        if (selected() && !selected().subtitle) repeat(state,e)
        scrollToSelected();
        return false;
      }
      // autocomplete on TAB
      else if (e.keyCode === 9) {
        e.target.value = selected().execute;
        return false;
      }
      else return true;
    },

    keyUp: function(state,e) {
      switch (e.keyCode) {
        // ignore these:
        case 38:
        case 40:
          return;
        // Escape
        case 27:
          e.target.blur();
          break;
        // Return
        case 13:
          e.preventDefault();
          var selectedItem = selected();
          if (selectedItem) {
            State.exec(selectedItem);
            e.target.blur();
          }
          break;
      }
    },

    focus: function() {
      active(true);
    },

    blur: function() {
      // Delay hiding of omnisearch list to catch mouse click on list before it disappears
      setTimeout(function(){
        active(false);
      }, 100);
    },

    exec: function(option) {
      debug && console.log(option)
      option.callback(option);
      options([]);
      selected(null);
      searchString("");
      document.body.focus();
    }
  }

  return ko.bindhtml(tpl, State);

});
