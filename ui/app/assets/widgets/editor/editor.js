/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "ace/ace",
  "text!./editor.html",
  "commons/settings",
  "css!./editor",
  "css!./themes/activator-dark",
  "css!./themes/activator-light"
], function(
  ace,
  tpl,
  settings
) {


  // Create the editor instance once and for all
  var editor = ace.edit(document.createElement('div'));

  // Theme and font
  var themes = {
    "Activator Light": 'ace/theme/activator-light',
    "Activator Dark": 'ace/theme/activator-dark',
    "Ambiance": "ace/theme/ambiance",
    "Chaos": "ace/theme/chaos",
    "Chrome": "ace/theme/chrome",
    "Clouds Midnight": "ace/theme/clouds_midnight",
    "Clouds": "ace/theme/clouds",
    "Cobalt": "ace/theme/cobalt",
    "Crimson Editor": "ace/theme/crimson_editor",
    "Dawn": "ace/theme/dawn",
    "Dreamweaver": "ace/theme/dreamweaver",
    "Eclipse": "ace/theme/eclipse",
    "Github": "ace/theme/github",
    "Idle Fingers": "ace/theme/idle_fingers",
    "Katzenmilch": "ace/theme/katzenmilch",
    "Kr Theme": "ace/theme/kr_theme",
    "Kuroir": "ace/theme/kuroir",
    "Merbivore Soft": "ace/theme/merbivore_soft",
    "Merbivore": "ace/theme/merbivore",
    "Mono Industrial": "ace/theme/mono_industrial",
    "Monokai": "ace/theme/monokai",
    "Pastel on Dark": "ace/theme/pastel_on_dark",
    "Solarized Dark": "ace/theme/solarized_dark",
    "Solarized Light": "ace/theme/solarized_light",
    "Terminal": "ace/theme/terminal",
    "Textmate": "ace/theme/textmate",
    "Tomorrow Night Blue": "ace/theme/tomorrow_night_blue",
    "Tomorrow Night Bright": "ace/theme/tomorrow_night_bright",
    "Tomorrow Night Eighties": "ace/theme/tomorrow_night_eighties",
    "Tomorrow Night": "ace/theme/tomorrow_night",
    "Tomorrow": "ace/theme/tomorrow",
    "Twilight": "ace/theme/twilight",
    "Vibrant Ink": "ace/theme/vibrant_ink",
    "Xcode": "ace/theme/xcode"
  };

  var chosenTheme = settings.observable("code.theme", Object.keys(themes)[0]);
  ko.doOnChange(chosenTheme, function(t) {
    editor.setTheme(themes[t]);
  });

  var fontSizes = {
    "Small": "12px",
    "Medium": "14px",
    "Large": "17px",
    "XLarge": "22px"
  };
  var chosenFontSize = settings.observable("code.fontSize", Object.keys(fontSizes)[0]);
  ko.doOnChange(chosenFontSize, function(t) {
    editor.container.style.fontSize = fontSizes[t];
  });

  // ------------------------ NOTE ------------------------
  // TAB SIZE and SOFT TABS are defined in `plugins/code/document.js`
  // Because ace editor requires them to be attached to an EditSession
  // ------------------------------------------------------

  var State = (function () {
    var self = {};
    self.themes = Object.keys(themes);
    self.chosenTheme = chosenTheme;
    self.fontSizes = Object.keys(fontSizes);
    self.chosenFontSize = chosenFontSize;
    self.editorContainer = editor.container;
    self.selectedDocument = ko.observable(null);
    self.hasSelectedDocument = ko.computed(function () {
      return self.selectedDocument() !== null;
    });
    return self;
  })();

  return {
    themes: Object.keys(themes),
    chosenTheme: chosenTheme,
    fontSizes: Object.keys(fontSizes),
    chosenFontSize: chosenFontSize,
    editorContainer: editor.container,

    setDocument: function(sd) {
      sd.subscribe(function (doc) {
        console.log("got doc: ",doc);
        State.selectedDocument(doc);
        if (doc && doc.isText) {
          if (doc.session !== editor.getSession()){
            editor.setSession(doc.session);
          }
          if (doc.lineNumber) {
            setTimeout(function() {
              editor.gotoLine(doc.lineNumber);
            },1);
          }
          editor.focus();
        }
      });
    },

    render: function() {
      return ko.bindhtml(tpl, State);
    }
  }

});
