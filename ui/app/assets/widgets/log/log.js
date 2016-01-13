/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['services/log','text!./log.html', 'commons/widget', 'commons/utils'], function(Log, template, Widget, utils){

  var LogView = utils.Class(Widget, {
    id: 'log-widget',
    template: template,
    init: function(log) {
      var self = this;

      if (!(log instanceof Log))
        throw new Error("Must provide a log to LogView, not " + typeof(log) + " " + log + " arguments=" + arguments);

      this.log = log;
      this.node = null;

      this.savedScrollState = null;
      // this subscription would need cleanup if we ever have
      // a LogView which can be destroyed.
      this.log.scrollFreeze.subscribe(function(newCount) {
        if (newCount === 1) {
          self.savedScrollState = self.findScrollState();
        } else if (newCount === 0 && self.savedScrollState !== null) {
          self.applyScrollState(self.savedScrollState);
          self.savedScrollState = null;
        }
      });
    },
    onRender: function(childNodes) {
      this.node = $(childNodes).parent();

      // force scroll to bottom to start, in case
      // when we render we already have a page full
      // of lines.
      var state = this.findScrollState();
      state.wasAtBottom = true;
      this.applyScrollState(state);
    },
    findScrollElement: function() {
      var element = null;
      if (this.node !== null) {
        // Look for the node that we intend to have the scrollbar.
        // If no 'auto' node found, just use our own node
        // (which is probably wrong).
        var logsListNode = $(this.node).children('ul.logsList')[0];
        var parents = [ logsListNode ].concat($(logsListNode).parents().get());
        element = parents[0];
        var i = 0;
        for (; i < parents.length; ++i) {
          var scrollMode = $(parents[i]).css('overflowY');
          if (scrollMode === 'auto' || scrollMode === 'scroll') {
            element = parents[i];
            break;
          }
        }
      }
      return element;
    },
    findScrollState: function() {
      var element = this.findScrollElement();
      var state = { wasAtBottom: true };
      if (element !== null) {
        // if we're within twenty pixels of the bottom, stick to the bottom;
        // if we require being *exactly* at the bottom it can feel like it's
        // too hard to get there.
        state.wasAtBottom = (element.scrollHeight - element.clientHeight - element.scrollTop) < 20;
        state.scrollTop = element.scrollTop;
      }
      return state;
    },
    applyScrollState: function(state) {
      var element = this.findScrollElement();
      if (element !== null) {
        if (state.wasAtBottom) {
          // stay on the bottom if we were on the bottom
          element.scrollTop = (element.scrollHeight - element.clientHeight);
        } else {
          element.scrollTop = state.scrollTop;
        }
      }
    }
  });

  return LogView;
});
