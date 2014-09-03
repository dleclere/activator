/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define([
  'commons/settings',
  'lib/knockout/knockout',
  'lib/ace/src/ace',
  'commons/markers',
  'css!./theme.css'],
  function(settings, ko, ignore_ace, markers) {

  // Create Editor container
  var editorDom = document.createElement('div');

  // Create the actual editor instance once and for all
  var editor = ace.edit(editorDom);

  // Store all Editor Sessions (cursor position, scroll, undo...)
  var savedSession = {};

  var aceThemes = {
    Dark: 'ace/theme/solarized_dark',
    Light: 'ace/theme/solarized_light'
  };
  var theme = settings.observable("editor.theme", "Dark");
  var fontSize = settings.observable("editor.fontSize", "12");
  editor.setTheme(aceThemes[theme()] || aceThemes["Dark"]);
  editor.setFontSize(fontSize());
  theme.subscribe(function(v) {
    editor.setTheme(aceThemes[v])
  });
  fontSize.subscribe(function(v) {
    editor.setFontSize(v)
  });

  function refreshFileMarkers(editor, markers) {
    var annotations = [];
    $.each(markers, function(idx, m) {
      // m.kind is supposed to match what we use for log levels,
      // i.e. info, warn, error; need to convert to ace which is
      // info, warning, error.
      var aceLevel = 'info';
      if (m.kind == 'error')
        aceLevel = 'error';
      else if (m.kind == 'warn')
        aceLevel = 'warning';
      annotations.push({ row: m.line - 1, column: 0, text: m.message, type: aceLevel });
    });

    editor.getSession().clearAnnotations();
    editor.getSession().setAnnotations(annotations);
  }

  // Add knockout bindings for ace editor.  Try to capture all info we need
  // here so we don't have to dig in like crazy when we need a good editor.
  // Example:
  //  <div class="editor" data-bind="ace: contents"/>
  // <div class="editor" data-bind="ace: { contents: contents, theme: 'ace/theme/xcode', dirty: isEditorDirty, highlight: 'scala', file: filemodel }"/>
  ko.bindingHandlers.ace = {
    init: function (element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
      // Append editor
      element.appendChild(editor.container);
    },
    update: function (element, valueAccessor, allBindingsAccessor, viewModel) {
      var options = valueAccessor();
      var editorValue = options.contents;
      var dirtyValue = options.dirty;
      var content = ko.utils.unwrapObservable(editorValue);
      var file = ko.utils.unwrapObservable(ko.utils.unwrapObservable(options.file).relative);
      var location = ko.utils.unwrapObservable(viewModel.file).location;
      var highlight = ko.utils.unwrapObservable(options.highlight || 'text');

      if (content == undefined) return 0;
      if (!savedSession[location]){
        savedSession[location] = new ace.EditSession(content, 'ace/mode/'+highlight);
        savedSession[location].setUndoManager(new ace.UndoManager());
      } else if(content != editor.getValue()){
        editor.setValue(content);
        editor.clearSelection();
      }
      editor.setSession(savedSession[location]);

      var line = viewModel.focusLine();
      if (line !== null) {
        editor.moveCursorTo(line - 1, 0);
        editor.scrollToRow(line - 1);
        viewModel.focusLine(null);
      }

      editor.focus();

      var fileMarkers = markers.ensureFileMarkers(file);
      var oldMarkers = null;
      var markersSub = null;
      if ('fileMarkers' in editor) {
        oldMarkers = editor.fileMarkers;
        markersSub = editor.fileMarkersSub;
      }
      // when file changes, subscribe to the new markers array
      if (fileMarkers !== oldMarkers) {
        if (markersSub !== null) {
          debug && console.log("editor dropping watch on old file markers: ", oldMarkers());
          markersSub.dispose();
        }
        debug && console.log("editor watching file markers for " + file + ": ", fileMarkers());
        editor.fileMarkers = fileMarkers;
        editor.fileMarkersSub = fileMarkers.subscribe(function(newMarkers) {
          refreshFileMarkers(editor, newMarkers);
        });
        // initially load the file markers
        refreshFileMarkers(editor, fileMarkers());
      }

      //handle edits made in the editor
      editor.getSession().on('change', function (e) {
        if (ko.isWriteableObservable(editorValue)) {
          editorValue(editor.getValue());
        }
        // mark things dirty after an edit.
        if(ko.isWriteableObservable(dirtyValue)) {
          dirtyValue(true);
        }
      });

    }
  };
  return {
    theme: theme,
    fontSize: fontSize
  };
});
